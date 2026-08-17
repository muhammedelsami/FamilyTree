package com.familytree.core.model

/** One record that differs between a tree and a copy of it that has come back. */
data class TreeDifference(
    val kind: Kind,
    val recordType: RecordType,
    /** The cross-reference id, which is what makes the two copies comparable at all. */
    val gedcomId: String,
    /** How the record reads here now; null when it does not exist here. */
    val localSummary: String? = null,
    /** How it reads in the copy that came back; null when it was deleted there. */
    val incomingSummary: String? = null,
    /** What the user decided. Defaults to accepting, which is the usual answer. */
    val accepted: Boolean = true,
) {
    enum class Kind {
        /** Present in the returning copy and not here. */
        ADDED,

        /** Present in both, and different. */
        CHANGED,

        /** Here but gone from the returning copy. */
        REMOVED,
    }

    val id: String get() = "${recordType.name}:$gedcomId"
}

/** The outcome of comparing a returning copy against the tree it came from. */
data class TreeComparison(
    val localTreeId: Long,
    val incomingTreeId: Long,
    val differences: List<TreeDifference>,
) {
    val hasUpdates: Boolean get() = differences.isNotEmpty()

    val added: Int get() = differences.count { it.kind == TreeDifference.Kind.ADDED }
    val changed: Int get() = differences.count { it.kind == TreeDifference.Kind.CHANGED }
    val removed: Int get() = differences.count { it.kind == TreeDifference.Kind.REMOVED }
}
