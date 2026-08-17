package com.familytree.core.data.mapper

import com.familytree.core.database.entity.EventEntity
import com.familytree.core.database.entity.PersonEntity
import com.familytree.core.database.entity.PersonNameEntity
import com.familytree.core.model.EventCatalog
import com.familytree.core.model.LifeEvent
import com.familytree.core.model.PersonSummary
import com.familytree.core.model.date.DateFormat
import com.familytree.core.model.date.GedcomDate
import kotlinx.datetime.LocalDate
import kotlinx.datetime.daysUntil

/**
 * Turns raw rows into the precomputed shape a person list needs.
 *
 * Dates are parsed once here, not on every comparison a sort makes.
 */
internal fun buildPersonSummary(
    person: PersonEntity,
    names: List<PersonNameEntity>,
    events: List<EventEntity>,
    relativeCount: Int,
    portraitMediaId: Long?,
    today: LocalDate,
    lifeSpan: Int,
): PersonSummary {
    val primaryName = names.minByOrNull { it.position }?.toDomain()
    val displayName = primaryName?.display().orEmpty()

    val birthEvent = EventCatalog.BIRTH_TAGS.firstNotNullOfOrNull { tag ->
        events.firstOrNull { it.tag == tag }
    }
    val deathEvent = EventCatalog.DEATH_TAGS.firstNotNullOfOrNull { tag ->
        events.firstOrNull { it.tag == tag }
    }

    val birthDate = birthEvent?.date?.let(::GedcomDate)
    val deathDate = deathEvent?.date?.let(::GedcomDate)

    // A person with no death record is presumed living, exactly as GEDCOM readers do.
    val isDeceased = deathEvent != null

    val ageInYears = computeAge(birthDate, deathDate, isDeceased, today, lifeSpan)

    return PersonSummary(
        person = person.toDomain(),
        displayName = displayName,
        searchText = buildSearchText(displayName, names, events),
        birth = birthEvent?.toLifeEvent(birthDate),
        death = deathEvent?.toLifeEvent(deathDate),
        isDeceased = isDeceased,
        birthSortKey = birthDate?.sortKey() ?: Int.MAX_VALUE,
        ageInYears = ageInYears,
        daysToNextBirthday = if (isDeceased) null else birthDate?.daysUntilNextBirthday(today),
        relativeCount = relativeCount,
        portraitMediaId = portraitMediaId,
        // Surname first so people with the same surname stay grouped; both fall back to
        // the raw value when the file never broke the name into pieces.
        sortableSurname = listOfNotNull(
            primaryName?.effectiveSurname(),
            primaryName?.effectiveGiven(),
        ).joinToString(" "),
    )
}

private fun EventEntity.toLifeEvent(parsed: GedcomDate?) =
    LifeEvent(date = date, place = place, year = parsed?.year())

/** Names plus event dates and places, lowercased once so search never re-does the work. */
private fun buildSearchText(
    displayName: String,
    names: List<PersonNameEntity>,
    events: List<EventEntity>,
): String = buildString {
    append(displayName)
    names.forEach { name ->
        listOfNotNull(name.value, name.nickname, name.marriedName, name.alsoKnownAs)
            .forEach { append(' ').append(it) }
    }
    events.forEach { event ->
        listOfNotNull(event.value, event.date, event.place, event.type)
            .forEach { append(' ').append(it) }
    }
}.lowercase()

/**
 * Age at death, or age now for the living.
 *
 * A living person older than the tree's life span is almost certainly someone whose
 * death was never recorded, so no age is claimed for them rather than reporting a
 * figure the user would have to distrust.
 */
private fun computeAge(
    birth: GedcomDate?,
    death: GedcomDate?,
    isDeceased: Boolean,
    today: LocalDate,
    lifeSpan: Int,
): Int? {
    val birthYear = birth?.year() ?: return null
    val endYear = if (isDeceased) death?.year() ?: return null else today.year
    val age = endYear - birthYear
    if (age < 0) return null
    return if (!isDeceased && age > lifeSpan) null else age
}

/**
 * Days until the next anniversary of a birth, for the birthday sort.
 *
 * Requires a complete date: "MAY 1970" names no day to count towards.
 */
private fun GedcomDate.daysUntilNextBirthday(today: LocalDate): Int? {
    if (!isSingleKind) return null
    if (firstDate.format != DateFormat.D_M_Y) return null
    val month = firstDate.month ?: return null
    val day = firstDate.day ?: return null

    val thisYear = runCatching { LocalDate(today.year, month, day) }.getOrNull() ?: return null
    val next = if (thisYear < today) {
        runCatching { LocalDate(today.year + 1, month, day) }.getOrNull() ?: return null
    } else {
        thisYear
    }
    return today.daysUntil(next)
}
