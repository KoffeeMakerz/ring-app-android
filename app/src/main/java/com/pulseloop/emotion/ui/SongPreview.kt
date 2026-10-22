package com.pulseloop.emotion.ui

import android.content.Context
import android.media.MediaPlayer
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pulseloop.emotion.MusicTrack

/** Plays one bundled song at a time for previews in the music lists. */
object SongPreview {
    var playingId by mutableStateOf<String?>(null)
        private set
    private var player: MediaPlayer? = null

    fun toggle(context: Context, track: MusicTrack) {
        if (playingId == track.id) { stop(); return }
        stop()
        val name = track.rawRes ?: return
        val id = context.resources.getIdentifier(name, "raw", context.packageName)
        if (id == 0) return
        player = runCatching { MediaPlayer.create(context.applicationContext, id) }.getOrNull()?.apply {
            setOnCompletionListener { stop() }
            start()
        }
        playingId = if (player != null) track.id else null
    }

    fun stop() {
        player?.runCatching { stop(); release() }
        player = null
        playingId = null
    }
}

@Composable
fun SongPlayButton(track: MusicTrack) {
    val context = LocalContext.current
    val playing = SongPreview.playingId == track.id
    Box(
        Modifier.size(36.dp).clip(CircleShape).background(track.theme.color).clickable { SongPreview.toggle(context, track) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (playing) Icons.Filled.Stop else Icons.Filled.PlayArrow,
            contentDescription = if (playing) "Stop ${track.title}" else "Play ${track.title}",
            tint = EColors.onAccent, modifier = Modifier.size(18.dp),
        )
    }
}
