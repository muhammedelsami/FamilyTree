package com.familytree.core.model

/**
 * A family tree — the top-level unit the user creates, opens, exports and shares.
 *
 * [personCount], [familyCount], [mediaCount] and [generationCount] are denormalised
 * counters kept for the tree list; they are recomputed on import and after bulk edits
 * rather than on every write.
 */
data class Tree(
    val id: Long = 0L,
    val title: String,
    val rootPersonId: Long? = null,
    val shareRootPersonId: Long? = null,
    val grade: TreeGrade = TreeGrade.ORIGINAL,
    val settings: TreeSettings = TreeSettings(),
    val personCount: Int = 0,
    val familyCount: Int = 0,
    val mediaCount: Int = 0,
    val generationCount: Int = 0,
    val sortOrder: Int = 0,
    val backupEnabled: Boolean = false,
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
)

/**
 * Where a tree sits in the share/return lifecycle.
 *
 * Ported from FamilyGem's `Settings.Tree.grade` state machine. The numeric values are
 * preserved so ZIP backups produced by either app remain interchangeable.
 */
enum class TreeGrade(val value: Int) {
    /** Created from scratch on this device. */
    ORIGINAL(0),

    /** Uploaded for sharing; submitters are waiting to be marked as passed. */
    SHARED(9),

    /** Received through a share. Can never go back to [ORIGINAL]. */
    RECEIVED(10),

    /** Came back from a share and proved to descend from an [ORIGINAL] or [RECEIVED] tree. */
    DERIVED(20),

    /** All updates have been extracted; safe to delete. */
    EXHAUSTED(30),
    ;

    companion object {
        fun fromValue(value: Int): TreeGrade = entries.firstOrNull { it.value == value } ?: ORIGINAL
    }
}

/**
 * Per-tree preferences.
 *
 * [lifeSpan] caps how old a person may be before the app stops treating them as living
 * (birthday reminders, age display). [fixedDate] lets the user pin "today" to a past
 * date so a historical tree reads correctly; when set, birthday alarms are suppressed.
 */
data class TreeSettings(
    val lifeSpan: Int = DEFAULT_LIFE_SPAN,
    val useCustomDate: Boolean = false,
    /** GEDCOM-formatted date string, e.g. `1 JAN 1900`. Only meaningful when [useCustomDate]. */
    val fixedDate: String? = null,
) {
    companion object {
        const val DEFAULT_LIFE_SPAN = 110
    }
}

/** A folder the app searches when resolving a media file path. */
data class MediaFolder(
    val id: Long = 0L,
    val treeId: Long,
    val kind: Kind,
    /** An absolute filesystem path for [Kind.PATH], or a persisted SAF tree URI for [Kind.URI]. */
    val value: String,
) {
    enum class Kind { PATH, URI }
}

/** A record of one outgoing share, used to match a returning tree to its origin. */
data class TreeShare(
    val id: Long = 0L,
    val treeId: Long,
    /** Server-assigned `yyyyMMddHHmmss` identifier. */
    val dateId: String,
    val submitterId: Long?,
)
