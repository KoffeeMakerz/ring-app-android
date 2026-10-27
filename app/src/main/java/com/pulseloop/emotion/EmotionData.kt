package com.pulseloop.emotion

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

// Port of the iOS SwiftData models ReminderRule, ReminderEvent, TrainingRecord and EmotionProfile
// (Emotion/Model/EmotionStore.swift). Keep field names and defaults identical across platforms.

/** A reminder schedule: spread through a window, or at set times on chosen days. */
@Serializable
data class ReminderRule(
    val id: String = UUID.randomUUID().toString(),
    val feelingRaw: String = Feeling.GRATITUDE.raw,
    val perDay: Int = 3,
    val windowStartMinutes: Int = 9 * 60,
    val windowEndMinutes: Int = 20 * 60,
    val randomTimes: Boolean = true,
    val fixedMinutes: List<Int> = emptyList(),
    val weekdaysOnly: Boolean = false,
    /** Days for set times, Monday first (7 flags). Empty = every day. */
    val fixedDays: List<Boolean> = emptyList(),
    /** When set, cues for this rule recall this experience and play its touch sequence. */
    val experienceId: String? = null,
    val checkInAfter: Boolean = true,
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val feeling: Feeling get() = Feeling.fromRaw(feelingRaw) ?: Feeling.GRATITUDE
}

/** One planned cue and what happened to it. */
@Serializable
data class ReminderEvent(
    val id: String = UUID.randomUUID().toString(),
    val ruleId: String? = null,
    val feelingRaw: String = Feeling.GRATITUDE.raw,
    val scheduledAt: Long = System.currentTimeMillis(),
    val deliveredAt: Long? = null,
    /** scheduled / delivered / missed */
    val status: String = "scheduled",
    /** 0 no · 1 a little · 2 yes */
    val feltRating: Int? = null,
    val note: String? = null,
    val experienceId: String? = null,
    /** How the cue was delivered ("ring", "notification", "watch") or why it was skipped. */
    val deliveredVia: String? = null,
    val delaySeconds: Int? = null,
    val attempts: Int = 0,
) {
    val feeling: Feeling get() = Feeling.fromRaw(feelingRaw) ?: Feeling.GRATITUDE
}

/** One finished practice session (one per feeling in an experience). */
@Serializable
data class TrainingRecord(
    val id: String = UUID.randomUUID().toString(),
    val feelingRaw: String = Feeling.GRATITUDE.raw,
    val experienceId: String? = null,
    /** 1–5 */
    val recallRating: Int = 0,
    val comfort: String = "right",
    val completedAt: Long = System.currentTimeMillis(),
)

@Serializable
data class EmotionProfile(
    val onboardingCompleted: Boolean = false,
    val firstFeelingRaw: String = Feeling.GRATITUDE.raw,
    val activeFeelingRaw: String = Feeling.GRATITUDE.raw,
    val consentReminders: Boolean = true,
    val consentHeartData: Boolean = false,
    val consentPhotos: Boolean = false,
    val minFeltGapMs: Int = 600,
    /** strong / right / weak */
    val buzzComfort: String = "right",
    val remindersPaused: Boolean = false,
    val pausedUntil: Long? = null,
    val dailyCap: Int = 6,
    val quietStartMinutes: Int = 21 * 60,
    val quietEndMinutes: Int = 8 * 60,
    val statsHeart: Boolean = true,
    val statsSleep: Boolean = false,
    val statsActivity: Boolean = false,
) {
    val activeFeeling: Feeling get() = Feeling.fromRaw(activeFeelingRaw) ?: Feeling.GRATITUDE
}

@Serializable
data class EmotionDataFile(
    val profile: EmotionProfile = EmotionProfile(),
    val rules: List<ReminderRule> = emptyList(),
    val events: List<ReminderEvent> = emptyList(),
    val training: List<TrainingRecord> = emptyList(),
)

/**
 * Profile, reminders and practice history, kept as one small JSON file next to the experiences
 * (`filesDir/emotion/data.json`). Never uploaded.
 */
class EmotionData private constructor(private val context: Context) {
    private val file = File(context.filesDir, "emotion/data.json")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val lock = Any()

    private val _state = MutableStateFlow(load())
    val state: StateFlow<EmotionDataFile> = _state.asStateFlow()

    val profile: EmotionProfile get() = _state.value.profile

    fun update(transform: (EmotionDataFile) -> EmotionDataFile) {
        synchronized(lock) {
            _state.value = transform(_state.value)
            persist()
        }
    }

    fun updateProfile(transform: (EmotionProfile) -> EmotionProfile) = update { it.copy(profile = transform(it.profile)) }

    fun upsertRule(rule: ReminderRule) = update { s ->
        val list = s.rules.toMutableList()
        val i = list.indexOfFirst { it.id == rule.id }
        if (i >= 0) list[i] = rule else list.add(rule)
        s.copy(rules = list)
    }

    fun deleteRule(id: String) = update { s -> s.copy(rules = s.rules.filterNot { it.id == id }) }

    fun updateEvent(id: String, transform: (ReminderEvent) -> ReminderEvent) = update { s ->
        s.copy(events = s.events.map { if (it.id == id) transform(it) else it })
    }

    fun addTraining(records: List<TrainingRecord>) = update { s -> s.copy(training = s.training + records) }

    /** Practice sessions done for a feeling (same rule as iOS `FeelingProgress.sessions`). */
    fun sessions(feeling: Feeling): Int = _state.value.training.count { it.feelingRaw == feeling.raw }

    /** Deletes reminders, practice history and the profile ("Privacy, export & delete"). */
    fun deleteAll() = update { EmotionDataFile(profile = EmotionProfile(onboardingCompleted = true)) }

    private fun load(): EmotionDataFile {
        if (!file.exists()) {
            // People who used the app before onboarding existed skip it.
            val existing = File(context.filesDir, "emotion/experiences.json").exists() ||
                context.getSharedPreferences("emotion.devices", Context.MODE_PRIVATE).getBoolean("asked", false)
            return EmotionDataFile(profile = EmotionProfile(onboardingCompleted = existing))
        }
        return runCatching { json.decodeFromString(EmotionDataFile.serializer(), file.readText()) }
            .getOrDefault(EmotionDataFile(profile = EmotionProfile(onboardingCompleted = true)))
    }

    private fun persist() {
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, "data.json.tmp")
            tmp.writeText(json.encodeToString(EmotionDataFile.serializer(), _state.value))
            if (!tmp.renameTo(file)) {
                file.writeText(tmp.readText())
                tmp.delete()
            }
        }
    }

    companion object {
        @Volatile private var instance: EmotionData? = null

        fun get(context: Context): EmotionData =
            instance ?: synchronized(this) {
                instance ?: EmotionData(context.applicationContext).also { instance = it }
            }

        /** Practice programme length per feeling (iOS `Feeling.programmeLength`). */
        const val PROGRAMME_LENGTH = 7
    }
}

/** What to feel in a single-feeling practice (iOS `Feeling.trainingPrompt`). */
val Feeling.trainingPrompt: String
    get() = when (this) {
        Feeling.GRATITUDE -> "Think of someone who made this week easier. Let the thanks fill your chest."
        Feeling.LOVE -> "Picture someone you love smiling at you. Let the warmth spread."
        Feeling.JOY -> "Remember a moment you laughed until it hurt. Feel it again."
        Feeling.BLISS -> "Imagine your vision already real. Let the excitement rise."
        Feeling.CALM -> "Let your shoulders drop. Breathe out slowly and soften."
        Feeling.CONFIDENCE -> "Recall a time you did something hard and it worked. Stand in it."
    }

/** The one-breath prompt on a reminder (iOS `Feeling.cuePrompt`). */
val Feeling.cuePrompt: String
    get() = when (this) {
        Feeling.GRATITUDE -> "Take one slow breath. What are you thankful for right now?"
        Feeling.LOVE -> "Take one slow breath. Who do you love right now?"
        Feeling.JOY -> "Take one slow breath. What made you smile today?"
        Feeling.BLISS -> "Take one slow breath. Picture your vision, already true."
        Feeling.CALM -> "Breathe out slowly. Let everything soften for a moment."
        Feeling.CONFIDENCE -> "Stand tall for one breath. You've got this."
    }

/** Short guide videos (iOS `GuideVideo.all`). */
data class GuideVideo(val id: String, val title: String, val meta: String, val group: String) {
    companion object {
        val all = listOf(
            GuideVideo("wear", "Wearing your ring", "[VIDEO] · 1 min", "Getting started"),
            GuideVideo("training", "How training works", "[VIDEO] · 2 min", "Getting started"),
            GuideVideo("reminders", "Making reminders work for you", "[VIDEO] · 2 min", "Getting started"),
            GuideVideo("coherence", "Coherence breathing, simply", "[VIDEO] · 3 min", "Meditation"),
            GuideVideo("comfort", "Using vibration comfortably", "[VIDEO] · 1 min", "Safety & help"),
            GuideVideo("connect", "Ring not connecting", "Step-by-step fixes", "Safety & help"),
        )
    }
}
