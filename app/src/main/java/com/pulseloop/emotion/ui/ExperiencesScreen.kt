package com.pulseloop.emotion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.material.icons.filled.MusicNote
import com.pulseloop.emotion.MusicTrack
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulseloop.emotion.EmotionStore
import com.pulseloop.emotion.Experience
import com.pulseloop.emotion.RingTouches
import com.pulseloop.emotion.SlideImage
import com.pulseloop.emotion.SlidePlaceholder
import com.pulseloop.emotion.VisionTemplate

/** Experiences (home of the emotion app): your visions, then templates to start a new one. */
@Composable
fun ExperiencesScreen(
    store: EmotionStore,
    touches: RingTouches,
    onPlay: (String) -> Unit,
    onEdit: (String) -> Unit,
    onNew: (templateId: String?) -> Unit,
) {
    val experiences by store.experiences.collectAsState()
    var segment by rememberSaveable { mutableIntStateOf(0) }
    DisposableEffect(Unit) { onDispose { SongPreview.stop() } }
    LazyColumn(
        Modifier.fillMaxSize().background(EColors.ground),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 56.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Experiences", color = EColors.text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
            Text(
                "Feel your vision as if it's already real. Your ring marks each feeling.",
                color = EColors.textSecondary, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
            )
            Row(Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(11.dp)).background(EColors.card).padding(3.dp)) {
                listOf("My experiences", "Songs").forEachIndexed { i, label ->
                    val on = segment == i
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(9.dp)).background(if (on) EColors.cardRaised else EColors.card)
                            .clickable { segment = i; if (i == 0) SongPreview.stop() }.padding(vertical = 9.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(label, color = if (on) EColors.text else EColors.textSecondary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
        if (segment == 1) {
            items(MusicTrack.songGroups, key = { "song-" + it.first }) { (_, versions) ->
                val first = versions.first()
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(EColors.card).padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(first.theme.art), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.MusicNote, contentDescription = null, tint = first.theme.color)
                        }
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(first.songName, color = EColors.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text(first.about, color = EColors.textSecondary, fontSize = 13.sp)
                        }
                    }
                    versions.forEach { t ->
                        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                            SongPlayButton(t)
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(t.version, color = EColors.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text("${t.durationLabel} · ${t.cueMarkersS.size} ring cues", color = EColors.textSecondary, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
            item {
                Text(
                    "Make a song for your own vision · My music, coming later",
                    color = EColors.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp),
                )
            }
            return@LazyColumn
        }
        items(experiences, key = { it.id }) { e ->
            ExperienceCard(e, onPlay = { onPlay(e.id) }, onEdit = { onEdit(e.id) }, onFeel = { touches.play(e.touchSequence) })
        }
        item {
            Text(
                if (experiences.isEmpty()) "START WITH A VISION" else "NEW EXPERIENCE",
                color = EColors.textMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
        items(VisionTemplate.all, key = { "t-" + it.id }) { t ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(EColors.card)
                    .clickable { onNew(t.id) }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(t.tile))
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(t.title, color = EColors.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(t.feelings.joinToString(" → ") { it.label }, color = EColors.textSecondary, fontSize = 12.sp)
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(EColors.card)
                    .clickable { onNew(null) }.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(EColors.cardRaised), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = EColors.accent)
                }
                Text("Write my own", color = EColors.text, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 14.dp))
            }
        }
    }
}

@Composable
private fun ExperienceCard(e: Experience, onPlay: () -> Unit, onEdit: () -> Unit, onFeel: () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(EColors.card).clickable(onClick = onPlay)) {
        val slides = e.slides
        Box(Modifier.fillMaxWidth().height(110.dp)) {
            if (slides.isEmpty()) SlidePlaceholder(e.feeling, Modifier.fillMaxSize())
            else SlideImage(slides[0], Modifier.fillMaxSize(), maxSide = 800)
        }
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(e.title.ifBlank { "My vision" }, color = EColors.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${e.imagesLabel} · ${e.track.durationLabel}", color = EColors.textSecondary, fontSize = 12.sp)
                }
                IconButton(onClick = onFeel) { Icon(Icons.Filled.Vibration, contentDescription = "Feel this experience's touches", tint = EColors.accent) }
                IconButton(onClick = onEdit) { Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = EColors.textSecondary) }
            }
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                ArcChips(e.feelings, Modifier.weight(1f))
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(e.feeling.color).clickable(onClick = onPlay),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.PlayArrow, contentDescription = "Live it", tint = EColors.onAccent) }
                Spacer(Modifier.size(8.dp))
            }
        }
    }
}
