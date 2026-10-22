package com.pulseloop.emotion.ui

import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import com.pulseloop.emotion.CoherenceLevel
import com.pulseloop.emotion.LiveHeart
import com.pulseloop.ring.RingBLEClient
import com.pulseloop.service.RingSyncCoordinator
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
private const val RING_TAP_EVERY_BREATHS = 3

/** Meditate tab: sessions plus breathing settings. */
@Composable
fun MeditateScreen(onStart: (MeditationKind) -> Unit) {
    val context = LocalContext.current
    var inS by remember { mutableFloatStateOf(BreathSettings.inSeconds(context)) }
    var outS by remember { mutableFloatStateOf(BreathSettings.outSeconds(context)) }
    var guides by remember { mutableStateOf(BreathSettings.ringGuides(context)) }
    var heartChart by remember { mutableStateOf(BreathSettings.heartChart(context)) }
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
                    Text("A gentle tap every third in-breath, so eyes can close. Never during coherence.", color = EColors.textSecondary, fontSize = 13.sp)
                }
                Switch(
                    checked = guides, onCheckedChange = { guides = it; BreathSettings.setRingGuides(context, it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = EColors.accent),
                )
            }
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Heart rhythm chart", color = EColors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text("Optional · HRV and coherence from the ring's heart rate", color = EColors.textSecondary, fontSize = 13.sp)
                }
                Switch(
                    checked = heartChart, onCheckedChange = { heartChart = it; BreathSettings.setHeartChart(context, it) },
                    colors = SwitchDefaults.colors(checkedTrackColor = EColors.accent),
                )
            }
        }
    }
}

private fun fmt(v: Float): String = if (v == v.toInt().toFloat()) v.toInt().toString() else "%.1f".format(v)

/**
 * Session player: phases, breath circle (4 in / 7 out by default), a ring tap every third in-breath
 * (none during coherence breathing),
 * and calm images changing every 20 s behind it.
 */
@Composable
fun MeditationSessionScreen(
    kind: MeditationKind,
    touches: RingTouches,
    bleClient: RingBLEClient,
    coordinator: RingSyncCoordinator,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val heartScope = rememberCoroutineScope()
    val heart = remember { LiveHeart(bleClient, coordinator, heartScope) }
    var showHeart by remember { mutableStateOf(BreathSettings.heartChart(context)) }
    val inhaleStarts = remember { mutableStateListOf<Long>() }
    DisposableEffect(showHeart) {
        if (showHeart) heart.start() else heart.stop()
        onDispose { heart.stop() }
    }
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
        var breathCount = 0
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
                if (inhale) {
                    val now = SystemClock.elapsedRealtime()
                    inhaleStarts.add(now)
                    inhaleStarts.removeAll { now - it > 90_000 }
                }
                if (inhale && kind.isBreathingAt(elapsed)) {
                    breathCount += 1
                    // Frequent taps were too much: tap every third in-breath, never during coherence.
                    val coherencePhase = kind.phases[kind.phaseIndexAt(elapsed)].first.lowercase().contains("coherence")
                    if (guides && !coherencePhase && (breathCount - 1) % RING_TAP_EVERY_BREATHS == 0) touches.play(TouchBlock.TAP.pattern)
                }
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
                    // Near-white so it stays clear over any background image.
                    Box(Modifier.size(240.dp).clip(CircleShape).border(1.5.dp, Color.White.copy(alpha = 0.55f), CircleShape))
                    Box(Modifier.size(190.dp).scale(circleScale).clip(CircleShape).background(Color(0xFFEEF4FF).copy(alpha = 0.9f)))
                    Column(
                        Modifier.size(width = 160.dp, height = 120.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            if (breathing) (if (inhale) "Breathe in" else "Breathe out") else if (phaseName == "Reflect") "Reflect" else "Let go",
                            color = CalmGround, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
                        )
                        Text(
                            if (breathing) "$secondsLeft" else if (phaseName == "Reflect") "What do you feel now?" else "Soften from head to toe",
                            color = CalmGround, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                if (showHeart) {
                    HeartPanel(heart, inhaleStarts, inS, inS + outS, breathing)
                    Spacer(Modifier.height(16.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(52.dp).clip(CircleShape).background(if (showHeart) CalmBlue.copy(alpha = 0.3f) else CalmCard)
                            .clickable { showHeart = !showHeart; BreathSettings.setHeartChart(context, showHeart) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (showHeart) Icons.Filled.MonitorHeart else Icons.Filled.VisibilityOff,
                            contentDescription = if (showHeart) "Hide heart rhythm chart" else "Show heart rhythm chart",
                            tint = EColors.text,
                        )
                    }
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

/** Heart rhythm (HRV) chart with in-breaths shaded, plus coherence during paced breathing. */
@Composable
private fun HeartPanel(heart: LiveHeart, inhaleStarts: List<Long>, inhaleSeconds: Float, breathPeriod: Float, breathing: Boolean) {
    heart.tick // read so this re-composes each second
    val status = heart.status
    val points = heart.recent(60)
    val message = when {
        status == LiveHeart.Status.NO_RING -> "Connect your ring to see your heart rhythm. The breathing guide works without it."
        status == LiveHeart.Status.STARTING || status == LiveHeart.Status.IDLE -> "Starting… keep your hand still. The first reading takes 10–20 seconds."
        status == LiveHeart.Status.STALLED && points.isEmpty() -> "No pulse from the ring yet. Make sure it's snug, sensor on the palm side."
        points.size < 2 -> "Waiting for heart readings…"
        else -> null
    }
    val level = heart.coherence(breathPeriod)
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp)).background(CalmCard.copy(alpha = 0.92f)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Heart rhythm · HRV", color = EColors.text, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Text(
                if (status == LiveHeart.Status.STALLED && points.isNotEmpty()) "Signal lost · hold still" else heart.current?.let { "$it bpm" } ?: "",
                color = if (status == LiveHeart.Status.STALLED) EColors.accent else EColors.textSecondary, fontSize = 12.sp,
            )
        }
        Box(Modifier.fillMaxWidth().height(84.dp).padding(top = 8.dp), contentAlignment = Alignment.Center) {
            val now = SystemClock.elapsedRealtime()
            Canvas(Modifier.fillMaxSize()) {
                val window = 60_000f
                fun x(t: Long) = size.width * (1f - (now - t) / window)
                inhaleStarts.forEach { start ->
                    val x0 = x(start).coerceAtLeast(0f)
                    val x1 = x(start + (inhaleSeconds * 1000).toLong()).coerceAtMost(size.width)
                    if (x1 > x0) drawRect(CalmBlue.copy(alpha = 0.14f), topLeft = Offset(x0, 0f), size = Size(x1 - x0, size.height))
                }
                listOf(0.25f, 0.5f, 0.75f).forEach { f ->
                    drawLine(Color.White.copy(alpha = 0.06f), Offset(0f, size.height * f), Offset(size.width, size.height * f), strokeWidth = 1f)
                }
                if (points.size > 1) {
                    val lo = points.minOf { it.second }.toFloat()
                    val hi = points.maxOf { it.second }.toFloat()
                    val mid = (lo + hi) / 2
                    val span = maxOf(8f, hi - lo) * 1.25f
                    fun y(v: Int) = size.height * (0.5f - (v - mid) / span)
                    val path = Path()
                    points.forEachIndexed { i, (t, bpm) -> if (i == 0) path.moveTo(x(t), y(bpm)) else path.lineTo(x(t), y(bpm)) }
                    drawPath(path, Color(0xFFEEF4FF), style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
                    val (lt, lb) = points.last()
                    drawCircle(EColors.accent, radius = 4.dp.toPx(), center = Offset(x(lt), y(lb)))
                }
            }
            if (message != null) {
                Text(message, color = EColors.textSecondary, fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(horizontal = 12.dp))
            }
        }
        if (breathing) {
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CoherenceLevel.entries.forEach { l ->
                    val color = if (level != l) Color(0xFF22324D) else when (l) {
                        CoherenceLevel.HIGH -> EColors.accent
                        CoherenceLevel.MEDIUM -> CalmBlue
                        CoherenceLevel.LOW -> Color(0xFF3A4560)
                    }
                    Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp)).background(color))
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
                Text("Low", color = EColors.textSecondary, fontSize = 12.sp)
                Text(
                    level?.let { "${it.label} coherence" } ?: "Measuring… ${minOf(100, heart.secondsOfData * 5)}%",
                    color = EColors.text, fontSize = 12.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                )
                Text("High", color = EColors.textSecondary, fontSize = 12.sp)
            }
        }
        Text(
            "Heart rate rising on the in-breath and falling on the out-breath is coherence. An estimate from your ring's pulse, not a medical reading.",
            color = EColors.textMuted, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp),
        )
    }
}
