package dev.kinora.kinora.watch

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.material.Button
import androidx.wear.compose.material.ButtonDefaults
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Text
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date
import kotlin.math.cos
import kotlin.math.sin

/** Screens of the watch app (a small back stack; the system back gesture pops it). */
sealed interface Screen {
    data object Home : Screen
    data object Experiences : Screen
    data class Experience(val id: String) : Screen
    data class Player(val id: String) : Screen
    data object Reminders : Screen
    data object Feelings : Screen
    data class Feel(val id: String) : Screen
}

private val Gold = Color(0xFFFFECD6)
private val Muted = Color(0xFFB9A9C4)

/**
 * Kinnora on Wear OS, in the spirit of a mindfulness app: big tiles, one thing per tap.
 * Experiences play on the wrist (bloom + each cue's touch) or start on the phone; reminders are
 * played by the watch itself at their time (see [CueStore]).
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CueStore.schedule(this)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }
        setContent { MaterialTheme { WatchApp() } }
    }
}

@Composable
private fun WatchApp() {
    var stack by remember { mutableStateOf(listOf<Screen>(Screen.Home)) }
    fun go(s: Screen) { stack = stack + s }
    fun back() { if (stack.size > 1) stack = stack.dropLast(1) }
    BackHandler(enabled = stack.size > 1) { back() }
    when (val s = stack.last()) {
        Screen.Home -> HomeScreen(::go)
        Screen.Experiences -> ExperiencesScreen(::go)
        is Screen.Experience -> ExperienceScreen(s.id, ::go)
        is Screen.Player -> PlayerScreen(s.id, ::back)
        Screen.Reminders -> RemindersScreen()
        Screen.Feelings -> FeelingsScreen(::go)
        is Screen.Feel -> FeelScreen(WatchFeeling.named(s.id))
    }
}

@Composable
private fun HomeScreen(go: (Screen) -> Unit) {
    val context = LocalContext.current
    val experiences = remember { WatchStore.experiences(context) }
    val next = remember { CueStore.next(context) }
    ScalingLazyColumn(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { Text("KINNORA", fontSize = 14.sp, fontWeight = FontWeight.Light, letterSpacing = 5.sp, color = Gold) }
        next?.let { cue ->
            item {
                Text("Next: ${cue.optString("title")} · ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(cue.optLong("at")))}",
                    fontSize = 12.sp, textAlign = TextAlign.Center, color = Muted, modifier = Modifier.padding(horizontal = 12.dp))
            }
        }
        item { Tile("Experiences", if (experiences.isEmpty()) "Make one on your phone" else "${experiences.size} to play", Color(0xFFF2B45A)) { go(Screen.Experiences) } }
        item { Tile("Feel a touch", "Practise one feeling", Color(0xFFF27A9B)) { go(Screen.Feelings) } }
        item { Tile("Reminders", if (next == null) "None planned" else "${CueStore.cues(context).count { it.optLong("at") > System.currentTimeMillis() }} coming up", Color(0xFF7FB2F0)) { go(Screen.Reminders) } }
    }
}

@Composable
private fun Tile(title: String, subtitle: String, color: Color, onClick: () -> Unit) {
    Chip(
        onClick = onClick,
        label = { Text(title, fontWeight = FontWeight.SemiBold) },
        secondaryLabel = { Text(subtitle, color = Color.White.copy(alpha = 0.8f)) },
        colors = ChipDefaults.gradientBackgroundChipColors(startBackgroundColor = color.copy(alpha = 0.55f), endBackgroundColor = color.copy(alpha = 0.2f)),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun ExperiencesScreen(go: (Screen) -> Unit) {
    val context = LocalContext.current
    val experiences = remember { WatchStore.experiences(context) }
    ScalingLazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { Text("Experiences", fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
        if (experiences.isEmpty()) {
            item { Text("Create an experience in Kinnora on your phone and it appears here.", fontSize = 12.sp, color = Muted, textAlign = TextAlign.Center) }
        }
        experiences.forEach { exp ->
            item {
                Chip(
                    onClick = { go(Screen.Experience(exp.id)) },
                    label = { Text(exp.title, maxLines = 2) },
                    secondaryLabel = { Text(exp.feelings.joinToString(" → ") { WatchFeeling.named(it).name }, maxLines = 1) },
                    colors = ChipDefaults.chipColors(backgroundColor = exp.feelingAt(0.0).color.copy(alpha = 0.3f)),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ExperienceScreen(id: String, go: (Screen) -> Unit) {
    val context = LocalContext.current
    val exp = remember { WatchStore.experiences(context).firstOrNull { it.id == id } } ?: run {
        Text("This experience isn't on the watch any more.", modifier = Modifier.padding(20.dp)); return
    }
    var note by remember { mutableStateOf<String?>(null) }
    val color = exp.feelingAt(0.0).color
    ScalingLazyColumn(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { Bloom(color, Modifier.size(60.dp)) }
        item { Text(exp.title, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center) }
        item {
            Text("${exp.feelings.joinToString(" → ") { WatchFeeling.named(it).name }} · ${maxOf(1, exp.durationS / 60)} min",
                fontSize = 12.sp, color = Muted, textAlign = TextAlign.Center)
        }
        item {
            Chip(onClick = { go(Screen.Player(exp.id)) }, label = { Text("Play here") },
                secondaryLabel = { Text("Touches on your wrist") },
                colors = ChipDefaults.chipColors(backgroundColor = color.copy(alpha = 0.45f)), modifier = Modifier.fillMaxWidth())
        }
        item {
            Chip(onClick = {
                note = "Asking your phone…"
                startOnPhone(context, exp.id) { sent -> note = if (sent) "Check your phone to play it" else "Your phone isn't in reach" }
            }, label = { Text("Play on phone") }, secondaryLabel = { Text("Song and images there") },
                colors = ChipDefaults.secondaryChipColors(), modifier = Modifier.fillMaxWidth())
        }
        note?.let { item { Text(it, fontSize = 12.sp, color = Muted, textAlign = TextAlign.Center) } }
    }
}

/** Asks the Kinnora phone app to play an experience ("/kinora/start"). */
private fun startOnPhone(context: Context, experienceId: String, done: (Boolean) -> Unit) {
    Wearable.getNodeClient(context).connectedNodes
        .addOnSuccessListener { nodes ->
            if (nodes.isEmpty()) { done(false); return@addOnSuccessListener }
            val client = Wearable.getMessageClient(context)
            nodes.forEach { client.sendMessage(it.id, "/kinora/start", experienceId.toByteArray()) }
            done(true)
        }
        .addOnFailureListener { done(false) }
}

/** Plays an experience on the wrist: the bloom in the current feeling's colour and each cue's touch. */
@Composable
private fun PlayerScreen(id: String, onDone: () -> Unit) {
    val context = LocalContext.current
    val exp = remember { WatchStore.experiences(context).firstOrNull { it.id == id } } ?: return
    var elapsed by remember { mutableDoubleStateOf(0.0) }
    var finished by remember { mutableStateOf(false) }
    val view = LocalView.current
    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose { view.keepScreenOn = false }
    }
    LaunchedEffect(exp.id) {
        var nextCue = 0
        val start = System.currentTimeMillis()
        while (elapsed < exp.durationS) {
            delay(250)
            elapsed = (System.currentTimeMillis() - start) / 1000.0
            while (nextCue < exp.cues.size && exp.cues[nextCue].at <= elapsed) {
                val cue = exp.cues[nextCue]
                TouchPlayer.play(context, TouchPattern("cue", cue.feeling, cue.durations))
                nextCue++
            }
        }
        finished = true
    }
    val feeling = exp.feelingAt(elapsed)
    Column(Modifier.fillMaxSize().padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center) {
        if (finished) {
            Text("Well done", fontWeight = FontWeight.Bold, color = feeling.color)
            Text("You felt ${exp.feelings.joinToString(", then ") { WatchFeeling.named(it).name.lowercase() }}",
                fontSize = 12.sp, color = Muted, textAlign = TextAlign.Center)
            Button(onClick = onDone, colors = ButtonDefaults.primaryButtonColors(backgroundColor = feeling.color),
                modifier = Modifier.padding(top = 8.dp)) { Text("Done") }
        } else {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.size(110.dp)) {
                val progress = (elapsed / exp.durationS).toFloat().coerceIn(0f, 1f)
                Canvas(Modifier.fillMaxSize()) {
                    drawArc(Color.White.copy(alpha = 0.12f), 0f, 360f, false, style = Stroke(6f))
                    drawArc(feeling.color, -90f, 360f * progress, false, style = Stroke(6f))
                }
                Bloom(feeling.color, Modifier.size(80.dp))
            }
            Text("Feel ${feeling.name.lowercase()}", fontWeight = FontWeight.Bold, color = feeling.color, modifier = Modifier.padding(top = 6.dp))
            val left = maxOf(0, exp.durationS - elapsed.toInt())
            Text("%d:%02d left".format(left / 60, left % 60), fontSize = 12.sp, color = Muted)
        }
    }
}

@Composable
private fun RemindersScreen() {
    val context = LocalContext.current
    val now = System.currentTimeMillis()
    val upcoming = remember { CueStore.cues(context).filter { it.optLong("at") > now }.take(20) }
    val fmt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
    ScalingLazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item { Text("Reminders", fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
        if (upcoming.isEmpty()) {
            item { Text("No reminders planned. Set them in Kinnora on your phone.", fontSize = 12.sp, color = Muted, textAlign = TextAlign.Center) }
        }
        upcoming.forEach { cue ->
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Text(fmt.format(Date(cue.optLong("at"))), fontSize = 11.sp, color = Muted)
                    Text(cue.optString("title"), fontSize = 14.sp, maxLines = 2)
                }
            }
        }
        item {
            Text("Each reminder plays its feeling's touch on your wrist, right on time.", fontSize = 11.sp, color = Muted,
                textAlign = TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun FeelingsScreen(go: (Screen) -> Unit) {
    ScalingLazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        item { Text("Feel a touch", fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
        WatchFeeling.all.forEach { f ->
            item {
                Chip(onClick = { go(Screen.Feel(f.id)) }, label = { Text(f.name) },
                    colors = ChipDefaults.chipColors(backgroundColor = f.color.copy(alpha = 0.35f)), modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun FeelScreen(feeling: WatchFeeling) {
    val context = LocalContext.current
    LaunchedEffect(feeling.id) { TouchPlayer.play(context, feeling.pattern) }
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Bloom(feeling.color, Modifier.size(90.dp))
        Text(feeling.name, fontWeight = FontWeight.Bold, color = feeling.color, modifier = Modifier.padding(top = 6.dp))
        Button(onClick = { TouchPlayer.play(context, feeling.pattern) },
            colors = ButtonDefaults.secondaryButtonColors(), modifier = Modifier.padding(top = 6.dp)) {
            Text("Again", fontSize = 12.sp)
        }
    }
}

/** Six soft petals that open for 4 s and close for 7 s, like Kinnora's breathing. */
@Composable
private fun Bloom(color: Color, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "bloom")
    val open by t.animateFloat(
        initialValue = 0f, targetValue = 0f, label = "open",
        animationSpec = infiniteRepeatable(keyframes {
            durationMillis = 11_000
            0f at 0 using FastOutSlowInEasing
            1f at 4_000 using FastOutSlowInEasing
            0f at 11_000
        }, RepeatMode.Restart),
    )
    Canvas(modifier) {
        val r = size.minDimension / 2
        val petal = r * (0.38f + 0.12f * open)
        val dist = r * (0.12f + 0.3f * open)
        for (i in 0 until 6) {
            val a = Math.toRadians(i * 60.0 + 30.0 * open).toFloat()
            drawCircle(color.copy(alpha = 0.45f), petal, center + Offset(cos(a) * dist, sin(a) * dist))
        }
        drawCircle(color, r * 0.14f, center)
    }
}
