package com.familytree.core.model

/**
 * A family group (`FAM`) — a couple and/or their children.
 *
 * GEDCOM keeps membership on both sides (`FAM.HUSB/WIFE/CHIL` and `INDI.FAMS/FAMC`).
 * Here a single [FamilyMember] row per membership is the source of truth, and both
 * directions are regenerated from it on export. That removes the whole class of
 * one-sided-reference corruption FamilyGem had to detect and repair in `findErrors`.
 */
data class Family(
    val id: Long = 0L,
    val treeId: Long,
    val gedcomId: String? = null,
    val sortOrder: Int = 0,
    val uid: String? = null,
    val uidTag: String? = null,
    val rin: String? = null,
    val referenceNumbers: String? = null,
    val changeDate: String? = null,
    val changeZone: String? = null,
    val updatedAt: Long = 0L,
)

/** One person's membership in one family. */
data class FamilyMember(
    val id: Long = 0L,
    val familyId: Long,
    val personId: Long,
    val role: MemberRole,
    val position: Int = 0,
    /** `_PREF` — marks the preferred spouse when a person has several. */
    val preferred: Boolean = false,
    /** `PEDI` on the child's `FAMC` — birth, adopted, foster, sealing. */
    val pedigree: Pedigree? = null,
    /** `_FREL` — the child's relationship to the father. */
    val fatherRelation: Pedigree? = null,
    /** `_MREL` — the child's relationship to the mother. */
    val motherRelation: Pedigree? = null,
    /** `_PRIMARY` on `FAMC` — the family the diagram shows by default. */
    val isPrimary: Boolean = false,
)

enum class MemberRole { HUSBAND, WIFE, CHILD }

/** How a child is related to the parents in a family. */
enum class Pedigree(val gedcomValue: String) {
    BIRTH("birth"),
    ADOPTED("adopted"),
    FOSTER("foster"),
    SEALING("sealing"),
    ;

    companion object {
        fun fromGedcom(value: String?): Pedigree? =
            entries.firstOrNull { it.gedcomValue.equals(value?.trim(), ignoreCase = true) }
    }
}

/** A family with its members resolved, for list and detail rendering. */
data class FamilyDetails(
    val family: Family,
    val husbands: List<Person> = emptyList(),
    val wives: List<Person> = emptyList(),
    val children: List<Person> = emptyList(),
    val events: List<Event> = emptyList(),
    val media: List<MediaObject> = emptyList(),
    val notes: List<Note> = emptyList(),
    val citations: List<SourceCitation> = emptyList(),
)
