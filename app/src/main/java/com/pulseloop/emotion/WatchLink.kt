package com.pulseloop.emotion

import android.content.Context
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Where touches play. "Both" is the default, so a paired watch just works. */
enum class TouchTarget(val label: String) { RING("Ring"), WATCH("Watch"), BOTH("Both") }

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
    private const val PREFS = "emotion.watch"
    private const val KEY_TARGET = "touchTarget"

    data class Watch(val id: String, val name: String)

    private val _watches = MutableStateFlow<List<Watch>>(emptyList())
    /** Reachable watches with Kinnora installed. */
    val watches: StateFlow<List<Watch>> = _watches.asStateFlow()

    private val _target = MutableStateFlow(TouchTarget.BOTH)
    val target: StateFlow<TouchTarget> = _target.asStateFlow()

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
        _target.value = TouchTarget.entries.firstOrNull { it.name == saved } ?: TouchTarget.BOTH
        runCatching { Wearable.getCapabilityClient(app).addListener(listener, CAPABILITY) }
        refresh()
    }

    fun setTarget(target: TouchTarget) {
        _target.value = target
        appContext?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)?.edit()?.putString(KEY_TARGET, target.name)?.apply()
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

    /** The watch can take a touch now and the setting wants it there. */
    val canPlay: Boolean get() = _target.value != TouchTarget.RING && _watches.value.isNotEmpty()

    /** "Watch" chosen and a watch is there: touches skip the ring (and the phone's own motor). */
    val replacesRing: Boolean get() = _target.value == TouchTarget.WATCH && _watches.value.isNotEmpty()

    /** Sends a touch to every reachable watch. [force] ignores the setting (the test button). */
    fun play(pattern: TouchPattern, force: Boolean = false): Boolean {
        val app = appContext ?: return false
        val nodes = _watches.value
        if (nodes.isEmpty() || (!force && _target.value == TouchTarget.RING)) return false
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
