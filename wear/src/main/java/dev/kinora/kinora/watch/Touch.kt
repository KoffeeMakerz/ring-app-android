package dev.kinora.kinora.watch

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import org.json.JSONObject

/** A touch: alternating motor-on / off ms, starting with on (same format as the phone app). */
data class TouchPattern(val id: String, val name: String, val durationsMs: List<Long>) {
    companion object {
        fun fromJson(o: JSONObject): TouchPattern {
            val arr = o.optJSONArray("durations_ms")
            val list = if (arr == null) emptyList() else (0 until arr.length()).map { arr.getLong(it) }
            return TouchPattern(o.optString("id"), o.optString("name"), list)
        }

        /** Same patterns as the phone app (lib/model/feelings.dart). */
        val feelings = listOf(
            TouchPattern("feeling.gratitude", "Gratitude", listOf<Long>(350, 300, 350, 1200, 350, 300, 350, 1200, 500)),
            TouchPattern("feeling.joy", "Joy", listOf<Long>(250, 350, 250, 350, 250, 900, 250, 350, 250, 350, 250, 900, 250, 350, 250, 350, 250)),
            TouchPattern("feeling.love", "Love", listOf<Long>(300, 300, 450, 1000, 300, 300, 450, 1000, 300, 300, 450)),
            TouchPattern("feeling.confidence", "Confidence", listOf<Long>(450, 900, 450, 900, 300, 300, 300, 300, 600)),
            TouchPattern("feeling.bliss", "Bliss", listOf<Long>(300, 1300, 300, 1000, 300, 750, 300, 550, 700)),
            TouchPattern("feeling.calm", "Calm", listOf<Long>(700, 1300, 700, 1300, 700, 1300, 700)),
        )
    }
}

/** Plays touches on the watch motor: exact lengths and gaps, which watches do well. */
object TouchPlayer {
    fun play(context: Context, pattern: TouchPattern) {
        if (pattern.durationsMs.isEmpty()) return
        val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
        if (!vibrator.hasVibrator()) return
        // createWaveform: timings start with a wait; amplitudes 0 = off.
        val timings = longArrayOf(0) + pattern.durationsMs.toLongArray()
        val amplitudes = IntArray(timings.size) { i -> if (i == 0) 0 else if ((i - 1) % 2 == 0) 255 else 0 }
        val effect = if (vibrator.hasAmplitudeControl()) VibrationEffect.createWaveform(timings, amplitudes, -1)
        else VibrationEffect.createWaveform(timings, -1)
        vibrator.vibrate(effect)
    }
}
