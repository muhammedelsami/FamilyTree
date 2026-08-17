package com.familytree.core.media

import android.net.Uri
import java.io.File

/**
 * Where a media record's file actually lives on this device.
 *
 * A GEDCOM `OBJE.FILE` is only a string, and it was written by whatever program the tree
 * came from: it can be a Windows path, an Android path, a bare filename or a URL. None of
 * those are openable as-is, so resolution turns one into a concrete handle — or reports
 * that nothing was found, which is a normal outcome for a tree imported from another
 * device rather than an error.
 */
sealed interface MediaSource {

    /** A file on the filesystem this app can open directly. */
    data class LocalFile(val file: File) : MediaSource

    /** A document reached through a folder the user granted with the file picker. */
    data class DocumentUri(val uri: Uri) : MediaSource

    /** The link is an `http(s)` address, so there is nothing local to find. */
    data class Web(val url: String) : MediaSource

    /** The link points at nothing reachable from here. */
    data object Missing : MediaSource
}

/** What kind of thing the file is, decided from its extension. */
enum class MediaKind {
    IMAGE,
    VIDEO,
    PDF,
    /** Anything else openable: a text file, a spreadsheet, an audio recording. */
    DOCUMENT,
    /** The record has no file link at all. */
    NONE,
    ;

    val isCroppable: Boolean get() = this == IMAGE
}

/**
 * The outcome of resolving one media record.
 *
 * @property foundByFilename the file was found in the tree's own storage under a name
 *   that does not match the recorded link. The gallery offers to shorten such links,
 *   which is what makes a tree portable between devices.
 * @property foundInSubfolder the file was found below a granted folder rather than
 *   directly inside it.
 */
data class ResolvedMedia(
    val source: MediaSource,
    val name: String? = null,
    /** Always lowercase, without the dot. */
    val extension: String? = null,
    val kind: MediaKind = MediaKind.NONE,
    val foundByFilename: Boolean = false,
    val foundInSubfolder: Boolean = false,
) {
    /** True when there is something to open — a local file, a document or a web address. */
    val exists: Boolean get() = source !is MediaSource.Missing

    /** What Coil should be handed to load this. Null when there is nothing to load. */
    val loadable: Any?
        get() = when (source) {
            is MediaSource.LocalFile -> source.file
            is MediaSource.DocumentUri -> source.uri
            is MediaSource.Web -> source.url
            MediaSource.Missing -> null
        }

    companion object {
        val Missing = ResolvedMedia(MediaSource.Missing)
    }
}
