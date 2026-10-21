package com.pulseloop.emotion

import androidx.compose.ui.graphics.Color
import com.pulseloop.ui.slideshow.SlideshowImageSets
import kotlinx.serialization.Serializable
import java.util.UUID

// Port of the iOS emotion-app model (Emotion/Model/Feelings.swift, EmotionStore.swift,
// SlideImages.swift). Keep names, raw values and patterns identical across platforms.

/** A ring vibration: alternating motor-on / motor-off durations. */
data class TouchPattern(
    val id: String,
    val name: String,
    val durationsMs: List<Int>,
    val startsWithVibration: Boolean = true,
) {
    val totalMs: Int get() = durationsMs.sum()

    fun isOn(index: Int): Boolean = (index % 2 == 0) == startsWithVibration

    /** Motor-on segments as (startMs, lengthMs). */
    val pulses: List<Pair<Int, Int>>
        get() {
            var t = 0
            val out = mutableListOf<Pair<Int, Int>>()
            durationsMs.forEachIndexed { i, d ->
                if (isOn(i)) out += t to d
                t += d
            }
            return out
        }

    companion object {
        /** Calm cue: five hums with each gap longer, like a breath slowing down. */
        val CALM_CUE = TouchPattern("calm.cue", "Slow down", listOf(600, 700, 600, 1000, 600, 1400, 600, 1800, 600))
    }
}

/** Building blocks that feelings' touches are composed from. */
enum class TouchBlock(val label: String, val segments: List<Int>) {
    TAP("Tap", listOf(300)),
    DOUBLE_TAP("Double tap", listOf(200, 150, 200)),
    FLUTTER("Flutter", listOf(120, 100, 120, 100, 120)),
    HEARTBEAT("Heartbeat", listOf(150, 150, 350)),
    LONG_HUM("Long hum", listOf(800)),
    SIGNAL("Signal", listOf(150, 100, 150, 100, 150, 300, 450, 150, 450, 150, 450)),
    SHORT_REST("Short rest", listOf(400)),
    LONG_REST("Long rest", listOf(1000));

    val isRest: Boolean get() = this == SHORT_REST || this == LONG_REST

    companion object {
        /** Gap inserted between two touch blocks so they are felt separately. */
        const val AUTO_GAP_MS = 250

        /** Flatten blocks into one pattern (merging adjacent off segments). */
        fun compose(blocks: List<TouchBlock>, id: String, name: String): TouchPattern {
            val segs = mutableListOf<Int>() // alternating, index 0 = on
            fun appendOff(ms: Int) {
                if (segs.isEmpty()) return
                if (segs.size % 2 == 0) segs[segs.lastIndex] = segs[segs.lastIndex] + ms else segs.add(ms)
            }
            fun appendOn(ms: Int) {
                if (segs.size % 2 == 1) segs[segs.lastIndex] = segs[segs.lastIndex] + ms else segs.add(ms)
            }
            var previous: TouchBlock? = null
            for (block in blocks) {
                if (block.isRest) {
                    appendOff(block.segments[0])
                } else {
                    val prev = previous
                    if (prev != null && !prev.isRest) appendOff(AUTO_GAP_MS)
                    block.segments.forEachIndexed { i, ms -> if (i % 2 == 0) appendOn(ms) else appendOff(ms) }
                }
                previous = block
            }
            if (segs.isNotEmpty() && segs.size % 2 == 0) segs.removeAt(segs.lastIndex)
            return TouchPattern(id, name, segs)
        }
    }
}

/** The trained feelings. Each feeling owns its ring touch. */
enum class Feeling(
    val raw: String,
    val label: String,
    val color: Color,
    val ground: Color,
    val blocks: List<TouchBlock>,
    val rhythm: String,
    val experiencePrompt: String,
) {
    GRATITUDE(
        "gratitude", "Gratitude", Color(0xFFF2B45A), Color(0xFF2A1E10),
        listOf(TouchBlock.DOUBLE_TAP, TouchBlock.SHORT_REST, TouchBlock.TAP), "Two, two, then one",
        "It's real. Look around and thank everyone and everything that brought you here.",
    ),
    LOVE(
        "love", "Love", Color(0xFFF27A9B), Color(0xFF2E1420),
        listOf(TouchBlock.HEARTBEAT, TouchBlock.SHORT_REST, TouchBlock.HEARTBEAT), "Three heartbeats",
        "Feel the people you love here with you. Let the warmth fill your chest.",
    ),
    JOY(
        "joy", "Joy", Color(0xFFF5D76E), Color(0xFF2B2612),
        listOf(TouchBlock.FLUTTER, TouchBlock.SHORT_REST, TouchBlock.FLUTTER), "Three quick triplets",
        "It's happening. Let the joy rise and spill over.",
    ),
    BLISS(
        "bliss", "Bliss", Color(0xFFB892FF), Color(0xFF221A33),
        listOf(TouchBlock.FLUTTER, TouchBlock.DOUBLE_TAP), "Five taps, speeding up",
        "Let your whole body light up with it.",
    ),
    CALM(
        "calm", "Calm", Color(0xFF7FB2F0), Color(0xFF15233A),
        listOf(TouchBlock.LONG_HUM, TouchBlock.LONG_REST, TouchBlock.LONG_HUM), "Four slow hums",
        "Everything is taken care of. Breathe out and settle into it.",
    ),
    CONFIDENCE(
        "confidence", "Confidence", Color(0xFF6FD6A1), Color(0xFF14291F),
        listOf(TouchBlock.TAP, TouchBlock.SHORT_REST, TouchBlock.DOUBLE_TAP), "One, one, then three",
        "You made this happen. Stand tall in it.",
    );

    /**
     * Alternating motor-on / off ms, starting with on (v2: stronger, easier to feel). Most rings
     * only have a fixed "find device" buzz, so every motor-on segment becomes one buzz: each touch
     * is 5–9 buzzes in clear groups, buzzes in a group ~0.6 s apart, 0.9–1.3 s between groups.
     * Same values as iOS `Feeling.durationsMs`.
     */
    val durationsMs: List<Int>
        get() = when (this) {
            GRATITUDE -> listOf(350, 300, 350, 1200, 350, 300, 350, 1200, 500)
            LOVE -> listOf(300, 300, 450, 1000, 300, 300, 450, 1000, 300, 300, 450)
            JOY -> listOf(250, 350, 250, 350, 250, 900, 250, 350, 250, 350, 250, 900, 250, 350, 250, 350, 250)
            BLISS -> listOf(300, 1300, 300, 1000, 300, 750, 300, 550, 700)
            CALM -> listOf(700, 1300, 700, 1300, 700, 1300, 700)
            CONFIDENCE -> listOf(450, 900, 450, 900, 300, 300, 300, 300, 600)
        }

    val touch: TouchPattern get() = TouchPattern("feeling.$raw", "$label touch", durationsMs)

    companion object {
        const val MAX_PER_EXPERIENCE = 3
        /** Minimum silence between two feelings' touches when played as a sequence. */
        const val SEQUENCE_GAP_MS = 4_000

        /** Display / practice order. */
        val order: List<Feeling> = listOf(GRATITUDE, JOY, LOVE, CONFIDENCE, BLISS, CALM)

        fun fromRaw(raw: String?): Feeling? = entries.firstOrNull { it.raw == raw }

        /** Each feeling's own touch, in order, separated by at least [gapMs] of silence. */
        fun sequence(feelings: List<Feeling>, gapMs: Int = SEQUENCE_GAP_MS): TouchPattern {
            if (feelings.size <= 1) return (feelings.firstOrNull() ?: GRATITUDE).touch
            val segs = mutableListOf<Int>()
            feelings.forEachIndexed { i, f ->
                if (i > 0) segs.add(gapMs)
                segs.addAll(f.touch.durationsMs) // each touch starts and ends with motor-on
            }
            return TouchPattern(
                id = "sequence." + feelings.joinToString("+") { it.raw },
                name = feelings.joinToString(" → ") { it.label },
                durationsMs = segs,
            )
        }
    }
}

enum class MusicTheme(val raw: String, val label: String, val color: Color, val art: Color) {
    ABUNDANCE("abundance", "Abundance", Color(0xFF6FD6A1), Color(0xFF1E3A2C)),
    LOVE("love", "Love", Color(0xFFF27A9B), Color(0xFF3F1C28)),
    BUSINESS("business", "Business", Color(0xFFB892FF), Color(0xFF2D2245)),
    HEALTH("health", "Health", Color(0xFF7FB2F0), Color(0xFF1C2B40)),
    JOY("joy", "Joy", Color(0xFFF5D76E), Color(0xFF3D3418)),
    GRATITUDE("gratitude", "Gratitude", Color(0xFFF2B45A), Color(0xFF3A2A16)),
}

/**
 * A curated track. Songs made with ElevenLabs are bundled in res/raw (`rawRes` = file name without
 * extension); tracks without one run on a silent clock.
 */
data class MusicTrack(
    val id: String,
    val title: String,
    val theme: MusicTheme,
    val durationS: Int,
    /** Cue moments (each chorus), authored with the song. */
    val cueMarkersS: List<Int>,
    val rawRes: String? = null,
) {
    val durationLabel: String get() = "%d:%02d".format(durationS / 60, durationS % 60)
    /** "home" for "home-chill": the song both versions belong to. */
    val songKey: String get() = id.substringBefore("-")
    /** "Home" for "Home (chill)". */
    val songName: String get() = title.substringBefore(" (")
    val version: String get() = if (id.endsWith("upbeat")) "Upbeat" else "Chill"
    val about: String get() = SONG_ABOUT[songKey] ?: theme.label

    companion object {
        val SONG_ABOUT = mapOf(
            "home" to "Your dream home", "partner" to "Your dream partner", "together" to "Love with a partner · duet",
            "abundance" to "Abundance flowing to you", "freedom" to "Freedom", "car" to "Your dream car",
        )

        /** Songs in catalog order, each with its versions (songs aren't grouped by emotion). */
        val songGroups: List<Pair<String, List<MusicTrack>>>
            get() = catalog.groupBy { it.songKey }.toList()

        /** Real songs (same list as iOS `MusicTrack.songs`). */
        val songs: List<MusicTrack> = listOf(
            // Cue markers = the choruses, estimated from each song's own timing. Order matters:
            // the first song of a theme is that theme's default.
            MusicTrack("home-chill", "Home (chill)", MusicTheme.GRATITUDE, 180, listOf(53, 63, 127, 137), rawRes = "home_chill"),
            MusicTrack("home-upbeat", "Home (upbeat)", MusicTheme.GRATITUDE, 180, listOf(44, 54, 122, 132), rawRes = "home_upbeat"),
            MusicTrack("partner-chill", "You're Here (chill)", MusicTheme.LOVE, 120, listOf(38, 48, 88, 98), rawRes = "partner_chill"),
            MusicTrack("partner-upbeat", "You're Here (upbeat)", MusicTheme.LOVE, 120, listOf(36, 46, 88, 98), rawRes = "partner_upbeat"),
            MusicTrack("together-chill", "Together (chill)", MusicTheme.LOVE, 120, listOf(34, 44, 84, 94), rawRes = "together_chill"),
            MusicTrack("together-upbeat", "Together (upbeat)", MusicTheme.LOVE, 110, listOf(34, 44, 82, 92), rawRes = "together_upbeat"),
            MusicTrack("abundance-chill", "Flowing (chill)", MusicTheme.ABUNDANCE, 120, listOf(38, 48, 88, 98), rawRes = "abundance_chill"),
            MusicTrack("abundance-upbeat", "Flowing (upbeat)", MusicTheme.ABUNDANCE, 120, listOf(38, 48, 88, 98), rawRes = "abundance_upbeat"),
            MusicTrack("freedom-chill", "I'm Free (chill)", MusicTheme.JOY, 120, listOf(38, 48, 92, 102), rawRes = "freedom_chill"),
            MusicTrack("freedom-upbeat", "I'm Free (upbeat)", MusicTheme.JOY, 120, listOf(38, 48, 88, 98), rawRes = "freedom_upbeat"),
            MusicTrack("car-chill", "Driving (chill)", MusicTheme.JOY, 120, listOf(38, 48, 88, 98), rawRes = "car_chill"),
            MusicTrack("car-upbeat", "Driving (upbeat)", MusicTheme.JOY, 120, listOf(38, 48, 88, 98), rawRes = "car_upbeat"),
        )

        /**
         * Only real songs now (the placeholder tracks are gone). Experiences saved with a
         * placeholder track fall back to their feeling's default song.
         */
        val catalog: List<MusicTrack> get() = songs

        fun byId(id: String?): MusicTrack? = catalog.firstOrNull { it.id == id }

        fun default(feeling: Feeling): MusicTrack {
            val id = when (feeling) {
                Feeling.GRATITUDE -> "home-chill"
                Feeling.LOVE -> "partner-chill"
                Feeling.JOY -> "freedom-upbeat"
                Feeling.BLISS -> "car-upbeat"
                Feeling.CALM -> "abundance-chill"
                Feeling.CONFIDENCE -> "abundance-upbeat"
            }
            return byId(id) ?: songs.first()
        }
    }
}

/** Starter visions offered in "New vision". */
data class VisionTemplate(
    val id: String,
    val title: String,
    val points: List<String>,
    val feelings: List<Feeling>,
    val theme: MusicTheme,
    val tile: Color,
    /** Built-in image set the slideshow starts with. */
    val starterImageSet: String,
    /** The song this vision starts with. */
    val trackId: String = "",
) {
    val track: MusicTrack get() = MusicTrack.byId(trackId) ?: MusicTrack.default(feelings[0])

    companion object {
        val all: List<VisionTemplate> = listOf(
            VisionTemplate("home", "My dream home", listOf("The keys are in my hand", "Morning light fills our kitchen", "A garden where the people I love gather"), listOf(Feeling.JOY, Feeling.GRATITUDE), MusicTheme.GRATITUDE, Color(0xFF4A3418), "home", "home-chill"),
            VisionTemplate("money", "Financial freedom", listOf("The money is in my account", "I give generously and easily", "I choose how I spend my days"), listOf(Feeling.JOY, Feeling.CONFIDENCE, Feeling.GRATITUDE), MusicTheme.ABUNDANCE, Color(0xFF1E3A2C), "abundance", "abundance-upbeat"),
            VisionTemplate("love", "A loving relationship", listOf("We laugh together every day", "I feel completely seen and safe", "We build our life side by side"), listOf(Feeling.LOVE, Feeling.GRATITUDE), MusicTheme.LOVE, Color(0xFF3F1C28), "together", "together-chill"),
            VisionTemplate("business", "My business thriving", listOf("Clients come to me easily", "My team loves the work we do", "We grow every month"), listOf(Feeling.CONFIDENCE, Feeling.JOY), MusicTheme.BUSINESS, Color(0xFF1C2B40), "growth", "car-upbeat"),
            VisionTemplate("health", "Vibrant health", listOf("I wake up full of energy", "My body feels strong and light", "I move with ease every day"), listOf(Feeling.CALM, Feeling.GRATITUDE), MusicTheme.HEALTH, Color(0xFF2D2245), "shores", "freedom-chill"),
        )

        fun byId(id: String?): VisionTemplate? = all.firstOrNull { it.id == id }
    }
}

/**
 * An experience = a vision (title + points) felt through an ordered arc of 1–3 feelings,
 * with music and an ordered slideshow. Stored as JSON by [EmotionStore].
 */
@Serializable
data class Experience(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "",
    val visionPoints: List<String> = emptyList(),
    val feelingsRaw: List<String> = listOf(Feeling.GRATITUDE.raw),
    val trackId: String = "",
    /** Ordered slideshow, one ref per image (see [SlideRef]). */
    val imageRefs: List<String> = emptyList(),
    val cueAuto: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val lastPlayedAt: Long? = null,
) {
    val feelings: List<Feeling>
        get() = feelingsRaw.mapNotNull { Feeling.fromRaw(it) }.take(Feeling.MAX_PER_EXPERIENCE)
            .ifEmpty { listOf(Feeling.GRATITUDE) }
    val feeling: Feeling get() = feelings.first()
    val track: MusicTrack get() = MusicTrack.byId(trackId) ?: MusicTrack.default(feeling)
    val slides: List<SlideRef> get() = SlideRef.expand(imageRefs)
    val arcLabel: String get() = feelings.joinToString(" → ") { it.label }
    val touchSequence: TouchPattern get() = Feeling.sequence(feelings)
    val imagesLabel: String get() = if (imageRefs.isEmpty()) "No images" else SlideRef.label(imageRefs)

    fun withoutSlide(slide: SlideRef): Experience =
        copy(imageRefs = SlideRef.normalized(imageRefs).filter { it != slide.ref })
}

/**
 * One slideshow image, stored in [Experience.imageRefs] as a string:
 * - "img:<name>"  a single built-in image (res/drawable-nodpi, same names as iOS)
 * - "file:<name>" the user's photo, kept as a private resized copy in app storage (never uploaded)
 * "app:<setId>" (a whole set) is accepted and expanded to single images.
 */
sealed class SlideRef {
    abstract val ref: String

    data class App(val name: String) : SlideRef() {
        override val ref: String get() = "img:$name"
    }

    data class Own(val fileName: String) : SlideRef() {
        override val ref: String get() = "file:$fileName"
    }

    val isOwnPhoto: Boolean get() = this is Own

    companion object {
        const val MAX = 30

        fun parse(ref: String): SlideRef? = when {
            ref.startsWith("img:") -> ref.removePrefix("img:").let { n -> if (SlideshowImageSets.resId(n) != null) App(n) else null }
            ref.startsWith("file:") -> Own(ref.removePrefix("file:"))
            else -> null
        }

        /** Stored refs as an ordered list of single images, without duplicates. */
        fun normalized(refs: List<String>): List<String> {
            val out = mutableListOf<String>()
            for (r in refs) {
                if (r.startsWith("app:")) {
                    SlideshowImageSets.byId(r.removePrefix("app:"))?.images?.forEach { out.add("img:${it.name}") }
                } else if (parse(r) != null) {
                    out.add(r)
                }
            }
            return out.distinct().take(MAX)
        }

        fun expand(refs: List<String>): List<SlideRef> = normalized(refs).mapNotNull { parse(it) }

        fun label(refs: List<String>): String {
            val slides = expand(refs)
            if (slides.isEmpty()) return "None yet"
            val own = slides.count { it.isOwnPhoto }
            val app = slides.size - own
            return when {
                own == 0 -> "$app app image${if (app == 1) "" else "s"}"
                app == 0 -> "$own of your photo${if (own == 1) "" else "s"}"
                else -> "${slides.size} images · $own yours"
            }
        }
    }
}
