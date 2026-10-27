package com.pulseloop.emotion

import android.content.Context
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * How touches are routed when a ring and a watch are both in use. The ring comes first.
 * Same choices as iOS `TouchTarget`.
 */
enum class TouchTarget(val label: String, val detail: String) {
    /** The ring when it's connected; the watch only when no ring is connected. */
    RING_FIRST("Ring first", "Touches go to your ring. Your watch takes over when the ring isn't connected."),
    /** The ring and the watch together. */
    BOTH("Both", "Every touch plays on your ring and your watch together."),
}

/**
 * The link to the Kinnora app on a Wear OS watch (Pixel Watch, Galaxy Watch…), over the Wearable
 * Data Layer. Every touch [RingTouches] plays (practice, experiences, the calm cue, tests) is also
 * sent to the watch, which plays it on its own motor, with exact lengths and gaps.
 *
 * Watches are found by the capability the watch app declares (`kinora_watch_app`, in the `:wear`
 * module's `res/values/wear.xml`). The Data Layer only connects the two apps when they share the
 * applicationId and signing key, so `:wear` is built from this repo with the phone app's ID.
 *
 * Message: path `/kinora/play`, body `{"id","name","durations_ms":[on, off, on, …]}`, starting with on.
 */
object WatchLink {
    const val CAPABILITY = "kinora_watch_app"
    const val PATH_PLAY = "/kinora/play"
    /** Data item with the experiences and reminder plan for the watch (see [sync]). */
    const val PATH_SYNC = "/kinora/sync"
    /** Message from the watch: play this experience on the phone (body = experience id). */
    const val PATH_START = "/kinora/start"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private const val PREFS = "emotion.watch"
    private const val KEY_TARGET = "touchTarget"
    private const val KEY_USE_WATCH = "useWatch"
    private const val KEY_REMINDERS = "remindersOnWatch"

    data class Watch(val id: String, val name: String)

    private val _watches = MutableStateFlow<List<Watch>>(emptyList())
    /** Reachable watches with Kinnora installed. */
    val watches: StateFlow<List<Watch>> = _watches.asStateFlow()

    private val _target = MutableStateFlow(TouchTarget.RING_FIRST)
    val target: StateFlow<TouchTarget> = _target.asStateFlow()

    private val _useWatch = MutableStateFlow(true)
    /** Kinnora's "Use a watch" switch (Devices tab). Off = nothing is sent to the watch. */
    val useWatch: StateFlow<Boolean> = _useWatch.asStateFlow()

    private val _remindersOnWatch = MutableStateFlow(true)
    /**
     * "Reminders on the watch": with the watch app installed, the watch plays each reminder itself
     * (its own alarms) and the phone posts no cue notifications, so nobody gets two. Same setting
     * as iOS `WatchLink.remindersOnWatch`.
     */
    val remindersOnWatch: StateFlow<Boolean> = _remindersOnWatch.asStateFlow()

    /** The watch is delivering the reminders now. */
    val takesOverReminders: Boolean get() = available && _remindersOnWatch.value

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    @Volatile private var appContext: Context? = null

    private val listener = CapabilityClient.OnCapabilityChangedListener { info -> publish(info) }

    /** Safe to call many times; the first call loads the setting and starts watching for watches. */
    fun init(context: Context) {
        if (appContext != null) return
        val app = context.applicationContext
        appContext = app
        val saved = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_TARGET, null)
        // Older builds stored RING, WATCH or BOTH.
        _target.value = if (saved == "BOTH") TouchTarget.BOTH else TouchTarget.RING_FIRST
        _useWatch.value = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_USE_WATCH, true)
        _remindersOnWatch.value = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_REMINDERS, true)
        runCatching { Wearable.getCapabilityClient(app).addListener(listener, CAPABILITY) }
        refresh()
        // Keep the watch's copy of the experiences current: on every change, and when a watch appears.
        scope.launch {
            combine(EmotionStore.get(app).experiences, EmotionData.get(app).state, _watches, _useWatch, _remindersOnWatch) { exps, data, nodes, use, _ ->
                Triple(exps, data, use && nodes.isNotEmpty())
            }.collect { (exps, data, ready) -> if (ready) sync(exps, data) }
        }
    }

    /**
     * Sends the experiences and the reminder plan as a data item.
     * The Data Layer delivers it even when the watch app isn't running and keeps the latest copy.
     * Same content as iOS `WatchLink.sync()`.
     */
    private fun sync(experiences: List<Experience>, data: EmotionDataFile) {
        val app = appContext ?: return
        fun onFirst(p: TouchPattern): List<Int> = if (p.startsWithVibration) p.durationsMs else listOf(0) + p.durationsMs
        val exps = JSONArray()
        experiences.take(40).forEach { exp ->
            val track = exp.track
            val duration = maxOf(1, track.durationS)
            val feelings = exp.feelings
            val cues = JSONArray()
            track.cueMarkersS.filter { it < duration }.forEach { at ->
                val i = (at.toDouble() / duration * feelings.size).toInt().coerceIn(0, feelings.size - 1)
                val f = feelings[i]
                cues.put(JSONObject().put("at", at).put("feeling", f.raw).put("durations", JSONArray(onFirst(f.touch))))
            }
            exps.put(JSONObject()
                .put("id", exp.id).put("title", exp.title.ifBlank { exp.arcLabel })
                .put("feelings", JSONArray(feelings.map { it.raw }))
                .put("durationS", duration).put("cues", cues))
        }
        // The reminder plan (iOS sends the same): the watch plays each one with its own alarm.
        // Only while the watch has taken reminders over; otherwise an empty plan clears its alarms.
        val rems = JSONArray()
        if (_remindersOnWatch.value) {
            val now = System.currentTimeMillis()
            data.events.filter { it.status == "scheduled" && it.scheduledAt > now }.take(60).forEach { e ->
                val exp = experiences.firstOrNull { it.id == e.experienceId }
                val (title, text) = ReminderScheduler.cueText(e.feeling, exp)
                val pattern = exp?.touchSequence ?: e.feeling.touch
                rems.put(JSONObject()
                    .put("id", e.id).put("at", e.scheduledAt).put("title", title).put("body", text)
                    .put("feeling", e.feelingRaw).put("experienceId", exp?.id ?: JSONObject.NULL)
                    .put("pattern", JSONObject().put("id", pattern.id).put("name", pattern.name).put("durations_ms", JSONArray(onFirst(pattern)))))
            }
        }
        val body = JSONObject().put("experiences", exps).put("reminders", rems).toString()
        runCatching {
            val req = PutDataMapRequest.create(PATH_SYNC).apply {
                dataMap.putString("json", body)
                dataMap.putLong("t", System.currentTimeMillis())
            }.asPutDataRequest().setUrgent()
            Wearable.getDataClient(app).putDataItem(req)
        }
    }

    fun setTarget(target: TouchTarget) {
        _target.value = target
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putString(KEY_TARGET, target.name)?.apply()
    }

    fun setRemindersOnWatch(on: Boolean) {
        _remindersOnWatch.value = on
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putBoolean(KEY_REMINDERS, on)?.apply()
        appContext?.let { ReminderScheduler.scheduleNext(it) }
    }

    fun setUseWatch(on: Boolean) {
        _useWatch.value = on
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putBoolean(KEY_USE_WATCH, on)?.apply()
    }

    /** Looks for watches again (no Play services or no watch → none). */
    fun refresh() {
        val app = appContext ?: return
        runCatching {
            Wearable.getCapabilityClient(app)
                .getCapability(CAPABILITY, CapabilityClient.FILTER_REACHABLE)
                .addOnSuccessListener { publish(it) }
                .addOnFailureListener { _watches.value = emptyList() }
        }.onFailure { _watches.value = emptyList() }
    }

    private fun publish(info: CapabilityInfo) {
        _watches.value = info.nodes.map { Watch(it.id, it.displayName) }
    }

    /** The watch is in use and reachable. */
    val available: Boolean get() = _useWatch.value && _watches.value.isNotEmpty()

    /**
     * Sends a touch to every reachable watch (routing is decided by [RingTouches]). [force] skips
     * the "Use a watch" switch, for the test button.
     */
    fun play(pattern: TouchPattern, force: Boolean = false): Boolean {
        val app = appContext ?: return false
        val nodes = _watches.value
        if (nodes.isEmpty() || (!force && !_useWatch.value)) return false
        // The watch expects on/off/on…; a pattern that starts with a pause gets a 0 ms "on" first.
        val durations = if (pattern.startsWithVibration) pattern.durationsMs else listOf(0) + pattern.durationsMs
        val body = JSONObject()
            .put("id", pattern.id)
            .put("name", pattern.name)
            .put("durations_ms", JSONArray(durations))
            .toString().toByteArray()
        val client = runCatching { Wearable.getMessageClient(app) }.getOrNull() ?: return false
        _lastError.value = null
        nodes.forEach { node ->
            client.sendMessage(node.id, PATH_PLAY, body)
                .addOnFailureListener { _lastError.value = it.message ?: "Couldn't reach ${node.name}" }
        }
        return true
    }
}
