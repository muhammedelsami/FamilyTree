package com.familytree.core.media

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.familytree.core.common.di.Dispatcher
import com.familytree.core.common.di.FtDispatcher
import com.familytree.core.model.MediaFolder
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Finds the file behind a media record.
 *
 * GEDCOM stores only a string, written by whichever program produced the tree, so the
 * same photo can be recorded as `C:\Users\anna\Pictures\nonna.jpg` on one machine and
 * `nonna.jpg` on another. Resolution therefore tries several interpretations in a fixed
 * order, from most specific to least:
 *
 * 1. the link as an absolute path;
 * 2. the link's *filename* inside this tree's own storage — the case for anything
 *    imported through the app;
 * 3. each media folder the user granted, first as folder + full link, then folder + filename;
 * 4. each granted SAF folder, walking the link's segments as subfolders;
 * 5. a URL, if the link looks like one.
 *
 * Unlike the original this runs off the main thread and caches its answers: walking a SAF
 * tree costs a `ContentResolver` query per segment per folder, and the gallery and diagram
 * ask for the same records repeatedly while scrolling.
 */
@Singleton
class MediaResolver @Inject constructor(
    @ApplicationContext private val context: Context,
    @Dispatcher(FtDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) {

    private val cache = Collections.synchronizedMap(mutableMapOf<CacheKey, ResolvedMedia>())

    /**
     * @param link the `OBJE.FILE` value.
     * @param folders the media folders configured for the tree.
     * @param fileOnly skips the SAF search. The diagram uses this when it needs an answer
     *   for many records at once and can afford to miss the ones only reachable through a
     *   granted folder.
     */
    suspend fun resolve(
        treeId: Long,
        link: String?,
        folders: List<MediaFolder> = emptyList(),
        fileOnly: Boolean = false,
    ): ResolvedMedia {
        if (link.isNullOrBlank()) return ResolvedMedia.Missing
        // The folders are part of the key, not just an input. A screen asks for a photo
        // as soon as it composes, which is often before the tree's granted folders have
        // loaded; without this, that first answer — a miss — would be served to every
        // later attempt, and the photo would appear or not depending on which won the
        // race.
        val key = CacheKey(treeId, link, fileOnly, folders.map { it.id }.toSet())
        cache[key]?.let { return it }
        return withContext(ioDispatcher) {
            search(treeId, link, folders, fileOnly).also { cache[key] = it }
        }
    }

    /** Drops cached answers for a tree, after an import, a rename or a folder change. */
    fun invalidate(treeId: Long? = null) {
        if (treeId == null) {
            cache.clear()
        } else {
            synchronized(cache) { cache.keys.removeAll { it.treeId == treeId } }
        }
    }

    /** The per-tree folder the app copies imported media into. */
    fun treeStorage(treeId: Long): File =
        // getExternalFilesDir is scoped storage: no permission needed, and Android deletes
        // it with the app, which is why media is also listed in the ZIP backup.
        (context.getExternalFilesDir(treeId.toString()) ?: File(context.filesDir, treeId.toString()))
            .apply { mkdirs() }

    private fun search(
        treeId: Long,
        link: String,
        folders: List<MediaFolder>,
        fileOnly: Boolean,
    ): ResolvedMedia {
        // Windows separators are normalised once: every later step assumes '/'.
        val path = link.replace('\\', '/')
        val filename = path.substringAfterLast('/')

        findFile(treeId, path, filename, folders)?.let { return it }
        if (!fileOnly) findDocument(path, folders)?.let { return it }
        if (isWebLink(link)) {
            return ResolvedMedia(
                source = MediaSource.Web(link),
                name = filename.takeIf { it.isNotBlank() },
                extension = extensionOf(filename),
                kind = kindOf(extensionOf(filename)),
            )
        }
        // Nothing was found, but the record still describes a file: report its name and
        // kind so the gallery can show a typed placeholder instead of a blank tile.
        return ResolvedMedia(
            source = MediaSource.Missing,
            name = filename.takeIf { it.isNotBlank() },
            extension = extensionOf(filename),
            kind = kindOf(extensionOf(filename)),
        )
    }

    private fun findFile(
        treeId: Long,
        path: String,
        filename: String,
        folders: List<MediaFolder>,
    ): ResolvedMedia? {
        File(path).takeIf { it.isFile && it.canRead() }?.let { return it.resolved() }

        File(treeStorage(treeId), filename).takeIf { it.isFile && it.canRead() }?.let {
            // A link of "C:/photos/nonna.jpg" matched by filename alone means the tree
            // travelled here from another device; the gallery can offer to shorten it.
            return it.resolved(foundByFilename = filename != path)
        }

        folders.filter { it.kind == MediaFolder.Kind.PATH }.forEach { folder ->
            File("${folder.value}/$path").takeIf { it.isFile && it.canRead() }?.let { return it.resolved() }
            File("${folder.value}/$filename").takeIf { it.isFile && it.canRead() }
                ?.let { return it.resolved(foundByFilename = filename != path) }
        }
        return null
    }

    /**
     * Walks the link's segments through each granted folder.
     *
     * The walk stops at the first segment that is neither a file nor a folder, then tries
     * the bare filename where it stopped. That covers both `holiday/1998/anna.jpg` under a
     * granted parent and a link whose leading path is meaningless on this device.
     */
    private fun findDocument(path: String, folders: List<MediaFolder>): ResolvedMedia? {
        val segments = path.split('/').filter { it.isNotBlank() }
        if (segments.isEmpty()) return null

        folders.filter { it.kind == MediaFolder.Kind.URI }.forEach { folder ->
            val root = runCatching { DocumentFile.fromTreeUri(context, folder.value.toUri()) }
                .getOrNull() ?: return@forEach
            var directory: DocumentFile = root
            var descended = false
            for (segment in segments) {
                val match = runCatching { directory.findFile(segment) }.getOrNull() ?: break
                when {
                    match.isFile -> return match.resolved(foundInSubfolder = descended)
                    match.isDirectory -> {
                        directory = match
                        descended = true
                    }
                    else -> break
                }
            }
            runCatching { directory.findFile(segments.last()) }.getOrNull()
                ?.takeIf { it.isFile }
                ?.let { return it.resolved(foundInSubfolder = descended) }
        }
        return null
    }

    private fun File.resolved(foundByFilename: Boolean = false) = ResolvedMedia(
        source = MediaSource.LocalFile(this),
        name = name,
        extension = extensionOf(name),
        kind = kindOf(extensionOf(name)),
        foundByFilename = foundByFilename,
    )

    private fun DocumentFile.resolved(foundInSubfolder: Boolean) = ResolvedMedia(
        source = MediaSource.DocumentUri(uri),
        name = name,
        extension = extensionOf(name),
        kind = kindOf(extensionOf(name)),
        foundInSubfolder = foundInSubfolder,
    )

    private data class CacheKey(
        val treeId: Long,
        val link: String,
        val fileOnly: Boolean,
        val folderIds: Set<Long>,
    )
}

private fun String.toUri(): Uri = Uri.parse(this)

internal fun isWebLink(link: String): Boolean =
    link.startsWith("http://", ignoreCase = true) || link.startsWith("https://", ignoreCase = true)

/** The lowercase extension of a filename, or null when it has none. */
fun extensionOf(name: String?): String? {
    val dot = name?.lastIndexOf('.') ?: return null
    if (dot < 0 || dot == name.length - 1) return null
    return name.substring(dot + 1).lowercase()
}

fun kindOf(extension: String?): MediaKind = when (extension) {
    null -> MediaKind.NONE
    in IMAGE_EXTENSIONS -> MediaKind.IMAGE
    in VIDEO_EXTENSIONS -> MediaKind.VIDEO
    "pdf" -> MediaKind.PDF
    else -> MediaKind.DOCUMENT
}

private val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "heif", "avif")
private val VIDEO_EXTENSIONS = setOf("mp4", "3gp", "webm", "mkv", "mpg", "mpeg", "mov", "avi")
