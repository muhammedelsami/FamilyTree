package com.familytree.core.media

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.familytree.core.model.MediaFolder
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Holds on to the folders the user picked with the system file picker.
 *
 * A tree imported from a computer usually points at photos that live in some folder on
 * this device — Downloads, a memory card, a synced Drive folder. The app cannot read those
 * by path, so the user grants the folder once and Android remembers the grant across
 * restarts. Without the persist call the grant dies with the process and every photo would
 * quietly disappear on the next launch.
 */
@Singleton
class MediaFolderAccess @Inject constructor(
    @ApplicationContext private val context: Context,
) {

    /** Takes a lasting read grant on a folder returned by `OpenDocumentTree`. */
    fun persist(uri: Uri): Boolean = runCatching {
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        true
    }.getOrDefault(false)

    /** Gives the grant back, when the user removes the folder from the list. */
    fun release(uri: Uri) = runCatching {
        context.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    /**
     * Whether a stored folder is still usable.
     *
     * A grant survives a restart but not everything: the user can revoke it in settings,
     * and an SD card can be removed. A folder that reads as unavailable is shown as such
     * rather than silently producing missing files.
     */
    fun isAvailable(folder: MediaFolder): Boolean = when (folder.kind) {
        MediaFolder.Kind.PATH -> java.io.File(folder.value).isDirectory
        MediaFolder.Kind.URI -> runCatching {
            val uri = Uri.parse(folder.value)
            context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission } &&
                DocumentFile.fromTreeUri(context, uri)?.canRead() == true
        }.getOrDefault(false)
    }

    /** A readable name for a granted folder, for the folders screen. */
    fun displayName(folder: MediaFolder): String = when (folder.kind) {
        MediaFolder.Kind.PATH -> folder.value
        MediaFolder.Kind.URI -> runCatching {
            DocumentFile.fromTreeUri(context, Uri.parse(folder.value))?.name
        }.getOrNull() ?: Uri.decode(folder.value.substringAfterLast('/'))
    }
}
