package com.pulseloop.emotion.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalView

/** How many sessions on screen want the screen kept on (overlapping screens share one window). */
private var keepOnHolders = 0

/**
 * Keeps the screen on while this composable is showing (breathing, an experience, practice), so
 * the phone doesn't dim and lock halfway through. The rest of the app locks normally.
 * Same idea as iOS `keepsScreenAwake()`.
 */
@Composable
fun KeepScreenOn() {
    val view = LocalView.current
    DisposableEffect(view) {
        keepOnHolders += 1
        view.keepScreenOn = true
        onDispose {
            keepOnHolders = maxOf(0, keepOnHolders - 1)
            if (keepOnHolders == 0) view.keepScreenOn = false
        }
    }
}
