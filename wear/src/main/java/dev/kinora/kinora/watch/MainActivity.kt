package dev.kinora.kinora.watch

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import java.text.DateFormat
import java.util.Date

/** Watch screen: what the phone sends plays here, plus each feeling's touch to feel. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CueStore.schedule(this)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        setContent { MaterialTheme { WatchHome() } }
    }

    @Composable
    private fun WatchHome() {
        val next = CueStore.next(this)
        val colors = mapOf(
            "Gratitude" to Color(0xFFF2B45A), "Joy" to Color(0xFFF5D76E), "Love" to Color(0xFFF27A9B),
            "Confidence" to Color(0xFF6FD6A1), "Bliss" to Color(0xFFB892FF), "Calm" to Color(0xFF7FB2F0),
        )
        ScalingLazyColumn(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            item { Text("KINNORA", fontSize = 14.sp, fontWeight = FontWeight.Light, letterSpacing = 5.sp, color = Color(0xFFFFECD6)) }
            item {
                Text(
                    if (next == null) "Touches from Kinnora on your phone play here" else
                        "Next: ${next.optString("title")} · ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(next.optLong("at")))}",
                    fontSize = 12.sp, textAlign = TextAlign.Center, color = Color(0xFFB9A9C4),
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
            TouchPattern.feelings.forEach { p ->
                item {
                    Chip(
                        onClick = { TouchPlayer.play(this@MainActivity, p) },
                        label = { Text("Feel ${p.name.lowercase()}") },
                        colors = ChipDefaults.chipColors(backgroundColor = (colors[p.name] ?: Color.Gray).copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}
