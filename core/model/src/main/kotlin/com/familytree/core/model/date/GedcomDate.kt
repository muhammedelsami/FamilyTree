package com.familytree.core.model.date

import java.text.DateFormatSymbols
import java.util.Locale

/** The date kinds GEDCOM 5.5.1 allows, with the keyword that introduces each. */
enum class DateKind(val prefix: String) {
    EXACT(""),
    APPROXIMATE("ABT"),
    CALCULATED("CAL"),
    ESTIMATED("EST"),
    AFTER("AFT"),
    BEFORE("BEF"),
    BETWEEN_AND("BET"),
    FROM("FROM"),
    TO("TO"),
    FROM_TO("FROM"),
    INTERPRETED("INT"),
    PHRASE("("),
}

/**
 * Which parts of a date are actually present.
 *
 * GEDCOM lets any of day, month and year be omitted, and the app must remember which
 * were given so it can render "MAY 1878" without inventing a day.
 *
 * [D_m_Y] and [m_Y] are transient: they record that the month arrived as a number
 * rather than a name, and are folded into [D_M_Y] / [M_Y] once parsing succeeds.
 */
enum class DateFormat {
    D_M_Y,
    D_m_Y,
    M_Y,
    m_Y,
    D_M,
    D,
    M,
    Y,
    OTHER,
}

/** One point in time, with only the fields the source actually specified. */
class SingleDate {
    var year: Int? = null
    var month: Int? = null
    var day: Int? = null
    var format: DateFormat = DateFormat.OTHER

    /**
     * A double-dated year such as `1712/1713`, from the era when the new year began in
     * March in some countries and January in others. [year] holds the first of the pair.
     */
    var dual: Boolean = false

    /** Before Christ. Tracked separately so the era survives a round trip. */
    var negative: Boolean = false

    val isEmpty: Boolean get() = format == DateFormat.OTHER

    val hasYear: Boolean
        get() = format == DateFormat.D_M_Y || format == DateFormat.M_Y || format == DateFormat.Y

    /** The year as signed by its era, and advanced past a double year. */
    fun effectiveYear(): Int? {
        val base = year ?: return null
        val advanced = if (dual) base + 1 else base
        return if (negative) -advanced else advanced
    }

    override fun toString(): String = buildString {
        if (isEmpty) {
            append("null")
        } else {
            day?.let { append(it).append(' ') }
            month?.let { append(MONTH_ABBREVIATIONS[it - 1]).append(' ') }
            year?.let { append(it) }
            if (dual) append("/dual")
            if (negative) append(" BC")
        }
    }

    internal fun reset() {
        year = null
        month = null
        day = null
        format = DateFormat.OTHER
        dual = false
        negative = false
    }
}

/**
 * Parses the GEDCOM 5.5.1 date grammar.
 *
 * Rather than guessing a date by trying a list of formatter patterns — which makes the
 * result depend on a formatting library's leniency rules — this walks the grammar
 * directly: strip the kind keyword, split off a double year, tokenise, and classify each
 * token as a day, a month name, a month number or a year.
 *
 * Month names are recognised in the device locale as well as English, both in full and
 * abbreviated, because real GEDCOM files are written in whatever language their author
 * used.
 */
class GedcomDate {

    val firstDate = SingleDate()
    val secondDate = SingleDate()

    /** Free text, either a whole phrase date or the explanation after an `INT` date. */
    var phrase: String? = null
        private set

    var kind: DateKind? = null
        private set

    constructor(gedcomDate: String) {
        analyze(gedcomDate)
    }

    /** Builds an exact, fully specified date. */
    constructor(year: Int, month: Int, day: Int) {
        firstDate.year = year
        firstDate.month = month
        firstDate.day = day
        firstDate.format = DateFormat.D_M_Y
        kind = DateKind.EXACT
    }

    /** Kinds that denote a single moment rather than a range. */
    val isSingleKind: Boolean
        get() = kind == DateKind.EXACT || kind == DateKind.APPROXIMATE ||
            kind == DateKind.CALCULATED || kind == DateKind.ESTIMATED ||
            kind == DateKind.INTERPRETED

    private fun analyze(rawDate: String) {
        kind = null
        phrase = null
        firstDate.reset()
        secondDate.reset()

        val trimmed = rawDate.trim()
        if (trimmed.isEmpty()) {
            kind = DateKind.EXACT
            return
        }
        // A phrase date is anything the author put in parentheses.
        if (trimmed.startsWith("(")) {
            kind = DateKind.PHRASE
            phrase = trimmed.replace(PARENTHESES, "")
            return
        }

        // Normalise the era spellings, then reduce every separator the wild has produced
        // (dots, dashes, commas, stray punctuation) to a single space. Slashes survive
        // because they carry the double-year meaning.
        val upper = trimmed
            .replace("B.C.", "BC", ignoreCase = true)
            .replace("BCE", "BC", ignoreCase = true)
            .replace(NON_DATE_CHARACTERS, " ")
            .uppercase()

        for (candidate in DateKind.entries) {
            if (candidate == DateKind.EXACT || candidate == DateKind.PHRASE) continue
            if (!upper.startsWith(candidate.prefix)) continue
            kind = candidate
            when {
                candidate == DateKind.BETWEEN_AND && upper.contains(AND) -> {
                    val andAt = upper.indexOf(AND)
                    if (andAt > upper.indexOf(DateKind.BETWEEN_AND.prefix) + 4) {
                        firstDate.scan(upper.substring(3, andAt))
                    }
                    if (upper.length > andAt + 3) secondDate.scan(upper.substring(andAt + 3))
                }

                candidate == DateKind.FROM && upper.contains(TO) -> {
                    kind = DateKind.FROM_TO
                    val toAt = upper.indexOf(TO)
                    if (toAt > upper.indexOf(DateKind.FROM.prefix) + 4) {
                        firstDate.scan(upper.substring(4, toAt))
                    }
                    if (upper.length > toAt + 2) secondDate.scan(upper.substring(toAt + 2))
                }

                candidate == DateKind.INTERPRETED && trimmed.contains("(") -> {
                    firstDate.scan(upper.substring(3, upper.indexOf("(")))
                    phrase = trimmed.substring(trimmed.indexOf("(") + 1).replace(PARENTHESES, "")
                }

                upper.length > candidate.prefix.length -> {
                    firstDate.scan(upper.substring(candidate.prefix.length))
                }
            }
            break
        }

        // No keyword matched, so it is either a plain date or free text.
        if (kind == null) {
            firstDate.scan(upper)
            kind = if (!firstDate.isEmpty) DateKind.EXACT else DateKind.PHRASE
            if (kind == DateKind.PHRASE) phrase = trimmed
        }
    }

    /** Checks the string against the GEDCOM 5.5.1 grammar for Gregorian dates. */
    fun isValid(gedcomDate: String): Boolean {
        val value = gedcomDate.trim()
        if (value.isEmpty() || kind == DateKind.PHRASE) return true
        val day = "\\d{1,2}"
        val month = "(JAN|FEB|MAR|APR|MAY|JUN|JUL|AUG|SEP|OCT|NOV|DEC)"
        val year = "\\d{3,4}(/\\d{2})?"
        val datePart = "($day +$month +$year|$month +$year|$year( B\\.C\\.)?)"
        return value.matches(datePart.toRegex()) ||
            value.matches("(ABT|CAL|EST|BEF|AFT|FROM|TO) +$datePart".toRegex()) ||
            value.matches("BET +$datePart +AND +$datePart".toRegex()) ||
            value.matches("FROM +$datePart +TO +$datePart".toRegex()) ||
            value.matches("INT +$datePart +\\(.*\\)".toRegex())
    }

    /**
     * An `yyyyMMdd` integer for sorting, or [Int.MAX_VALUE] when the date carries no
     * year — undated records sort last, which is what a genealogist expects.
     */
    fun sortKey(): Int {
        if (firstDate.isEmpty || !firstDate.hasYear) return Int.MAX_VALUE
        val year = (firstDate.effectiveYear() ?: return Int.MAX_VALUE) * 10000
        val monthDay = (firstDate.month ?: 0) * 100 + (firstDate.day ?: 0)
        return if (year < 0) year - monthDay else year + monthDay
    }

    /** The year of the main date, but only for kinds that denote a single moment. */
    fun year(): Int? =
        if (!firstDate.isEmpty && firstDate.hasYear && isSingleKind) firstDate.effectiveYear() else null

    private companion object {
        val PARENTHESES = "[()]".toRegex()
        val NON_DATE_CHARACTERS = "[^0-9\\p{L}/()]+".toRegex()
        const val AND = "AND"
        const val TO = "TO"
    }
}

private val MONTH_ABBREVIATIONS = arrayOf(
    "JAN", "FEB", "MAR", "APR", "MAY", "JUN", "JUL", "AUG", "SEP", "OCT", "NOV", "DEC",
)

/**
 * Fills this date from one exact GEDCOM date expression.
 *
 * The era suffix is consumed even when nothing else parses, so `"XX B.C."` is still
 * remembered as a B.C. phrase rather than losing the era silently.
 */
internal fun SingleDate.scan(expression: String) {
    reset()
    var value = expression.trim()

    if (value.endsWith("BC")) {
        value = value.dropLast(2).trim()
        negative = true
    }
    value = value.replace("\\s+".toRegex(), " ")

    // Tell a double year (1712/1713) from a slash-separated date (17/12/1713): if the
    // token before the last slash could be a month number, the slashes are separators.
    val slashAt = value.indexOf('/')
    if (slashAt > 0) {
        val tokens = value.split(SLASH_OR_SPACE).dropLastWhile { it.isEmpty() }
        val beforeLast = tokens.getOrNull(tokens.size - 2)
        if (tokens.size > 1 && beforeLast != null && beforeLast.length < 3 && beforeLast.digits() <= 12) {
            value = value.replace('/', ' ')
        } else {
            value = value.substring(0, slashAt).trim()
            if (value.length > 1) dual = true
        }
    }
    if (value.isEmpty()) return

    val tokens = value.split(' ').filter { it.isNotBlank() }
    val months = monthNames()

    when (tokens.size) {
        1 -> {
            val token = tokens[0]
            val month = months[token]
            when {
                month != null -> {
                    this.month = month
                    format = DateFormat.M
                }
                // A short number that names a real day is a day; anything else is a year.
                // That is why "31" is a day but "32" and the zero-padded "031" are years.
                token.isNumeric() && token.length <= 2 && token.toInt() in 1..31 -> {
                    day = token.toInt()
                    format = DateFormat.D
                }
                token.isNumeric() -> {
                    year = token.toInt()
                    format = DateFormat.Y
                }
            }
        }

        2 -> {
            val (first, second) = tokens
            val firstMonth = months[first]
            when {
                firstMonth != null && second.isNumeric() -> {
                    month = firstMonth
                    year = second.toInt()
                    format = DateFormat.M_Y
                }

                first.isNumeric() && months[second] != null -> {
                    if (first.toInt() !in 1..31) return
                    day = first.toInt()
                    month = months[second]
                    format = DateFormat.D_M
                }

                first.isNumeric() && second.isNumeric() && first.toInt() in 1..12 -> {
                    month = first.toInt()
                    year = second.toInt()
                    format = DateFormat.m_Y
                }
            }
        }

        3 -> {
            val (first, second, third) = tokens
            if (!first.isNumeric() || !third.isNumeric()) return
            val monthByName = months[second]
            val monthNumber = when {
                monthByName != null -> monthByName
                second.isNumeric() && second.toInt() in 1..12 -> second.toInt()
                else -> return
            }
            if (first.toInt() !in 1..31) return
            day = first.toInt()
            month = monthNumber
            year = third.toInt()
            format = if (monthByName != null) DateFormat.D_M_Y else DateFormat.D_m_Y
        }
    }

    // A day must exist in its month: 29 FEB 1999 is not a date, it is a mistake, and
    // treating it as free text preserves what the author wrote instead of silently
    // shifting it to 1 March.
    if (!isDayValid()) {
        val hadEra = negative
        reset()
        negative = hadEra
        return
    }

    // The numeric-month spellings have served their purpose.
    if (format == DateFormat.D_m_Y) format = DateFormat.D_M_Y
    if (format == DateFormat.m_Y) format = DateFormat.M_Y
}

private fun SingleDate.isDayValid(): Boolean {
    val day = day ?: return true
    val month = month ?: return day in 1..31
    val maxDay = when (month) {
        1, 3, 5, 7, 8, 10, 12 -> 31
        4, 6, 9, 11 -> 30
        2 -> if (isLeapYear(year)) 29 else 28
        else -> return false
    }
    return day in 1..maxDay
}

/**
 * A February date with no year could be a leap day, so it is allowed; only a stated
 * non-leap year rules 29 February out.
 */
private fun isLeapYear(year: Int?): Boolean {
    if (year == null) return true
    return (year % 4 == 0 && year % 100 != 0) || year % 400 == 0
}

private val SLASH_OR_SPACE = "[/ ]".toRegex()

private fun String.isNumeric(): Boolean = isNotEmpty() && all { it.isDigit() }

/** The leading digits of a token, or 0 when it has none. */
private fun String.digits(): Int = filter { it.isDigit() }.toIntOrNull() ?: 0

/**
 * Month name lookup for the device locale plus English, in both full and abbreviated
 * forms. Cached per locale because [DateFormatSymbols] is not cheap and dates are
 * parsed in tight loops when a whole tree is imported.
 */
private val monthNameCache = HashMap<Locale, Map<String, Int>>()

@Synchronized
private fun monthNames(): Map<String, Int> {
    val locale = Locale.getDefault()
    monthNameCache[locale]?.let { return it }
    val names = HashMap<String, Int>()
    for (candidate in listOf(locale, Locale.ENGLISH).distinct()) {
        val symbols = DateFormatSymbols.getInstance(candidate)
        for (month in 1..12) {
            symbols.months.getOrNull(month - 1)?.takeIf { it.isNotBlank() }
                ?.let { names.putIfAbsent(it.uppercase(candidate), month) }
            symbols.shortMonths.getOrNull(month - 1)?.takeIf { it.isNotBlank() }
                ?.let { names.putIfAbsent(it.uppercase(candidate).trimEnd('.'), month) }
        }
    }
    // The canonical GEDCOM abbreviations always win, whatever the locale.
    MONTH_ABBREVIATIONS.forEachIndexed { index, abbreviation -> names[abbreviation] = index + 1 }
    monthNameCache[locale] = names
    return names
}
