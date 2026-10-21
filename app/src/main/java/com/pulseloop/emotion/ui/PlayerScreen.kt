package com.pulseloop.emotion.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.pulseloop.emotion.EmotionStore
import com.pulseloop.emotion.Experience
import com.pulseloop.emotion.ExperiencePlayback
import com.pulseloop.emotion.Feeling
import com.pulseloop.emotion.RingTouches
import com.pulseloop.emotion.SlideImage
import com.pulseloop.emotion.SlidePlaceholder
import com.pulseloop.emotion.SlideRef
import kotlinx.coroutines.delay

/**
 * Live an experience. The image fills the space above the controls (nothing covers it but a thin
 * top bar); tap it, or the expand button, for full screen. Music and ring touches keep playing.
 */
@Composable
fun PlayerScreen(
    experience: Experience,
    store: EmotionStore,
    touches: RingTouches,
    onClose: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val playback = remember(experience.id) { ExperiencePlayback(experience.track, experience.feelings, touches, scope, context) }
    val slides = experience.slides
    var immersive by remember { mutableStateOf(false) }
    var showVision by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf<SlideRef?>(null) }
    var showHint by remember { mutableStateOf(false) }

    LaunchedEffect(slides.size) { playback.slideCount = if (slides.isEmpty()) 6 else slides.size }
    LaunchedEffect(experience.id) {
        playback.play()
        store.markPlayed(experience.id)
    }
    DisposableEffect(experience.id) { onDispose { playback.stop() } }

    // Full screen hides the system bars too.
    val view = LocalView.current
    DisposableEffect(immersive) {
        val window = view.context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        if (immersive) {
            controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller?.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller?.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { controller?.show(WindowInsetsCompat.Type.systemBars()) }
    }
    LaunchedEffect(immersive) {
        if (immersive) {
            showHint = true
            delay(2500)
        }
        showHint = false
    }

    val now = playback.currentFeeling
    val currentSlide = if (slides.isEmpty()) null else slides[playback.slideIndex % slides.size]

    fun removeSlide(slide: SlideRef) {
        store.upsert(experience.withoutSlide(slide)) // also deletes an unused photo copy
    }

    Column(Modifier.fillMaxSize().background(EColors.ground)) {
        // Image area.
        Box(
            Modifier.weight(1f).fillMaxWidth()
                .clip(if (immersive) RectangleShape else RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClickLabel = if (immersive) "Show controls" else "Show the image full screen",
                ) { immersive = !immersive },
        ) {
            Crossfade(targetState = currentSlide, animationSpec = tween(1200), label = "slide") { slide ->
                if (slide == null) SlidePlaceholder(now, Modifier.fillMaxSize())
                else SlideImage(slide, Modifier.fillMaxSize(), maxSide = 1600)
            }
            if (!immersive) {
                Column(Modifier.statusBarsPadding().padding(horizontal = 12.dp, vertical = 4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BarButton(Icons.Filled.Close, "Close") { playback.stop(); onClose() }
                        Spacer(Modifier.weight(1f))
                        if (playback.inCue) CueChip(now)
                        Spacer(Modifier.weight(1f))
                        BarButton(Icons.Filled.Fullscreen, "Full screen image") { immersive = true }
                        if (currentSlide != null) {
                            Box {
                                BarButton(Icons.Filled.MoreVert, "Image options") { menuOpen = true }
                                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                    DropdownMenuItem(
                                        text = { Text("Remove this image") },
                                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                                        onClick = {
                                            menuOpen = false
                                            if (currentSlide.isOwnPhoto) confirmRemove = currentSlide else removeSlide(currentSlide)
                                        },
                                    )
                                }
                            }
                        }
                        BarButton(Icons.Filled.List, "Vision") { showVision = true }
                    }
                    if (playback.parts.size > 1) {
                        PartBars(
                            playback.parts, { playback.fraction(it) },
                            Modifier.padding(start = 4.dp, end = 4.dp, top = 10.dp),
                            track = EColors.ground.copy(alpha = 0.8f),
                        )
                    }
                }
            } else {
                if (playback.inCue) {
                    Box(Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 8.dp)) { CueChip(now) }
                }
                if (showHint) {
                    Text(
                        "Tap to show controls", color = EColors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 16.dp)
                            .clip(RoundedCornerShape(17.dp)).background(EColors.ground.copy(alpha = 0.8f))
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                    )
                }
            }
        }

        // Controls, below the image.
        AnimatedVisibility(visible = !immersive, enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut()) {
            Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp)) {
                Text(experience.title.ifBlank { "My vision" }, color = EColors.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).clip(CircleShape).background(now.color))
                    Text(now.label, color = now.color, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
                    if (playback.parts.size > 1) {
                        Text(
                            "  ·  Part ${playback.currentPart.index + 1} of ${playback.parts.size}",
                            color = EColors.textSecondary, fontSize = 13.sp,
                        )
                    }
                }
                Text(now.experiencePrompt, color = EColors.text, fontSize = 19.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 6.dp))
                Row(
                    Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(EColors.card)
                        .border(1.5.dp, if (playback.inCue) now.color else EColors.card, RoundedCornerShape(16.dp))
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(18.dp).clip(CircleShape).border(4.dp, if (playback.inCue) now.color else EColors.line, CircleShape))
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(
                            if (playback.inCue) "Ring is playing ${now.label.lowercase()}" else "Next ${now.label.lowercase()} touch coming",
                            color = EColors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            if (playback.inCue) "Let the feeling peak as it buzzes" else "Stay with the picture. Feel it as real.",
                            color = EColors.textSecondary, fontSize = 13.sp,
                        )
                    }
                }
                LinearProgressIndicator(
                    progress = { playback.progress.toFloat() },
                    color = EColors.text, trackColor = EColors.line,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(4.dp).clip(RoundedCornerShape(2.dp)),
                )
                Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Text(timeString(playback.position), color = EColors.textMuted, fontSize = 12.sp)
                    Spacer(Modifier.weight(1f))
                    Text(timeString(playback.duration), color = EColors.textMuted, fontSize = 12.sp)
                }
                Row(
                    Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val multi = playback.parts.size > 1
                    IconButton(onClick = {
                        if (multi) playback.seekToPart((playback.currentPart.index - 1).coerceAtLeast(0)) else playback.seekBy(-15.0)
                    }) { Icon(Icons.Filled.SkipPrevious, contentDescription = if (multi) "Previous part" else "Back 15 seconds", tint = EColors.text) }
                    Box(
                        Modifier.size(72.dp).clip(CircleShape).background(now.color).clickable { playback.toggle() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (playback.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (playback.isPlaying) "Pause" else "Play",
                            tint = EColors.onAccent, modifier = Modifier.size(32.dp),
                        )
                    }
                    IconButton(onClick = {
                        if (multi) playback.seekToPart((playback.currentPart.index + 1).coerceAtMost(playback.parts.size - 1)) else playback.seekBy(15.0)
                    }) { Icon(Icons.Filled.SkipNext, contentDescription = if (multi) "Next part" else "Forward 15 seconds", tint = EColors.text) }
                    IconButton(onClick = { playback.playCueNow() }) {
                        Icon(Icons.Filled.Vibration, contentDescription = "Play the ring touch now", tint = now.color)
                    }
                }
            }
        }
    }

    if (showVision) {
        AlertDialog(
            onDismissRequest = { showVision = false },
            containerColor = EColors.card,
            title = { Text(experience.title.ifBlank { "My vision" }, color = EColors.text) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (experience.visionPoints.isEmpty()) Text("No vision lines yet.", color = EColors.textSecondary)
                    experience.visionPoints.forEach { Text("• $it", color = EColors.text, fontSize = 16.sp) }
                    ArcChips(experience.feelings, Modifier.padding(top = 8.dp))
                }
            },
            confirmButton = { TextButton(onClick = { showVision = false }) { Text("Close", color = EColors.accent) } },
        )
    }

    confirmRemove?.let { slide ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            containerColor = EColors.card,
            title = { Text("Remove this photo from the slideshow?", color = EColors.text) },
            text = { Text("The app's copy is deleted. The original in your gallery isn't touched.", color = EColors.textSecondary) },
            confirmButton = {
                TextButton(onClick = { confirmRemove = null; removeSlide(slide) }) { Text("Remove photo", color = EColors.danger) }
            },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text("Cancel", color = EColors.text) } },
        )
    }
}

@Composable
private fun BarButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.padding(horizontal = 2.dp).size(44.dp).clip(CircleShape).background(EColors.ground.copy(alpha = 0.85f)).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = label, tint = EColors.text, modifier = Modifier.size(20.dp)) }
}

@Composable
private fun CueChip(feeling: Feeling) {
    Row(
        Modifier.clip(RoundedCornerShape(17.dp)).background(EColors.ground.copy(alpha = 0.85f)).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(14.dp).clip(CircleShape).border(3.dp, feeling.color, CircleShape))
        Text("${feeling.label} touch", color = EColors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
    }
}

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}
