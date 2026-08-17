package com.familytree.core.model.date

/**
 * The parts of one date as the editor holds them, before they become a GEDCOM string.
 *
 * Every field is optional because GEDCOM allows a year alone, a month and year, or a
 * full date, and the editor must not invent the parts the user left out.
 */
data class DateParts(
    val day: Int? = null,
    val month: Int? = null,
    val year: Int? = null,
    /** Before Christ. */
    val negative: Boolean = false,
    /** A double-dated year such as `1712/13`. */
    val dual: Boolean = false,
) {
    val isEmpty: Boolean get() = day == null && month == null && year == null

    companion object {
        /** Reads the parts back out of a parsed date, so editing round-trips. */
        fun from(single: SingleDate) = DateParts(
            day = single.day,
            month = single.month,
            year = single.year,
            negative = single.negative,
            dual = single.dual,
        )
    }
}

private val MONTHS = arrayOf(
    "JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC",
)

/**
 * Writes the canonical GEDCOM form of a date.
 *
 * Always the English month abbreviations and uppercase keywords the standard requires,
 * whatever language the user's device is in — the file has to be readable by every
 * other genealogy program, not just this one.
 */
fun buildGedcomDate(
    kind: DateKind,
    first: DateParts,
    second: DateParts? = null,
    phrase: String? = null,
): String {
    if (kind == DateKind.PHRASE) {
        val text = phrase.orEmpty().trim()
        return if (text.isEmpty()) "" else "($text)"
    }

    val firstText = first.format()
    if (firstText.isEmpty() && kind != DateKind.INTERPRETED) return ""

    return when (kind) {
        DateKind.EXACT -> firstText
        DateKind.BETWEEN_AND -> {
            val secondText = second?.format().orEmpty()
            if (secondText.isEmpty()) "BET $firstText" else "BET $firstText AND $secondText"
        }
        DateKind.FROM_TO -> {
            val secondText = second?.format().orEmpty()
            if (secondText.isEmpty()) "FROM $firstText" else "FROM $firstText TO $secondText"
        }
        DateKind.INTERPRETED -> {
            val text = phrase.orEmpty().trim()
            listOfNotNull(
                "INT".takeIf { firstText.isNotEmpty() || text.isNotEmpty() },
                firstText.takeIf { it.isNotEmpty() },
                if (text.isEmpty()) null else "($text)",
            ).joinToString(" ")
        }
        else -> "${kind.prefix} $firstText".trim()
    }
}

/** `31 JAN 1900`, `JAN 1900`, `1900`, plus the `/13` and ` B.C.` suffixes when set. */
private fun DateParts.format(): String {
    if (isEmpty) return ""
    val pieces = buildList {
        day?.takeIf { it in 1..31 }?.let { add(it.toString()) }
        month?.takeIf { it in 1..12 }?.let { add(MONTHS[it - 1]) }
        year?.let { value ->
            // GEDCOM 5.5.1 requires a three- or four-digit year, so early years are
            // padded: the year 44 has to be written 044 to be readable elsewhere.
            val text = value.toString().padStart(3, '0')
            // A double year is written as the stated year plus the following one's last
            // two digits: 1712/13.
            add(if (dual) "$text/${((value + 1) % 100).toString().padStart(2, '0')}" else text)
        }
    }
    if (pieces.isEmpty()) return ""
    val text = pieces.joinToString(" ")
    return if (negative) "$text B.C." else text
}
