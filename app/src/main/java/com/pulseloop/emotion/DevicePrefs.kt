package com.pulseloop.emotion

import android.content.Context
import com.pulseloop.ring.RingBLEClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Kinnora's "Use a ring" switch (Devices tab). Off: no touches go to the ring, and the ring is
 * disconnected and kept off (the client's stay-disconnected flag, so no auto-reconnect path brings
 * it back). Pairing a ring clears that flag, so we turn the switch back on when a ring connects.
 * The watch's switch lives in [WatchLink.useWatch]. Same behaviour as iOS.
 */
object DevicePrefs {
    private const val PREFS = "emotion.devices"
    private const val KEY_USE_RING = "useRing"

    private val _useRing = MutableStateFlow<Boolean?>(null)

    fun useRing(context: Context): Boolean =
        _useRing.value ?: context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_USE_RING, true)
            .also { _useRing.value = it }

    fun useRingFlow(context: Context): StateFlow<Boolean?> {
        useRing(context)
        return _useRing.asStateFlow()
    }

    fun setUseRing(context: Context, on: Boolean, client: RingBLEClient) {
        _useRing.value = on
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY_USE_RING, on).apply()
        if (on) {
            if (client.hasLastKnownRing) client.userConnect()
        } else {
            client.userDisconnect()
        }
    }
}
