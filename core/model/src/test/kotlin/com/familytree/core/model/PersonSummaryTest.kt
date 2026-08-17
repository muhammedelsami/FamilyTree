package com.familytree.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PersonSummaryTest {

    private fun summary(
        gedcomId: String,
        name: String = gedcomId,
        surname: String = "",
        birthSortKey: Int = Int.MAX_VALUE,
        age: Int? = null,
        daysToBirthday: Int? = null,
        relatives: Int = 0,
    ) = PersonSummary(
        person = Person(id = 0, treeId = 1, gedcomId = gedcomId),
        displayName = name,
        searchText = "$name $surname".lowercase(),
        birth = null,
        death = null,
        isDeceased = false,
        birthSortKey = birthSortKey,
        ageInYears = age,
        daysToNextBirthday = daysToBirthday,
        relativeCount = relatives,
        portraitMediaId = null,
        sortableSurname = surname,
    )

    @Test
    fun `identifiers sort numerically, not as text`() {
        val people = listOf(summary("I12"), summary("I2"), summary("I100"))
        val sorted = people.sortedBy(PersonSorting(PersonSort.ID, ascending = true))
        assertEquals(listOf("I2", "I12", "I100"), sorted.map { it.person.gedcomId })
    }

    @Test
    fun `people with no value for the criterion stay at the end in both directions`() {
        val people = listOf(
            summary("I1", birthSortKey = 19500101),
            summary("I2"), // undated
            summary("I3", birthSortKey = 19200101),
        )
        val ascending = people.sortedBy(PersonSorting(PersonSort.DATE, ascending = true))
        assertEquals(listOf("I3", "I1", "I2"), ascending.map { it.person.gedcomId })

        // Reversing the order must not drag the undated person to the front.
        val descending = people.sortedBy(PersonSorting(PersonSort.DATE, ascending = false))
        assertEquals(listOf("I1", "I3", "I2"), descending.map { it.person.gedcomId })
    }

    @Test
    fun `tapping the same criterion reverses it, a different one starts ascending`() {
        val initial = PersonSorting(PersonSort.ID, ascending = true)
        val reversed = initial.toggled(PersonSort.ID)
        assertFalse(reversed.ascending)
        val switched = reversed.toggled(PersonSort.SURNAME)
        assertEquals(PersonSort.SURNAME, switched.sort)
        assertTrue(switched.ascending)
    }

    @Test
    fun `search requires every word to match`() {
        val person = summary("I1", name = "Ahmet Yilmaz", surname = "Yilmaz")
        assertTrue(person.matches("ahmet"))
        assertTrue(person.matches("YILMAZ ahmet"))
        // The second word is absent, so the row must not match.
        assertFalse(person.matches("ahmet 1920"))
        assertTrue(person.matches("   "))
    }

    @Test
    fun `surnameless people sort after the named ones`() {
        val people = listOf(summary("I1"), summary("I2", surname = "Yilmaz"))
        val sorted = people.sortedBy(PersonSorting(PersonSort.SURNAME, ascending = true))
        assertEquals(listOf("I2", "I1"), sorted.map { it.person.gedcomId })
    }
}
