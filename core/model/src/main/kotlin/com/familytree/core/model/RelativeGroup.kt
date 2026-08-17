package com.familytree.core.model

/**
 * One family a person belongs to, seen from that person's point of view.
 *
 * The same family structure means different things depending on which side the person
 * is on: in the family they were born into, the partners are their parents and the
 * children are their siblings; in a family they formed, the partners are their spouses
 * and the children are their own. One type with a [kind] keeps that symmetry visible
 * instead of duplicating it.
 */
data class RelativeGroup(
    val family: Family,
    val kind: Kind,
    /** Parents when [Kind.ORIGIN], spouses when [Kind.OWN]. */
    val partners: List<PersonSummary>,
    /** Siblings when [Kind.ORIGIN], children when [Kind.OWN]. The person is never listed. */
    val children: List<PersonSummary>,
) {
    enum class Kind { ORIGIN, OWN }

    val isEmpty: Boolean get() = partners.isEmpty() && children.isEmpty()
}
