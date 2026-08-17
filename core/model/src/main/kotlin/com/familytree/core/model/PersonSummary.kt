package com.familytree.core.model

/**
 * Everything a person list row needs, precomputed once per person.
 *
 * Assembling this up front rather than per row is what keeps a 5,000-person list
 * scrolling: sorting by age or by next birthday would otherwise re-parse GEDCOM dates
 * on every comparison, and searching would re-concatenate names on every keystroke.
 */
data class PersonSummary(
    val person: Person,
    val displayName: String,
    /** Lowercased names and events, for the multi-word search to match against. */
    val searchText: String,
    val birth: LifeEvent?,
    val death: LifeEvent?,
    val isDeceased: Boolean,
    /** `yyyyMMdd`, or [Int.MAX_VALUE] when undated so those rows sort last. */
    val birthSortKey: Int,
    val ageInYears: Int?,
    /** Only meaningful for living people with a complete birth date. */
    val daysToNextBirthday: Int?,
    val relativeCount: Int,
    val portraitMediaId: Long?,
    /** Surname then given name, so people with the same surname stay grouped. */
    val sortableSurname: String = "",
)

data class LifeEvent(val date: String?, val place: String?, val year: Int?)

enum class PersonSort {
    /** The GEDCOM identifier, numerically rather than lexically. */
    ID,
    SURNAME,
    /** Earliest known date, oldest first when ascending. */
    DATE,
    AGE,
    /** How soon the next birthday falls — living people only. */
    BIRTHDAY,
    RELATIVES,
}

/**
 * Tapping the same criterion again reverses it, which is how the original behaved and
 * saves a separate direction control.
 */
data class PersonSorting(val sort: PersonSort = PersonSort.ID, val ascending: Boolean = true) {
    fun toggled(next: PersonSort): PersonSorting =
        if (next == sort) copy(ascending = !ascending) else PersonSorting(next, ascending = true)
}

/** Sorts a list, always pushing rows with no value for the criterion to the end. */
fun List<PersonSummary>.sortedBy(sorting: PersonSorting): List<PersonSummary> {
    val comparator: Comparator<PersonSummary> = when (sorting.sort) {
        PersonSort.ID -> compareBy { it.person.gedcomId.numericSuffix() }
        PersonSort.SURNAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.sortableSurname }
        PersonSort.DATE -> compareBy { it.birthSortKey }
        PersonSort.AGE -> compareBy { it.ageInYears ?: Int.MAX_VALUE }
        PersonSort.BIRTHDAY -> compareBy { it.daysToNextBirthday ?: Int.MAX_VALUE }
        PersonSort.RELATIVES -> compareBy { it.relativeCount }
    }
    // Reversing would also flip the "unknowns last" rule, so the missing values are
    // partitioned out and re-appended instead.
    val (known, unknown) = partition { it.hasValueFor(sorting.sort) }
    val ordered = known.sortedWith(if (sorting.ascending) comparator else comparator.reversed())
    return ordered + unknown.sortedBy { it.person.gedcomId.numericSuffix() }
}

private fun PersonSummary.hasValueFor(sort: PersonSort): Boolean = when (sort) {
    PersonSort.ID, PersonSort.RELATIVES -> true
    PersonSort.SURNAME -> sortableSurname.isNotBlank()
    PersonSort.DATE -> birthSortKey != Int.MAX_VALUE
    PersonSort.AGE -> ageInYears != null
    PersonSort.BIRTHDAY -> daysToNextBirthday != null
}

/** `I12` sorts after `I9`, which a plain string comparison would get wrong. */
fun String?.numericSuffix(): Int =
    this?.filter(Char::isDigit)?.toIntOrNull() ?: Int.MAX_VALUE

/**
 * Matches when every word appears somewhere in the person's text.
 *
 * Words are ANDed rather than ORed so that typing more narrows the result, which is
 * what "ahmet 1920" is meant to do.
 */
fun PersonSummary.matches(query: String): Boolean {
    if (query.isBlank()) return true
    return query.trim().lowercase().split(WHITESPACE).all { word -> searchText.contains(word) }
}

private val WHITESPACE = "\\s+".toRegex()
