package com.pulseloop.emotion

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.pulseloop.MainActivity
import java.util.Calendar
import kotlin.math.abs

/**
 * The app's touch player while it runs (set by the app with the calm cue hooks), so a reminder
 * can play on the ring, or on the watch when no ring is connected.
 */
object CueHooks {
    @Volatile var touches: RingTouches? = null
}

/**
 * Plans reminder times and delivers them. Port of iOS `ReminderScheduler` + `CueDispatcher`.
 *
 * Planning: the next 3 days of cues, from each enabled rule (spread through a window, or at set
 * times), at most `dailyCap` a day, spread cues never in quiet hours.
 *
 * Delivery: one alarm for the next cue (inexact, allowed while idle, so it can land a few minutes
 * off, like on iPhone). When it fires the app plays the cue's touches (ring first, then watch, see
 * [RingTouches.route]) if it's running, and always posts a notification with the cue prompt,
 * unless the watch delivers the reminders itself ("Reminders on the watch"). Due cues are also
 * checked whenever the app is open. Each cue plays at most once; cues more than 15 min late are
 * missed; if several are due at once only the newest plays.
 */
object ReminderScheduler {
    const val CHANNEL_ID = "kinnora_cues"
    /** `EmotionIntents.pending` value that opens the check-in: "cue:<event id>". */
    const val CUE_PREFIX = "cue:"
    const val GRACE_MS = 15 * 60_000L
    private const val NOTIFICATION_ID = 4004
    private const val HISTORY_DAYS = 14

    /** The notification title and text for a cue (also sent to the watch). */
    fun cueText(feeling: Feeling, experience: Experience?): Pair<String, String> {
        if (experience != null) {
            val arc = experience.feelings.joinToString(", then ") { it.label.lowercase() }
            val picture = experience.visionPoints.firstOrNull()?.let { "Picture it: $it. " } ?: ""
            return experience.title.ifBlank { "Your vision" } to "${picture}Feel $arc."
        }
        return "${feeling.label} cue" to feeling.cuePrompt
    }

    /** Start of the day containing [millis], plus [offsetDays]. */
    fun startOfDay(millis: Long, offsetDays: Int = 0): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_YEAR, offsetDays)
    }.timeInMillis

    /** Spread (or set) times for one rule on one day, respecting window and quiet hours. */
    fun plannedTimes(rule: ReminderRule, dayStart: Long, profile: EmotionProfile): List<Long> {
        val cal = Calendar.getInstance().apply { timeInMillis = dayStart }
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        if (rule.weekdaysOnly && (dow == Calendar.SATURDAY || dow == Calendar.SUNDAY)) return emptyList()
        if (!rule.randomTimes && rule.fixedDays.size == 7) {
            val mondayFirst = (dow + 5) % 7
            if (!rule.fixedDays[mondayFirst]) return emptyList()
        }
        fun allowed(minute: Int): Boolean {
            val qs = profile.quietStartMinutes
            val qe = profile.quietEndMinutes
            val inQuiet = if (qs > qe) minute >= qs || minute < qe else minute in qs until qe
            return !inQuiet
        }
        val minutes: List<Int> = if (rule.randomTimes) {
            val span = maxOf(1, rule.windowEndMinutes - rule.windowStartMinutes)
            val slot = span / maxOf(1, rule.perDay)
            // Deterministic per rule + day, so re-planning the same day gives the same times.
            var seed = (rule.id.hashCode().toLong() and 0xffffffffL) + dayStart / 1000
            (0 until rule.perDay).map { i ->
                seed = seed * 6364136223846793005L + 1442695040888963407L
                val jitter = ((seed ushr 33) % maxOf(1, slot - 10)).toInt()
                rule.windowStartMinutes + i * slot + 5 + jitter
            }
        } else {
            rule.fixedMinutes
        }
        // Set times are exactly what the person chose; quiet hours only shape spread reminders.
        return minutes.filter { !rule.randomTimes || allowed(it) }.sorted().map { dayStart + it * 60_000L }
    }

    /** Rebuild the next 3 days of reminders and the alarm for the next one. */
    fun rebuild(context: Context) {
        val data = EmotionData.get(context)
        val s = data.state.value
        val profile = s.profile
        val now = System.currentTimeMillis()
        val planned = mutableListOf<Pair<ReminderRule, Long>>()
        for (offset in 0 until 3) {
            val day = startOfDay(now, offset)
            planned += s.rules.filter { it.enabled }
                .flatMap { rule -> plannedTimes(rule, day, profile).map { rule to it } }
                .filter { it.second > now }
                .sortedBy { it.second }
                .take(profile.dailyCap)
        }
        val until = profile.pausedUntil
        val kept = if (profile.remindersPaused && until != null) planned.filter { it.second > until } else planned
        val historyFrom = now - HISTORY_DAYS * 86_400_000L
        data.update { file ->
            val history = file.events.filter { (it.status != "scheduled" || it.scheduledAt <= now) && it.scheduledAt >= historyFrom }
            val fresh = kept.map { (rule, at) ->
                ReminderEvent(ruleId = rule.id, feelingRaw = rule.feelingRaw, scheduledAt = at, experienceId = rule.experienceId)
            }
            file.copy(events = (history + fresh).sortedBy { it.scheduledAt })
        }
        // A pause that has ended.
        if (profile.remindersPaused && until != null && until <= now) {
            data.updateProfile { it.copy(remindersPaused = false, pausedUntil = null) }
        }
        scheduleNext(context)
    }

    /** One alarm for the next scheduled cue (replaces the previous one). */
    fun scheduleNext(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = alarmIntent(context)
        am.cancel(pi)
        val now = System.currentTimeMillis()
        val next = EmotionData.get(context).state.value.events
            .filter { it.status == "scheduled" && it.scheduledAt > now }
            .minByOrNull { it.scheduledAt } ?: return
        // Inexact on purpose: no exact-alarm permission. Allowed while idle, so it still fires
        // in Doze, possibly a few minutes late.
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.scheduledAt, pi)
    }

    private fun alarmIntent(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, 4004,
            Intent(context, ReminderAlarmReceiver::class.java).setAction("com.pulseloop.emotion.CUE"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** Delivers due cues. [via] is how we got here ("alarm", "app open"). */
    fun deliverDue(context: Context, via: String) {
        val data = EmotionData.get(context)
        val now = System.currentTimeMillis()
        val due = data.state.value.events.filter { it.status == "scheduled" && it.scheduledAt <= now + 30_000 }
        if (due.isEmpty()) {
            scheduleNext(context)
            return
        }
        val fresh = due.filter { now - it.scheduledAt <= GRACE_MS }
        val newest = fresh.maxByOrNull { it.scheduledAt }
        // Too late, or replaced by a newer cue.
        due.filter { it.id != newest?.id }.forEach { e ->
            data.updateEvent(e.id) {
                it.copy(status = "missed", deliveredVia = if (now - e.scheduledAt > GRACE_MS) "too late, the phone was asleep" else "a newer cue played instead")
            }
        }
        if (newest != null) deliver(context, newest, via)
        scheduleNext(context)
    }

    private fun deliver(context: Context, event: ReminderEvent, via: String) {
        val data = EmotionData.get(context)
        val now = System.currentTimeMillis()
        val experience = EmotionStore.get(context).get(event.experienceId)
        val pattern = experience?.touchSequence ?: event.feeling.touch
        val watchHasIt = WatchLink.takesOverReminders
        val touches = CueHooks.touches
        val played = when {
            touches == null -> null
            // The watch plays its own copy; the ring still plays first when it's connected.
            watchHasIt -> if (touches.ringActive) { touches.play(pattern); "ring" } else "watch"
            touches.canDeliverTouch -> { touches.play(pattern); if (touches.ringActive) "ring" else "watch" }
            else -> null
        }
        val how = played ?: if (watchHasIt) "watch" else "notification"
        data.updateEvent(event.id) {
            it.copy(
                status = "delivered", deliveredAt = now, deliveredVia = "$how · $via",
                delaySeconds = ((now - event.scheduledAt) / 1000).toInt().coerceAtLeast(0), attempts = it.attempts + 1,
            )
        }
        if (!watchHasIt) postNotification(context, event, experience)
    }

    private fun postNotification(context: Context, event: ReminderEvent, experience: Experience?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Reminder cues", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Your feeling reminders through the day"
                },
            )
        }
        val (title, body) = cueText(event.feeling, experience)
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(CalmCue.EXTRA_OPEN, CUE_PREFIX + event.id)
        }
        val pending = PendingIntent.getActivity(context, NOTIFICATION_ID, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
    }

    /** "Pause 1 h" / "Resume" / "Mute today". */
    fun setPaused(context: Context, until: Long?) {
        EmotionData.get(context).updateProfile { it.copy(remindersPaused = until != null, pausedUntil = until) }
        rebuild(context)
    }
}

/** Fires at a cue's time. */
class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        ReminderScheduler.deliverDue(context.applicationContext, "alarm")
    }
}

/** Alarms are cleared on reboot, update and clock changes: plan again. */
class ReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
            -> ReminderScheduler.rebuild(context.applicationContext)
        }
    }
}

/** "On time", "4 min late", "Missed · …" or "Scheduled" (iOS `cueStatusText`). */
fun cueStatusText(e: ReminderEvent): String = when (e.status) {
    "delivered" -> {
        val late = (e.delaySeconds ?: 0) / 60
        val timing = if (late < 2) "On time" else "$late min late"
        if (e.feltRating != null) "$timing · you checked in" else timing
    }
    "missed" -> "Missed · " + (e.deliveredVia ?: "not delivered")
    else -> if (e.scheduledAt > System.currentTimeMillis()) "Scheduled" else "Due · waiting for the app to wake"
}

fun hm(minutes: Int): String = "%02d:%02d".format(abs(minutes) / 60 % 24, abs(minutes) % 60)

/** "Every day", "Weekdays", "Weekends" or "Mon, Wed, Fri". */
fun daysLabel(days: List<Boolean>): String {
    val names = listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val d = if (days.size == 7) days else List(7) { true }
    return when {
        d.all { it } -> "Every day"
        d == WEEKDAYS -> "Weekdays"
        d == WEEKENDS -> "Weekends"
        d.none { it } -> "No days picked"
        else -> names.indices.filter { d[it] }.joinToString(", ") { names[it] }
    }
}

val WEEKDAYS = listOf(true, true, true, true, true, false, false)
val WEEKENDS = listOf(false, false, false, false, false, true, true)
