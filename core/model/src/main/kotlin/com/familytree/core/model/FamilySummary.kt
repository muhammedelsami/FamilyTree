package com.familytree.core.model

/** A family as a list row: who is in it, and when it was formed. */
data class FamilySummary(
    val family: Family,
    val partners: List<PersonSummary>,
    val children: List<PersonSummary>,
    val marriage: LifeEvent?,
    val searchText: String,
) {
    val memberCount: Int get() = partners.size + children.size

    /** Surname of the first partner, or of the first child when a family has no partners. */
    val sortableSurname: String
        get() = (partners.firstOrNull() ?: children.firstOrNull())?.sortableSurname.orEmpty()
}

enum class FamilySort { ID, SURNAME, MEMBERS }

data class FamilySorting(val sort: FamilySort = FamilySort.ID, val ascending: Boolean = true) {
    fun toggled(next: FamilySort): FamilySorting =
        if (next == sort) copy(ascending = !ascending) else FamilySorting(next, ascending = true)
}

fun List<FamilySummary>.sortedBy(sorting: FamilySorting): List<FamilySummary> {
    val comparator: Comparator<FamilySummary> = when (sorting.sort) {
        FamilySort.ID -> compareBy { it.family.gedcomId.numericSuffix() }
        FamilySort.SURNAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.sortableSurname }
        FamilySort.MEMBERS -> compareBy { it.memberCount }
    }
    return sortedWith(if (sorting.ascending) comparator else comparator.reversed())
}

fun FamilySummary.matches(query: String): Boolean {
    if (query.isBlank()) return true
    return query.trim().lowercase().split("\\s+".toRegex()).all { searchText.contains(it) }
}
