package com.familytree.core.model

/**
 * How a new person relates to the one the user started from.
 *
 * These four cover every link GEDCOM can express, because a family is the only
 * relationship record: an uncle is a sibling of a parent, a grandchild is a child of a
 * child, and so on. Offering exactly four keeps the editor honest about that.
 */
enum class Relation { PARENT, SIBLING, PARTNER, CHILD }

/**
 * Everything the editor collects about a person.
 *
 * A draft rather than a [Person] because the editor works in the shapes a form has —
 * separate name pieces, dates as the text the user typed — and only the use case knows
 * how those become records.
 */
data class PersonDraft(
    val treeId: Long,
    val personId: Long? = null,
    val given: String = "",
    val surname: String = "",
    val sex: Sex = Sex.NONE,
    val birthDate: String = "",
    val birthPlace: String = "",
    val deceased: Boolean = false,
    val deathDate: String = "",
    val deathPlace: String = "",
) {
    val hasBirthDetails: Boolean get() = birthDate.isNotBlank() || birthPlace.isNotBlank()
    val hasDeathDetails: Boolean get() = deathDate.isNotBlank() || deathPlace.isNotBlank()

    /** The GEDCOM `NAME` value, with the surname delimited as the standard requires. */
    fun nameValue(): String? {
        val parts = buildList {
            given.trim().takeIf { it.isNotEmpty() }?.let { add(it) }
            surname.trim().takeIf { it.isNotEmpty() }?.let { add("/$it/") }
        }
        return parts.joinToString(" ").takeIf { it.isNotBlank() }
    }
}

/**
 * Where a new relative should be attached.
 *
 * [familyId] null means "make a new family", which is what happens when the starting
 * person has none yet, or when the user is adding a second marriage.
 */
data class RelativeTarget(val relation: Relation, val familyId: Long? = null)
