package com.pulseloop.emotion.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulseloop.emotion.EmotionStore
import com.pulseloop.emotion.Experience
import com.pulseloop.emotion.Feeling
import com.pulseloop.emotion.MusicTrack
import com.pulseloop.emotion.RingTouches
import com.pulseloop.emotion.SlideImage
import com.pulseloop.emotion.SlideRef
import com.pulseloop.emotion.VisionTemplate
import com.pulseloop.ui.slideshow.SlideshowImageSets

/** Create or edit an experience: vision, ordered feelings (1–3), music and slideshow images. */
@Composable
fun BuilderScreen(
    store: EmotionStore,
    touches: RingTouches,
    experienceId: String?,
    templateId: String?,
    onClose: () -> Unit,
    onSaved: (id: String, startNow: Boolean) -> Unit,
) {
    val existing = remember(experienceId) { store.get(experienceId) }
    val template = remember(templateId) { VisionTemplate.byId(templateId) }

    var title by remember { mutableStateOf(existing?.title ?: template?.title ?: "") }
    val points = remember {
        mutableStateListOf<String>().apply {
            addAll(existing?.visionPoints ?: template?.points ?: emptyList())
            if (isEmpty()) add("")
        }
    }
    val arc = remember {
        mutableStateListOf<Feeling>().apply {
            addAll(existing?.feelings ?: template?.feelings ?: listOf(Feeling.JOY, Feeling.GRATITUDE))
        }
    }
    var track by remember { mutableStateOf(existing?.track ?: template?.track ?: MusicTrack.default(arc.firstOrNull() ?: Feeling.JOY)) }
    var imageRefs by remember {
        mutableStateOf(
            existing?.let { SlideRef.normalized(it.imageRefs) }
                ?: template?.let { t -> SlideshowImageSets.byId(t.starterImageSet)?.images?.map { "img:${it.name}" } }
                ?: emptyList(),
        )
    }
    var showImages by remember { mutableStateOf(false) }
    var showTracks by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    if (showImages) {
        BackHandler { showImages = false }
        ImagesPickerScreen(
            initial = imageRefs,
            feelings = arc.toList().ifEmpty { listOf(Feeling.GRATITUDE) },
            onDone = { imageRefs = it; showImages = false },
            onCancel = { showImages = false },
        )
        return
    }

    fun save(startNow: Boolean) {
        val base = existing ?: Experience()
        val saved = base.copy(
            title = title.trim().ifBlank { "My vision" },
            visionPoints = points.map { it.trim() }.filter { it.isNotEmpty() },
            feelingsRaw = arc.ifEmpty { listOf(Feeling.GRATITUDE) }.map { it.raw },
            trackId = track.id,
            imageRefs = imageRefs,
        )
        store.upsert(saved)
        onSaved(saved.id, startNow)
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = EColors.text, unfocusedTextColor = EColors.text,
        focusedBorderColor = EColors.accent, unfocusedBorderColor = EColors.line,
        cursorColor = EColors.accent, focusedContainerColor = EColors.card, unfocusedContainerColor = EColors.card,
    )

    Column(Modifier.fillMaxSize().background(EColors.ground).statusBarsPadding().navigationBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = EColors.text) }
            Text(
                if (existing == null) "New experience" else "Edit experience",
                color = EColors.text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
            )
            if (existing != null) TextButton(onClick = { confirmDelete = true }) { Text("Delete", color = EColors.danger) }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionLabel("YOUR VISION")
            OutlinedTextField(
                value = title, onValueChange = { title = it }, singleLine = true,
                label = { Text("Title") }, colors = fieldColors, modifier = Modifier.fillMaxWidth(),
            )
            points.forEachIndexed { i, p ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = p, onValueChange = { points[i] = it }, singleLine = true,
                        placeholder = { Text("It's real: what do you see?", color = EColors.textMuted) },
                        colors = fieldColors, modifier = Modifier.weight(1f),
                    )
                    if (points.size > 1) {
                        IconButton(onClick = { points.removeAt(i) }) { Icon(Icons.Filled.Close, contentDescription = "Remove line", tint = EColors.textMuted) }
                    }
                }
            }
            if (points.size < 5) {
                TextButton(onClick = { points.add("") }) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = EColors.accent)
                    Text("Add a line", color = EColors.accent, modifier = Modifier.padding(start = 6.dp))
                }
            }

            SectionLabel("WHAT YOU'LL FEEL, IN ORDER")
            Text(
                "Pick up to ${Feeling.MAX_PER_EXPERIENCE}. Your ring plays each feeling's touch in this order, at least 4 seconds apart.",
                color = EColors.textSecondary, fontSize = 13.sp,
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Feeling.order.forEach { f ->
                    val pos = arc.indexOf(f)
                    val selected = pos >= 0
                    Row(
                        Modifier.clip(RoundedCornerShape(20.dp))
                            .background(if (selected) f.ground else EColors.card)
                            .border(1.5.dp, if (selected) f.color else EColors.card, RoundedCornerShape(20.dp))
                            .clickable {
                                if (selected) arc.remove(f) else if (arc.size < Feeling.MAX_PER_EXPERIENCE) arc.add(f)
                            }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (selected) {
                            Box(Modifier.size(20.dp).clip(CircleShape).background(f.color), contentAlignment = Alignment.Center) {
                                Text("${pos + 1}", color = EColors.onAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(f.color))
                        }
                        Text(f.label, color = EColors.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp))
                    }
                }
            }
            if (arc.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ArcChips(arc.toList(), Modifier.weight(1f))
                    TextButton(onClick = { touches.play(Feeling.sequence(arc.toList())) }) { Text("Feel it", color = EColors.accent) }
                }
            }

            SectionLabel("MUSIC AND IMAGES")
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(EColors.card)
                    .clickable { showTracks = true }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Music", color = EColors.textSecondary, fontSize = 12.sp)
                    Text("${track.title} · ${track.durationLabel}", color = EColors.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                Text("Change", color = EColors.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(EColors.card)
                    .clickable { showImages = true }.padding(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Images (optional)", color = EColors.textSecondary, fontSize = 12.sp)
                        Text(SlideRef.label(imageRefs), color = EColors.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(if (imageRefs.isEmpty()) "Add" else "Edit", color = EColors.accent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                val slides = SlideRef.expand(imageRefs)
                if (slides.isNotEmpty()) {
                    Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        slides.take(5).forEach { s ->
                            SlideImage(s, Modifier.size(width = 48.dp, height = 56.dp).clip(RoundedCornerShape(8.dp)), maxSide = 300)
                        }
                        if (slides.size > 5) {
                            Box(
                                Modifier.size(width = 48.dp, height = 56.dp).clip(RoundedCornerShape(8.dp)).background(EColors.cardRaised),
                                contentAlignment = Alignment.Center,
                            ) { Text("+${slides.size - 5}", color = EColors.text, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
        Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
            Button(
                onClick = { save(startNow = true) }, enabled = arc.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(containerColor = EColors.accent, contentColor = EColors.onAccent),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) { Text("Save and live it", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            TextButton(onClick = { save(startNow = false) }, enabled = arc.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                Text("Save for later", color = EColors.accent)
            }
        }
    }

    if (showTracks) {
        AlertDialog(
            onDismissRequest = { showTracks = false },
            containerColor = EColors.card,
            title = { Text("Music", color = EColors.text) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    MusicTrack.catalog.forEach { t ->
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .clickable { track = t; showTracks = false }.padding(vertical = 10.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(10.dp).clip(CircleShape).background(t.theme.color))
                            Text(t.title, color = if (t.id == track.id) EColors.accent else EColors.text, modifier = Modifier.weight(1f).padding(start = 10.dp))
                            Text(t.durationLabel, color = EColors.textMuted, fontSize = 13.sp)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showTracks = false }) { Text("Close", color = EColors.accent) } },
        )
    }

    if (confirmDelete && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = EColors.card,
            title = { Text("Delete this experience?", color = EColors.text) },
            text = { Text("Its photos are removed from the app too. Your originals aren't touched.", color = EColors.textSecondary) },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; store.delete(existing.id); onClose() }) { Text("Delete", color = EColors.danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel", color = EColors.text) } },
        )
    }
}

@Composable
internal fun SectionLabel(text: String) {
    Text(text, color = EColors.textMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp, modifier = Modifier.padding(top = 8.dp))
}
