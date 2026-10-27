package com.pulseloop.emotion

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import com.pulseloop.MainActivity

/**
 * "Play on phone" from the Kinnora watch app ("/kinora/start", body = experience id). With Kinnora
 * on screen the player opens straight away; otherwise a notification opens it (Android doesn't let
 * an app start its screen from the background). Same behaviour as iOS.
 */
class PhoneWearListenerService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != WatchLink.PATH_START) return
        val id = String(event.data)
        val exp = EmotionStore.get(applicationContext).get(id) ?: return
        val open = EmotionIntents.PLAY_PREFIX + id
        val inFront = runCatching {
            ProcessLifecycleOwner.get().lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        }.getOrDefault(false)
        if (inFront) {
            EmotionIntents.pending.value = open
            return
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("kinnora_watch", "From your watch", NotificationManager.IMPORTANCE_HIGH))
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(CalmCue.EXTRA_OPEN, open)
        val pi = PendingIntent.getActivity(this, id.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        nm.notify(id.hashCode(), NotificationCompat.Builder(this, "kinnora_watch")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Play ${exp.title.ifBlank { exp.arcLabel }}")
            .setContentText("Started from your watch. Tap to play it here.")
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build())
    }
}
