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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pulseloop.emotion.Feeling
import com.pulseloop.emotion.RingTouches
import com.pulseloop.ring.RingBLEClient
import com.pulseloop.ring.RingConnectionState
import com.pulseloop.wearables.WearableModel

/** Ring status, each feeling's touch to try, and the way to pairing and health stats. */
@Composable
fun RingScreen(bleClient: RingBLEClient, touches: RingTouches, onOpenOriginalUi: (route: String?) -> Unit, onOpenCalmCue: () -> Unit) {
    val state by bleClient.state.collectAsState()
    val connected = state.connectionState == RingConnectionState.CONNECTED
    val ringName = WearableModel.model(state.activeWearableModelID)?.displayName
        ?: state.activeDeviceType?.displayName ?: "Your ring"
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
        Text("Ring", color = EColors.text, fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(EColors.card).padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(if (connected) EColors.success else EColors.textMuted))
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(ringName, color = EColors.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(status, color = EColors.textSecondary, fontSize = 13.sp)
            }
            if (!connected && bleClient.hasLastKnownRing) {
                TextButton(onClick = { bleClient.connectLastKnown() }) { Text("Connect", color = EColors.accent) }
            }
        }
        if (!bleClient.hasLastKnownRing) {
            Button(
                onClick = { onOpenOriginalUi("pairing") },
                colors = ButtonDefaults.buttonColors(containerColor = EColors.accent, contentColor = EColors.onAccent),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
            ) { Text("Pair your ring", fontWeight = FontWeight.Bold) }
        }

        SectionLabel("FEEL EACH TOUCH")
        Text(
            if (connected) "Tap a feeling to play its touch on your ring."
            else "No ring connected: your phone vibrates instead, so you can still try each touch.",
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

        SectionLabel("MORE")
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
