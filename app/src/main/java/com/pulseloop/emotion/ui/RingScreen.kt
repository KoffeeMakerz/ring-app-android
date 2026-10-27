package com.pulseloop.emotion.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulseloop.emotion.Feeling
import com.pulseloop.emotion.DevicePrefs
import com.pulseloop.emotion.RingTouches
import com.pulseloop.emotion.TouchTarget
import com.pulseloop.emotion.WatchLink
import com.pulseloop.ring.RingBLEClient
import com.pulseloop.ring.RingConnectionState
import com.pulseloop.wearables.WearableModel

/**
 * Devices: the ring and the watch, each with its own "use it" switch, how touches are routed (ring
 * first), each feeling's touch to try, and the way to pairing and health stats.
 */
@Composable
fun RingScreen(
    bleClient: RingBLEClient,
    touches: RingTouches,
    onOpenOriginalUi: (route: String?) -> Unit,
    onOpenCalmCue: () -> Unit,
    onOpenReminders: () -> Unit = {},
    onOpenLearn: () -> Unit = {},
    onOpenVibeCheck: () -> Unit = {},
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val useRing by DevicePrefs.useRingFlow(context).collectAsState()
    val ringOn = useRing ?: true
    val useWatch by WatchLink.useWatch.collectAsState()
    val watches by WatchLink.watches.collectAsState()
    val routeTarget by WatchLink.target.collectAsState()
    val state by bleClient.state.collectAsState()
    val connected = state.connectionState == RingConnectionState.CONNECTED
    val ringName = RingBLEClient.displayableName(WearableModel.model(state.activeWearableModelID)?.displayName)
        ?: RingBLEClient.displayableName(state.activeDeviceType?.displayName)
        ?: bleClient.connectedDeviceName ?: "Smart ring"
    // The ring's own Bluetooth name, small under the model name when it adds something.
    val deviceName = remember(state.connectionState, state.activeWearableModelID) { bleClient.connectedDeviceName }
        ?.takeIf { it != ringName }
    var confirmReset by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf(false) }
    if (confirmReset) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmReset = false },
            containerColor = EColors.card,
            title = { Text("Delete all emotion app data on this phone?", color = EColors.text) },
            text = { Text("Your experiences and their photos, practice history and reminders. Health data from the ring stays.", color = EColors.textSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    val store = com.pulseloop.emotion.EmotionStore.get(context)
                    store.experiences.value.forEach { store.delete(it.id) }
                    com.pulseloop.emotion.EmotionData.get(context).deleteAll()
                    com.pulseloop.emotion.ReminderScheduler.rebuild(context)
                    confirmReset = false
                }) { Text("Delete experiences, training and reminders", color = EColors.danger) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel", color = EColors.text) } },
        )
    }
    val status = when (state.connectionState) {
        RingConnectionState.CONNECTED -> "Connected" + (state.batteryPercent?.let { " · $it% battery" } ?: "")
        RingConnectionState.CONNECTING, RingConnectionState.RECONNECTING -> "Connecting…"
        RingConnectionState.SCANNING -> "Looking for your ring…"
        else -> if (bleClient.hasLastKnownRing) "Not connected" else "No ring paired yet"
    }

    Column(
        Modifier.fillMaxSize().background(EColors.ground).verticalScroll(rememberScrollState())
            .padding(start = 20.dp, end = 20.dp, top = 56.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Devices", color = EColors.text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text(
            "Each feeling plays as a touch on your ring first, or on your watch when no ring is connected. With neither, your phone vibrates.",
            color = EColors.textSecondary, fontSize = 13.sp,
        )

        SectionLabel("SMART RING")
        DeviceSwitch("Use a ring", if (ringOn) "Touches go to your ring first" else "Off: no touches go to a ring, and it stays disconnected", ringOn) {
            DevicePrefs.setUseRing(context, it, bleClient)
        }
        if (ringOn) Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(EColors.card).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(if (connected) EColors.success else EColors.textMuted))
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(ringName, color = EColors.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                deviceName?.let { Text(it, color = EColors.textMuted, fontSize = 12.sp) }
                Text(status, color = EColors.textSecondary, fontSize = 13.sp)
            }
            if (!connected && bleClient.hasLastKnownRing) {
                TextButton(onClick = { bleClient.connectLastKnown() }) { Text("Connect", color = EColors.accent) }
            }
        }
        if (ringOn) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PrimaryButton("Test buzz", Modifier.weight(1f), height = 44.dp) {
                touches.play(com.pulseloop.emotion.TouchPattern("test.single", "One buzz", listOf(300)))
            }
            SecondaryButton("Vibration check", Modifier.weight(1f), height = 44.dp, onClick = onOpenVibeCheck)
        }
        if (ringOn && bleClient.hasLastKnownRing) {
            TextButton(onClick = { onOpenOriginalUi("pairing") }, modifier = Modifier.fillMaxWidth()) {
                Text("Change ring", color = EColors.accent, fontWeight = FontWeight.SemiBold)
            }
        }
        if (ringOn && !bleClient.hasLastKnownRing) {
            Button(
                onClick = { onOpenOriginalUi("pairing") },
                colors = ButtonDefaults.buttonColors(containerColor = EColors.accent, contentColor = EColors.onAccent),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) { Text("Pair your ring", fontWeight = FontWeight.Bold) }
        }

        SectionLabel("WEAR OS WATCH")
        DeviceSwitch("Use a watch", if (useWatch) "Pixel Watch, Galaxy Watch and other Wear OS watches" else "Off: nothing is sent to a watch", useWatch) {
            WatchLink.setUseWatch(it)
        }
        if (useWatch) WatchCard()

        SectionLabel("FEEL EACH TOUCH")
        val ringPlays = ringOn && connected
        val watchPlays = useWatch && watches.isNotEmpty()
        Text(
            when {
                ringPlays && watchPlays && routeTarget == TouchTarget.BOTH -> "Tap a feeling to play its touch on your ring and watch."
                ringPlays -> "Tap a feeling to play its touch on your ring."
                watchPlays -> "No ring connected: tap a feeling to play its touch on your watch."
                else -> "No ring or watch connected: your phone vibrates instead, so you can still try each touch."
            },
            color = EColors.textSecondary, fontSize = 13.sp,
        )
        Feeling.order.forEach { f ->
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(EColors.card)
                    .clickable { touches.play(f.touch) }.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(10.dp).clip(CircleShape).background(f.color))
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(f.label, color = EColors.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(f.rhythm, color = EColors.textSecondary, fontSize = 12.sp)
                }
                Text("Feel", color = f.color, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }

        SectionLabel("WELLBEING")
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(EColors.card)
                .clickable(onClick = onOpenCalmCue).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Calm cue when tense", color = EColors.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Text(if (com.pulseloop.emotion.CalmCue.isEnabled(androidx.compose.ui.platform.LocalContext.current)) "On" else "Off · beta", color = EColors.textSecondary, fontSize = 12.sp)
            }
            Text("›", color = EColors.textMuted, fontSize = 22.sp)
        }

        StatsSection(onOpenOriginalUi)

        SectionLabel("MORE")
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(EColors.card).padding(horizontal = 16.dp)) {
            MoreLink("Reminders & quiet hours", onOpenReminders)
            androidx.compose.material3.HorizontalDivider(color = EColors.line)
            MoreLink("Guides & help", onOpenLearn)
            androidx.compose.material3.HorizontalDivider(color = EColors.line)
            MoreLink("Privacy, export & delete") { confirmReset = true }
        }
        OutlinedButton(onClick = { onOpenOriginalUi("settings/wearable") }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("Manage ring", color = EColors.text)
        }
        OutlinedButton(onClick = { onOpenOriginalUi(null) }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
            Text("Health stats (original health screens)", color = EColors.text)
        }
        Text(
            "The original screens have an \"Emotion app\" button at the top to come back here.",
            color = EColors.textMuted, fontSize = 12.sp,
        )
    }
}


/** The Wear OS watch: status, where touches play, and a test. */
@Composable
private fun WatchCard() {
    val watches by WatchLink.watches.collectAsState()
    val target by WatchLink.target.collectAsState()
    val error by WatchLink.lastError.collectAsState()
    LaunchedEffect(Unit) { WatchLink.refresh() }
    val ready = watches.isNotEmpty()
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(EColors.card).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(if (ready) EColors.success else EColors.textMuted))
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(watches.firstOrNull()?.name ?: "Wear OS watch", color = EColors.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(
                    if (ready) "Ready · touches play on your wrist" else "No watch with Kinnora found",
                    color = EColors.textSecondary, fontSize = 13.sp,
                )
            }
            TextButton(onClick = { WatchLink.refresh() }) { Text("Look again", color = EColors.accent) }
        }
        Text("With a ring and a watch", color = EColors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(11.dp)).background(EColors.ground).padding(3.dp)) {
            TouchTarget.entries.forEach { t ->
                val on = t == target
                Box(
                    Modifier.weight(1f).clip(RoundedCornerShape(9.dp)).background(if (on) EColors.cardRaised else EColors.ground)
                        .clickable { WatchLink.setTarget(t) }.padding(vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) { Text(t.label, color = if (on) EColors.text else EColors.textSecondary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
        Text(
            if (ready) target.detail
            else "Pixel Watch, Galaxy Watch and other Wear OS watches: install Kinnora on the watch (the \"wear\" app in this project), keep it paired to this phone, then tap Look again.",
            color = EColors.textMuted, fontSize = 12.sp,
        )
        if (ready) {
            val remindersOnWatch by WatchLink.remindersOnWatch.collectAsState()
            ToggleRow(
                "Reminders on the watch",
                if (remindersOnWatch) "Your watch shows the upcoming reminders and taps each one with its feeling. Your phone stays quiet so you don't get two."
                else "Your phone shows each reminder; the watch plays the touch when no ring is connected.",
                remindersOnWatch,
            ) { WatchLink.setRemindersOnWatch(it) }
        }
        OutlinedButton(
            onClick = { WatchLink.play(Feeling.GRATITUDE.touch, force = true) },
            enabled = ready,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Test watch", color = if (ready) EColors.text else EColors.textMuted) }
        error?.let { Text("Couldn't reach the watch: $it", color = EColors.danger, fontSize = 12.sp) }
    }
}


/** A card row with a title, a line of detail and a switch. */
@Composable
private fun DeviceSwitch(title: String, detail: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(EColors.card)
            .clickable { onChange(!checked) }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = EColors.text, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            Text(detail, color = EColors.textSecondary, fontSize = 12.sp)
        }
        Switch(
            checked = checked, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedThumbColor = EColors.onAccent, checkedTrackColor = EColors.accent),
        )
    }
}


@Composable
private fun MoreLink(title: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = EColors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
        Text("›", color = EColors.textMuted, fontSize = 22.sp)
    }
}

/**
 * Your stats (iOS `RingStatsSection`): the latest heart rate, HRV and stress from the ring, behind
 * the optional-stats switches. Sleep and activity open the full health screens.
 */
@Composable
private fun StatsSection(onOpenOriginalUi: (route: String?) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val data = remember { com.pulseloop.emotion.EmotionData.get(context) }
    val profile by data.state.collectAsState()
    val p = profile.profile
    var readings by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<List<Triple<String, String, Long?>>>(emptyList()) }
    LaunchedEffect(p.statsHeart) { if (p.statsHeart) readings = com.pulseloop.emotion.CalmCue.latestReadings(context) }
    if (p.statsHeart || p.statsSleep || p.statsActivity) {
        SectionLabel("YOUR STATS")
        if (p.statsHeart) Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(EColors.card).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (readings.isEmpty()) Text("No data from your ring yet. Keep it connected; heart data appears after the first sync.", color = EColors.textSecondary, fontSize = 13.sp)
            readings.forEach { (name, value, at) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(name, color = EColors.text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Column(horizontalAlignment = Alignment.End) {
                        Text(value, color = EColors.text, fontSize = 15.sp)
                        at?.let { Text(android.text.format.DateUtils.getRelativeTimeSpanString(it).toString(), color = EColors.textMuted, fontSize = 11.sp) }
                    }
                }
            }
        }
        if (p.statsSleep || p.statsActivity) {
            TextButton(onClick = { onOpenOriginalUi(null) }, modifier = Modifier.fillMaxWidth()) {
                Text(listOfNotNull("Sleep".takeIf { p.statsSleep }, "Steps & activity".takeIf { p.statsActivity }).joinToString(" and ") + " in health stats", color = EColors.accent)
            }
        }
    }
    SectionLabel("OPTIONAL STATS")
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(EColors.card).padding(horizontal = 16.dp)) {
        ToggleRow("Heart rate & HRV", checked = p.statsHeart) { on -> data.updateProfile { it.copy(statsHeart = on) } }
        androidx.compose.material3.HorizontalDivider(color = EColors.line)
        ToggleRow("Sleep", checked = p.statsSleep) { on -> data.updateProfile { it.copy(statsSleep = on) } }
        androidx.compose.material3.HorizontalDivider(color = EColors.line)
        ToggleRow("Steps & activity", checked = p.statsActivity) { on -> data.updateProfile { it.copy(statsActivity = on) } }
    }
}
