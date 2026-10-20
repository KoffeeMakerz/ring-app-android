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
        /** Calm cue: three soft hums with lengthening gaps, like a breath slowing down. */
        val CALM_CUE = TouchPattern("calm.cue", "Slow down", listOf(800, 1200, 800, 1800, 800))
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
        listOf(TouchBlock.DOUBLE_TAP, TouchBlock.SHORT_REST, TouchBlock.TAP), "Two, pause, one",
        "It's real. Look around and thank everyone and everything that brought you here.",
    ),
    LOVE(
        "love", "Love", Color(0xFFF27A9B), Color(0xFF2E1420),
        listOf(TouchBlock.HEARTBEAT, TouchBlock.SHORT_REST, TouchBlock.HEARTBEAT), "A heartbeat, twice",
        "Feel the people you love here with you. Let the warmth fill your chest.",
    ),
    JOY(
        "joy", "Joy", Color(0xFFF5D76E), Color(0xFF2B2612),
        listOf(TouchBlock.FLUTTER, TouchBlock.SHORT_REST, TouchBlock.FLUTTER), "Two quick flutters",
        "It's happening. Let the joy rise and spill over.",
    ),
    BLISS(
        "bliss", "Bliss", Color(0xFFB892FF), Color(0xFF221A33),
        listOf(TouchBlock.FLUTTER, TouchBlock.DOUBLE_TAP), "Five rising taps",
        "Let your whole body light up with it.",
    ),
    CALM(
        "calm", "Calm", Color(0xFF7FB2F0), Color(0xFF15233A),
        listOf(TouchBlock.LONG_HUM, TouchBlock.LONG_REST, TouchBlock.LONG_HUM), "Two slow hums",
        "Everything is taken care of. Breathe out and settle into it.",
    ),
    CONFIDENCE(
        "confidence", "Confidence", Color(0xFF6FD6A1), Color(0xFF14291F),
        listOf(TouchBlock.TAP, TouchBlock.SHORT_REST, TouchBlock.DOUBLE_TAP), "One, then two",
        "You made this happen. Stand tall in it.",
    );

    val touch: TouchPattern get() = TouchBlock.compose(blocks, "feeling.$raw", "$label touch")

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

/** A curated track. Until real songs are bundled the player runs on a silent clock. */
data class MusicTrack(
    val id: String,
    val title: String,
    val theme: MusicTheme,
    val durationS: Int,
    /** Cue moments (each chorus), authored with the song. */
    val cueMarkersS: List<Int>,
) {
    val durationLabel: String get() = "%d:%02d".format(durationS / 60, durationS % 60)

    companion object {
        val catalog: List<MusicTrack> = MusicTheme.entries.flatMap { theme ->
            (1..3).map { i ->
                val len = 210 + i * 25
                MusicTrack(
                    id = "${theme.raw}-$i",
                    title = "[${theme.label} track $i]",
                    theme = theme,
                    durationS = len,
                    cueMarkersS = (38 until len - 10 step 42).toList(),
                )
            }
        }

        fun byId(id: String?): MusicTrack? = catalog.firstOrNull { it.id == id }

        fun default(feeling: Feeling): MusicTrack {
            val theme = when (feeling) {
                Feeling.GRATITUDE -> MusicTheme.GRATITUDE
                Feeling.LOVE -> MusicTheme.LOVE
                Feeling.JOY, Feeling.BLISS -> MusicTheme.JOY
                Feeling.CALM -> MusicTheme.HEALTH
                Feeling.CONFIDENCE -> MusicTheme.BUSINESS
            }
            return catalog.first { it.theme == theme }
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
) {
    val track: MusicTrack get() = MusicTrack.catalog.firstOrNull { it.theme == theme } ?: MusicTrack.default(feelings[0])

    companion object {
        val all: List<VisionTemplate> = listOf(
            VisionTemplate("home", "My dream home", listOf("The keys are in my hand", "Morning light fills our kitchen", "A garden where the people I love gather"), listOf(Feeling.JOY, Feeling.GRATITUDE), MusicTheme.GRATITUDE, Color(0xFF4A3418), "home"),
            VisionTemplate("money", "Financial freedom", listOf("The money is in my account", "I give generously and easily", "I choose how I spend my days"), listOf(Feeling.JOY, Feeling.CONFIDENCE, Feeling.GRATITUDE), MusicTheme.ABUNDANCE, Color(0xFF1E3A2C), "abundance"),
            VisionTemplate("love", "A loving relationship", listOf("We laugh together every day", "I feel completely seen and safe", "We build our life side by side"), listOf(Feeling.LOVE, Feeling.GRATITUDE), MusicTheme.LOVE, Color(0xFF3F1C28), "together"),
            VisionTemplate("business", "My business thriving", listOf("Clients come to me easily", "My team loves the work we do", "We grow every month"), listOf(Feeling.CONFIDENCE, Feeling.JOY), MusicTheme.BUSINESS, Color(0xFF1C2B40), "growth"),
            VisionTemplate("health", "Vibrant health", listOf("I wake up full of energy", "My body feels strong and light", "I move with ease every day"), listOf(Feeling.CALM, Feeling.GRATITUDE), MusicTheme.HEALTH, Color(0xFF2D2245), "shores"),
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
