package com.pulseloop.ui.slideshow

import androidx.annotation.DrawableRes
import com.pulseloop.R

/**
 * Built-in slideshow images, mirroring `ImageSet.all` in the iOS app (Feelings.swift).
 * Images are 1080x1920 JPEGs in res/drawable-nodpi.
 * `art_*` are original illustrations; `photo_*` are Unsplash License photos
 * (credits: claude/image-credits-v1.md in the project docs).
 *
 * Experiences store single images by name ("img:<name>", same names as the iOS asset catalog),
 * so a slideshow can mix images from several sets and the user's own photos.
 */
data class SlideshowImage(val name: String, @DrawableRes val resId: Int)

data class SlideshowImageSet(
    val id: String,
    val name: String,
    /** Feeling id, same raw values as iOS: gratitude, love, joy, calm, confidence, bliss. */
    val feeling: String,
    val images: List<SlideshowImage>,
)

object SlideshowImageSets {
    private fun img(name: String, @DrawableRes resId: Int) = SlideshowImage(name, resId)

    val all: List<SlideshowImageSet> = listOf(
        SlideshowImageSet(
            "golden", "Golden mornings", "gratitude",
            listOf(
                img("art_golden_01", R.drawable.art_golden_01),
                img("art_golden_02", R.drawable.art_golden_02),
                img("art_golden_03", R.drawable.art_golden_03),
            ),
        ),
        SlideshowImageSet(
            "hearts", "Warm hearts", "love",
            listOf(
                img("art_hearts_01", R.drawable.art_hearts_01),
                img("art_hearts_02", R.drawable.art_hearts_02),
                img("art_hearts_03", R.drawable.art_hearts_03),
            ),
        ),
        SlideshowImageSet(
            "sky", "Open sky", "joy",
            listOf(
                img("art_sky_01", R.drawable.art_sky_01),
                img("art_sky_02", R.drawable.art_sky_02),
                img("art_sky_03", R.drawable.art_sky_03),
            ),
        ),
        SlideshowImageSet(
            "growth", "Green growth", "confidence",
            listOf(
                img("art_growth_01", R.drawable.art_growth_01),
                img("art_growth_02", R.drawable.art_growth_02),
                img("art_growth_03", R.drawable.art_growth_03),
            ),
        ),
        SlideshowImageSet(
            "water", "Still water", "calm",
            listOf(
                img("art_water_01", R.drawable.art_water_01),
                img("art_water_02", R.drawable.art_water_02),
                img("art_water_03", R.drawable.art_water_03),
            ),
        ),
        SlideshowImageSet(
            "summit", "Summit", "bliss",
            listOf(
                img("art_summit_01", R.drawable.art_summit_01),
                img("art_summit_02", R.drawable.art_summit_02),
                img("art_summit_03", R.drawable.art_summit_03),
                img("art_summit_04", R.drawable.art_summit_04),
            ),
        ),
        SlideshowImageSet(
            "home", "Dream home", "joy",
            listOf(
                img("photo_home_01", R.drawable.photo_home_01),
                img("photo_home_02", R.drawable.photo_home_02),
                img("photo_home_03", R.drawable.photo_home_03),
                img("photo_home_04", R.drawable.photo_home_04),
                img("photo_home_05", R.drawable.photo_home_05),
            ),
        ),
        SlideshowImageSet(
            "beach", "Beach & vacation", "joy",
            listOf(
                img("photo_beach_01", R.drawable.photo_beach_01),
                img("photo_beach_02", R.drawable.photo_beach_02),
                img("photo_beach_03", R.drawable.photo_beach_03),
                img("photo_beach_04", R.drawable.photo_beach_04),
            ),
        ),
        SlideshowImageSet(
            "together", "Together", "love",
            listOf(
                img("photo_together_01", R.drawable.photo_together_01),
                img("photo_together_02", R.drawable.photo_together_02),
                img("photo_together_03", R.drawable.photo_together_03),
                img("photo_together_04", R.drawable.photo_together_04),
                img("photo_together_05", R.drawable.photo_together_05),
            ),
        ),
        SlideshowImageSet(
            "abundance", "Abundance", "confidence",
            listOf(
                img("photo_abundance_01", R.drawable.photo_abundance_01),
                img("photo_abundance_02", R.drawable.photo_abundance_02),
                img("photo_abundance_03", R.drawable.photo_abundance_03),
            ),
        ),
        SlideshowImageSet(
            "shores", "Calm shores", "calm",
            listOf(
                img("photo_shores_01", R.drawable.photo_shores_01),
                img("photo_shores_02", R.drawable.photo_shores_02),
            ),
        ),
    )

    private val byName: Map<String, Int> by lazy { all.flatMap { it.images }.associate { it.name to it.resId } }

    fun byId(id: String?): SlideshowImageSet? = all.firstOrNull { it.id == id }

    /** Drawable for a single image name, or null if it isn't a built-in image. */
    fun resId(name: String): Int? = byName[name]

    /** Sets that fit the feelings first (in the given order), then the rest. */
    fun ordered(feelings: List<String>): Pair<List<SlideshowImageSet>, List<SlideshowImageSet>> {
        val suggested = feelings.flatMap { f -> all.filter { it.feeling == f } }.distinct()
        return suggested to all.filter { it !in suggested }
    }
}
