package com.familytree.core.ui.media

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import android.graphics.Bitmap
import com.familytree.core.media.MediaResolver
import com.familytree.core.media.PdfPreviewer
import com.familytree.core.media.ResolvedMedia
import com.familytree.core.model.MediaFolder
import com.familytree.core.model.MediaObject

/**
 * Everything needed to turn a media record into something displayable.
 *
 * Resolution depends on the open tree and its granted folders, and those are the same for
 * every image on screen — so they are provided once around the tree's screens rather than
 * threaded through every list item, card and avatar that might show a photo.
 */
data class MediaContext(
    val resolver: MediaResolver,
    val pdfPreviewer: PdfPreviewer,
    val treeId: Long,
    val folders: List<MediaFolder> = emptyList(),
)

val LocalMediaContext = compositionLocalOf<MediaContext?> { null }

/**
 * Resolves a media record, off the main thread, re-running when the record or the
 * granted folders change.
 *
 * Starts as [ResolvedMedia.Missing], so a slow SAF lookup shows a placeholder rather than
 * blocking the frame — which is what the original's on-construction search did.
 */
@Composable
fun rememberResolvedMedia(media: MediaObject?): State<ResolvedMedia> {
    val context = LocalMediaContext.current
    val link = media?.file
    if (context == null || link.isNullOrBlank()) {
        return remember(link) { mutableStateOf(ResolvedMedia.Missing) }
    }
    return produceState(ResolvedMedia.Missing, link, context.treeId, context.folders) {
        value = context.resolver.resolve(context.treeId, link, context.folders)
    }
}

/** Resolves several records at once, for a gallery page. */
@Composable
fun rememberResolvedMedia(media: List<MediaObject>): State<Map<Long, ResolvedMedia>> {
    val context = LocalMediaContext.current
    val links = media.map { it.id to it.file }
    return produceState(emptyMap(), links, context?.treeId, context?.folders) {
        if (context == null) return@produceState
        value = links.mapNotNull { (id, link) ->
            id to context.resolver.resolve(context.treeId, link, context.folders)
        }.toMap()
    }
}

/** Renders the first page of a resolved PDF, once, off the main thread. */
@Composable
fun rememberPdfPreview(resolved: ResolvedMedia): State<Bitmap?> {
    val context = LocalMediaContext.current
    return produceState<Bitmap?>(null, resolved.source, context) {
        value = context?.pdfPreviewer?.firstPage(resolved.source)
    }
}

/** Re-resolves everything after a file is added, renamed or a folder is granted. */
@Composable
fun InvalidateMediaOn(vararg keys: Any?) {
    val context = LocalMediaContext.current
    LaunchedEffect(*keys) { context?.resolver?.invalidate(context.treeId) }
}
