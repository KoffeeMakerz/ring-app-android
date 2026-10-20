package com.pulseloop.emotion

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.pulseloop.emotion.ui.EColors
import com.pulseloop.ui.slideshow.SlideshowImageSets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlin.math.min

/**
 * Slideshow images: the user's photos are copied in as resized JPEGs (longest side 2048 px) so a
 * slideshow keeps working if the original is deleted, and nothing leaves the phone. Built-in
 * images are decoded down-sampled too: they are 1080x1920 and a full decode per thumbnail would
 * exhaust memory in the picker grid.
 */
object ExperienceImages {
    private const val MAX_SIDE = 2048

    fun folder(context: Context): File = File(context.filesDir, "experience_images").apply { mkdirs() }

    /** Copies a picked photo into app storage. Returns the file name, or null if it can't be read. */
    suspend fun import(context: Context, uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = context.contentResolver
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
            val options = BitmapFactory.Options().apply { inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, MAX_SIDE) }
            val decoded = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
                ?: return@runCatching null
            val orientation = runCatching {
                resolver.openInputStream(uri)?.use {
                    ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                }
            }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
            val degrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            val rotated = if (degrees != 0f) {
                Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, Matrix().apply { postRotate(degrees) }, true)
            } else decoded
            val scale = min(1f, MAX_SIDE.toFloat() / max(rotated.width, rotated.height))
            val output = if (scale < 1f) {
                Bitmap.createScaledBitmap(rotated, (rotated.width * scale).toInt(), (rotated.height * scale).toInt(), true)
            } else rotated
            val name = UUID.randomUUID().toString() + ".jpg"
            File(folder(context), name).outputStream().use { output.compress(Bitmap.CompressFormat.JPEG, 85, it) }
            name
        }.getOrNull()
    }

    private val cache = object : LruCache<String, Bitmap>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private fun key(slide: SlideRef, maxSide: Int) = "${slide.ref}@$maxSide"

    fun cached(slide: SlideRef, maxSide: Int): Bitmap? = cache.get(key(slide, maxSide))

    /** Decodes a slide no larger than about [maxSide] px on its longest side. */
    suspend fun load(context: Context, slide: SlideRef, maxSide: Int): Bitmap? {
        cached(slide, maxSide)?.let { return it }
        val bitmap = withContext(Dispatchers.IO) {
            runCatching {
                when (slide) {
                    is SlideRef.App -> {
                        val res = SlideshowImageSets.resId(slide.name) ?: return@runCatching null
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeResource(context.resources, res, bounds)
                        BitmapFactory.decodeResource(context.resources, res, BitmapFactory.Options().apply {
                            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxSide)
                        })
                    }
                    is SlideRef.Own -> {
                        val file = File(folder(context), slide.fileName)
                        if (!file.exists()) return@runCatching null
                        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        BitmapFactory.decodeFile(file.path, bounds)
                        BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply {
                            inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxSide)
                        })
                    }
                }
            }.getOrNull()
        }
        if (bitmap != null) cache.put(key(slide, maxSide), bitmap)
        return bitmap
    }

    private fun sampleSize(width: Int, height: Int, maxSide: Int): Int {
        var sample = 1
        while (max(width, height) / (sample * 2) >= maxSide) sample *= 2
        return sample
    }
}

/** One slide filling [modifier]'s bounds (centre-cropped). [maxSide] picks the decode size. */
@Composable
fun SlideImage(slide: SlideRef, modifier: Modifier = Modifier, maxSide: Int = 1600) {
    val context = LocalContext.current
    val bitmap by produceState(initialValue = ExperienceImages.cached(slide, maxSide), slide, maxSide) {
        value = ExperienceImages.load(context, slide, maxSide)
    }
    val image = remember(bitmap) { bitmap?.asImageBitmap() }
    if (image != null) {
        Image(bitmap = image, contentDescription = null, modifier = modifier, contentScale = ContentScale.Crop)
    } else {
        Box(modifier.background(EColors.card))
    }
}

/** A tinted stand-in when an experience has no images yet. */
@Composable
fun SlidePlaceholder(feeling: Feeling, modifier: Modifier = Modifier) {
    Box(modifier.background(feeling.ground))
}
