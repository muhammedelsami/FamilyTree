package com.familytree.core.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import com.familytree.core.common.di.Dispatcher
import com.familytree.core.common.di.FtDispatcher
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A crop window, as fractions of the displayed image.
 *
 * Fractions rather than pixels so the screen never has to know the file's real
 * resolution: the same numbers that describe a selection over a 900-pixel preview
 * describe it over the 6000-pixel original.
 */
data class CropRect(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    val isWholeImage: Boolean
        get() = left <= 0.001f && top <= 0.001f && right >= 0.999f && bottom >= 0.999f

    fun coerced(minSize: Float = MIN_SIZE): CropRect {
        val l = left.coerceIn(0f, 1f - minSize)
        val t = top.coerceIn(0f, 1f - minSize)
        return CropRect(
            left = l,
            top = t,
            right = right.coerceIn(l + minSize, 1f),
            bottom = bottom.coerceIn(t + minSize, 1f),
        )
    }

    private companion object {
        const val MIN_SIZE = 0.05f
    }
}

/**
 * Crops and rotates the photographs the app owns.
 *
 * Family Gem used a View-based cropping library; this does the same job with plain
 * bitmap operations so the screen above it can be Compose. Only files inside the app's
 * own storage are editable — a photo reached through a granted folder belongs to some
 * other app, and rewriting it would be overstepping.
 */
@Singleton
class ImageCropper @Inject constructor(
    @Dispatcher(FtDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * Loads a downscaled copy for the editing screen.
     *
     * @param maxSize longest edge, in pixels. A full-resolution phone photograph is
     *   comfortably over 50 MB decoded, which is not worth holding to draw a rectangle on.
     */
    suspend fun loadPreview(file: File, maxSize: Int = PREVIEW_SIZE): Bitmap? =
        withContext(ioDispatcher) {
            runCatching {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(file.path, bounds)
                if (bounds.outWidth <= 0) return@runCatching null

                val options = BitmapFactory.Options().apply {
                    inSampleSize = sampleSize(bounds.outWidth, bounds.outHeight, maxSize)
                }
                BitmapFactory.decodeFile(file.path, options)?.let { orientedForDisplay(it, file) }
            }.getOrNull()
        }

    /**
     * Writes the cropped image back over the original.
     *
     * The new image is built first and only then replaces the file, so an interrupted
     * crop leaves the user's photograph intact rather than half-written.
     */
    suspend fun cropInPlace(file: File, crop: CropRect, rotationDegrees: Int): Boolean =
        withContext(ioDispatcher) {
            if (crop.isWholeImage && rotationDegrees % 360 == 0) return@withContext true
            runCatching {
                val source = BitmapFactory.decodeFile(file.path) ?: return@runCatching false
                val upright = orientedForDisplay(source, file)
                val rotated = rotate(upright, rotationDegrees)

                val safe = crop.coerced()
                val x = (safe.left * rotated.width).roundToInt().coerceIn(0, rotated.width - 1)
                val y = (safe.top * rotated.height).roundToInt().coerceIn(0, rotated.height - 1)
                val width = (safe.width * rotated.width).roundToInt().coerceIn(1, rotated.width - x)
                val height = (safe.height * rotated.height).roundToInt().coerceIn(1, rotated.height - y)
                val cropped = Bitmap.createBitmap(rotated, x, y, width, height)

                val temporary = File(file.parentFile, "${file.name}.cropping")
                temporary.outputStream().use { out ->
                    cropped.compress(formatFor(file.name), QUALITY, out)
                }
                val replaced = temporary.renameTo(file) || run {
                    // renameTo will not overwrite on some filesystems; fall back to a copy.
                    temporary.copyTo(file, overwrite = true)
                    temporary.delete()
                    true
                }

                if (rotated !== upright) rotated.recycle()
                if (upright !== source) upright.recycle()
                source.recycle()
                cropped.recycle()
                replaced
            }.getOrDefault(false)
        }

    /**
     * Applies the orientation the camera recorded in EXIF.
     *
     * A phone photograph is often stored sideways with a tag saying which way is up. Left
     * unapplied, the crop rectangle the user drew would land on a rotated image.
     */
    private fun orientedForDisplay(bitmap: Bitmap, file: File): Bitmap {
        val degrees = runCatching {
            when (ExifInterface(file).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90
                ExifInterface.ORIENTATION_ROTATE_180 -> 180
                ExifInterface.ORIENTATION_ROTATE_270 -> 270
                else -> 0
            }
        }.getOrDefault(0)
        return rotate(bitmap, degrees)
    }

    private fun rotate(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees % 360 == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun formatFor(name: String): Bitmap.CompressFormat =
        // PNG for anything with transparency to lose; JPEG for photographs, which is
        // what almost every file here is.
        if (extensionOf(name) == "png") Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG

    private fun sampleSize(width: Int, height: Int, maxSize: Int): Int {
        var sample = 1
        while (max(width, height) / sample > maxSize) sample *= 2
        return sample
    }

    private companion object {
        const val PREVIEW_SIZE = 1600
        const val QUALITY = 92
    }
}
