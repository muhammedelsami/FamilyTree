package com.familytree.core.model

/**
 * A structural problem found in a tree.
 *
 * Real GEDCOM files arrive with all of these; the point of surfacing them is that the
 * user can see what is wrong before deciding to let the app change their data.
 */
data class TreeIssue(
    val kind: Kind,
    val count: Int,
) {
    enum class Kind(val repairable: Boolean) {
        /** The tree points at a root person who is not in it, or names none at all. */
        MISSING_ROOT(repairable = true),

        /** Records with no cross-reference id, which cannot be exported as they are. */
        MISSING_IDS(repairable = true),

        /** Families with fewer than two members carry no information GEDCOM can express. */
        UNDERPOPULATED_FAMILIES(repairable = true),

        /** Media records with no file path — nothing can ever be displayed for them. */
        MEDIA_WITHOUT_FILE(repairable = false),

        /** Notes or media still attached to a record that no longer exists. */
        ORPHANED_LINKS(repairable = true),

        /** People with no name at all, which every list would render as blank. */
        PEOPLE_WITHOUT_NAME(repairable = false),
    }
}

/**
 * Note on what is deliberately absent: FamilyGem also had to hunt for duplicate ids and
 * one-sided family references. Neither can occur here — a unique index on
 * `(treeId, gedcomId)` rules out duplicates, and membership is stored once rather than
 * on both the person and the family.
 */
data class TreeRepairResult(val repaired: List<TreeIssue>)
