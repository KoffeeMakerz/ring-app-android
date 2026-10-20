package com.pulseloop.emotion

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * The user's experiences, kept as one small JSON file in app storage. Deliberately separate from
 * the health database (PulseLoopDatabase) so the emotion app never needs a Room migration there.
 */
class EmotionStore private constructor(private val context: Context) {
    private val file = File(context.filesDir, "emotion/experiences.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = ListSerializer(Experience.serializer())

    private val _experiences = MutableStateFlow(load())
    val experiences: StateFlow<List<Experience>> = _experiences.asStateFlow()

    fun get(id: String?): Experience? = _experiences.value.firstOrNull { it.id == id }

    /** Inserts (newest first) or replaces by id. */
    fun upsert(experience: Experience) {
        val list = _experiences.value.toMutableList()
        val i = list.indexOfFirst { it.id == experience.id }
        if (i >= 0) list[i] = experience else list.add(0, experience)
        _experiences.value = list
        persist()
        pruneImages()
    }

    fun delete(id: String) {
        _experiences.value = _experiences.value.filterNot { it.id == id }
        persist()
        pruneImages()
    }

    fun markPlayed(id: String) {
        get(id)?.let { upsert(it.copy(lastPlayedAt = System.currentTimeMillis())) }
    }

    /**
     * Deletes private photo copies no saved experience uses any more (removed from a slideshow,
     * or added in a builder that was closed without saving).
     */
    fun pruneImages() {
        val used = _experiences.value.flatMap { it.imageRefs }
            .filter { it.startsWith("file:") }.map { it.removePrefix("file:") }.toSet()
        ExperienceImages.folder(context).listFiles()?.forEach { f ->
            if (f.name !in used) f.delete()
        }
    }

    private fun load(): List<Experience> = runCatching {
        if (file.exists()) json.decodeFromString(serializer, file.readText()) else emptyList()
    }.getOrDefault(emptyList())

    private fun persist() {
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, "experiences.json.tmp")
            tmp.writeText(json.encodeToString(serializer, _experiences.value))
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText())
                tmp.delete()
            }
        }
    }

    companion object {
        @Volatile private var instance: EmotionStore? = null

        fun get(context: Context): EmotionStore =
            instance ?: synchronized(this) {
                instance ?: EmotionStore(context.applicationContext).also { instance = it }
            }
    }
}
