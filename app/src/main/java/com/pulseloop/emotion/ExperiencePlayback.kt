package com.pulseloop.emotion

import android.content.Context
import android.media.MediaPlayer
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** One feeling's stretch of the song in an experience session. */
data class ExperiencePart(val index: Int, val feeling: Feeling, val start: Double, val end: Double)

/**
 * Plays an experience: music clock + slideshow, split into one part per feeling of its arc.
 * At each cue marker the ring plays the touch of the part the cue falls in. Cues are driven from
 * the playback position so they never drift. Port of iOS `ExperiencePlayback`. Tracks with a
 * bundled song (`MusicTrack.rawRes`, in res/raw) play it; the rest run on a silent clock.
 */
class ExperiencePlayback(
    val track: MusicTrack,
    feelings: List<Feeling>,
    private val touches: RingTouches,
    private val scope: CoroutineScope,
    context: Context? = null,
) {
    val feelings: List<Feeling> = feelings.ifEmpty { listOf(Feeling.GRATITUDE) }

    var position by mutableDoubleStateOf(0.0)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    /** True for a moment after the ring plays a touch. */
    var inCue by mutableStateOf(false)
        private set
    var slideCount by mutableIntStateOf(6)
    val slideSeconds = 8.0
    var cuesEnabled by mutableStateOf(true)

    private var tick: Job? = null
    private var cueJob: Job? = null
    private val firedCues = mutableSetOf<Int>()

    /** The bundled song, if this track has one. */
    private var player: MediaPlayer? = context?.let { ctx ->
        track.rawRes?.let { name ->
            val id = ctx.resources.getIdentifier(name, "raw", ctx.packageName)
            if (id != 0) runCatching { MediaPlayer.create(ctx, id) }.getOrNull() else null
        }
    }

    val duration: Double
        get() = player?.duration?.takeIf { it > 0 }?.let { it / 1000.0 } ?: track.durationS.toDouble()
    val progress: Double get() = if (duration > 0) position / duration else 0.0
    val slideIndex: Int get() = if (slideCount > 0) (position / slideSeconds).toInt() % slideCount else 0

    /** Equal parts of the song, one per feeling, in arc order. */
    val parts: List<ExperiencePart>
        get() {
            val n = feelings.size.toDouble()
            return feelings.mapIndexed { i, f -> ExperiencePart(i, f, duration * i / n, duration * (i + 1) / n) }
        }
    val currentPart: ExperiencePart get() = partAt(position)
    val currentFeeling: Feeling get() = currentPart.feeling

    fun partAt(seconds: Double): ExperiencePart = parts.lastOrNull { seconds >= it.start } ?: parts[0]

    fun fraction(part: ExperiencePart): Float {
        if (part.end <= part.start) return 0f
        return ((position - part.start) / (part.end - part.start)).coerceIn(0.0, 1.0).toFloat()
    }

    fun play() {
        if (isPlaying) return
        if (position >= duration) seekTo(0.0)
        isPlaying = true
        player?.let {
            it.seekTo((position * 1000).toInt())
            it.start()
        }
        val startWall = SystemClock.elapsedRealtime()
        val startPos = position
        tick = scope.launch {
            while (isActive) {
                delay(200)
                position = player?.let { it.currentPosition / 1000.0 }
                    ?: (startPos + (SystemClock.elapsedRealtime() - startWall) / 1000.0)
                checkCues()
                // A bundled song that has stopped on its own has reached its end.
                if (position >= duration || player?.isPlaying == false) {
                    position = duration
                    pause()
                    return@launch
                }
            }
        }
    }

    fun pause() {
        tick?.cancel()
        tick = null
        player?.takeIf { it.isPlaying }?.pause()
        isPlaying = false
    }

    fun toggle() = if (isPlaying) pause() else play()

    fun seekBy(seconds: Double) = seekTo(position + seconds)

    fun seekTo(seconds: Double) {
        val wasPlaying = isPlaying
        pause()
        position = seconds.coerceIn(0.0, duration)
        // Don't re-fire cues already passed.
        firedCues.clear()
        track.cueMarkersS.forEachIndexed { i, m -> if (m < position) firedCues.add(i) }
        if (wasPlaying) play()
    }

    fun seekToPart(index: Int) {
        parts.getOrNull(index)?.let { seekTo(it.start) }
    }

    fun stop() {
        pause()
        touches.stop()
        player?.release()
        player = null
    }

    /** Play the current part's touch now. */
    fun playCueNow() = fire(currentFeeling)

    private fun checkCues() {
        if (!cuesEnabled) return
        track.cueMarkersS.forEachIndexed { i, marker ->
            if (i !in firedCues && position >= marker && position < marker + 2) {
                firedCues.add(i)
                fire(partAt(marker.toDouble()).feeling)
            }
        }
    }

    private fun fire(feeling: Feeling) {
        touches.play(feeling.touch)
        inCue = true
        cueJob?.cancel()
        cueJob = scope.launch {
            delay(2500)
            inCue = false
        }
    }
}
