package com.pulseloop.emotion

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.pulseloop.MainActivity
import com.pulseloop.data.PulseLoopDatabase
import com.pulseloop.data.entity.MeasurementEntity
import com.pulseloop.ring.MeasurementKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

/**
 * Calm cue (port of iOS `CalmCueMonitor`): when you've been still for a while and your ring's
 * readings look tense compared with your normal, the ring plays the slow "Slow down" touch and a
 * notification offers a short 4-in / 7-out breath.
 *
 * Rules (all must hold):
 * - still for 15 minutes: no workout recording or ended in that time, and at most 40 steps —
 *   so a walk or run never counts, and after an activity it waits until you've settled;
 * - a fresh reading (last 15 min) is raised: the ring's stress score, heart rate above your
 *   resting level, or HRV below your usual;
 * - your normal is known (3+ days of heart readings);
 * - at most once every 2 hours, 4 a day, never in quiet hours.
 *
 * Checked when ring data is saved, when the app comes to the foreground, and by the background
 * ring sync (RingSyncWorker), so it reacts when new data arrives.
 */
object CalmCue {
    enum class Level(val label: String) { NONE("normal"), ELEVATED("raised"), HIGH("high") }

    data class Baseline(val days: Int = 0, val restingHr: Double? = null, val hrvMedian: Double? = null) {
        /** Any of heart rate, HRV or stress counts: some rings send HRV but no heart-rate history. */
        val ready: Boolean get() = days >= MIN_BASELINE_DAYS
    }

    const val STILL_MINUTES = 15
    const val STEPS_ALLOWANCE = 40
    const val READING_WINDOW_MINUTES = 15
    const val COOLDOWN_HOURS = 2
    const val MAX_PER_DAY = 4
    const val MIN_BASELINE_DAYS = 3
    private const val CHANNEL_ID = "calm_cue"
    const val EXTRA_OPEN = "emotion_open"
    const val OPEN_CALM_BREATH = "calm_breath"

    /** Plays a touch on the app's ring connection; set by the app while it's running. */
    @Volatile var buzzer: ((TouchPattern) -> Unit)? = null
    @Volatile var isRingConnected: () -> Boolean = { false }
    @Volatile var reconnect: () -> Unit = {}
    /** One spot heart-rate reading from the app's ring connection (null when it can't). */
    @Volatile var measureHr: (suspend () -> Int?)? = null
    @Volatile var canMeasureHr: () -> Boolean = { false }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val mutex = Mutex()
    private var lastEvaluation = 0L
    private var lastLoggedOutcome = ""
    private var baselineCache: Pair<Long, Baseline>? = null
    /** A spot heart-rate reading taken for the check when the ring sent no recent heart rate. */
    private var spotHr: Pair<Double, Long>? = null
    @Volatile private var measuringSpot = false

    private val _lastAssessment = MutableStateFlow("Not checked yet")
    val lastAssessment: StateFlow<String> = _lastAssessment.asStateFlow()
    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log.asStateFlow()

    fun prefs(context: Context): SharedPreferences = context.getSharedPreferences("emotion", Context.MODE_PRIVATE)

    fun isEnabled(context: Context) = prefs(context).getBoolean("calm_enabled", false)
    fun setEnabled(context: Context, on: Boolean) = prefs(context).edit().putBoolean("calm_enabled", on).apply()
    /** "elevated" (raised or high readings) or "high" (high only). */
    fun sensitivity(context: Context): String = prefs(context).getString("calm_sensitivity", "elevated") ?: "elevated"
    fun setSensitivity(context: Context, value: String) = prefs(context).edit().putString("calm_sensitivity", value).apply()

    fun loadLog(context: Context) {
        _log.value = readList(prefs(context), "calm_log")
    }

    fun clearLog(context: Context) {
        prefs(context).edit().remove("calm_log").apply()
        _log.value = emptyList()
    }

    /** Fire-and-forget check (coalesced to once a minute unless [force]). */
    fun evaluateLater(context: Context, trigger: String, force: Boolean = false) {
        val app = context.applicationContext
        scope.launch { evaluate(app, trigger, force) }
    }

    /**
     * Check now. [buzz] overrides how the touch is played (the background worker passes its own
     * ring connection); otherwise the app's [buzzer] is used.
     */
    suspend fun evaluate(context: Context, trigger: String, force: Boolean = false, buzz: ((TouchPattern) -> Unit)? = null, ringConnected: (() -> Boolean)? = null) {
        mutex.withLock {
            val now = System.currentTimeMillis()
            if (!force && now - lastEvaluation < 60_000) return
            lastEvaluation = now
            val db = PulseLoopDatabase.getInstance(context)
            recordStepSnapshot(context, db)
            if (!isEnabled(context)) { _lastAssessment.value = "Calm cue is off"; return }

            val baseline = baseline(db, now)
            if (!baseline.ready) { outcome(context, trigger, "learning your normal (${baseline.days} of $MIN_BASELINE_DAYS days)"); return }
            stillness(context, db, now)?.let { outcome(context, trigger, it); return }

            // No recent heart rate from the ring's history: take one spot reading (at most every 10 min).
            if (needsSpotHr(db, now)) {
                measuringSpot = true
                outcome(context, trigger, "still · measuring heart rate")
                scope.launch {
                    val v = runCatching { measureHr?.invoke() }.getOrNull()
                    measuringSpot = false
                    spotHr = (v ?: 0).toDouble() to System.currentTimeMillis()
                    if (v != null) evaluate(context, "spot heart rate", force = true)
                }
                return
            }
            val (level, reason) = assess(db, now, baseline)
            val needed = if (sensitivity(context) == "high") Level.HIGH else Level.ELEVATED
            if (level < needed) {
                outcome(context, trigger, "still · readings ${level.label}${if (reason.isEmpty()) "" else " ($reason)"}")
                return
            }
            blocked(context, now)?.let { outcome(context, trigger, "tense ($reason) but $it"); return }
            fire(context, reason, level, trigger, buzz ?: buzzer, ringConnected ?: isRingConnected)
        }
    }

    /** "Feel it" / developer test: play the cue (and optionally notify), skipping every check. */
    fun playTest(context: Context, withNotification: Boolean) {
        buzzer?.invoke(TouchPattern.CALM_CUE)
        if (withNotification) postNotification(context, test = true)
        append(context, "test · played calm cue${if (withNotification) " + notification" else ""}")
    }

    private fun fire(context: Context, reason: String, level: Level, trigger: String, buzz: ((TouchPattern) -> Unit)?, connected: () -> Boolean) {
        val p = prefs(context)
        val now = System.currentTimeMillis()
        val sent = readLongs(p, "calm_sent").plus(now).takeLast(20)
        p.edit().putLong("calm_last", now).putString("calm_sent", JSONArray(sent).toString()).apply()
        if (buzz != null && connected()) {
            buzz(TouchPattern.CALM_CUE)
            append(context, "$trigger · SENT calm cue · ${level.label}: $reason")
        } else {
            append(context, "$trigger · tense ($reason) · ring not connected, notification only")
            reconnect()
            scope.launch {
                delay(20_000)
                if (isRingConnected()) {
                    buzzer?.invoke(TouchPattern.CALM_CUE)
                    append(context, "retry · SENT calm cue after reconnecting")
                }
            }
        }
        postNotification(context, test = false)
        _lastAssessment.value = "Calm cue sent ${time(now)} · $reason"
    }

    fun postNotification(context: Context, test: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Calm cue", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "A short breathing break when your ring's readings look tense"
            }
            (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN, OPEN_CALM_BREATH)
        }
        val pending = PendingIntent.getActivity(context, 3003, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(if (test) "[Test] Feeling tense?" else "Feeling tense?")
            .setContentText("Your ring played a slow-down touch. Take a minute: in for 4, out for 7.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Your ring played a slow-down touch. Take a minute: breathe in for 4, out for 7."))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        NotificationManagerCompat.from(context).notify(3003, notification)
    }

    private fun blocked(context: Context, now: Long): String? {
        val p = prefs(context)
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val minute = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val qs = p.getInt("quiet_start", 21 * 60)
        val qe = p.getInt("quiet_end", 8 * 60)
        val quiet = if (qs > qe) minute >= qs || minute < qe else minute in qs until qe
        if (quiet) return "quiet hours"
        val last = p.getLong("calm_last", 0L)
        if (last > 0 && now - last < COOLDOWN_HOURS * 3_600_000L) return "last calm cue was ${(now - last) / 60_000} min ago"
        val today = startOfDay(now)
        if (readLongs(p, "calm_sent").count { it >= today } >= MAX_PER_DAY) return "already $MAX_PER_DAY today"
        return null
    }

    /** Null when still; otherwise why not (moving, or can't tell). */
    private suspend fun stillness(context: Context, db: PulseLoopDatabase, now: Long): String? {
        val since = now - STILL_MINUTES * 60_000L
        val sessions = db.activitySessionDao()
        if (sessions.active() != null) return "not still · workout in progress"
        sessions.recent(5).firstOrNull { (it.endedAt ?: 0L) > since }?.let {
            return "not still · workout ended ${(now - (it.endedAt ?: now)) / 60_000} min ago"
        }
        val snaps = readSnaps(prefs(context))
        val fresh = snaps.lastOrNull()
        if (fresh == null || now - fresh.first > 10 * 60_000L) return "can't tell if still · no fresh step count from the ring"
        val old = snaps.lastOrNull { it.first <= since && now - it.first < 90 * 60_000L }
            ?: return "can't tell if still · step history covers less than $STILL_MINUTES min"
        val delta = if (startOfDay(old.first) == startOfDay(fresh.first)) fresh.second - old.second else fresh.second
        if (delta > STEPS_ALLOWANCE) return "not still · $delta steps since ${time(old.first)}"
        return null
    }

    private suspend fun needsSpotHr(db: PulseLoopDatabase, now: Long): Boolean {
        if (measuringSpot || measureHr == null || !canMeasureHr()) return false
        spotHr?.let { if (now - it.second < 10 * 60_000L) return false }
        return readings(db, MeasurementKind.HEART_RATE, now - READING_WINDOW_MINUTES * 60_000L, now).size < 2
    }

    /** Latest heart rate, HRV and stress the ring has sent: (name, value, epoch millis or null). */
    suspend fun latestReadings(context: Context): List<Triple<String, String, Long?>> {
        val db = PulseLoopDatabase.getInstance(context)
        val now = System.currentTimeMillis()
        val week = now - 7 * 86_400_000L
        fun row(name: String, m: MeasurementEntity?, unit: String) =
            Triple(name, m?.let { "${it.value.toInt()}$unit" } ?: "none in 7 days", m?.timestamp)
        return listOf(
            row("Heart rate", readings(db, MeasurementKind.HEART_RATE, week, now).lastOrNull(), " bpm"),
            row("HRV", readings(db, MeasurementKind.HRV, week, now).lastOrNull(), " ms"),
            row("Stress", readings(db, MeasurementKind.STRESS, week, now).lastOrNull(), ""),
        )
    }

    private suspend fun assess(db: PulseLoopDatabase, now: Long, baseline: Baseline): Pair<Level, String> {
        val since = now - READING_WINDOW_MINUTES * 60_000L
        var level = Level.NONE
        val reasons = mutableListOf<String>()
        fun raise(l: Level) { if (l > level) level = l }

        val stress = readings(db, MeasurementKind.STRESS, since, now)
        stress.lastOrNull()?.value?.let { s ->
            // Ring stress score 0–100 (Colmi app bands: 60–79 medium, 80+ high).
            val l = if (s >= 80) Level.HIGH else if (s >= 60) Level.ELEVATED else Level.NONE
            if (l > Level.NONE) reasons += "stress ${s.toInt()}"
            raise(l)
        }
        var hr = readings(db, MeasurementKind.HEART_RATE, since, now).map { it.value }.filter { it in 35.0..200.0 }
        val spot = spotHr
        val usedSpot = hr.size < 2 && spot != null && now - spot.second < READING_WINDOW_MINUTES * 60_000L && spot.first >= 35
        if (usedSpot && spot != null) hr = listOf(spot.first)
        val rest = baseline.restingHr
        if ((hr.size >= 2 || usedSpot) && rest != null) {
            val low = hr.min()
            val over = low - rest
            val l = if (over >= 25) Level.HIGH else if (over >= 15) Level.ELEVATED else Level.NONE
            if (l > Level.NONE) reasons += "heart ${low.toInt()}+ bpm vs ${rest.toInt()} resting"
            raise(l)
        }
        val median = baseline.hrvMedian
        val hrv = readings(db, MeasurementKind.HRV, now - 30 * 60_000L, now).lastOrNull()?.value
        if (hrv != null && median != null && median > 0) {
            val ratio = hrv / median
            val l = if (ratio <= 0.6) Level.HIGH else if (ratio <= 0.75) Level.ELEVATED else Level.NONE
            if (l > Level.NONE) reasons += "HRV ${hrv.toInt()} vs usual ${median.toInt()} ms"
            raise(l)
        }
        if (stress.isEmpty() && hr.isEmpty() && reasons.isEmpty()) reasons += "no fresh readings"
        return level to reasons.joinToString(", ")
    }

    /** Resting heart rate (20th percentile) and usual HRV (median) over the last 7 days. */
    suspend fun baseline(db: PulseLoopDatabase, now: Long = System.currentTimeMillis()): Baseline {
        baselineCache?.let { (at, value) -> if (now - at < 3_600_000L) return value }
        val since = now - 7 * 86_400_000L
        val hr = readings(db, MeasurementKind.HEART_RATE, since, now).filter { it.value in 35.0..200.0 }
        val any = hr + readings(db, MeasurementKind.HRV, since, now) + readings(db, MeasurementKind.STRESS, since, now)
        val days = any.map { startOfDay(it.timestamp) }.toSet().size
        val resting = if (hr.size >= 20) hr.map { it.value }.sorted().let { it[(it.size * 0.2).toInt()] } else null
        val hrv = readings(db, MeasurementKind.HRV, since, now).map { it.value }.filter { it > 0 }.sorted()
        val b = Baseline(days, resting, if (hrv.size >= 10) hrv[hrv.size / 2] else null)
        baselineCache = now to b
        return b
    }

    private suspend fun readings(db: PulseLoopDatabase, kind: MeasurementKind, start: Long, end: Long): List<MeasurementEntity> =
        db.measurementDao().range(kind.name, start, end)
            .filter { it.sourceRaw !in setOf("demo", "mock", "workout") && it.activitySessionId == null }

    // Step snapshots: today's total as the ring reports it, with when it was last synced.

    private suspend fun recordStepSnapshot(context: Context, db: PulseLoopDatabase) {
        val row = db.activityDailyDao().byDay(startOfDay(System.currentTimeMillis())) ?: return
        val at = maxOf(row.updatedAt, row.syncedAt ?: 0L)
        val p = prefs(context)
        val snaps = readSnaps(p).toMutableList()
        if (snaps.isNotEmpty() && snaps.last().first >= at) return
        snaps += at to row.steps
        val kept = snaps.filter { System.currentTimeMillis() - it.first < 3 * 3_600_000L }
        val arr = JSONArray()
        kept.forEach { (t, s) -> arr.put(JSONArray().put(t).put(s)) }
        p.edit().putString("calm_steps", arr.toString()).apply()
    }

    private fun readSnaps(p: SharedPreferences): List<Pair<Long, Int>> = runCatching {
        val arr = JSONArray(p.getString("calm_steps", "[]"))
        (0 until arr.length()).map { arr.getJSONArray(it).let { e -> e.getLong(0) to e.getInt(1) } }
    }.getOrDefault(emptyList())

    // Log

    private fun outcome(context: Context, trigger: String, text: String) {
        _lastAssessment.value = "${time(System.currentTimeMillis())} · $text"
        if (text != lastLoggedOutcome) {
            lastLoggedOutcome = text
            append(context, "$trigger · $text")
        }
    }

    private fun append(context: Context, line: String) {
        val p = prefs(context)
        val list = (listOf("${time(System.currentTimeMillis(), seconds = true)} · $line") + readList(p, "calm_log")).take(40)
        p.edit().putString("calm_log", JSONArray(list).toString()).apply()
        _log.value = list
    }

    private fun readList(p: SharedPreferences, key: String): List<String> = runCatching {
        val arr = JSONArray(p.getString(key, "[]"))
        (0 until arr.length()).map { arr.getString(it) }
    }.getOrDefault(emptyList())

    private fun readLongs(p: SharedPreferences, key: String): List<Long> = runCatching {
        val arr = JSONArray(p.getString(key, "[]"))
        (0 until arr.length()).map { arr.getLong(it) }
    }.getOrDefault(emptyList())

    private fun startOfDay(t: Long): Long = Calendar.getInstance().apply {
        timeInMillis = t
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun time(t: Long, seconds: Boolean = false): String =
        DateFormat.getTimeInstance(if (seconds) DateFormat.MEDIUM else DateFormat.SHORT).format(Date(t))
}

/** Requests from outside the composition (e.g. a notification tap) for the emotion app to open a screen. */
object EmotionIntents {
    val pending = MutableStateFlow<String?>(null)
}
