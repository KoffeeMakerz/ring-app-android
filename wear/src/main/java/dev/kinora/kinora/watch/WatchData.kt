package dev.kinora.kinora.watch

import android.content.Context
import androidx.compose.ui.graphics.Color
import org.json.JSONArray
import org.json.JSONObject

/** One feeling as the watch shows it. Same names, colours and touches as the phone app. */
data class WatchFeeling(val id: String, val name: String, val color: Color, val durationsMs: List<Long>) {
    val pattern: TouchPattern get() = TouchPattern("feeling.$id", name, durationsMs)

    companion object {
        private fun ms(vararg v: Int): List<Long> = v.map { it.toLong() }

        val all = listOf(
            WatchFeeling("gratitude", "Gratitude", Color(0xFFF2B45A), ms(350, 300, 350, 1200, 350, 300, 350, 1200, 500)),
            WatchFeeling("love", "Love", Color(0xFFF27A9B), ms(300, 300, 450, 1000, 300, 300, 450, 1000, 300, 300, 450)),
            WatchFeeling("joy", "Joy", Color(0xFFF5D76E), ms(250, 350, 250, 350, 250, 900, 250, 350, 250, 350, 250, 900, 250, 350, 250, 350, 250)),
            WatchFeeling("bliss", "Bliss", Color(0xFFB892FF), ms(300, 1300, 300, 1000, 300, 750, 300, 550, 700)),
            WatchFeeling("calm", "Calm", Color(0xFF7FB2F0), ms(700, 1300, 700, 1300, 700, 1300, 700)),
            WatchFeeling("confidence", "Confidence", Color(0xFF6FD6A1), ms(450, 900, 450, 900, 300, 300, 300, 300, 600)),
        )

        fun named(id: String?): WatchFeeling = all.firstOrNull { it.id == id?.lowercase() } ?: all.first()
    }
}

/** An experience synced from the phone: title, feelings in order, and when each touch plays. */
data class WatchExperience(
    val id: String,
    val title: String,
    val feelings: List<String>,
    val durationS: Int,
    val cues: List<Cue>,
) {
    data class Cue(val at: Int, val feeling: String, val durations: List<Long>)

    /** The feeling for a moment: the parts split the time evenly, like on the phone. */
    fun feelingAt(seconds: Double): WatchFeeling {
        if (feelings.isEmpty() || durationS <= 0) return WatchFeeling.all.first()
        val i = (seconds / durationS * feelings.size).toInt().coerceIn(0, feelings.size - 1)
        return WatchFeeling.named(feelings[i])
    }

    companion object {
        fun fromJson(o: JSONObject): WatchExperience {
            val f = o.optJSONArray("feelings") ?: JSONArray()
            val c = o.optJSONArray("cues") ?: JSONArray()
            return WatchExperience(
                id = o.optString("id"),
                title = o.optString("title"),
                feelings = (0 until f.length()).map { f.getString(it) },
                durationS = o.optInt("durationS", 60),
                cues = (0 until c.length()).map { i ->
                    val cue = c.getJSONObject(i)
                    val d = cue.optJSONArray("durations") ?: JSONArray()
                    Cue(cue.optInt("at"), cue.optString("feeling"), (0 until d.length()).map { d.getLong(it) })
                },
            )
        }
    }
}

/**
 * What the phone last synced ("/kinora/sync": experiences + the reminder plan). Experiences are kept
 * here; reminders go to [CueStore], which plays each one on time with an exact alarm.
 */
object WatchStore {
    private const val PREFS = "kinora_sync"

    fun apply(context: Context, json: String) {
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString("experiences", (o.optJSONArray("experiences") ?: JSONArray()).toString()).apply()
        o.optJSONArray("reminders")?.let { CueStore.save(context, it.toString()) }
    }

    fun experiences(context: Context): List<WatchExperience> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("experiences", "[]") ?: "[]"
        val arr = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
        return (0 until arr.length()).map { WatchExperience.fromJson(arr.getJSONObject(it)) }
    }
}
