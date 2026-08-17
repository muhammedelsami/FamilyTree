package com.familytree.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PersonNameTest {

    /**
     * The case that made this rule necessary: a real file carried the full name only in
     * the `NAME` value, with just `SURN` broken out, and preferring the pieces showed
     * the person as "Yılmaz" with no given name.
     */
    @Test
    fun `a partially broken-out name still shows in full`() {
        val name = PersonName(personId = 1, value = "Ayşe /Yılmaz/", surname = "Yılmaz")
        assertEquals("Ayşe Yılmaz", name.display())
        assertEquals("Ayşe", name.effectiveGiven())
        assertEquals("Yılmaz", name.effectiveSurname())
    }

    @Test
    fun `pieces are used when there is no value`() {
        val name = PersonName(personId = 1, given = "Ahmet", surname = "Yılmaz", prefix = "Dr.")
        assertEquals("Dr. Ahmet Yılmaz", name.display())
        assertEquals("Ahmet", name.effectiveGiven())
        assertEquals("Yılmaz", name.effectiveSurname())
    }

    @Test
    fun `slashes and stray spacing are cleaned up`() {
        val name = PersonName(personId = 1, value = "Ahmet  /Yılmaz/ ")
        assertEquals("Ahmet Yılmaz", name.display())
        assertEquals("Ahmet", name.effectiveGiven())
        assertEquals("Yılmaz", name.effectiveSurname())
    }

    @Test
    fun `a surname-only value reports no given name`() {
        val name = PersonName(personId = 1, value = "/Yılmaz/")
        assertEquals("Yılmaz", name.display())
        assertEquals(null, name.effectiveGiven())
        assertEquals("Yılmaz", name.effectiveSurname())
    }

    @Test
    fun `a name with no surname markers is all given name`() {
        val name = PersonName(personId = 1, value = "Fatma")
        assertEquals("Fatma", name.display())
        assertEquals("Fatma", name.effectiveGiven())
        assertEquals(null, name.effectiveSurname())
    }

    @Test
    fun `an empty name yields an empty display`() {
        assertEquals("", PersonName(personId = 1).display())
    }
}
