package com.familytree.core.media

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.familytree.core.common.di.Dispatcher
import com.familytree.core.common.di.FtDispatcher
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** What happened when a picked file was brought into the tree. */
sealed interface ImportResult {

    /** The file was copied in and the media link should be set to [filename]. */
    data class Copied(val file: File, val filename: String, val kind: MediaKind) : ImportResult

    /**
     * A file of the same name and size is already in the tree's storage.
     *
     * The user decides: reuse it (one photo, two records) or keep a second copy. The
     * original asked the same question, and it matters — a tree where every relative's
     * portrait is a separate copy of the same scan gets large quickly.
     */
    data class AlreadyPresent(val file: File, val filename: String, val kind: MediaKind) : ImportResult

    data class Failed(val cause: Throwable) : ImportResult
}

/**
 * Brings files into the tree's own storage and manages them there.
 *
 * Media never leaves the device — that was a deliberate decision for this rewrite — so
 * this is the only place files are written, and the per-tree folder it uses is what the
 * ZIP backup packs up.
 */
@Singleton
class MediaStorage @Inject constructor(
    @ApplicationContext private val context: Context,
    private val resolver: MediaResolver,
    @Dispatcher(FtDispatcher.IO) private val ioDispatcher: CoroutineDispatcher,
) {

    /** Copies the picked document into the tree folder, keeping its name where possible. */
    suspend fun importFile(treeId: Long, uri: Uri): ImportResult = withContext(ioDispatcher) {
        runCatching {
            val folder = resolver.treeStorage(treeId)
            val filename = displayName(uri) ?: DEFAULT_NAME
            val incomingLength = DocumentFile.fromSingleUri(context, uri)?.length() ?: -1L

            val twin = File(folder, filename)
            if (twin.isFile && incomingLength >= 0 && twin.length() == incomingLength) {
                return@runCatching ImportResult.AlreadyPresent(twin, filename, kindOf(extensionOf(filename)))
            }

            val target = nextAvailableFile(folder, filename)
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Cannot read the selected file." }
                target.outputStream().use(input::copyTo)
            }
            resolver.invalidate(treeId)
            ImportResult.Copied(target, target.name, kindOf(extensionOf(target.name)))
        }.getOrElse(ImportResult::Failed)
    }

    /** Makes a second copy of a file already in the tree folder. */
    suspend fun duplicate(treeId: Long, file: File): ImportResult = withContext(ioDispatcher) {
        runCatching {
            val copy = file.copyTo(nextAvailableFile(resolver.treeStorage(treeId), file.name))
            resolver.invalidate(treeId)
            ImportResult.Copied(copy, copy.name, kindOf(extensionOf(copy.name)))
        }.getOrElse(ImportResult::Failed)
    }

    /** A file inside the tree folder for the camera to write into. */
    suspend fun newCameraFile(treeId: Long): File = withContext(ioDispatcher) {
        nextAvailableFile(resolver.treeStorage(treeId), CAMERA_NAME)
    }

    suspend fun rename(treeId: Long, source: MediaSource, newName: String): Boolean =
        withContext(ioDispatcher) {
            val renamed = when (source) {
                is MediaSource.LocalFile -> source.file.renameTo(File(source.file.parentFile, newName))
                is MediaSource.DocumentUri -> runCatching {
                    DocumentsContract.renameDocument(context.contentResolver, source.uri, newName) != null
                    // Android 9 renames the file and then throws FileNotFoundException
                    // anyway, so a thrown exception is not proof of failure here.
                }.getOrDefault(true)
                else -> false
            }
            if (renamed) resolver.invalidate(treeId)
            renamed
        }

    suspend fun delete(treeId: Long, source: MediaSource): Boolean = withContext(ioDispatcher) {
        val deleted = when (source) {
            is MediaSource.LocalFile -> source.file.delete()
            is MediaSource.DocumentUri -> DocumentFile.fromSingleUri(context, source.uri)?.delete() == true
            else -> false
        }
        if (deleted) resolver.invalidate(treeId)
        deleted
    }

    /** Removes a whole tree's media folder, when the tree itself is deleted. */
    suspend fun deleteTreeStorage(treeId: Long) = withContext(ioDispatcher) {
        resolver.treeStorage(treeId).deleteRecursively()
        resolver.invalidate(treeId)
    }

    /** Total bytes held by a tree's own media, for the tree info screen. */
    suspend fun storageSize(treeId: Long): Long = withContext(ioDispatcher) {
        resolver.treeStorage(treeId).walkTopDown().filter(File::isFile).sumOf(File::length)
    }

    /** True when the file sits in storage this app owns and may therefore rename or delete. */
    fun isOwned(file: File): Boolean {
        val path = file.absolutePath
        val owned = context.getExternalFilesDirs(null).asSequence() + context.externalMediaDirs.asSequence()
        return owned.filterNotNull().any { path.startsWith(it.absolutePath) }
    }

    private fun displayName(uri: Uri): String? {
        if (uri.scheme.equals("file", ignoreCase = true)) return uri.lastPathSegment
        context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val column = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (column >= 0 && cursor.moveToFirst()) return cursor.getString(column)
        }
        return DocumentFile.fromSingleUri(context, uri)?.name
    }

    private companion object {
        const val DEFAULT_NAME = "media file"
        const val CAMERA_NAME = "photo.jpg"
    }
}

/**
 * A free filename in [folder], appending ` (1)`, ` (2)`… before the extension.
 *
 * Kept as a free function with no Android types so the numbering can be tested directly;
 * it is the part that gets edge cases wrong.
 */
fun nextAvailableFile(folder: File, filename: String): File {
    var candidate = filename
    var file = File(folder, candidate)
    while (file.exists()) {
        candidate = incrementName(candidate)
        file = File(folder, candidate)
    }
    return file
}

internal fun incrementName(filename: String): String {
    // Already numbered, e.g. "scan (3).jpg" — bump the number rather than nesting another.
    NUMBERED.matchEntire(filename)?.let { match ->
        val (stem, number, suffix) = match.destructured
        return "$stem(${number.toInt() + 1})$suffix"
    }
    val dot = filename.lastIndexOf('.')
    // A leading dot is part of the name, not an extension: ".profile" has no extension.
    return if (dot > 0) {
        filename.substring(0, dot) + " (1)" + filename.substring(dot)
    } else {
        "$filename (1)"
    }
}

private val NUMBERED = Regex("""(.*)\((\d+)\)\s*(\.\w+|)$""")
