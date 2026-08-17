package com.familytree.core.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import com.familytree.core.common.di.Dispatcher
import com.familytree.core.common.di.FtDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * Renders the first page of a PDF so scanned documents get a real thumbnail.
 *
 * Genealogy attachments are full of PDFs — birth certificates, parish records, land
 * deeds — and a grid of identical PDF icons is useless for finding the right one.
 */
@Singleton
class PdfPreviewer @Inject constructor(
    @ApplicationContext private val context: Context,
    @Dispatcher(FtDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) {

    /**
     * @param targetWidth the width to render at, in pixels. The page is rendered at the
     *   size it will be shown, since a full-resolution A4 page is a large bitmap to hold
     *   for a thumbnail.
     */
    suspend fun firstPage(source: MediaSource, targetWidth: Int = DEFAULT_WIDTH): Bitmap? =
        withContext(ioDispatcher) {
            runCatching {
                descriptor(source)?.use { file ->
                    PdfRenderer(file).use { renderer ->
                        if (renderer.pageCount == 0) return@use null
                        renderer.openPage(0).use { page ->
                            val width = targetWidth.coerceAtMost(MAX_WIDTH).coerceAtLeast(1)
                            val height = (width * page.height.toFloat() / page.width).roundToInt().coerceAtLeast(1)
                            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                            // A PDF page is transparent where nothing is drawn, which on a
                            // dark theme renders black text on black. Paper first.
                            Canvas(bitmap).drawColor(Color.WHITE)
                            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bitmap
                        }
                    }
                }
            }.getOrNull()
        }

    private fun descriptor(source: MediaSource): ParcelFileDescriptor? = when (source) {
        is MediaSource.LocalFile -> ParcelFileDescriptor.open(source.file, ParcelFileDescriptor.MODE_READ_ONLY)
        is MediaSource.DocumentUri -> context.contentResolver.openFileDescriptor(source.uri, "r")
        // A PDF behind a URL would have to be downloaded first; the viewer hands those
        // to the browser instead.
        else -> null
    }

    private companion object {
        const val DEFAULT_WIDTH = 512
        const val MAX_WIDTH = 2048
    }
}
