package com.pulseloop.emotion.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.ExperimentalLayoutApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulseloop.emotion.EmotionData
import com.pulseloop.emotion.EmotionStore
import com.pulseloop.emotion.Feeling
import com.pulseloop.emotion.ReminderRule
import com.pulseloop.emotion.ReminderScheduler
import com.pulseloop.emotion.cuePrompt
import com.pulseloop.emotion.cueStatusText
import com.pulseloop.emotion.daysLabel
import com.pulseloop.emotion.hm
import java.text.DateFormat
import java.util.Date
import java.util.UUID

/** Same as iOS `SetTimesEditor.iPhoneTimingNote`, for Android. */
const val TIMING_NOTE = "A cue can arrive a few minutes before or after the time you set, when your phone is saving battery."

private fun timeOf(millis: Long): String = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(millis))

/** 4.1 Reminders: pause, schedules, quiet hours, today's cues. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun RemindersScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val data = remember { EmotionData.get(context) }
    val state by data.state.collectAsState()
    val experiences by EmotionStore.get(context).experiences.collectAsState()
    val profile = state.profile
    var editing by remember { mutableStateOf<ReminderRule?>(null) }
    var showQuiet by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf<ReminderRule?>(null) }
    LaunchedEffect(Unit) { ReminderScheduler.rebuild(context) }

    val todayStart = ReminderScheduler.startOfDay(System.currentTimeMillis())
    val today = state.events.filter { it.scheduledAt in todayStart until todayStart + 86_400_000L }

    val edit = editing
    if (edit != null) {
        RuleEditorScreen(edit, onDone = { r ->
            data.upsertRule(r); ReminderScheduler.rebuild(context); editing = null
        }, onCancel = { editing = null })
        return
    }

    Column(Modifier.fillMaxSize().background(EColors.ground)) {
        ScreenBar("Reminders", onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth().eCard().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (profile.remindersPaused) "Ring reminders paused" else "Ring reminders on", color = EColors.text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (profile.remindersPaused) "Until ${profile.pausedUntil?.let { timeOf(it) } ?: "you resume"}" else "Up to ${profile.dailyCap} buzzes a day",
                        color = EColors.textSecondary, fontSize = 13.sp,
                    )
                }
                Box(
                    Modifier.clip(RoundedCornerShape(18.dp)).background(EColors.cardRaised).clickable {
                        ReminderScheduler.setPaused(context, if (profile.remindersPaused) null else System.currentTimeMillis() + 3_600_000L)
                    }.padding(horizontal = 14.dp, vertical = 9.dp),
                ) { Text(if (profile.remindersPaused) "Resume" else "Pause 1 h", color = EColors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
            }

            SectionLabel("SCHEDULES")
            state.rules.sortedBy { it.createdAt }.forEach { rule ->
                val exp = experiences.firstOrNull { it.id == rule.experienceId }
                Row(
                    Modifier.fillMaxWidth().eCard(18.dp)
                        .combinedClickable(onClick = { editing = rule }, onLongClick = { confirmDelete = rule })
                        .padding(horizontal = 16.dp).heightIn(min = 64.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ArcDots(exp?.feelings ?: listOf(rule.feeling), size = 9.dp)
                    Column(Modifier.weight(1f).padding(start = 12.dp, top = 10.dp, bottom = 10.dp)) {
                        Text(exp?.title?.ifBlank { null } ?: rule.feeling.label, color = EColors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Text(describe(rule), color = EColors.textSecondary, fontSize = 13.sp)
                    }
                    Switch(
                        checked = rule.enabled,
                        onCheckedChange = { data.upsertRule(rule.copy(enabled = it)); ReminderScheduler.rebuild(context) },
                        colors = SwitchDefaults.colors(checkedTrackColor = EColors.accent, checkedThumbColor = EColors.onAccent),
                    )
                }
            }
            if (state.rules.isNotEmpty()) Text("Touch and hold a schedule to delete it.", color = EColors.textMuted, fontSize = 12.sp)
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).border(1.5.dp, Color(0xFF4A4160), RoundedCornerShape(18.dp))
                    .clickable {
                        val exp = experiences.firstOrNull()
                        editing = ReminderRule(feelingRaw = (exp?.feeling ?: profile.activeFeeling).raw, experienceId = exp?.id)
                    }
                    .padding(horizontal = 16.dp).heightIn(min = 48.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = EColors.text)
                Text("Add a schedule", color = EColors.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
            }
            Row(
                Modifier.fillMaxWidth().eCard(18.dp).clickable { showQuiet = true }.padding(horizontal = 16.dp).heightIn(min = 56.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Bedtime, contentDescription = null, tint = EColors.textSecondary)
                Text("Quiet hours", color = EColors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 12.dp))
                Text("${hm(profile.quietStartMinutes)}–${hm(profile.quietEndMinutes)}", color = EColors.textSecondary, fontSize = 14.sp)
            }

            SectionLabel("TODAY")
            Column(Modifier.fillMaxWidth().eCard(18.dp).padding(horizontal = 16.dp)) {
                if (today.isEmpty()) {
                    Text("Nothing scheduled today", color = EColors.textSecondary, fontSize = 14.sp, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp))
                }
                today.forEachIndexed { i, e ->
                    val past = e.scheduledAt <= System.currentTimeMillis()
                    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(timeOf(e.scheduledAt), color = EColors.textSecondary, fontSize = 14.sp, modifier = Modifier.width(72.dp))
                        Text(cueStatusText(e), color = if (past) EColors.text else EColors.textSecondary, fontSize = 14.sp,
                            fontWeight = if (past) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f))
                        e.feltRating?.let {
                            Text(listOf("No", "A little", "Yes")[it.coerceIn(0, 2)], color = EColors.success, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    if (i < today.lastIndex) HorizontalDivider(color = EColors.line)
                }
            }
            InfoNote(
                "At each reminder the app sends your vision's touches to the ring, and a phone notification as well. $TIMING_NOTE",
                Modifier.padding(top = 6.dp),
            )
        }
    }

    if (showQuiet) QuietHoursDialog(onDismiss = { showQuiet = false })
    confirmDelete?.let { rule ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            containerColor = EColors.card,
            title = { Text("Delete this schedule?", color = EColors.text) },
            confirmButton = {
                TextButton(onClick = { data.deleteRule(rule.id); ReminderScheduler.rebuild(context); confirmDelete = null }) {
                    Text("Delete", color = EColors.danger)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel", color = EColors.text) } },
        )
    }
}

private fun describe(r: ReminderRule): String =
    if (r.randomTimes) "${r.perDay} a day · spread · ${hm(r.windowStartMinutes)}–${hm(r.windowEndMinutes)}"
    else "${r.fixedMinutes.joinToString(", ") { hm(it) }} · ${daysLabel(r.fixedDays)}"

/** Edit one schedule: what it brings back, spread or set time. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RuleEditorScreen(rule: ReminderRule, onDone: (ReminderRule) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val experiences by EmotionStore.get(context).experiences.collectAsState()
    var r by remember(rule.id) { mutableStateOf(rule) }
    var pickTime by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(EColors.ground)) {
        ScreenBar("Schedule", onCancel)
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionLabel(if (experiences.isEmpty()) "FEELING" else "BRING BACK")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                experiences.forEach { e ->
                    DotChip(e.title.ifBlank { "My vision" }, e.feeling.color, r.experienceId == e.id) {
                        r = r.copy(experienceId = e.id, feelingRaw = e.feeling.raw)
                    }
                }
                Feeling.order.forEach { f ->
                    DotChip(f.label, f.color, r.experienceId == null && r.feeling == f) { r = r.copy(experienceId = null, feelingRaw = f.raw) }
                }
            }
            Segmented(listOf(true to "Spread through the day", false to "At a set time"), r.randomTimes, { r = r.copy(randomTimes = it) })
            if (r.randomTimes) {
                ReminderEditor(
                    perDay = r.perDay, start = r.windowStartMinutes, end = r.windowEndMinutes, checkIn = r.checkInAfter,
                    onChange = { perDay, start, end, checkIn ->
                        r = r.copy(perDay = perDay, windowStartMinutes = start, windowEndMinutes = end, checkInAfter = checkIn)
                    },
                )
            } else {
                val minutes = r.fixedMinutes.firstOrNull() ?: (9 * 60)
                Box(Modifier.fillMaxWidth().eCard().clickable { pickTime = true }.padding(vertical = 18.dp), contentAlignment = Alignment.Center) {
                    Text(hm(minutes), color = EColors.text, fontSize = 40.sp, fontWeight = FontWeight.Light)
                }
                SectionLabel("REPEAT")
                DaysPicker(if (r.fixedDays.size == 7) r.fixedDays else List(7) { true }) { r = r.copy(fixedDays = it) }
                InfoNote(TIMING_NOTE)
                if (pickTime) TimePickerDialog("Time", minutes, onDismiss = { pickTime = false }) {
                    r = r.copy(fixedMinutes = listOf(it)); pickTime = false
                }
            }
        }
        PrimaryButton("Done", Modifier.padding(20.dp).navigationBarsPadding()) {
            onDone(if (!r.randomTimes && r.fixedMinutes.isEmpty()) r.copy(fixedMinutes = listOf(9 * 60)) else r)
        }
    }
}

/** How often + window + check-in (iOS `ReminderEditor`). */
@Composable
fun ReminderEditor(perDay: Int, start: Int, end: Int, checkIn: Boolean, onChange: (perDay: Int, start: Int, end: Int, checkIn: Boolean) -> Unit) {
    var picking by remember { mutableIntStateOf(0) } // 1 start, 2 end
    Column {
        SectionLabel("HOW OFTEN")
        Segmented(listOf(2 to "2 a day", 3 to "3 a day", 5 to "5 a day"), perDay, { onChange(it, start, end, checkIn) }, Modifier.padding(top = 10.dp))
        SectionLabel("WHEN")
        Column(Modifier.padding(top = 10.dp).fillMaxWidth().eCard().padding(horizontal = 16.dp)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Between", color = EColors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text("Times vary each day", color = EColors.textSecondary, fontSize = 12.sp)
                }
                TimeChip(hm(start)) { picking = 1 }
                Text("–", color = EColors.text, modifier = Modifier.padding(horizontal = 6.dp))
                TimeChip(hm(end)) { picking = 2 }
            }
            HorizontalDivider(color = EColors.line)
            ToggleRow("Quick check-in after", "A notification to rate the moment", checkIn) { onChange(perDay, start, end, it) }
        }
    }
    if (picking != 0) {
        TimePickerDialog(if (picking == 1) "Start" else "End", if (picking == 1) start else end, onDismiss = { picking = 0 }) { m ->
            if (picking == 1) onChange(perDay, m, maxOf(end, m + 60), checkIn) else onChange(perDay, minOf(start, m - 60).coerceAtLeast(0), m, checkIn)
            picking = 0
        }
    }
}

@Composable
private fun TimeChip(text: String, onClick: () -> Unit) {
    Box(Modifier.clip(RoundedCornerShape(10.dp)).background(EColors.cardRaised).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp)) {
        Text(text, color = EColors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Quiet hours and the daily cap. */
@Composable
fun QuietHoursDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val data = remember { EmotionData.get(context) }
    val p = data.profile
    var start by remember { mutableIntStateOf(p.quietStartMinutes) }
    var end by remember { mutableIntStateOf(p.quietEndMinutes) }
    var cap by remember { mutableIntStateOf(p.dailyCap) }
    var picking by remember { mutableIntStateOf(0) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = EColors.card,
        title = { Text("Quiet hours", color = EColors.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Quiet from", color = EColors.text, modifier = Modifier.weight(1f)); TimeChip(hm(start)) { picking = 1 }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Until", color = EColors.text, modifier = Modifier.weight(1f)); TimeChip(hm(end)) { picking = 2 }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Up to $cap buzzes a day", color = EColors.text, modifier = Modifier.weight(1f))
                    TimeChip("−") { cap = (cap - 1).coerceAtLeast(1) }
                    Spacer(Modifier.width(6.dp))
                    TimeChip("+") { cap = (cap + 1).coerceAtMost(12) }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                data.updateProfile { it.copy(quietStartMinutes = start, quietEndMinutes = end, dailyCap = cap) }
                ReminderScheduler.rebuild(context)
                onDismiss()
            }) { Text("Done", color = EColors.accent) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = EColors.text) } },
    )
    if (picking != 0) {
        TimePickerDialog(if (picking == 1) "Quiet from" else "Until", if (picking == 1) start else end, onDismiss = { picking = 0 }) {
            if (picking == 1) start = it else end = it
            picking = 0
        }
    }
}

/** 4.2 Cue received: one-breath reflection after a reminder. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CueReceivedScreen(eventId: String, onDone: () -> Unit, onFewerReminders: () -> Unit) {
    val context = LocalContext.current
    val data = remember { EmotionData.get(context) }
    val event = remember(eventId) { data.state.value.events.firstOrNull { it.id == eventId } }
    val experience = remember(event) { EmotionStore.get(context).get(event?.experienceId) }
    val f = experience?.feeling ?: event?.feeling ?: Feeling.GRATITUDE
    var note by remember { mutableStateOf("") }
    var felt by remember { mutableStateOf<Int?>(null) }
    val now = timeOf(System.currentTimeMillis())

    fun save() {
        val e = event ?: return
        data.updateEvent(e.id) {
            it.copy(
                status = "delivered", deliveredAt = it.deliveredAt ?: System.currentTimeMillis(),
                feltRating = felt, note = note.ifBlank { null },
            )
        }
    }

    Column(Modifier.fillMaxSize().background(f.ground).imePadding()) {
        Column(
            Modifier.weight(1f).statusBarsPadding().padding(horizontal = 28.dp).padding(top = 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (experience != null) {
                TouchGlyph(experience.touchSequence, f.color, height = 14.dp, msPerDp = 80f)
                Text("${experience.title.uppercase()} · $now", color = f.color, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                Text(
                    experience.visionPoints.firstOrNull()?.let { "Picture it: $it." } ?: "Picture your vision, already real.",
                    color = EColors.text, fontSize = 26.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
                )
                ArcChips(experience.feelings)
            } else {
                TouchGlyph(f.touch, f.color, height = 18.dp, msPerDp = 40f)
                Text("${f.label.uppercase()} CUE · $now", color = f.color, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp)
                Text(f.cuePrompt, color = EColors.text, fontSize = 28.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
            }
        }
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(EColors.ground)
                .navigationBarsPadding().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("A few words (optional)", color = EColors.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            OutlinedTextField(
                value = note, onValueChange = { note = it }, placeholder = { Text("The sun on my walk…") },
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = EColors.text, unfocusedTextColor = EColors.text,
                    focusedBorderColor = EColors.accent, unfocusedBorderColor = EColors.line,
                    focusedContainerColor = EColors.card, unfocusedContainerColor = EColors.card,
                ),
            )
            Text(if (experience != null) "Did it feel real for a moment?" else "Did the buzz bring the feeling?", color = EColors.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(2 to "Yes", 1 to "A little", 0 to "Not really").forEach { (value, title) ->
                    val on = felt == value
                    Box(
                        Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp)).background(if (on) f.color else EColors.cardRaised)
                            .clickable { felt = value },
                        contentAlignment = Alignment.Center,
                    ) { Text(title, color = if (on) EColors.onAccent else EColors.text, fontSize = 14.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.SemiBold) }
                }
            }
            PrimaryButton("Done", color = EColors.text, foreground = EColors.ground) { save(); onDone() }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                TextLink("Fewer reminders") { save(); onFewerReminders() }
                Spacer(Modifier.width(24.dp))
                TextLink("Mute today") {
                    save()
                    ReminderScheduler.setPaused(context, ReminderScheduler.startOfDay(System.currentTimeMillis(), 1))
                    onDone()
                }
            }
        }
    }
}

/** One "at set time" reminder being edited before it becomes a [ReminderRule]. */
data class SetTime(
    val id: String = UUID.randomUUID().toString(),
    val minutes: Int,
    val days: List<Boolean> = List(7) { true },
    val on: Boolean = true,
)

/** Alarm-style list of set times (iOS `SetTimesEditor`). */
@Composable
fun SetTimesEditor(times: List<SetTime>, onChange: (List<SetTime>) -> Unit) {
    var editing by remember { mutableStateOf<SetTime?>(null) }
    var editingIsNew by remember { mutableStateOf(false) }
    val max = 6
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row {
            SectionLabel("YOUR TIMES")
            Spacer(Modifier.weight(1f))
            Text("${times.size} of $max", color = EColors.textMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
        }
        times.forEach { t ->
            Row(Modifier.fillMaxWidth().eCard(18.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).clickable { editingIsNew = false; editing = t }.padding(vertical = 8.dp)) {
                    Text(hm(t.minutes), color = EColors.text.copy(alpha = if (t.on) 1f else 0.45f), fontSize = 30.sp, fontWeight = FontWeight.Light)
                    Text(daysLabel(t.days) + if (t.on) "" else " · off", color = EColors.textSecondary, fontSize = 13.sp)
                }
                Switch(
                    checked = t.on, onCheckedChange = { on -> onChange(times.map { if (it.id == t.id) it.copy(on = on) else it }) },
                    colors = SwitchDefaults.colors(checkedTrackColor = EColors.accent, checkedThumbColor = EColors.onAccent),
                )
            }
        }
        if (times.size < max) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).border(1.5.dp, Color(0xFF4A4160), RoundedCornerShape(18.dp))
                    .clickable { editingIsNew = true; editing = SetTime(minutes = 9 * 60) }.padding(horizontal = 16.dp).heightIn(min = 52.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = EColors.accent)
                Text("Add a time", color = EColors.accent, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
    editing?.let { t ->
        SetTimeDialog(t, editingIsNew, onSave = { result ->
            val list = if (times.any { it.id == result.id }) times.map { if (it.id == result.id) result else it } else times + result
            onChange(list.sortedBy { it.minutes }); editing = null
        }, onDelete = { onChange(times.filterNot { it.id == t.id }); editing = null }, onDismiss = { editing = null })
    }
}

@Composable
private fun SetTimeDialog(initial: SetTime, isNew: Boolean, onSave: (SetTime) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var t by remember { mutableStateOf(initial) }
    var pick by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = EColors.card,
        title = { Text(if (isNew) "Add a time" else "Edit time", color = EColors.text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.fillMaxWidth().eCard(16.dp, EColors.cardRaised).clickable { pick = true }.padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
                    Text(hm(t.minutes), color = EColors.text, fontSize = 36.sp, fontWeight = FontWeight.Light)
                }
                DaysPicker(t.days) { t = t.copy(days = it) }
                Text("Your ring will play your vision's touches at ${hm(t.minutes)}, ${daysLabel(t.days).lowercase()}.", color = EColors.textSecondary, fontSize = 13.sp)
                if (!isNew) TextLink("Delete this time", color = EColors.danger, onClick = onDelete)
            }
        },
        confirmButton = { TextButton(onClick = { if (t.days.any { it }) onSave(t) }) { Text("Save", color = EColors.accent, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = EColors.text) } },
    )
    if (pick) TimePickerDialog("Time", t.minutes, onDismiss = { pick = false }) { t = t.copy(minutes = it); pick = false }
}
