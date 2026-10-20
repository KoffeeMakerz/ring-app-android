package com.pulseloop.emotion

import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pulseloop.ring.PulseEvent
import com.pulseloop.ring.PulseEventBus
import com.pulseloop.ring.RingBLEClient
import com.pulseloop.ring.RingConnectionState
import com.pulseloop.service.RingSyncCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class CoherenceLevel(val label: String) { LOW("Low"), MEDIUM("Medium"), HIGH("High") }

/**
 * Live heart rate for meditation from the ring's live HR stream (port of iOS `LiveHeartRate`).
 * Coherence is an estimate: how strongly heart rate rises and falls with the paced breath. These
 * rings send heart rate about once a second at best, with no beat-to-beat data, so everything
 * works on time rather than on sample counts.
 */
class LiveHeart(
    private val client: RingBLEClient,
    private val coordinator: RingSyncCoordinator,
    private val scope: CoroutineScope,
) {
    enum class Status { IDLE, NO_RING, STARTING, LIVE, STALLED }

    /** (elapsedRealtime ms, bpm) for the last 2 minutes. */
    private val timed = ArrayDeque<Pair<Long, Int>>()
    var current by mutableStateOf<Int?>(null)
        private set
    /** Bumped on every sample and once a second, so Compose re-reads derived values. */
    var tick by mutableIntStateOf(0)
        private set
    private var running = false
    private var startedAt = 0L
    private var lastSampleAt = 0L
    private var lastRestartAt = 0L
    private var job: Job? = null
    private var watchdog: Job? = null

    val status: Status
        get() {
            if (!running) return Status.IDLE
            if (client.state.value.connectionState != RingConnectionState.CONNECTED) return Status.NO_RING
            val now = SystemClock.elapsedRealtime()
            if (lastSampleAt > 0) return if (now - lastSampleAt > 12_000) Status.STALLED else Status.LIVE
            return if (now - startedAt > 30_000) Status.STALLED else Status.STARTING
        }

    fun start() {
        if (running) return
        running = true
        timed.clear(); current = null
        startedAt = SystemClock.elapsedRealtime(); lastSampleAt = 0; lastRestartAt = 0
        coordinator.startWorkoutHeartRate("meditation")
        job = scope.launch {
            PulseEventBus.events.collect { e -> if (e is PulseEvent.HeartRateSample && !e.spot) append(e.bpm) }
        }
        watchdog = scope.launch {
            while (true) {
                delay(1000)
                tick += 1
                // Rings sometimes stop streaming (firmware timeout, brief disconnect): nudge it.
                val now = SystemClock.elapsedRealtime()
                if (status == Status.NO_RING) continue
                if (status == Status.STALLED && now - lastRestartAt > 15_000) {
                    lastRestartAt = now
                    coordinator.startWorkoutHeartRate("meditation")
                }
            }
        }
    }

    fun stop() {
        job?.cancel(); job = null
        watchdog?.cancel(); watchdog = null
        if (running) coordinator.stopWorkoutHeartRate()
        running = false
    }

    private fun append(bpm: Int) {
        if (bpm !in 31..219) return
        val now = SystemClock.elapsedRealtime()
        timed.addLast(now to bpm)
        while (timed.isNotEmpty() && now - timed.first().first > 120_000) timed.removeFirst()
        lastSampleAt = now
        current = bpm
        tick += 1
    }

    /** Samples from the last [seconds], as (elapsedRealtime ms, bpm). */
    fun recent(seconds: Int): List<Pair<Long, Int>> {
        val now = SystemClock.elapsedRealtime()
        return timed.filter { now - it.first <= seconds * 1000L }
    }

    val secondsOfData: Int
        get() = if (timed.size < 2) 0 else ((timed.last().first - timed.first().first) / 1000).toInt()

    /** Amplitude of the breath-linked heart-rate swing over the last ~30 s, resampled to 1 Hz. */
    fun coherence(breathPeriodS: Float): CoherenceLevel? {
        val now = SystemClock.elapsedRealtime()
        val window = timed.filter { now - it.first <= 30_000 }
        if (window.size < 6 || now - window.first().first < 20_000 || now - lastSampleAt > 12_000) return null
        val series = mutableListOf<Double>()
        var idx = 0
        var value = window[0].second.toDouble()
        var t = window.first().first
        while (t <= now) {
            while (idx < window.size && window[idx].first <= t) { value = window[idx].second.toDouble(); idx++ }
            series += value
            t += 1000
        }
        val period = maxOf(4, Math.round(breathPeriodS))
        val swings = mutableListOf<Double>()
        var i = 0
        while (i + period <= series.size) {
            val slice = series.subList(i, i + period)
            swings += (slice.max() - slice.min())
            i += period
        }
        if (swings.isEmpty()) return null
        val swing = swings.average()
        return when {
            swing < 3 -> CoherenceLevel.LOW
            swing < 7 -> CoherenceLevel.MEDIUM
            else -> CoherenceLevel.HIGH
        }
    }
}
