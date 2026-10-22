package com.pulseloop.emotion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulseloop.emotion.Feeling
import com.pulseloop.emotion.ExperiencePart

/** Design tokens for the emotion app, same values as iOS `EColor`. */
object EColors {
    val ground = Color(0xFF0E0B16)
    val card = Color(0xFF1A1625)
    val cardRaised = Color(0xFF241F31)
    val line = Color(0xFF2E2840)
    val tabBar = Color(0xFF14111C)
    val text = Color(0xFFF4F0FA)
    val textSecondary = Color(0xFFABA3BA)
    val textMuted = Color(0xFF8A8299)
    val accent = Color(0xFFF2B45A)
    val onAccent = Color(0xFF1A1206)
    val success = Color(0xFF6FD6A1)
    val danger = Color(0xFFFF4D6D)
}

/** "Joy → Gratitude" as coloured chips. */
@Composable
fun ArcChips(feelings: List<Feeling>, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        feelings.forEachIndexed { i, f ->
            if (i > 0) Text("→", color = EColors.textMuted, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 6.dp))
            Row(
                Modifier.clip(RoundedCornerShape(14.dp)).background(f.ground).padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(f.color))
                Text(f.label, color = EColors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

/** One progress bar per part of the arc. */
@Composable
fun PartBars(parts: List<ExperiencePart>, fraction: (ExperiencePart) -> Float, modifier: Modifier = Modifier, track: Color? = null) {
    Row(modifier.fillMaxWidth()) {
        parts.forEachIndexed { i, part ->
            if (i > 0) Box(Modifier.width(4.dp))
            Box(
                Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp))
                    .background(track ?: part.feeling.color.copy(alpha = 0.25f)),
            ) {
                Box(Modifier.fillMaxWidth(fraction(part)).height(4.dp).background(part.feeling.color))
            }
        }
    }
}

fun timeString(seconds: Double): String {
    val s = seconds.toInt().coerceAtLeast(0)
    return "%d:%02d".format(s / 60, s % 60)
}
