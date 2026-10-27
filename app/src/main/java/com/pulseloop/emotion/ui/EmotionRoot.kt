package com.pulseloop.emotion.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.RadioButtonChecked
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.pulseloop.emotion.CalmCue
import com.pulseloop.emotion.EmotionIntents
import com.pulseloop.emotion.EmotionStore
import com.pulseloop.emotion.MeditationKind
import androidx.compose.material.icons.filled.SelfImprovement
import com.pulseloop.emotion.RingTouches
import com.pulseloop.ring.RingBLEClient

/** Screens of the emotion app (experience core). */
sealed class EScreen {
    data object Tabs : EScreen()
    data class Builder(val experienceId: String?, val templateId: String?) : EScreen()
    data class Player(val experienceId: String) : EScreen()
    data class Meditation(val kind: MeditationKind) : EScreen()
    data object CalmCueSettings : EScreen()
}

/**
 * Root of the emotion app on Android (experience core: experiences, builder with the image
 * picker, player). Ring pairing and health stats still live in the original PulseLoop screens,
 * reached through [onOpenOriginalUi].
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
    val scope = rememberCoroutineScope()
    val touches = remember { RingTouches(context, bleClient, scope) }
    var screen by remember { mutableStateOf<EScreen>(EScreen.Tabs) }
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val experiences by store.experiences.collectAsState()

    // Photos added in a builder that was closed without saving.
    LaunchedEffect(Unit) { store.pruneImages() }

    // A calm cue notification was tapped: open the calm breath.
    val pendingOpen by EmotionIntents.pending.collectAsState()
    LaunchedEffect(pendingOpen) {
        if (pendingOpen == CalmCue.OPEN_CALM_BREATH) {
            EmotionIntents.pending.value = null
            screen = EScreen.Meditation(MeditationKind.CALM_BREATH)
        }
        // "Play on phone" from the watch.
        val open = pendingOpen
        if (open != null && open.startsWith(EmotionIntents.PLAY_PREFIX)) {
            EmotionIntents.pending.value = null
            val id = open.removePrefix(EmotionIntents.PLAY_PREFIX)
            if (store.get(id) != null) screen = EScreen.Player(id)
        }
    }

    BackHandler(enabled = screen != EScreen.Tabs) { screen = EScreen.Tabs }

    // First launch: which devices do you use? (Ring and watch can be changed any time under Devices.)
    val devicePrefs = remember { context.getSharedPreferences("emotion.devices", android.content.Context.MODE_PRIVATE) }
    var askDevices by remember { mutableStateOf(!devicePrefs.getBoolean("asked", false)) }
    if (askDevices && screen == EScreen.Tabs) {
        DevicesQuestion(hasRing = bleClient.hasLastKnownRing) { ring, watch ->
            devicePrefs.edit().putBoolean("asked", true).apply()
            com.pulseloop.emotion.DevicePrefs.setUseRing(context, ring, bleClient)
            com.pulseloop.emotion.WatchLink.setUseWatch(watch)
            askDevices = false
            // A ring that isn't paired yet: go to Devices to pair it.
            if (ring && !bleClient.hasLastKnownRing) tab = 2
        }
    }

    when (val s = screen) {
        is EScreen.Builder -> BuilderScreen(
            store = store,
            touches = touches,
            experienceId = s.experienceId,
            templateId = s.templateId,
            onClose = { screen = EScreen.Tabs },
            onSaved = { id, startNow -> screen = if (startNow) EScreen.Player(id) else EScreen.Tabs },
        )
        is EScreen.Player -> {
            val experience = experiences.firstOrNull { it.id == s.experienceId }
            if (experience == null) {
                LaunchedEffect(Unit) { screen = EScreen.Tabs }
            } else {
                PlayerScreen(
                    experience = experience,
                    store = store,
                    touches = touches,
                    onClose = { screen = EScreen.Tabs },
                )
            }
        }
        is EScreen.Meditation -> MeditationSessionScreen(
            kind = s.kind, touches = touches, bleClient = bleClient, coordinator = coordinator,
            onClose = { screen = EScreen.Tabs },
        )
        EScreen.CalmCueSettings -> CalmCueScreen(
            touches = touches,
            onBack = { screen = EScreen.Tabs },
            onTryBreath = { screen = EScreen.Meditation(MeditationKind.CALM_BREATH) },
        )
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
                    NavigationBarItem(
                        selected = tab == 0, onClick = { tab = 0 }, colors = colors,
                        icon = { Icon(Icons.Filled.Collections, contentDescription = null) },
                        label = { Text("Experiences") },
                    )
                    NavigationBarItem(
                        selected = tab == 1, onClick = { tab = 1 }, colors = colors,
                        icon = { Icon(Icons.Filled.SelfImprovement, contentDescription = null) },
                        label = { Text("Meditate") },
                    )
                    NavigationBarItem(
                        selected = tab == 2, onClick = { tab = 2 }, colors = colors,
                        icon = { Icon(Icons.Filled.RadioButtonChecked, contentDescription = null) },
                        label = { Text("Devices") },
                    )
                }
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                if (tab == 0) {
                    ExperiencesScreen(
                        store = store,
                        touches = touches,
                        onPlay = { screen = EScreen.Player(it) },
                        onEdit = { screen = EScreen.Builder(it, null) },
                        onNew = { templateId -> screen = EScreen.Builder(null, templateId) },
                    )
                } else if (tab == 1) {
                    MeditateScreen(onStart = { screen = EScreen.Meditation(it) })
                } else {
                    RingScreen(
                        bleClient = bleClient, touches = touches, onOpenOriginalUi = onOpenOriginalUi,
                        onOpenCalmCue = { screen = EScreen.CalmCueSettings },
                    )
                }
            }
        }
    }
}


/** First-launch question: which devices play the touches. Nothing is required: the phone can vibrate. */
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
