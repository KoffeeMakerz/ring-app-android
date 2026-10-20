package com.pulseloop.emotion.ui

import android.content.pm.ApplicationInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulseloop.data.PulseLoopDatabase
import com.pulseloop.emotion.CalmCue
import com.pulseloop.emotion.Feeling
import com.pulseloop.emotion.RingTouches
import com.pulseloop.emotion.TouchPattern

/** Calm cue when tense (beta, off by default): status, rules, sensitivity, try it, recent checks. */
@Composable
fun CalmCueScreen(touches: RingTouches, onBack: () -> Unit, onTryBreath: () -> Unit) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(CalmCue.isEnabled(context)) }
    var sensitivity by remember { mutableStateOf(CalmCue.sensitivity(context)) }
    val assessment by CalmCue.lastAssessment.collectAsState()
    val log by CalmCue.log.collectAsState()
    val baseline by produceState(initialValue = CalmCue.Baseline(), enabled) {
        value = CalmCue.baseline(PulseLoopDatabase.getInstance(context))
    }
    val debuggable = remember { (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0 }
    LaunchedEffect(Unit) { CalmCue.loadLog(context) }

    Column(Modifier.fillMaxSize().background(EColors.ground).statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = EColors.text) }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("BETA · OFF BY DEFAULT", color = Color(0xFFB892FF), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
            Text("A calm cue when you seem tense", color = EColors.text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text(
                "When you've been sitting still and your ring's readings look tense compared with your normal, your ring plays a slow-down touch and you get a 1½-minute breath: in for 4, out for 7. It's a guess, so it will sometimes be wrong.",
                color = EColors.textSecondary, fontSize = 15.sp,
            )
            if (enabled) {
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Right now", color = EColors.text, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        TextButton(onClick = { CalmCue.evaluateLater(context, "manual check", force = true) }) { Text("Check now", color = EColors.accent) }
                    }
                    Text(assessment, color = EColors.textSecondary, fontSize = 14.sp)
                }
            }
            Card {
                Text("Only when all of these are true", color = EColors.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                listOf(
                    "Still for ${CalmCue.STILL_MINUTES} minutes, so a walk or workout never counts",
                    "A fresh reading from the ring in the last ${CalmCue.READING_WINDOW_MINUTES} minutes",
                    "Stress score, heart rate or HRV clearly off your normal",
                    "At most once every ${CalmCue.COOLDOWN_HOURS} hours, never in quiet hours",
                ).forEach { line ->
                    Row(Modifier.padding(top = 8.dp)) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = EColors.text, modifier = Modifier.size(18.dp))
                        Text(line, color = EColors.text, fontSize = 14.sp, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
            Card {
                Row {
                    Text("Learning your normal", color = EColors.text, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Text("${minOf(baseline.days, 7)} of 7 days", color = EColors.textSecondary, fontSize = 13.sp)
                }
                LinearProgressIndicator(
                    progress = { minOf(baseline.days, 7) / 7f }, color = Color(0xFF7FB2F0), trackColor = EColors.line,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
                )
                Text(
                    if (baseline.ready) "Resting heart rate about ${baseline.restingHr?.toInt()} bpm${baseline.hrvMedian?.let { " · usual HRV ${it.toInt()} ms" } ?: ""}. Works from now; gets better over 7 days."
                    else "Needs ${CalmCue.MIN_BASELINE_DAYS} days of heart readings from your ring before it can start.",
                    color = EColors.textSecondary, fontSize = 13.sp,
                )
            }
            Card {
                Text("When to cue", color = EColors.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Row(Modifier.padding(top = 10.dp).clip(RoundedCornerShape(11.dp)).background(EColors.cardRaised).padding(3.dp)) {
                    listOf("elevated" to "Raised or high", "high" to "High only").forEach { (value, label) ->
                        val on = sensitivity == value
                        Box(
                            Modifier.weight(1f).clip(RoundedCornerShape(9.dp)).background(if (on) EColors.card else Color.Transparent)
                                .clickable { sensitivity = value; CalmCue.setSensitivity(context, value) }.padding(vertical = 9.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(label, color = if (on) EColors.text else EColors.textSecondary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                    }
                }
            }
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(CircleShape).background(Feeling.CALM.color))
                    Column(Modifier.weight(1f).padding(start = 12.dp)) {
                        Text("Slow down", color = EColors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text("Three soft hums, each gap longer", color = EColors.textSecondary, fontSize = 13.sp)
                    }
                    Button(
                        onClick = { touches.play(TouchPattern.CALM_CUE) },
                        colors = ButtonDefaults.buttonColors(containerColor = Feeling.CALM.color, contentColor = Color(0xFF0B1220)),
                    ) { Text("Feel it", fontWeight = FontWeight.Bold) }
                }
            }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(EColors.card).clickable(onClick = onTryBreath).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Try the calm breath", color = EColors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text("1½ min · in for 4, out for 7 · ring taps each in-breath", color = EColors.textSecondary, fontSize = 13.sp)
                }
                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = EColors.textMuted)
            }
            if (log.isNotEmpty()) {
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Recent checks", color = EColors.text, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        TextButton(onClick = { CalmCue.clearLog(context) }) { Text("Clear", color = EColors.accent) }
                    }
                    log.take(10).forEach { Text(it, color = EColors.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) }
                }
            }
            if (debuggable) {
                Card {
                    Text("DEVELOPER", color = Color(0xFFF5D76E), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                    TextButton(onClick = { CalmCue.playTest(context, withNotification = true) }) {
                        Text("Test: calm cue + notification now", color = Color(0xFFF5D76E))
                    }
                    Text("Skips every check. Lock the phone right after tapping to see the notification.", color = EColors.textMuted, fontSize = 12.sp)
                }
            }
            Text(
                "Not a medical or diagnostic feature. It checks when new ring data arrives, so a cue can come a little after the moment.",
                color = EColors.textMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp),
            )
        }
        Button(
            onClick = {
                enabled = !enabled
                CalmCue.setEnabled(context, enabled)
                if (enabled) CalmCue.evaluateLater(context, "turned on", force = true)
            },
            colors = ButtonDefaults.buttonColors(containerColor = EColors.accent, contentColor = EColors.onAccent),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp).heightIn(min = 52.dp),
        ) { Text(if (enabled) "Turn off calm cue" else "Turn on calm cue", fontWeight = FontWeight.Bold, fontSize = 16.sp) }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(EColors.card).padding(16.dp)) { content() }
}
