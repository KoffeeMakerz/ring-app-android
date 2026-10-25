package dev.kinora.kinora.watch

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import org.json.JSONArray
import org.json.JSONObject

/**
 * Reminder cues planned by the phone. The watch keeps them and plays each touch itself with an
 * exact alarm, so reminders arrive on time even when the phone is asleep or far away.
 */
object CueStore {
    private const val PREFS = "kinora_cues"
    private const val MAX = 60

    fun save(context: Context, json: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("cues", json).apply()
        schedule(context)
    }

    fun cues(context: Context): List<JSONObject> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("cues", "[]") ?: "[]"
        val arr = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
        return (0 until arr.length()).map { arr.getJSONObject(it) }.sortedBy { it.optLong("at") }
    }

    fun next(context: Context): JSONObject? = cues(context).firstOrNull { it.optLong("at") > System.currentTimeMillis() }

    /** Re-creates the alarms for all future cues (clears the old ones first). */
    fun schedule(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java)
        for (i in 0 until MAX) am.cancel(pending(context, i, null))
        val now = System.currentTimeMillis()
        cues(context).filter { it.optLong("at") > now }.take(MAX).forEachIndexed { i, cue ->
            val pi = pending(context, i, cue.toString())
            val at = cue.optLong("at")
            val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
            if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    private fun pending(context: Context, index: Int, cueJson: String?): PendingIntent {
        val intent = Intent(context, CueAlarmReceiver::class.java).setAction("dev.kinora.CUE_$index")
        if (cueJson != null) intent.putExtra("cue", cueJson)
        return PendingIntent.getBroadcast(context, index, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}

/** Fires at a cue's time: plays the touch and shows the vision (title + line) on the watch. */
class CueAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val cue = runCatching { JSONObject(intent.getStringExtra("cue") ?: return) }.getOrNull() ?: return
        // Skip cues that are far too late (e.g. the watch was off).
        if (System.currentTimeMillis() - cue.optLong("at") > 15 * 60_000L) return
        val wake = context.getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "kinora:cue")
        wake.acquire(15_000)
        TouchPlayer.play(context, TouchPattern.fromJson(cue.optJSONObject("pattern") ?: JSONObject()))
        notify(context, cue.optString("title", "Kinnora"), cue.optString("body"))
    }

    private fun notify(context: Context, title: String, body: String) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("cues", "Reminder cues", NotificationManager.IMPORTANCE_DEFAULT).apply {
            enableVibration(false) // the touch itself is the vibration
            setSound(null, null)
        })
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE)
        nm.notify(7001, NotificationCompat.Builder(context, "cues")
            .setSmallIcon(R.drawable.ic_kinora)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build())
    }
}

/** Alarms are cleared on reboot or when the exact-alarm permission changes: set them again. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = CueStore.schedule(context)
}
