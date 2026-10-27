package com.pulseloop.emotion.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TimePickerDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulseloop.emotion.Feeling
import com.pulseloop.emotion.TouchPattern
import com.pulseloop.emotion.WEEKDAYS
import com.pulseloop.emotion.WEEKENDS
import kotlinx.coroutines.delay

// Shared building blocks for the emotion screens, matching the iOS components of the same names
// (PrimaryButtonStyle, ToggleRow, InfoNote, DaysPicker, TouchGlyph, ArcDots, BreathCircle…).

fun Modifier.eCard(radius: Dp = 20.dp, color: Color = EColors.card): Modifier =
    this.clip(RoundedCornerShape(radius)).background(color)

@Composable
fun PrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = EColors.accent,
    foreground: Color = EColors.onAccent,
    height: Dp = 54.dp,
    onClick: () -> Unit,
) {
    Box(
        modifier.fillMaxWidth().heightIn(min = height).clip(RoundedCornerShape(height / 2)).background(color).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = foreground, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
}

@Composable
fun SecondaryButton(text: String, modifier: Modifier = Modifier, height: Dp = 54.dp, onClick: () -> Unit) {
    Box(
        modifier.fillMaxWidth().heightIn(min = height).clip(RoundedCornerShape(height / 2)).background(EColors.cardRaised).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Text(text, color = EColors.text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun TextLink(text: String, modifier: Modifier = Modifier, color: Color = EColors.accent, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = modifier) { Text(text, color = color, fontSize = 15.sp, fontWeight = FontWeight.SemiBold) }
}

@Composable
fun ToggleRow(title: String, subtitle: String? = null, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 60.dp).clickable { onChange(!checked) }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = EColors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            subtitle?.let { Text(it, color = EColors.textSecondary, fontSize = 13.sp) }
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = EColors.accent, checkedThumbColor = EColors.onAccent),
        )
    }
}

@Composable
fun InfoNote(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color(0xFF17131F))
            .border(1.dp, EColors.line, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Icon(Icons.Filled.Info, contentDescription = null, tint = EColors.textSecondary, modifier = Modifier.size(18.dp))
        Text(text, color = EColors.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(start = 12.dp))
    }
}

/** Title bar for pushed screens: back arrow + title. */
@Composable
fun ScreenBar(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = EColors.text) }
        Text(title, color = EColors.text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

/** Two or three options in a pill (iOS segmented Picker). */
@Composable
fun <T> Segmented(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(EColors.card).padding(3.dp)) {
        options.forEach { (value, label) ->
            val on = value == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(9.dp)).background(if (on) EColors.cardRaised else EColors.card)
                    .clickable { onSelect(value) }.padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) { Text(label, color = if (on) EColors.text else EColors.textSecondary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

/** A selectable chip with a coloured dot. */
@Composable
fun DotChip(title: String, dot: Color, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(18.dp)).background(if (selected) dot.copy(alpha = 0.25f) else EColors.card)
            .border(1.5.dp, if (selected) dot else EColors.line, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Text(title, color = EColors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 6.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialog(title: String, initialMinutes: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val state = rememberTimePickerState(initialHour = initialMinutes / 60, initialMinute = initialMinutes % 60, is24Hour = true)
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = EColors.card,
        title = { Text(title, color = EColors.text) },
        text = {
            TimePicker(
                state = state,
                colors = TimePickerDefaults.colors(
                    clockDialColor = EColors.cardRaised,
                    selectorColor = EColors.accent,
                    timeSelectorSelectedContainerColor = EColors.accent,
                    timeSelectorSelectedContentColor = EColors.onAccent,
                    timeSelectorUnselectedContainerColor = EColors.cardRaised,
                    timeSelectorUnselectedContentColor = EColors.text,
                    clockDialUnselectedContentColor = EColors.text,
                ),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text("Save", color = EColors.accent) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = EColors.text) } },
    )
}

/** M T W T F S S toggles plus Every day / Weekdays / Weekends. */
@Composable
fun DaysPicker(days: List<Boolean>, onChange: (List<Boolean>) -> Unit) {
    val d = if (days.size == 7) days else List(7) { true }
    val short = listOf("M", "T", "W", "T", "F", "S", "S")
    val full = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (i in 0 until 7) {
                val on = d[i]
                Box(
                    Modifier.weight(1f).height(44.dp).clip(CircleShape).background(if (on) EColors.accent else EColors.cardRaised)
                        .clickable { onChange(d.toMutableList().also { it[i] = !it[i] }) }
                        .semantics { contentDescription = full[i] + if (on) ", on" else ", off" },
                    contentAlignment = Alignment.Center,
                ) { Text(short[i], color = if (on) EColors.onAccent else EColors.textSecondary, fontWeight = FontWeight.Bold) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Every day" to List(7) { true }, "Weekdays" to WEEKDAYS, "Weekends" to WEEKENDS).forEach { (label, value) ->
                Box(
                    Modifier.clip(RoundedCornerShape(18.dp)).background(if (d == value) EColors.cardRaised else EColors.ground)
                        .border(1.dp, EColors.line, RoundedCornerShape(18.dp)).clickable { onChange(value) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) { Text(label, color = EColors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
    }
}

/** A touch drawn as bars: one bar per buzz, its length the buzz length. */
@Composable
fun TouchGlyph(pattern: TouchPattern, color: Color, modifier: Modifier = Modifier, height: Dp = 8.dp, msPerDp: Float = 50f) {
    val total = pattern.totalMs.coerceAtLeast(1)
    Canvas(modifier.width((total / msPerDp).dp.coerceAtMost(240.dp)).height(height)) {
        val scale = size.width / total
        pattern.pulses.forEach { (start, length) ->
            drawRoundRect(
                color = color,
                topLeft = Offset(start * scale, 0f),
                size = Size((length * scale).coerceAtLeast(2f), size.height),
                cornerRadius = CornerRadius(size.height / 2, size.height / 2),
            )
        }
    }
}

/** Compact row of feeling dots. */
@Composable
fun ArcDots(feelings: List<Feeling>, size: Dp = 8.dp) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        feelings.forEach { Box(Modifier.size(size).clip(CircleShape).background(it.color)) }
    }
}

/** A circle that grows on the in-breath and shrinks on the out-breath. */
@Composable
fun BreathCircle(color: Color, inSeconds: Float = 4f, outSeconds: Float = 7f) {
    val scale = remember { Animatable(0.6f) }
    LaunchedEffect(inSeconds, outSeconds) {
        while (true) {
            scale.animateTo(1f, tween((inSeconds * 1000).toInt(), easing = FastOutSlowInEasing))
            scale.animateTo(0.6f, tween((outSeconds * 1000).toInt(), easing = FastOutSlowInEasing))
            delay(1)
        }
    }
    Box(
        Modifier.size(200.dp).scale(scale.value).clip(CircleShape).background(color.copy(alpha = 0.25f)).border(2.dp, color, CircleShape),
    )
}

/** Thin progress bar. */
@Composable
fun ThinProgress(value: Float, color: Color, modifier: Modifier = Modifier) {
    Box(modifier.height(4.dp).clip(RoundedCornerShape(2.dp)).background(EColors.line)) {
        Box(Modifier.fillMaxWidth(value.coerceIn(0f, 1f)).height(4.dp).background(color))
    }
}
