package com.pulseloop.emotion.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulseloop.emotion.BreathSettings
import com.pulseloop.emotion.MeditationKind
import com.pulseloop.emotion.RingTouches
import com.pulseloop.emotion.TouchBlock
import kotlinx.coroutines.delay

private val CalmBlue = Color(0xFF7FB2F0)
private val CalmGround = Color(0xFF0B1220)
private val CalmCard = Color(0xFF121C2E)

/** Meditate tab: sessions plus breathing settings. */
@Composable
fun MeditateScreen(onStart: (MeditationKind) -> Unit) {
    val context = LocalContext.current
    var inS by remember { mutableFloatStateOf(BreathSettings.inSeconds(context)) }
    var outS by remember { mutableFloatStateOf(BreathSettings.outSeconds(context)) }
    var guides by remember { mutableStateOf(BreathSettings.ringGuides(context)) }
    var paceMenu by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().background(EColors.ground).verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 56.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Meditate", color = EColors.text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        MeditationKind.entries.forEach { kind ->
            Box(
                Modifier.fillMaxWidth().height(if (kind == MeditationKind.JOURNEY) 150.dp else 104.dp)
                    .clip(RoundedCornerShape(22.dp)).clickable { onStart(kind) },
            ) {
                Image(painterResource(kind.images.first()), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.horizontalGradient(listOf(CalmGround.copy(alpha = 0.85f), CalmGround.copy(alpha = 0.2f)))))
                Column(Modifier.align(Alignment.CenterStart).padding(18.dp)) {
                    Text(kind.meta.uppercase(), color = CalmBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Text(kind.title, color = EColors.text, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
        SectionLabel("SESSION SETTINGS")
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(EColors.card).padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Breathing pace", color = EColors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Box {
                    Text(
                        "${fmt(inS)} in · ${fmt(outS)} out", color = EColors.accent, fontSize = 14.sp,
                        modifier = Modifier.clickable { paceMenu = true }.padding(8.dp),
                    )
                    DropdownMenu(expanded = paceMenu, onDismissRequest = { paceMenu = false }) {
                        BreathSettings.paces.forEach { (i, o, label) ->
                            DropdownMenuItem(text = { Text(label) }, onClick = {
                                inS = i; outS = o; BreathSettings.setPace(context, i, o); paceMenu = false
                            })
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Ring guides my breath", color = EColors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text("A tap at each in-breath, so eyes can close", color = EColors.textSecondary, fontSize = 13.sp)
                }
                Switch(
                    checked = guides, onCheckedChange = { guides = it; BreathSettings.setRingGuides(context, it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = EColors.accent),
                )
            }
        }
    }
}

private fun fmt(v: Float): String = if (v == v.toInt().toFloat()) v.toInt().toString() else "%.1f".format(v)

/**
 * Session player: phases, breath circle (4 in / 7 out by default), a ring tap at each in-breath,
 * and calm images changing every 20 s behind it.
 */
@Composable
fun MeditationSessionScreen(kind: MeditationKind, touches: RingTouches, onClose: () -> Unit) {
    val context = LocalContext.current
    val inS = if (kind == MeditationKind.CALM_BREATH) 4f else BreathSettings.inSeconds(context)
    val outS = if (kind == MeditationKind.CALM_BREATH) 7f else BreathSettings.outSeconds(context)
    val guides = remember { BreathSettings.ringGuides(context) }
    var elapsed by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(true) }
    var inhale by remember { mutableStateOf(true) }
    var secondsLeft by remember { mutableIntStateOf(inS.toInt()) }
    var finished by remember { mutableStateOf(false) }
    var feelAfter by remember { mutableStateOf<String?>(null) }

    val phaseIndex = kind.phaseIndexAt(elapsed)
    val phaseName = kind.phases[phaseIndex].first
    val breathing = kind.isBreathingAt(elapsed)

    LaunchedEffect(kind) {
        var breathT = 0f
        var ticks = 0
        while (elapsed < kind.totalSeconds && !finished) {
            delay(250)
            if (!running) continue
            breathT += 0.25f
            val cycle = inS + outS
            val pos = breathT % cycle
            val nowInhale = pos < inS
            if (nowInhale != inhale) {
                inhale = nowInhale
                if (inhale && kind.isBreathingAt(elapsed) && guides) touches.play(TouchBlock.TAP.pattern)
            }
            secondsLeft = kotlin.math.ceil(if (nowInhale) inS - pos else cycle - pos).toInt()
            ticks += 1
            if (ticks % 4 == 0) elapsed += 1
        }
        finished = true
    }

    val circleScale by animateFloatAsState(
        targetValue = if (!breathing) 1f else if (inhale) 1.12f else 0.78f,
        animationSpec = tween(durationMillis = ((if (inhale) inS else outS) * 1000).toInt()),
        label = "breath",
    )

    Box(Modifier.fillMaxSize().background(CalmGround)) {
        val images = kind.images
        Crossfade(targetState = (elapsed / MeditationKind.SECONDS_PER_IMAGE) % images.size, animationSpec = tween(2500), label = "calm") { i ->
            Image(painterResource(images[i]), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(CalmGround.copy(alpha = 0.55f), CalmGround.copy(alpha = 0.25f), CalmGround.copy(alpha = 0.85f))),
            ),
        )
        if (!finished) {
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { finished = true }) { Icon(Icons.Filled.Close, contentDescription = "End session", tint = EColors.text) }
                    Spacer(Modifier.weight(1f))
                    Text("${timeString((kind.totalSeconds - elapsed).toDouble())} left", color = EColors.textSecondary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.size(48.dp))
                }
                Text(
                    "PHASE ${phaseIndex + 1} · ${phaseName.uppercase()}", color = CalmBlue, fontSize = 12.sp,
                    fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, modifier = Modifier.padding(top = 8.dp),
                )
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(240.dp).clip(CircleShape).border(1.dp, Color(0xFF22324D), CircleShape))
                    Box(
                        Modifier.size(190.dp).scale(circleScale).clip(CircleShape).background(Color(0xFF1B3355).copy(alpha = 0.6f))
                            .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape),
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (breathing) (if (inhale) "Breathe in" else "Breathe out") else if (phaseName == "Reflect") "Reflect" else "Let go",
                            color = EColors.text, fontSize = 28.sp, fontWeight = FontWeight.Bold,
                        )
                        Text(
                            if (breathing) "$secondsLeft" else if (phaseName == "Reflect") "What do you feel now?" else "Soften from head to toe",
                            color = EColors.textSecondary, fontSize = 15.sp,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(52.dp))
                    Box(
                        Modifier.size(72.dp).clip(CircleShape).background(CalmBlue).clickable { running = !running },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(if (running) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = if (running) "Pause" else "Resume", tint = CalmGround, modifier = Modifier.size(30.dp))
                    }
                    Box(
                        Modifier.size(52.dp).clip(CircleShape).background(CalmCard).clickable { finished = true },
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Filled.Check, contentDescription = "Finish session", tint = EColors.text) }
                }
            }
        } else {
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Spacer(Modifier.height(24.dp))
                Text(
                    "${kind.title.uppercase()} · ${if (elapsed < 60) "$elapsed SEC" else "${elapsed / 60} MIN"}",
                    color = CalmBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp,
                )
                Text("Nicely done", color = EColors.text, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                Text("How do you feel now?", color = EColors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Calmer", "About the same", "Restless").forEach { label ->
                        val on = feelAfter == label
                        Text(
                            label, color = if (on) EColors.ground else EColors.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.clip(RoundedCornerShape(22.dp)).background(if (on) EColors.text else EColors.cardRaised)
                                .clickable { feelAfter = label }.padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = onClose,
                    colors = ButtonDefaults.buttonColors(containerColor = EColors.text, contentColor = EColors.ground),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                ) { Text("Done", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
            }
        }
    }
}
