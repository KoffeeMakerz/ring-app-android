package com.pulseloop.emotion

import android.content.Context
import com.pulseloop.R

/** Meditation sessions (port of iOS `MeditationKind`). */
enum class MeditationKind(val title: String, val meta: String, val phases: List<Pair<String, Int>>) {
    JOURNEY("Relax, find coherence, reflect", "Full journey · 15 min", listOf("Relax" to 300, "Coherence breathing" to 480, "Reflect" to 120)),
    RELAX("Deep relax", "Body scan · 10 min", listOf("Body scan" to 600)),
    COHERENCE("Coherence", "Paced breathing · 5 min", listOf("Coherence breathing" to 300)),
    /** Short session opened from a calm cue: always 4 in / 7 out. */
    CALM_BREATH("Calm breath", "1½ min · in 4, out 7", listOf("Calm breathing" to 99));

    val totalSeconds: Int get() = phases.sumOf { it.second }

    fun phaseIndexAt(second: Int): Int {
        var t = 0
        phases.forEachIndexed { i, p -> t += p.second; if (second < t) return i }
        return phases.lastIndex
    }

    fun isBreathingAt(second: Int): Boolean = phases[phaseIndexAt(second)].first.lowercase().contains("breathing")

    /** Calm illustrations shown behind this session, in order. */
    val images: List<Int>
        get() = when (this) {
            RELAX -> listOf(R.drawable.calm_lake_02, R.drawable.calm_monk_02, R.drawable.calm_water_01, R.drawable.calm_mountains_01, R.drawable.calm_stream_01, R.drawable.calm_lake_01)
            COHERENCE -> listOf(R.drawable.calm_circles_01, R.drawable.calm_waterfall_01, R.drawable.calm_monk_01, R.drawable.calm_circles_02, R.drawable.calm_lake_01, R.drawable.calm_monk_03)
            CALM_BREATH -> listOf(R.drawable.calm_monk_03, R.drawable.calm_water_01, R.drawable.calm_lake_02)
            JOURNEY -> listOf(R.drawable.calm_lake_02, R.drawable.calm_monk_02, R.drawable.calm_water_01, R.drawable.calm_circles_01, R.drawable.calm_mountains_01, R.drawable.calm_waterfall_01, R.drawable.calm_monk_01, R.drawable.calm_stream_01, R.drawable.calm_lake_01, R.drawable.calm_circles_02, R.drawable.calm_monk_03)
        }

    companion object {
        const val SECONDS_PER_IMAGE = 20
    }
}

/** Breathing pace and ring guidance (shared prefs, same defaults as iOS: 4 in / 7 out). */
object BreathSettings {
    private fun prefs(context: Context) = context.getSharedPreferences("emotion", Context.MODE_PRIVATE)
    fun inSeconds(context: Context): Float = prefs(context).getFloat("breath_in", 4f)
    fun outSeconds(context: Context): Float = prefs(context).getFloat("breath_out", 7f)
    fun setPace(context: Context, inS: Float, outS: Float) = prefs(context).edit().putFloat("breath_in", inS).putFloat("breath_out", outS).apply()
    fun ringGuides(context: Context): Boolean = prefs(context).getBoolean("ring_guides_breath", true)
    fun setRingGuides(context: Context, on: Boolean) = prefs(context).edit().putBoolean("ring_guides_breath", on).apply()

    val paces = listOf(Triple(4f, 7f, "4 in · 7 out (calming)"), Triple(5f, 5f, "5 in · 5 out (6/min)"), Triple(5.5f, 5.5f, "5.5 in · 5.5 out (5.5/min)"))
}
