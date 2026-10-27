package com.pulseloop.emotion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.Waves
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulseloop.emotion.DevicePrefs
import com.pulseloop.emotion.EmotionData
import com.pulseloop.emotion.EmotionStore
import com.pulseloop.emotion.Experience
import com.pulseloop.emotion.Feeling
import com.pulseloop.emotion.RingTouches
import com.pulseloop.emotion.SlideImage
import com.pulseloop.emotion.SlidePlaceholder
import com.pulseloop.emotion.VisionTemplate
import com.pulseloop.emotion.WatchLink
import com.pulseloop.ring.RingBLEClient
import com.pulseloop.ring.RingConnectionState
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

/**
 * 7.2 Home: experiences first: today's vision to live, the next cue, your other visions, and a
 * smaller way into practising single emotions. Port of iOS `HomeView`.
 */
@Composable
fun HomeScreen(
    store: EmotionStore,
    touches: RingTouches,
    bleClient: RingBLEClient,
    onLive: (Experience) -> Unit,
    onNew: (templateId: String?) -> Unit,
    onReminders: () -> Unit,
    onDevices: () -> Unit,
    onLibrary: () -> Unit,
    onPractice: () -> Unit,
    onMeditate: () -> Unit,
    onLearn: () -> Unit,
) {
    val context = LocalContext.current
    val experiences by store.experiences.collectAsState()
    val data by EmotionData.get(context).state.collectAsState()
    val ring by bleClient.state.collectAsState()
    val useRing by DevicePrefs.useRingFlow(context).collectAsState()
    val watches by WatchLink.watches.collectAsState()
    val useWatch by WatchLink.useWatch.collectAsState()

    val today = experiences.maxByOrNull { it.lastPlayedAt ?: it.createdAt }
    val others = experiences.filter { it.id != today?.id }
    val now = System.currentTimeMillis()
    val next = data.events.firstOrNull { it.status == "scheduled" && it.scheduledAt > now }
    val ringActive = (useRing ?: true) && ring.connectionState == RingConnectionState.CONNECTED
    val watchActive = useWatch && watches.isNotEmpty()
    val deviceText = when {
        ringActive -> ring.batteryPercent?.let { "$it%" } ?: "On"
        watchActive -> "Watch"
        else -> "Off"
    }

    Column(
        Modifier.fillMaxSize().background(EColors.ground).verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 48.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(greeting(), color = EColors.textSecondary, fontSize = 14.sp)
                Text("Live it today", color = EColors.text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
            }
            Row(
                Modifier.clip(RoundedCornerShape(18.dp)).background(EColors.card).clickable(onClick = onDevices)
                    .padding(horizontal = 12.dp, vertical = 9.dp)
                    .semantics { contentDescription = if (ringActive || watchActive) "Devices: $deviceText" else "No device connected" },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(8.dp).clip(CircleShape).background(if (ringActive || watchActive) EColors.success else Color(0xFFF2B45A)))
                Text(deviceText, color = EColors.text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
            }
        }

        if (today != null) TodayCard(today, onLive = { onLive(today) }, onFeel = { touches.play(today.touchSequence) })
        else FirstVisionCard(onNew)

        Row(
            Modifier.fillMaxWidth().eCard().clickable(onClick = onReminders).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(EColors.cardRaised), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Alarm, contentDescription = null, tint = EColors.accent)
            }
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                val nextText = next?.let { e ->
                    val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(e.scheduledAt))
                    val isToday = e.scheduledAt < com.pulseloop.emotion.ReminderScheduler.startOfDay(now, 1)
                    if (isToday) "Next cue around $time" else "Next cue tomorrow, $time"
                } ?: "No reminders yet"
                Text(nextText, color = EColors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                val subtitle = when {
                    next == null -> "Set reminders to keep your vision alive"
                    else -> experiences.firstOrNull { it.id == next.experienceId }
                        ?.let { "${it.title} · " + it.feelings.joinToString(", then ") { f -> f.label.lowercase() } }
                        ?: next.feeling.label
                }
                Text(subtitle, color = EColors.textSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = EColors.textMuted)
        }

        if (today != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel("YOUR EXPERIENCES")
                Box(Modifier.weight(1f))
                TextLink("See all", onClick = onLibrary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                others.take(2).forEach { exp ->
                    Column(
                        Modifier.weight(1f).heightIn(min = 132.dp).eCard(18.dp).clickable { onLive(exp) },
                    ) {
                        Box(Modifier.fillMaxWidth().height(64.dp)) {
                            val slides = exp.slides
                            if (slides.isEmpty()) SlidePlaceholder(exp.feeling, Modifier.fillMaxSize())
                            else SlideImage(slides[0], Modifier.fillMaxSize(), maxSide = 400)
                        }
                        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(exp.title.ifBlank { "My vision" }, color = EColors.text, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                            ArcDots(exp.feelings)
                        }
                    }
                }
                Column(
                    Modifier.weight(1f).heightIn(min = 132.dp).clip(RoundedCornerShape(18.dp))
                        .border(1.5.dp, Color(0xFF4A4160), RoundedCornerShape(18.dp)).clickable { onNew(null) },
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, tint = EColors.accent)
                    Text("New experience", color = EColors.accent, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().eCard().clickable(onClick = onPractice).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Practise an emotion", color = EColors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("Strengthen a touch on its own · 5 min", color = EColors.textSecondary, fontSize = 13.sp)
            }
            ArcDots(listOf(Feeling.JOY, Feeling.GRATITUDE, Feeling.LOVE), size = 10.dp)
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = EColors.textMuted)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Quick("Meditate", Icons.Filled.Waves, Color(0xFF7FB2F0), Modifier.weight(1f), onMeditate)
            Quick("Learn", Icons.Filled.OndemandVideo, Color(0xFFB892FF), Modifier.weight(1f), onLearn)
        }
    }
}

@Composable
private fun TodayCard(exp: Experience, onLive: () -> Unit, onFeel: () -> Unit) {
    Column(Modifier.fillMaxWidth().eCard(24.dp)) {
        Box(Modifier.fillMaxWidth().height(104.dp)) {
            val slides = exp.slides
            if (slides.isEmpty()) SlidePlaceholder(exp.feeling, Modifier.fillMaxSize())
            else SlideImage(slides[0], Modifier.fillMaxSize(), maxSide = 800)
            Text(
                "TODAY'S EXPERIENCE", color = EColors.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp,
                modifier = Modifier.padding(12.dp).clip(RoundedCornerShape(13.dp)).background(EColors.ground).padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
        Column(Modifier.padding(18.dp)) {
            Text(exp.title.ifBlank { "My vision" }, color = EColors.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            exp.visionPoints.firstOrNull()?.let {
                Text("“$it.”", color = EColors.textSecondary, fontSize = 13.sp, maxLines = 2, modifier = Modifier.padding(top = 3.dp))
            }
            ArcChips(exp.feelings, Modifier.padding(top = 12.dp))
            Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp)).background(EColors.accent).clickable(onClick = onLive),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = EColors.onAccent)
                    Text("Live it · ${maxOf(1, exp.track.durationS / 60 + 1)} min", color = EColors.onAccent, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 6.dp))
                }
                Box(
                    Modifier.padding(start = 10.dp).size(48.dp).clip(CircleShape).background(EColors.ground).clickable(onClick = onFeel),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Vibration, contentDescription = "Feel this vision's ring touches now", tint = EColors.accent) }
            }
        }
    }
}

@Composable
private fun FirstVisionCard(onNew: (String?) -> Unit) {
    Column(Modifier.fillMaxWidth().eCard(24.dp).padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("What would you love to live?", color = EColors.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Text("Pick a vision, feel it as if it's already real, and your ring will bring it back through the day.", color = EColors.textSecondary, fontSize = 14.sp)
        VisionTemplate.all.take(3).forEach { t -> VisionTemplateRow(t, selected = false) { onNew(t.id) } }
        TextLink("Write my own", Modifier.align(Alignment.CenterHorizontally)) { onNew(null) }
    }
}

@Composable
fun VisionTemplateRow(t: VisionTemplate, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(EColors.cardRaised)
            .border(1.5.dp, if (selected) EColors.accent else Color.Transparent, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(t.tile))
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(t.title, color = EColors.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(t.feelings.joinToString(" → ") { it.label }, color = EColors.textSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun Quick(title: String, icon: ImageVector, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Row(modifier.eCard(18.dp).clickable(onClick = onClick).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = color)
        Text(title, color = EColors.text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 10.dp))
    }
}

private fun greeting(): String {
    val h = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return if (h < 12) "Good morning" else if (h < 18) "Good afternoon" else "Good evening"
}

/** Short videos to get the most from your ring (iOS `LearnView`). */
@Composable
fun LearnScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(EColors.ground)) {
        ScreenBar("Learn", onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("Short videos to get the most from your ring.", color = EColors.textSecondary, fontSize = 15.sp, modifier = Modifier.padding(bottom = 8.dp))
            listOf("Getting started", "Meditation", "Safety & help").forEach { group ->
                SectionLabel(group.uppercase())
                com.pulseloop.emotion.GuideVideo.all.filter { it.group == group }.forEach { v ->
                    Row(Modifier.fillMaxWidth().eCard(16.dp).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(84.dp, 56.dp).clip(RoundedCornerShape(10.dp)).background(EColors.cardRaised), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = EColors.text)
                        }
                        Column(Modifier.padding(start = 14.dp)) {
                            Text(v.title, color = EColors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text(v.meta, color = EColors.textSecondary, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
}
