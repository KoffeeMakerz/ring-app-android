package com.pulseloop.emotion.ui

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulseloop.emotion.DevicePrefs
import com.pulseloop.emotion.EmotionData
import com.pulseloop.emotion.EmotionStore
import com.pulseloop.emotion.RingTouches
import com.pulseloop.emotion.TouchPattern
import com.pulseloop.emotion.VisionTemplate
import com.pulseloop.emotion.WatchLink
import com.pulseloop.ring.RingBLEClient
import com.pulseloop.ring.RingConnectionState
import kotlinx.coroutines.delay

private const val PREFS = "emotion.onboarding"

/**
 * First run (iOS `EmotionOnboardingView`): welcome → how it works (consent) → connect your devices
 * → feel your ring → pick a first vision → shape it → live it once → reminders for it.
 * The step is saved, so pairing a ring in the original screens comes back to the same place.
 */
@Composable
fun OnboardingFlow(
    store: EmotionStore,
    touches: RingTouches,
    bleClient: RingBLEClient,
    onOpenPairing: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    var step by remember { mutableIntStateOf(prefs.getInt("step", 0)) }
    var templateId by remember { mutableStateOf(prefs.getString("template", null)) }
    var experienceId by remember { mutableStateOf(prefs.getString("experience", null)) }
    fun go(s: Int) {
        step = s
        prefs.edit().putInt("step", s).putString("template", templateId).putString("experience", experienceId).apply()
    }
    fun finish() {
        EmotionData.get(context).updateProfile { it.copy(onboardingCompleted = true) }
        prefs.edit().clear().apply()
    }
    BackHandler(enabled = step in 1..4) { go(step - 1) }

    when (step) {
        0 -> WelcomeStep(onStart = { go(1) }, onHaveAccount = { finish() })
        1 -> ConsentStep(onBack = { go(0) }, onNext = { go(2) })
        2 -> DevicesStep(bleClient, touches, onBack = { go(1) }, onPair = onOpenPairing, onNext = { go(3) })
        3 -> VibeTestScreen(touches, progress = 3, onBack = { go(2) }, onDone = { go(4) })
        4 -> FirstVisionStep(onBack = { go(3) }, onPick = { templateId = it; go(5) }, onSkip = { finish() })
        5 -> BuilderScreen(
            store = store, touches = touches, experienceId = null, templateId = templateId,
            onClose = { go(4) },
            onSaved = { id, startNow -> experienceId = id; go(if (startNow) 6 else 7) },
        )
        6 -> {
            val exp = store.get(experienceId)
            if (exp == null) LaunchedEffect(Unit) { go(7) }
            else TrainingSessionScreen(exp.feeling, exp.id, null, store, touches) { go(7) }
        }
        else -> FirstReminderScreen(experienceId) { finish() }
    }
}

/** Back arrow + step dots. */
@Composable
private fun OnboardingHeader(progress: Int?, total: Int = 4, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(48.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = EColors.text) }
        if (progress != null) {
            Row(Modifier.weight(1f).padding(start = 8.dp, end = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                (1..total).forEach { i ->
                    Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(2.dp)).background(if (i <= progress) EColors.accent else EColors.line))
                }
            }
        }
    }
}

@Composable
private fun StepColumn(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(
        Modifier.fillMaxSize().background(EColors.ground).statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 8.dp),
        content = content,
    )
}

/** 1.1 Welcome. */
@Composable
private fun WelcomeStep(onStart: () -> Unit, onHaveAccount: () -> Unit) {
    StepColumn {
        Text("KINNORA", color = EColors.text, fontSize = 18.sp, fontWeight = FontWeight.Light, letterSpacing = 8.sp, modifier = Modifier.padding(top = 24.dp))
        Spacer(Modifier.weight(1f))
        KinnoraMark(Modifier.size(220.dp).align(Alignment.CenterHorizontally))
        Spacer(Modifier.weight(1f))
        Text("Feel it.\nThen let your ring remind you.", color = EColors.text, fontSize = 34.sp, fontWeight = FontWeight.Bold, lineHeight = 40.sp,
            modifier = Modifier.padding(bottom = 14.dp))
        Text("Practise gratitude, love and joy with music, and give each feeling its own touch on your ring.",
            color = EColors.textSecondary, fontSize = 16.sp, modifier = Modifier.padding(bottom = 32.dp))
        PrimaryButton("Get started", onClick = onStart)
        TextLink("I already have an account", Modifier.align(Alignment.CenterHorizontally), color = EColors.text, onClick = onHaveAccount)
    }
}

/** The Kinnora mark: a ring lit like an eclipse around a four-point star (same as the app icon). */
@Composable
fun KinnoraMark(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val side = minOf(size.width, size.height)
        val c = Offset(size.width / 2, size.height / 2)
        val ring = side * 0.3f
        drawCircle(
            brush = Brush.linearGradient(listOf(Color(0xFFB8805A), Color(0xFFE2B48C), Color(0xFFFFE3C8)),
                start = Offset(c.x - ring, c.y - ring), end = Offset(c.x + ring, c.y + ring)),
            radius = ring, center = c, style = Stroke(width = maxOf(1.5f, side * 0.011f)),
        )
        drawArc(Color(0x80FFC9A6), startAngle = -10f, sweepAngle = 110f, useCenter = false,
            topLeft = Offset(c.x - ring, c.y - ring), size = androidx.compose.ui.geometry.Size(ring * 2, ring * 2),
            style = Stroke(width = side * 0.035f, cap = androidx.compose.ui.graphics.StrokeCap.Round))
        drawCircle(Brush.radialGradient(listOf(Color(0x80FFDCBE), Color.Transparent), center = c, radius = side * 0.18f), radius = side * 0.2f, center = c)
        val r = side * 0.12f
        val k = r * 0.13f
        val star = Path().apply {
            moveTo(c.x, c.y - r)
            quadraticBezierTo(c.x + k, c.y - k, c.x + r, c.y)
            quadraticBezierTo(c.x + k, c.y + k, c.x, c.y + r)
            quadraticBezierTo(c.x - k, c.y + k, c.x - r, c.y)
            quadraticBezierTo(c.x - k, c.y - k, c.x, c.y - r)
            close()
        }
        drawPath(star, Color(0xFFFFECD6))
    }
}

/** 1.2 How this works (consent). */
@Composable
private fun ConsentStep(onBack: () -> Unit, onNext: () -> Unit) {
    val context = LocalContext.current
    var reminders by remember { mutableStateOf(true) }
    var heart by remember { mutableStateOf(false) }
    var photos by remember { mutableStateOf(false) }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { onNext() }
    StepColumn {
        OnboardingHeader(1, onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text("How this works", color = EColors.text, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 28.dp, bottom = 10.dp))
            Text(
                "You practise a feeling with music while your ring gives a rhythm of gentle buzzes. Over time, that rhythm can become a reminder of the feeling. It's a practice, not a promise, and results differ from person to person.",
                color = EColors.textSecondary, fontSize = 15.sp, modifier = Modifier.padding(bottom = 22.dp),
            )
            Column(Modifier.fillMaxWidth().eCard().padding(horizontal = 18.dp, vertical = 6.dp)) {
                ToggleRow("Ring reminders", "Buzz your ring at times you choose", reminders) { reminders = it }
                HorizontalDivider(color = EColors.line)
                ToggleRow("Heart rate & HRV", "Optional. Used for meditation feedback only", heart) { heart = it }
                HorizontalDivider(color = EColors.line)
                ToggleRow("Your photos", "Kept on this phone, never uploaded", photos) { photos = it }
            }
            InfoNote(
                "A wellness app, not a medical device. You can mute your ring instantly, export or delete your data any time.",
                Modifier.padding(top = 18.dp),
            )
        }
        PrimaryButton("I understand, continue", Modifier.padding(vertical = 16.dp)) {
            EmotionData.get(context).updateProfile { it.copy(consentReminders = reminders, consentHeartData = heart, consentPhotos = photos) }
            if (reminders && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
            else onNext()
        }
    }
}

/** 1.3 Connect your devices: a ring, a Wear OS watch, both or neither. */
@Composable
private fun DevicesStep(bleClient: RingBLEClient, touches: RingTouches, onBack: () -> Unit, onPair: () -> Unit, onNext: () -> Unit) {
    val context = LocalContext.current
    val useRingState by DevicePrefs.useRingFlow(context).collectAsState()
    val useRing = useRingState ?: true
    val useWatch by WatchLink.useWatch.collectAsState()
    val watches by WatchLink.watches.collectAsState()
    val state by bleClient.state.collectAsState()
    val connected = state.connectionState == RingConnectionState.CONNECTED
    LaunchedEffect(Unit) { context.getSharedPreferences("emotion.devices", Context.MODE_PRIVATE).edit().putBoolean("asked", true).apply() }
    StepColumn {
        OnboardingHeader(2, onBack = onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text("Connect your devices", color = EColors.text, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 24.dp, bottom = 10.dp))
            Text(
                "Kinnora plays each feeling as a touch on a smart ring, a Wear OS watch, or both. The ring comes first when both are on. You can change this any time under Devices.",
                color = EColors.textSecondary, fontSize = 15.sp,
            )
            SectionLabel("SMART RING")
            Row(
                Modifier.padding(top = 10.dp).fillMaxWidth().eCard(18.dp).clickable(enabled = useRing) { if (!connected) onPair() }
                    .padding(horizontal = 16.dp).heightIn(min = 68.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(36.dp).clip(CircleShape).border(5.dp, EColors.text.copy(alpha = if (useRing) 1f else 0.4f), CircleShape))
                Column(Modifier.weight(1f).padding(start = 14.dp)) {
                    Text(if (connected) (bleClient.connectedDeviceName ?: "Smart ring") else "Find my ring",
                        color = EColors.text.copy(alpha = if (useRing) 1f else 0.5f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Text(if (connected) "Connected" + (state.batteryPercent?.let { " · $it%" } ?: "") else "Scan for nearby rings",
                        color = EColors.textSecondary, fontSize = 13.sp)
                }
                if (connected) Icon(Icons.Filled.Check, contentDescription = null, tint = EColors.success)
            }
            TextLink(if (useRing) "I don't have a ring" else "I have a ring after all") { DevicePrefs.setUseRing(context, !useRing, bleClient) }
            SectionLabel("WEAR OS WATCH")
            Column(Modifier.padding(top = 10.dp).fillMaxWidth().eCard(18.dp).padding(horizontal = 16.dp)) {
                ToggleRow(
                    "Use my Wear OS watch",
                    if (watches.isNotEmpty()) "Found: ${watches.joinToString { it.name }}" else "Install Kinnora on your watch from Google Play",
                    useWatch,
                ) { WatchLink.setUseWatch(it) }
            }
            if (connected || (useWatch && watches.isNotEmpty())) {
                TextLink("Feel a test buzz") { touches.play(TouchPattern("test.single", "One buzz", listOf(300))) }
            }
        }
        val ready = (useRing && connected) || (useWatch && watches.isNotEmpty()) || !useRing
        PrimaryButton(if (ready) "Continue" else "Skip for now", Modifier.padding(vertical = 16.dp), onClick = onNext)
    }
}

/** 1.4 Vibration check: one buzz, the closest two buzzes you can feel apart, and comfort. */
@Composable
fun VibeTestScreen(touches: RingTouches, progress: Int? = null, onBack: () -> Unit, onDone: () -> Unit) {
    val context = LocalContext.current
    var step by remember { mutableIntStateOf(0) }
    var gapIndex by remember { mutableIntStateOf(1) }
    var comfort by remember { mutableStateOf<String?>(null) }
    val gaps = listOf(300, 600, 900, 1200)
    fun single() = touches.play(TouchPattern("test.single", "One buzz", listOf(300)))
    fun pair() = touches.play(TouchPattern("test.pair", "Two buzzes", listOf(200, gaps[gapIndex], 200)))
    fun save() = EmotionData.get(context).updateProfile { p -> p.copy(minFeltGapMs = gaps[gapIndex], buzzComfort = comfort ?: p.buzzComfort) }
    LaunchedEffect(Unit) { delay(600); single() }

    StepColumn {
        if (progress != null) OnboardingHeader(progress, onBack = onBack) else ScreenBar("Vibration check", onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Text("Let's feel your ring", color = EColors.text, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 24.dp, bottom = 10.dp))
            Text("Three short checks so we know how your ring buzzes. Rest your hand somewhere still.",
                color = EColors.textSecondary, fontSize = 15.sp, modifier = Modifier.padding(bottom = 22.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CheckCard(0, step, "One buzz", if (step > 0) "Felt" else "Did you feel a buzz?") {
                    Answer("Felt it", true, Modifier.weight(1f)) { step = 1; pair() }
                    Answer("Buzz again", false, Modifier.weight(1f)) { single() }
                }
                CheckCard(1, step, "Two buzzes, close together",
                    if (step > 1) "Closest you can feel: ${gaps[gapIndex]} ms" else "Did you feel two separate buzzes?") {
                    Answer("Two", true, Modifier.weight(1f)) { if (gapIndex > 0) { gapIndex--; pair() } else step = 2 }
                    Answer("One long", false, Modifier.weight(1f)) { if (gapIndex < gaps.lastIndex) { gapIndex++; pair() } else step = 2 }
                    Answer("That's fine", false, Modifier.weight(1f)) { step = 2 }
                }
                CheckCard(2, step, "Comfort", "Too strong, just right, or too weak?") {
                    listOf("strong" to "Too strong", "right" to "Just right", "weak" to "Too weak").forEach { (key, label) ->
                        Answer(label, comfort == key, Modifier.weight(1f)) { comfort = key; step = 3 }
                    }
                }
            }
        }
        Text("We'll use the closest spacing you can feel to build every pattern.", color = EColors.textMuted, fontSize = 13.sp,
            textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp))
        PrimaryButton(
            if (progress != null) "Continue" else "Save", Modifier.padding(bottom = 16.dp),
            color = if (step >= 3 || progress == null) EColors.accent else EColors.cardRaised,
            foreground = if (step >= 3 || progress == null) EColors.onAccent else EColors.textSecondary,
        ) { save(); onDone() }
    }
}

@Composable
private fun CheckCard(index: Int, step: Int, title: String, subtitle: String, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    val done = step > index
    val active = step == index
    Column(
        Modifier.fillMaxWidth().eCard(18.dp).border(1.5.dp, if (active) EColors.accent else Color.Transparent, RoundedCornerShape(18.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(32.dp).clip(CircleShape).background(if (done) EColors.success else Color.Transparent)
                    .border(2.dp, if (done) EColors.success else if (active) EColors.accent else EColors.textMuted, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (done) Icon(Icons.Filled.Check, contentDescription = null, tint = EColors.ground, modifier = Modifier.size(16.dp))
                else Text("${index + 1}", color = if (active) EColors.accent else EColors.textSecondary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.padding(start = 14.dp)) {
                Text(title, color = EColors.text.copy(alpha = if (step < index) 0.6f else 1f), fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = EColors.textSecondary, fontSize = 13.sp)
            }
        }
        if (active) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

@Composable
private fun Answer(title: String, primary: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(22.dp)).background(if (primary) EColors.accent else EColors.cardRaised).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(title, color = if (primary) EColors.onAccent else EColors.text, fontSize = 14.sp,
            fontWeight = if (primary) FontWeight.Bold else FontWeight.SemiBold, textAlign = TextAlign.Center)
    }
}

/** 7.1 Pick your first vision. */
@Composable
private fun FirstVisionStep(onBack: () -> Unit, onPick: (String?) -> Unit, onSkip: () -> Unit) {
    var picked by remember { mutableStateOf<String?>(VisionTemplate.all.firstOrNull()?.id) }
    StepColumn {
        OnboardingHeader(4, onBack = onBack)
        Text("What would you love to live?", color = EColors.text, fontSize = 30.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 20.dp, bottom = 10.dp))
        Text(
            "Pick a vision to start. You'll make it yours, then feel it as if it's already real, with your ring marking each feeling.",
            color = EColors.textSecondary, fontSize = 15.sp, modifier = Modifier.padding(bottom = 18.dp),
        )
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            VisionTemplate.all.forEach { t -> VisionTemplateRow(t, picked == t.id) { picked = t.id } }
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(EColors.cardRaised)
                    .border(1.5.dp, if (picked == null) EColors.accent else Color.Transparent, RoundedCornerShape(18.dp))
                    .clickable { picked = null }.padding(horizontal = 14.dp).heightIn(min = 64.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(EColors.card), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Edit, contentDescription = null, tint = EColors.textSecondary)
                }
                Column(Modifier.padding(start = 14.dp)) {
                    Text("Write my own", color = EColors.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("You choose the feelings", color = EColors.textSecondary, fontSize = 12.sp)
                }
            }
        }
        PrimaryButton(if (picked == null) "Write my vision" else "Shape this vision", Modifier.padding(top = 12.dp)) { onPick(picked) }
        TextLink("Skip for now", Modifier.align(Alignment.CenterHorizontally), onClick = onSkip)
    }
}
