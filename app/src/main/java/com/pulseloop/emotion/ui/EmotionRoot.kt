package com.pulseloop.emotion.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulseloop.emotion.CalmCue
import com.pulseloop.emotion.EmotionData
import com.pulseloop.emotion.EmotionIntents
import com.pulseloop.emotion.EmotionStore
import com.pulseloop.emotion.Feeling
import com.pulseloop.emotion.MeditationKind
import com.pulseloop.emotion.MusicTrack
import com.pulseloop.emotion.ReminderScheduler
import com.pulseloop.emotion.RingTouches
import com.pulseloop.ring.RingBLEClient
import kotlinx.coroutines.delay

/** Screens of the emotion app, above the tabs. */
sealed class EScreen {
    data object Tabs : EScreen()
    data class Builder(val experienceId: String?, val templateId: String?) : EScreen()
    data class Player(val experienceId: String) : EScreen()
    data class Meditation(val kind: MeditationKind) : EScreen()
    data object CalmCueSettings : EScreen()
    data object Reminders : EScreen()
    data class CueReceived(val eventId: String) : EScreen()
    data class TrainIntro(val feeling: Feeling) : EScreen()
    data class Training(val feeling: Feeling, val experienceId: String?, val trackId: String?) : EScreen()
    data object Learn : EScreen()
    data object VibeCheck : EScreen()
}

/** Tabs, same order as iOS: Home, Experiences, Practice, Meditate, Devices. */
object ETab {
    const val HOME = 0
    const val EXPERIENCES = 1
    const val PRACTICE = 2
    const val MEDITATE = 3
    const val DEVICES = 4
}

/**
 * Root of the emotion app on Android. Same tabs and flows as iOS `EmotionRootView`; ring pairing
 * and full health stats still live in the original PulseLoop screens, reached through
 * [onOpenOriginalUi].
 *
 * @param onOpenOriginalUi switch to the original UI, optionally opening a route there.
 */
@Composable
fun EmotionRoot(
    bleClient: RingBLEClient,
    coordinator: com.pulseloop.service.RingSyncCoordinator,
    onOpenOriginalUi: (route: String?) -> Unit,
) {
    val context = LocalContext.current
    val store = remember { EmotionStore.get(context) }
    val data = remember { EmotionData.get(context) }
    val scope = rememberCoroutineScope()
    val touches = remember { RingTouches(context, bleClient, scope) }
    var stack by remember { mutableStateOf(listOf<EScreen>()) }
    val screen = stack.lastOrNull() ?: EScreen.Tabs
    fun push(s: EScreen) { stack = stack + s }
    fun pop() { stack = stack.dropLast(1) }
    fun replace(s: EScreen) { stack = stack.dropLast(1) + s }
    fun home() { stack = emptyList() }
    var tab by rememberSaveable { mutableIntStateOf(ETab.HOME) }
    val experiences by store.experiences.collectAsState()
    val state by data.state.collectAsState()

    // Photos added in a builder that was closed without saving.
    LaunchedEffect(Unit) { store.pruneImages() }

    // Reminders: plan, then deliver due cues while the app is open (iOS checks every 30 s too).
    LaunchedEffect(Unit) {
        ReminderScheduler.rebuild(context)
        while (true) {
            ReminderScheduler.deliverDue(context, "app open")
            delay(30_000)
        }
    }

    // Notification taps and "Play on phone" from the watch.
    val pendingOpen by EmotionIntents.pending.collectAsState()
    LaunchedEffect(pendingOpen) {
        val open = pendingOpen ?: return@LaunchedEffect
        when {
            open == CalmCue.OPEN_CALM_BREATH -> push(EScreen.Meditation(MeditationKind.CALM_BREATH))
            open.startsWith(EmotionIntents.PLAY_PREFIX) -> {
                val id = open.removePrefix(EmotionIntents.PLAY_PREFIX)
                if (store.get(id) != null) push(EScreen.Player(id))
            }
            open.startsWith(ReminderScheduler.CUE_PREFIX) -> push(EScreen.CueReceived(open.removePrefix(ReminderScheduler.CUE_PREFIX)))
            else -> return@LaunchedEffect
        }
        EmotionIntents.pending.value = null
    }

    // First run: onboarding (it asks about devices too).
    if (!state.profile.onboardingCompleted) {
        OnboardingFlow(store, touches, bleClient, onOpenPairing = { onOpenOriginalUi("pairing") })
        return
    }

    BackHandler(enabled = stack.isNotEmpty()) { pop() }

    // Asked once, for people who used the app before onboarding asked it.
    val devicePrefs = remember { context.getSharedPreferences("emotion.devices", android.content.Context.MODE_PRIVATE) }
    var askDevices by remember { mutableStateOf(!devicePrefs.getBoolean("asked", false)) }
    if (askDevices && screen == EScreen.Tabs) {
        DevicesQuestion(hasRing = bleClient.hasLastKnownRing) { ring, watch ->
            devicePrefs.edit().putBoolean("asked", true).apply()
            com.pulseloop.emotion.DevicePrefs.setUseRing(context, ring, bleClient)
            com.pulseloop.emotion.WatchLink.setUseWatch(watch)
            askDevices = false
            // A ring that isn't paired yet: go to Devices to pair it.
            if (ring && !bleClient.hasLastKnownRing) tab = ETab.DEVICES
        }
    }

    when (val s = screen) {
        is EScreen.Builder -> BuilderScreen(
            store = store,
            touches = touches,
            experienceId = s.experienceId,
            templateId = s.templateId,
            onClose = { pop() },
            onSaved = { id, startNow ->
                val exp = store.get(id)
                if (startNow && exp != null) replace(EScreen.Training(exp.feeling, id, null)) else pop()
            },
        )
        is EScreen.Player -> {
            val experience = experiences.firstOrNull { it.id == s.experienceId }
            if (experience == null) {
                LaunchedEffect(Unit) { pop() }
            } else {
                PlayerScreen(experience = experience, store = store, touches = touches, onClose = { pop() })
            }
        }
        is EScreen.Meditation -> MeditationSessionScreen(
            kind = s.kind, touches = touches, bleClient = bleClient, coordinator = coordinator,
            onClose = { pop() },
        )
        EScreen.CalmCueSettings -> CalmCueScreen(
            touches = touches,
            onBack = { pop() },
            onTryBreath = { push(EScreen.Meditation(MeditationKind.CALM_BREATH)) },
        )
        EScreen.Reminders -> RemindersScreen(onBack = { pop() })
        is EScreen.CueReceived -> CueReceivedScreen(
            eventId = s.eventId,
            onDone = { pop() },
            onFewerReminders = { replace(EScreen.Reminders) },
        )
        is EScreen.TrainIntro -> TrainIntroScreen(
            feeling = s.feeling,
            onBack = { pop() },
            onBegin = { track -> push(EScreen.Training(s.feeling, null, track.id)) },
        )
        is EScreen.Training -> TrainingSessionScreen(
            feeling = s.feeling,
            experienceId = s.experienceId,
            track = MusicTrack.byId(s.trackId),
            store = store,
            touches = touches,
            // A finished practice goes back to the hub; ending early returns to where you were.
            onClose = { finished -> if (finished && s.experienceId == null) home() else pop() },
        )
        EScreen.Learn -> LearnScreen(onBack = { pop() })
        EScreen.VibeCheck -> VibeTestScreen(touches, progress = null, onBack = { pop() }, onDone = { pop() })
        EScreen.Tabs -> Scaffold(
            containerColor = EColors.ground,
            bottomBar = {
                NavigationBar(containerColor = EColors.tabBar) {
                    val colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = EColors.accent,
                        selectedTextColor = EColors.accent,
                        unselectedIconColor = EColors.textMuted,
                        unselectedTextColor = EColors.textMuted,
                        indicatorColor = EColors.cardRaised,
                    )
                    listOf(
                        Triple(ETab.HOME, "Home", Icons.Filled.Home),
                        Triple(ETab.EXPERIENCES, "Experiences", Icons.Filled.Collections),
                        Triple(ETab.PRACTICE, "Practice", Icons.Filled.Favorite),
                        Triple(ETab.MEDITATE, "Meditate", Icons.Filled.SelfImprovement),
                        Triple(ETab.DEVICES, "Devices", Icons.Filled.RadioButtonChecked),
                    ).forEach { (i, label, icon) ->
                        NavigationBarItem(
                            selected = tab == i, onClick = { tab = i }, colors = colors,
                            icon = { Icon(icon, contentDescription = null) },
                            label = { Text(label, fontSize = 11.sp, maxLines = 1) },
                        )
                    }
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                when (tab) {
                    ETab.HOME -> HomeScreen(
                        store = store, touches = touches, bleClient = bleClient,
                        onLive = { push(EScreen.Training(it.feeling, it.id, null)) },
                        onNew = { templateId -> push(EScreen.Builder(null, templateId)) },
                        onReminders = { push(EScreen.Reminders) },
                        onDevices = { tab = ETab.DEVICES },
                        onLibrary = { tab = ETab.EXPERIENCES },
                        onPractice = { tab = ETab.PRACTICE },
                        onMeditate = { tab = ETab.MEDITATE },
                        onLearn = { push(EScreen.Learn) },
                    )
                    ETab.EXPERIENCES -> ExperiencesScreen(
                        store = store,
                        touches = touches,
                        onPlay = { push(EScreen.Player(it)) },
                        onEdit = { push(EScreen.Builder(it, null)) },
                        onNew = { templateId -> push(EScreen.Builder(null, templateId)) },
                    )
                    ETab.PRACTICE -> PracticeHubScreen(
                        onPractise = { push(EScreen.TrainIntro(it)) },
                        onReminders = { push(EScreen.Reminders) },
                    )
                    ETab.MEDITATE -> MeditateScreen(onStart = { push(EScreen.Meditation(it)) })
                    else -> RingScreen(
                        bleClient = bleClient, touches = touches, onOpenOriginalUi = onOpenOriginalUi,
                        onOpenCalmCue = { push(EScreen.CalmCueSettings) },
                        onOpenReminders = { push(EScreen.Reminders) },
                        onOpenLearn = { push(EScreen.Learn) },
                        onOpenVibeCheck = { push(EScreen.VibeCheck) },
                    )
                }
            }
        }
    }
}

/** For people who used the app before onboarding: which devices play the touches. */
@Composable
private fun DevicesQuestion(hasRing: Boolean, onDone: (ring: Boolean, watch: Boolean) -> Unit) {
    var ring by remember { mutableStateOf(true) }
    var watch by remember { mutableStateOf(false) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = {},
        containerColor = EColors.card,
        title = { Text("Which devices do you use?", color = EColors.text) },
        text = {
            Column {
                Text(
                    "Kinnora plays each feeling as a touch: on a smart ring first, or on a Wear OS watch. With neither, your phone vibrates. You can change this any time under Devices.",
                    color = EColors.textSecondary, fontSize = 13.sp,
                )
                listOf(
                    Triple("Smart ring", if (hasRing) "Already paired" else "You'll pair it next", ring),
                    Triple("Wear OS watch", "Pixel Watch, Galaxy Watch…", watch),
                ).forEachIndexed { i, (title, detail, on) ->
                    androidx.compose.foundation.layout.Row(
                        Modifier.fillMaxWidth().padding(top = 10.dp)
                            .clickable { if (i == 0) ring = !ring else watch = !watch },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.Checkbox(checked = on, onCheckedChange = { if (i == 0) ring = it else watch = it })
                        Column(Modifier.padding(start = 4.dp)) {
                            Text(title, color = EColors.text, fontSize = 15.sp)
                            Text(detail, color = EColors.textMuted, fontSize = 12.sp)
                        }
                    }
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = { onDone(ring, watch) }) {
                Text(if (!ring && !watch) "Use my phone" else "Continue", color = EColors.accent)
            }
        },
    )
}
