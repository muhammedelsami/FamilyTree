package com.familytree.core.backup

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * What a backup archive contains, beyond the family data itself.
 *
 * The archive holds three things:
 *
 * ```
 * manifest.json   this
 * tree.ged        the whole tree, as GEDCOM 5.5.1
 * media/…         the files the tree keeps in its own storage
 * ```
 *
 * The family data is GEDCOM rather than a database dump on purpose. A dump is readable
 * only by the version of the app that wrote it, and a backup that outlives its format is
 * not a backup. GEDCOM opens in any genealogy program, and the extensions table makes the
 * round trip lossless — so an archive is both a restore point and an export.
 */
@Serializable
data class BackupManifest(
    val version: Int = CURRENT_VERSION,
    val title: String,
    /** Milliseconds since the epoch, for showing "saved yesterday" and for pruning. */
    val createdAt: Long,
    val personCount: Int = 0,
    val familyCount: Int = 0,
    val mediaCount: Int = 0,
    val generationCount: Int = 0,
    /** The sharing state machine's position; see [com.familytree.core.model.TreeGrade]. */
    val grade: Int = 0,
    /** `gedcomId` of the person the diagram opens on. */
    val rootGedcomId: String? = null,
    /** `gedcomId` of the person a shared copy should open on. */
    val shareRootGedcomId: String? = null,
    /** Past shares, so a returning tree can be matched to the one it came from. */
    val shares: List<ShareRecord> = emptyList(),
    /** Which application wrote this, purely for diagnosing an archive that will not open. */
    @SerialName("producedBy") val producedBy: String = "FamilyTree",
) {
    companion object {
        const val CURRENT_VERSION = 1
        const val FILE_NAME = "manifest.json"
        const val GEDCOM_NAME = "tree.ged"
        const val MEDIA_PREFIX = "media/"
    }
}

/** One outgoing share, identified by the moment it was made. */
@Serializable
data class ShareRecord(
    /** `yyyyMMddHHmmss`, which is also how the shared file is named. */
    val dateId: String,
    val submitterGedcomId: String? = null,
)
