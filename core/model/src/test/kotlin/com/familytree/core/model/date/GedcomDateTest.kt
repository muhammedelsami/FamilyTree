package com.familytree.core.model.date

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/**
 * Ported verbatim from FamilyGem's `DateTest`, which is the most valuable test in the
 * original project: the GEDCOM date grammar is its riskiest logic and these cases were
 * accumulated from real-world files.
 *
 * The expectations are the contract. The parser was written to satisfy them, not the
 * other way round.
 */
class GedcomDateTest {

    private data class Expected(
        val gedcomDate: String,
        val kind: DateKind,
        val firstFormat: DateFormat = DateFormat.OTHER,
        val secondFormat: DateFormat = DateFormat.OTHER,
        val firstDual: Boolean = false,
        val secondDual: Boolean = false,
        val firstNegative: Boolean = false,
        val secondNegative: Boolean = false,
        val valid: Boolean = false,
    )

    private val cases = mutableListOf<Expected>()

    private fun add(
        date: String,
        kind: DateKind,
        firstFormat: DateFormat = DateFormat.OTHER,
        secondFormat: DateFormat = DateFormat.OTHER,
        firstDual: Boolean = false,
        secondDual: Boolean = false,
        firstNegative: Boolean = false,
        secondNegative: Boolean = false,
        valid: Boolean = false,
    ) {
        cases += Expected(
            date, kind, firstFormat, secondFormat,
            firstDual, secondDual, firstNegative, secondNegative, valid,
        )
    }

    @Test
    fun `parses the GEDCOM date grammar`() {
        add("", DateKind.EXACT, valid = true)
        add("   ", DateKind.EXACT, valid = true)
        add("31", DateKind.EXACT, DateFormat.D)
        add("031", DateKind.EXACT, DateFormat.Y, valid = true)
        add("32", DateKind.EXACT, DateFormat.Y)
        add("032", DateKind.EXACT, DateFormat.Y, valid = true)
        add(" APR  ", DateKind.EXACT, DateFormat.M)
        add("7     AUG", DateKind.EXACT, DateFormat.D_M)
        add("MAy 78 ", DateKind.EXACT, DateFormat.M_Y)
        add("10 agosto 2024", DateKind.EXACT, DateFormat.D_M_Y)
        add("10 August 2024", DateKind.EXACT, DateFormat.D_M_Y)
        add("10 Août 2024", DateKind.PHRASE, valid = true)
        add("29 FEB 1999", DateKind.PHRASE, valid = true)

        add("12/1713", DateKind.EXACT, DateFormat.M_Y)
        add("15/05/1970", DateKind.EXACT, DateFormat.D_M_Y)
        add("CAL <<<12>>>OtTobre .. 1713", DateKind.CALCULATED, DateFormat.D_M_Y)
        add("ABT. 2003", DateKind.APPROXIMATE, DateFormat.Y)

        add(" abt  29  feb    2000 ", DateKind.APPROXIMATE, DateFormat.D_M_Y)
        add("ABT", DateKind.APPROXIMATE)
        add("ABT9", DateKind.APPROXIMATE, DateFormat.D)
        add("ABTJan 1234", DateKind.APPROXIMATE, DateFormat.M_Y)
        add(" BEF   ", DateKind.BEFORE)
        add("AFT foobaz...", DateKind.AFTER)
        add("to 15 1544", DateKind.TO)

        add("BET 1000 ABCxyz", DateKind.BETWEEN_AND)
        add("  Bet 28 feb 005 and 5 may 123 ", DateKind.BETWEEN_AND, DateFormat.D_M_Y, DateFormat.D_M_Y)
        add("BET 13 NOV 1333", DateKind.BETWEEN_AND, DateFormat.D_M_Y)
        add("BET 22 FEB 1500 AND", DateKind.BETWEEN_AND, DateFormat.D_M_Y)
        add("BET 5 MAY AND 20 MAY", DateKind.BETWEEN_AND, DateFormat.D_M, DateFormat.D_M)
        add("BET 1 AUG 2005 AND quaquaqua", DateKind.BETWEEN_AND, DateFormat.D_M_Y)
        add("FROM 1111 TO", DateKind.FROM_TO, DateFormat.Y)
        add("from     TO 1234", DateKind.FROM_TO, DateFormat.OTHER, DateFormat.Y)
        add("   FROM 1001 to 1001  ", DateKind.FROM_TO, DateFormat.Y, DateFormat.Y)

        add("INT 15 DEC 2000 (More or less)", DateKind.INTERPRETED, DateFormat.D_M_Y, valid = true)
        add("INT (parenthesis-only)", DateKind.INTERPRETED, DateFormat.OTHER)
        add("INT 9999 B.C. (Long ago, for sure B.C.)", DateKind.INTERPRETED, DateFormat.Y, firstNegative = true, valid = true)

        add("Söme ràndom téxt", DateKind.PHRASE, valid = true)
        add("BE 7 Jan 1913", DateKind.PHRASE, valid = true)
        add("(JAn 1458 B.C.)", DateKind.PHRASE, valid = true)
        add("(one parenthesis only!", DateKind.PHRASE, valid = true)
        add("  (  True phrase ) ", DateKind.PHRASE, valid = true)

        add("jan 1699/00 ", DateKind.EXACT, DateFormat.M_Y, firstDual = true)
        add("/99", DateKind.PHRASE, valid = true)
        add("FROM 3 FEB 1715/16", DateKind.FROM, DateFormat.D_M_Y, firstDual = true, valid = true)
        add("ABT AUG 123/24", DateKind.APPROXIMATE, DateFormat.M_Y, firstDual = true, valid = true)
        add("TOAUG 1595/96", DateKind.TO, DateFormat.M_Y, firstDual = true)
        add("bet 1701/02 and 1756/1757", DateKind.BETWEEN_AND, DateFormat.Y, DateFormat.Y, true, true)

        add("0", DateKind.EXACT, DateFormat.Y) // Not turned into 1 BC
        add("000", DateKind.EXACT, DateFormat.Y, valid = true)
        add("11 DEC 000", DateKind.EXACT, DateFormat.D_M_Y, valid = true)
        add("000 B.C.", DateKind.EXACT, DateFormat.Y, firstNegative = true, valid = true)

        add("b.c.", DateKind.PHRASE, firstNegative = true, valid = true)
        add("1BC", DateKind.EXACT, DateFormat.D, firstNegative = true)
        add("XX b.c.", DateKind.PHRASE, firstNegative = true, valid = true)
        add("ABT 7/8 B.C.", DateKind.APPROXIMATE, DateFormat.M_Y, firstNegative = true)
        add("CAL 007/08 B.C.", DateKind.CALCULATED, DateFormat.Y, firstDual = true, firstNegative = true, valid = true)
        add("111B.C.", DateKind.EXACT, DateFormat.Y, firstNegative = true)
        add("1-2-123B.C.", DateKind.EXACT, DateFormat.D_M_Y, firstNegative = true)
        add("CAL 2000/99 B.C.", DateKind.CALCULATED, DateFormat.Y, firstDual = true, firstNegative = true, valid = true)
        add("BET 3 AUG 020 B.C. AND SEP 010 B.C.", DateKind.BETWEEN_AND, DateFormat.D_M_Y, DateFormat.M_Y, firstNegative = true, secondNegative = true)
        add("BET 50/49 BC AND 5 BC", DateKind.BETWEEN_AND, DateFormat.Y, DateFormat.D, firstDual = true, firstNegative = true, secondNegative = true)
        add("FROM 1000 B.C. TO AUG 200", DateKind.FROM_TO, DateFormat.Y, DateFormat.M_Y, firstNegative = true, valid = true)

        Locale.setDefault(Locale.ITALIAN)
        val failures = mutableListOf<String>()
        for (case in cases) {
            val date = GedcomDate(case.gedcomDate)
            val problems = buildList {
                if (date.kind != case.kind) add("kind ${date.kind} != ${case.kind}")
                if (date.firstDate.format != case.firstFormat) {
                    add("firstFormat ${date.firstDate.format} != ${case.firstFormat}")
                }
                if (date.secondDate.format != case.secondFormat) {
                    add("secondFormat ${date.secondDate.format} != ${case.secondFormat}")
                }
                if (date.firstDate.dual != case.firstDual) add("firstDual ${date.firstDate.dual}")
                if (date.secondDate.dual != case.secondDual) add("secondDual ${date.secondDate.dual}")
                if (date.firstDate.negative != case.firstNegative) add("firstNegative ${date.firstDate.negative}")
                if (date.secondDate.negative != case.secondNegative) add("secondNegative ${date.secondDate.negative}")
                if (date.isValid(case.gedcomDate) != case.valid) add("valid ${!case.valid}")
            }
            if (problems.isNotEmpty()) failures += "'${case.gedcomDate}' -> ${problems.joinToString("; ")}"
        }
        assertEquals("${failures.size} of ${cases.size} dates parsed wrongly:\n" + failures.joinToString("\n"), 0, failures.size)
    }

    @Test
    fun `sorts by year, month and day`() {
        assertEquals(20000131, GedcomDate("31 JAN 2000").sortKey())
        assertEquals(19700500, GedcomDate("MAY 1970").sortKey())
        assertEquals(18000000, GedcomDate("1800").sortKey())
        // No year means no position on a timeline, so it sorts last.
        assertEquals(Int.MAX_VALUE, GedcomDate("5 MAY").sortKey())
        assertEquals(Int.MAX_VALUE, GedcomDate("nonsense").sortKey())
    }

    @Test
    fun `reports the year only for single moments`() {
        assertEquals(1900, GedcomDate("1900").year())
        assertEquals(1900, GedcomDate("ABT 1900").year())
        assertEquals(-500, GedcomDate("500 B.C.").year())
        // A double year records the later of the pair as the effective one.
        assertEquals(1713, GedcomDate("1712/13").year())
        // Ranges span two moments, so no single year applies.
        assertEquals(null, GedcomDate("BET 1900 AND 1910").year())
        assertEquals(null, GedcomDate("FROM 1900 TO 1910").year())
    }
}
