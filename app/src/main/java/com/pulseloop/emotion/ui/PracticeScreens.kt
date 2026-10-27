package com.pulseloop.emotion.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulseloop.emotion.EmotionData
import com.pulseloop.emotion.EmotionStore
import com.pulseloop.emotion.Experience
import com.pulseloop.emotion.Feeling
import com.pulseloop.emotion.MusicTrack
import com.pulseloop.emotion.ReminderRule
import com.pulseloop.emotion.ReminderScheduler
import com.pulseloop.emotion.RingTouches
import com.pulseloop.emotion.TrainingRecord
import kotlinx.coroutines.delay

private val PROGRAMME = EmotionData.PROGRAMME_LENGTH

/** Practice tab: each feeling's 7-session programme (iOS `TrainHubView`). */
@Composable
fun PracticeHubScreen(onPractise: (Feeling) -> Unit, onReminders: () -> Unit) {
    val context = LocalContext.current
    val state by EmotionData.get(context).state.collectAsState()
    val experiences by EmotionStore.get(context).experiences.collectAsState()
    Column(
        Modifier.fillMaxSize().background(EColors.ground).verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 56.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Practise emotions", color = EColors.text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Text(
            "Five minutes with one feeling and its ring touch. The stronger each link, the more alive your experiences feel.",
            color = EColors.textSecondary, fontSize = 15.sp, modifier = Modifier.padding(bottom = 4.dp),
        )
        Feeling.order.forEach { feeling ->
            val done = state.training.count { it.feelingRaw == feeling.raw }.coerceAtMost(PROGRAMME)
            val used = experiences.count { feeling in it.feelings }
            Row(
                Modifier.fillMaxWidth().eCard(18.dp).clickable { onPractise(feeling) }
                    .padding(start = 16.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(feeling.color))
                        Text(feeling.label, color = EColors.text, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
                    }
                    TouchGlyph(feeling.touch, feeling.color, height = 7.dp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ThinProgress(done.toFloat() / PROGRAMME, feeling.color, Modifier.width(64.dp))
                        Text(
                            "$done of $PROGRAMME · " + if (used == 0) "not in an experience yet" else "in $used ${if (used == 1) "experience" else "experiences"}",
                            color = EColors.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
                Box(Modifier.clip(RoundedCornerShape(20.dp)).background(EColors.cardRaised).padding(horizontal = 14.dp, vertical = 11.dp)) {
                    Text("Practise", color = EColors.text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        Row(
            Modifier.padding(top = 6.dp).fillMaxWidth().eCard(16.dp).clickable(onClick = onReminders).padding(horizontal = 16.dp).heightIn(min = 52.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Alarm, contentDescription = null, tint = EColors.text)
            Text("Reminders & quiet hours", color = EColors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 12.dp))
        }
    }
}

/** 2.1 Training intro: the three steps and the track. */
@Composable
fun TrainIntroScreen(feeling: Feeling, onBack: () -> Unit, onBegin: (MusicTrack) -> Unit, showBack: Boolean = true) {
    val context = LocalContext.current
    val state by EmotionData.get(context).state.collectAsState()
    var track by remember(feeling) { mutableStateOf(MusicTrack.practiceFor(feeling)) }
    var showPicker by remember { mutableStateOf(false) }
    val session = (state.training.count { it.feelingRaw == feeling.raw } + 1).coerceAtMost(PROGRAMME)
    Column(Modifier.fillMaxSize().background(EColors.ground)) {
        if (showBack) ScreenBar("", onBack) else Spacer(Modifier.statusBarsPadding().height(16.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            Text("Session $session of $PROGRAMME", color = EColors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.align(Alignment.CenterHorizontally))
            Text("PRACTISE · ${feeling.label.uppercase()}", color = feeling.color, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp,
                modifier = Modifier.padding(top = 18.dp))
            Text("Link ${feeling.label.lowercase()} to its touch", color = EColors.text, fontSize = 30.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 6.dp, bottom = 16.dp))
            Column(
                Modifier.fillMaxWidth().heightIn(min = 160.dp).eCard(color = EColors.cardRaised),
                horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
            ) {
                Box(Modifier.size(56.dp).clip(CircleShape).background(EColors.text), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = "Play guide video", tint = EColors.ground)
                }
                Text("[GUIDE VIDEO] How training works · 2 min", color = EColors.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 10.dp))
            }
            Column(Modifier.padding(top = 22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                StepRow(1, "Settle in · 1 min", "Slow breaths to relax and focus.")
                StepRow(2, "Feel it with music · ${maxOf(1, track.durationS / 60)} min", "When the feeling peaks, your ring plays the ${feeling.label.lowercase()} rhythm.")
                StepRow(3, "Check in · 30 s", "Rate how strongly the rhythm brought the feeling back.")
            }
            Row(
                Modifier.padding(top = 22.dp).fillMaxWidth().eCard(16.dp).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(track.theme.art))
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(track.title, color = EColors.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text("${track.version} · ${track.durationLabel} · ${track.cueMarkersS.size} ring cues", color = EColors.textSecondary, fontSize = 12.sp)
                }
                TextLink("Change") { showPicker = true }
            }
        }
        PrimaryButton("Begin session", Modifier.padding(24.dp).navigationBarsPadding()) { SongPreview.stop(); onBegin(track) }
    }
    if (showPicker) TrackPickerDialog(track, onPick = { track = it; showPicker = false }, onDismiss = { showPicker = false })
}

@Composable
private fun StepRow(n: Int, title: String, detail: String) {
    Row {
        Box(Modifier.size(28.dp).clip(CircleShape).background(EColors.cardRaised), contentAlignment = Alignment.Center) {
            Text("$n", color = EColors.text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.padding(start = 14.dp)) {
            Text(title, color = EColors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(detail, color = EColors.textSecondary, fontSize = 13.sp)
        }
    }
}

/** Practice tracks first, then each song's versions (iOS `TrackPickerSheet`). */
@Composable
fun TrackPickerDialog(selected: MusicTrack, onPick: (MusicTrack) -> Unit, onDismiss: () -> Unit) {
    DisposableEffect(Unit) { onDispose { SongPreview.stop() } }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = EColors.card,
        title = { Text("Music", color = EColors.text) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Practice tracks", color = EColors.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text("1 minute, made for practising one feeling", color = EColors.textSecondary, fontSize = 12.sp)
                MusicTrack.practice.forEach { TrackRow(it, it.title, it == selected) { SongPreview.stop(); onPick(it) } }
                MusicTrack.songGroups.forEach { (_, versions) ->
                    Text(versions.first().songName, color = EColors.text, fontSize = 15.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 12.dp))
                    Text(versions.first().about, color = EColors.textSecondary, fontSize = 12.sp)
                    versions.forEach { TrackRow(it, it.version, it == selected) { SongPreview.stop(); onPick(it) } }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close", color = EColors.accent) } },
    )
}

@Composable
private fun TrackRow(track: MusicTrack, title: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        SongPlayButton(track)
        Column(Modifier.weight(1f).clickable(onClick = onClick).padding(start = 12.dp)) {
            Text(title, color = EColors.text, fontSize = 14.sp)
            Text("${track.durationLabel} · ${track.cueMarkersS.size} ring cues", color = EColors.textSecondary, fontSize = 12.sp)
        }
        if (selected) Icon(Icons.Filled.Check, contentDescription = "Selected", tint = EColors.accent)
    }
}

/**
 * 2.2 + 2.3 Training session: settle (60 s of 4-in / 7-out breathing), feel it with music (the
 * player; the ring plays the touch at each cue), then check in. With an experience, the player
 * shows its slideshow and plays its whole arc. Port of iOS `TrainingSessionView`.
 */
@Composable
fun TrainingSessionScreen(
    feeling: Feeling,
    experienceId: String?,
    track: MusicTrack?,
    store: EmotionStore,
    touches: RingTouches,
    onClose: (finished: Boolean) -> Unit,
) {
    KeepScreenOn()
    val context = LocalContext.current
    val data = remember { EmotionData.get(context) }
    val experience: Experience? = remember(experienceId) { store.get(experienceId) }
    // A single-feeling practice plays through the same player as an experience.
    val practice = remember(feeling, track) {
        Experience(id = "practice-${feeling.raw}", title = "${feeling.label} practice", feelingsRaw = listOf(feeling.raw),
            trackId = (track ?: MusicTrack.practiceFor(feeling)).id)
    }
    var stage by remember { mutableIntStateOf(0) } // 0 settle, 1 feel, 2 reflect
    var settleLeft by remember { mutableIntStateOf(60) }
    var round by remember { mutableIntStateOf(0) }
    var rating by remember { mutableIntStateOf(3) }
    var comfort by remember { mutableStateOf("right") }
    val arc = experience?.feelings ?: listOf(feeling)
    val closingTouch = if (experience != null && experience.feelings.size > 1) experience.touchSequence else arc.first().touch

    fun save() {
        data.addTraining(arc.map { TrainingRecord(feelingRaw = it.raw, experienceId = experienceId, recallRating = rating, comfort = comfort) })
        experience?.let { store.markPlayed(it.id) }
    }

    BackHandler { touches.stop(); onClose(false) }

    when (stage) {
        0 -> {
            LaunchedEffect(Unit) {
                while (settleLeft > 0) { delay(1000); settleLeft-- }
                stage = 1
            }
            Column(
                Modifier.fillMaxSize().background(EColors.ground).statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onClose(false) }) { Icon(Icons.Filled.Close, contentDescription = "End session", tint = EColors.text) }
                    Text("Step 1 · Settle in", color = EColors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f).padding(end = 48.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                }
                Spacer(Modifier.weight(1f))
                BreathCircle(feeling.color, 4f, 7f)
                Text("Breathe in for 4, out for 7", color = EColors.text, fontSize = 22.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 24.dp))
                Text("$settleLeft s", color = EColors.textSecondary, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))
                Spacer(Modifier.weight(1f))
                SecondaryButton("Skip to music") { stage = 1 }
            }
        }
        1 -> {
            val exp = experience ?: practice
            Box(Modifier.fillMaxSize()) {
                // Keyed by round, so "Train again" starts a fresh playback.
                androidx.compose.runtime.key(round) {
                    PlayerScreen(
                        experience = exp, store = store, touches = touches,
                        onClose = { onClose(false) },
                        practiceFeeling = if (experience == null) feeling else null,
                        onFinish = { stage = 2; touches.play(closingTouch) },
                    )
                }
            }
        }
        else -> ReflectView(
            feeling = feeling, experience = experience, arc = arc,
            done = data.sessions(feeling), rating = rating, comfort = comfort,
            onRating = { rating = it }, onComfort = { comfort = it },
            onSave = { save(); onClose(true) },
            onAgain = { save(); round++; stage = 1 },
        )
    }
}

@Composable
private fun ReflectView(
    feeling: Feeling, experience: Experience?, arc: List<Feeling>, done: Int, rating: Int, comfort: String,
    onRating: (Int) -> Unit, onComfort: (String) -> Unit, onSave: () -> Unit, onAgain: () -> Unit,
) {
    val arcText = arc.joinToString(", then ") { it.label.lowercase() }
    Column(
        Modifier.fillMaxSize().background(EColors.ground).statusBarsPadding().navigationBarsPadding()
            .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            experience?.let { "SESSION COMPLETE · ${it.title.uppercase()}" } ?: "SESSION ${(done + 1).coerceAtMost(PROGRAMME)} COMPLETE",
            color = feeling.color, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp, maxLines = 1, modifier = Modifier.padding(top = 12.dp),
        )
        Text("One last buzz", color = EColors.text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text("Your ring played $arcText once more, without music. Notice what comes up.", color = EColors.textSecondary, fontSize = 15.sp)
        Column(Modifier.fillMaxWidth().eCard().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                if (experience != null) "How real did it feel?" else "How much ${feeling.label.lowercase()} did the rhythm bring back?",
                color = EColors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..5).forEach { n ->
                    Box(
                        Modifier.weight(1f).heightIn(min = 52.dp).clip(RoundedCornerShape(14.dp))
                            .background(if (rating == n) feeling.color else EColors.cardRaised).clickable { onRating(n) },
                        contentAlignment = Alignment.Center,
                    ) { Text("$n", color = if (rating == n) EColors.onAccent else EColors.text, fontSize = 17.sp, fontWeight = FontWeight.Bold) }
                }
            }
            Row { Text("Nothing yet", color = EColors.textMuted, fontSize = 12.sp); Spacer(Modifier.weight(1f)); Text("Strongly", color = EColors.textMuted, fontSize = 12.sp) }
        }
        Column(Modifier.fillMaxWidth().eCard().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("How did the buzz feel?", color = EColors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("strong" to "Too strong", "right" to "Just right", "weak" to "Hard to notice").forEach { (key, title) ->
                    val on = comfort == key
                    Box(
                        Modifier.clip(RoundedCornerShape(20.dp)).background(if (on) EColors.text else EColors.cardRaised)
                            .clickable { onComfort(key) }.padding(horizontal = 14.dp, vertical = 10.dp),
                    ) { Text(title, color = if (on) EColors.ground else EColors.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
                }
            }
        }
        if (experience == null) {
            Column(Modifier.fillMaxWidth().eCard(color = Color(0xFF17131F)).padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row {
                    Text("${feeling.label} practice", color = EColors.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.weight(1f))
                    Text("${(done + 1).coerceAtMost(PROGRAMME)} of $PROGRAMME sessions", color = EColors.textSecondary, fontSize = 13.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    (0 until PROGRAMME).forEach { i ->
                        Box(Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(if (i <= done) feeling.color else EColors.line))
                    }
                }
                Text("Each practice strengthens this touch in every experience that uses it.", color = EColors.textSecondary, fontSize = 13.sp)
            }
        } else {
            ArcChips(arc)
            Text("Each feeling in this vision also counts as practice for its touch.", color = EColors.textSecondary, fontSize = 13.sp)
        }
        Spacer(Modifier.height(12.dp))
        PrimaryButton("Save", color = feeling.color, onClick = onSave)
        TextLink("Train again", Modifier.align(Alignment.CenterHorizontally), onClick = onAgain)
    }
}

/** 2.4 First reminder: spread through the day (default) or at set times. */
@Composable
fun FirstReminderScreen(experienceId: String?, onDone: () -> Unit) {
    val context = LocalContext.current
    val data = remember { EmotionData.get(context) }
    val experience = remember(experienceId) { EmotionStore.get(context).get(experienceId) }
    val arc = experience?.feelings ?: listOf(data.profile.activeFeeling)
    var setTimes by remember { mutableStateOf(false) }
    var perDay by remember { mutableIntStateOf(3) }
    var start by remember { mutableIntStateOf(9 * 60) }
    var end by remember { mutableIntStateOf(20 * 60) }
    var checkIn by remember { mutableStateOf(true) }
    var times by remember { mutableStateOf(listOf(SetTime(minutes = 7 * 60 + 30), SetTime(minutes = 13 * 60, days = com.pulseloop.emotion.WEEKDAYS))) }

    fun finish(save: Boolean) {
        if (save) {
            val rules = if (setTimes) times.map { t ->
                ReminderRule(feelingRaw = arc[0].raw, perDay = 1, experienceId = experience?.id, randomTimes = false,
                    fixedMinutes = listOf(t.minutes), fixedDays = t.days, enabled = t.on, checkInAfter = checkIn)
            } else listOf(
                ReminderRule(feelingRaw = arc[0].raw, perDay = perDay, experienceId = experience?.id, windowStartMinutes = start,
                    windowEndMinutes = end, randomTimes = true, checkInAfter = checkIn),
            )
            data.update { it.copy(rules = it.rules + rules) }
        }
        data.updateProfile { it.copy(onboardingCompleted = true) }
        ReminderScheduler.rebuild(context)
        onDone()
    }

    Column(Modifier.fillMaxSize().background(EColors.ground).statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text("${experience?.title?.ifBlank { null } ?: arc[0].label}, through the day", color = EColors.text, fontSize = 28.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 30.dp, bottom = 6.dp))
            Text("Your ring plays ${arc.joinToString(", then ") { it.label.lowercase() }}. Pause for a breath and see it as real.",
                color = EColors.textSecondary, fontSize = 15.sp, modifier = Modifier.padding(bottom = 16.dp))
            Segmented(listOf(false to "Spread through the day", true to "At set times"), setTimes, { setTimes = it }, Modifier.padding(bottom = 20.dp))
            if (setTimes) {
                SetTimesEditor(times) { times = it }
                InfoNote(TIMING_NOTE, Modifier.padding(top = 14.dp))
                Column(Modifier.padding(top = 12.dp).fillMaxWidth().eCard().padding(horizontal = 16.dp)) {
                    ToggleRow("Quick check-in after", "A notification to rate the moment", checkIn) { checkIn = it }
                }
            } else {
                ReminderEditor(perDay, start, end, checkIn) { p, s, e, c -> perDay = p; start = s; end = e; checkIn = c }
                InfoNote("Quiet hours 21:00–08:00 are on. Change them in Reminders any time.", Modifier.padding(top = 16.dp))
            }
        }
        PrimaryButton("Save and finish", Modifier.padding(top = 12.dp)) { finish(true) }
        TextLink("Later", Modifier.align(Alignment.CenterHorizontally).padding(bottom = 8.dp)) { finish(false) }
    }
}
