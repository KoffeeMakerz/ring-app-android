package com.pulseloop.emotion

import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.pulseloop.ring.RingBLEClient
import com.pulseloop.ring.RingConnectionState
import com.pulseloop.ring.RingDeviceType
import com.pulseloop.ring.WearableCapability
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * What a ring family can do with its buzz. Today every family exposes only a fixed-length
 * "find device" buzz, so pulses closer together than [minGapMs] are pushed apart.
 * Same values as iOS `RingCapabilityProfile`.
 */
data class RingTouchProfile(val buzzMs: Int?, val minGapMs: Int) {
    companion object {
        val COLMI = RingTouchProfile(300, 600)
        val YCBT = RingTouchProfile(400, 700)
        val CRP = RingTouchProfile(500, 800)
        val RWFIT = RingTouchProfile(null, 100)
        /** The phone's own vibration motor: any length, any gap. */
        val PHONE = RingTouchProfile(null, 0)

        fun forFamily(type: RingDeviceType?): RingTouchProfile {
            val name = type?.name?.lowercase() ?: return COLMI
            return when {
                "rwfit" in name -> RWFIT
                "ycbt" in name || "tk5" in name -> YCBT
                "crp" in name -> CRP
                else -> COLMI
            }
        }
    }
}

/**
 * The only place in the emotion app that makes the ring vibrate. Turns a [TouchPattern] into timed
 * buzz commands for the connected ring. With no ring connected, the phone vibrates instead so the
 * touch can still be felt while trying the app.
 */
class RingTouches(
    private val context: Context,
    private val client: RingBLEClient,
    private val scope: CoroutineScope,
) {
    private var job: Job? = null

    val isRingConnected: Boolean
        get() = client.state.value.connectionState == RingConnectionState.CONNECTED

    private val canBuzzRing: Boolean
        get() = isRingConnected && client.state.value.activeCapabilities.contains(WearableCapability.FIND_DEVICE)

    /** Pulses as (startMs, lengthMs) after clamping to the profile's minimum gap. */
    fun plan(pattern: TouchPattern, profile: RingTouchProfile): List<Pair<Int, Int>> {
        val result = mutableListOf<Pair<Int, Int>>()
        var lastStart = Int.MIN_VALUE / 2
        var shift = 0
        for ((pulseStart, pulseLength) in pattern.pulses) {
            var start = pulseStart + shift
            val fixed = profile.buzzMs
            if (fixed != null && result.isNotEmpty() && start - lastStart < profile.minGapMs) {
                val newStart = lastStart + maxOf(profile.minGapMs, fixed)
                shift += newStart - start
                start = newStart
            }
            result.add(start to (fixed ?: pulseLength))
            lastStart = start
        }
        return result
    }

    fun play(pattern: TouchPattern) {
        stop()
        val onRing = canBuzzRing
        val profile = if (onRing) RingTouchProfile.forFamily(client.state.value.activeDeviceType) else RingTouchProfile.PHONE
        val steps = plan(pattern, profile)
        job = scope.launch {
            val begin = SystemClock.elapsedRealtime()
            for ((start, length) in steps) {
                val wait = begin + start - SystemClock.elapsedRealtime()
                if (wait > 0) delay(wait)
                if (!isActive) return@launch
                if (onRing) {
                    if (canBuzzRing) client.syncEngine?.findDevice()
                } else {
                    phoneBuzz(length)
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private fun phoneBuzz(lengthMs: Int) {
        val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        if (vibrator?.hasVibrator() != true) return
        vibrator.vibrate(VibrationEffect.createOneShot(lengthMs.toLong().coerceAtLeast(1), VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
