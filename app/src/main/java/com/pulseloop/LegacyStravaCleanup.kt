package com.pulseloop

import android.content.Context

/**
 * Strava was removed from Kinnora. Older builds kept its OAuth tokens in their own encrypted
 * preferences file ("pulseloop_strava"); this deletes that file. Cheap and idempotent, so it runs at
 * every launch and on "Reset App Data". Same idea as iOS `LegacyStravaCleanup`.
 */
object LegacyStravaCleanup {
    fun run(context: Context) {
        runCatching { context.deleteSharedPreferences("pulseloop_strava") }
    }
}
