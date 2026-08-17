package com.familytree.core.model.date

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class GedcomDateBuilderTest {

    @Test
    fun `writes the shapes GEDCOM allows`() {
        assertEquals("31 JAN 1900", buildGedcomDate(DateKind.EXACT, DateParts(31, 1, 1900)))
        assertEquals("JAN 1900", buildGedcomDate(DateKind.EXACT, DateParts(month = 1, year = 1900)))
        assertEquals("1900", buildGedcomDate(DateKind.EXACT, DateParts(year = 1900)))
        assertEquals("ABT 1900", buildGedcomDate(DateKind.APPROXIMATE, DateParts(year = 1900)))
        assertEquals("BEF 5 MAY 1900", buildGedcomDate(DateKind.BEFORE, DateParts(5, 5, 1900)))
        assertEquals(
            "BET 1900 AND 1910",
            buildGedcomDate(DateKind.BETWEEN_AND, DateParts(year = 1900), DateParts(year = 1910)),
        )
        assertEquals(
            "FROM 1940 TO 1945",
            buildGedcomDate(DateKind.FROM_TO, DateParts(year = 1940), DateParts(year = 1945)),
        )
        assertEquals(
            "INT 1900 (from a letter)",
            buildGedcomDate(DateKind.INTERPRETED, DateParts(year = 1900), phrase = "from a letter"),
        )
        assertEquals("(some time ago)", buildGedcomDate(DateKind.PHRASE, DateParts(), phrase = "some time ago"))
    }

    @Test
    fun `writes eras and double years`() {
        assertEquals("500 B.C.", buildGedcomDate(DateKind.EXACT, DateParts(year = 500, negative = true)))
        assertEquals("1712/13", buildGedcomDate(DateKind.EXACT, DateParts(year = 1712, dual = true)))
        // The following year's last two digits keep their leading zero.
        assertEquals("1799/00", buildGedcomDate(DateKind.EXACT, DateParts(year = 1799, dual = true)))
    }

    @Test
    fun `an empty date stays empty rather than becoming a stray keyword`() {
        assertEquals("", buildGedcomDate(DateKind.EXACT, DateParts()))
        assertEquals("", buildGedcomDate(DateKind.APPROXIMATE, DateParts()))
        assertEquals("", buildGedcomDate(DateKind.PHRASE, DateParts(), phrase = "  "))
    }

    /**
     * The builder and the parser have to agree, otherwise editing a date would quietly
     * change it.
     */
    @Test
    fun `everything written can be parsed back to the same parts`() {
        Locale.setDefault(Locale.ITALIAN) // the parser must not depend on the device language
        val cases = listOf(
            DateKind.EXACT to DateParts(31, 12, 1899),
            DateKind.EXACT to DateParts(month = 6, year = 1750),
            DateKind.EXACT to DateParts(year = 1066),
            DateKind.APPROXIMATE to DateParts(year = 1900),
            DateKind.CALCULATED to DateParts(1, 1, 1800),
            DateKind.ESTIMATED to DateParts(year = 1500),
            DateKind.BEFORE to DateParts(15, 3, 1920),
            DateKind.AFTER to DateParts(year = 1600),
            DateKind.EXACT to DateParts(year = 44, negative = true),
            DateKind.EXACT to DateParts(year = 1712, dual = true),
        )
        cases.forEach { (kind, parts) ->
            val text = buildGedcomDate(kind, parts)
            val parsed = GedcomDate(text)
            assertEquals("kind of '$text'", kind, parsed.kind)
            assertEquals("day of '$text'", parts.day, parsed.firstDate.day)
            assertEquals("month of '$text'", parts.month, parsed.firstDate.month)
            assertEquals("year of '$text'", parts.year, parsed.firstDate.year)
            assertEquals("era of '$text'", parts.negative, parsed.firstDate.negative)
            assertEquals("double year of '$text'", parts.dual, parsed.firstDate.dual)
            assertTrue("'$text' should be valid GEDCOM", parsed.isValid(text))
        }
    }

    @Test
    fun `ranges also survive a round trip`() {
        val text = buildGedcomDate(
            DateKind.BETWEEN_AND,
            DateParts(year = 1900),
            DateParts(day = 5, month = 5, year = 1910),
        )
        val parsed = GedcomDate(text)
        assertEquals(DateKind.BETWEEN_AND, parsed.kind)
        assertEquals(1900, parsed.firstDate.year)
        assertEquals(5, parsed.secondDate.day)
        assertEquals(1910, parsed.secondDate.year)
        assertTrue(parsed.isValid(text))
    }
}
