package com.familytree.core.model

/**
 * An event or attribute (`BIRT`, `DEAT`, `MARR`, `OCCU`, `RESI`, …).
 *
 * GEDCOM draws no structural line between events and attributes, so one type covers
 * both. [date] holds the raw GEDCOM date string — parsing lives in `core:gedcom`
 * so this module stays free of date-grammar concerns.
 */
data class Event(
    val id: Long = 0L,
    val treeId: Long,
    /** [OwnerType.PERSON] or [OwnerType.FAMILY]. */
    val ownerType: OwnerType,
    val ownerId: Long,
    val tag: String,
    /** The event's own value. `Y` conventionally means "happened, details unknown". */
    val value: String? = null,
    /** `TYPE` — free-text qualifier, e.g. the flavour of a marriage. */
    val type: String? = null,
    val date: String? = null,
    val place: String? = null,
    val cause: String? = null,
    val addressId: Long? = null,
    val phone: String? = null,
    val fax: String? = null,
    val www: String? = null,
    val email: String? = null,
    val rin: String? = null,
    val uid: String? = null,
    val uidTag: String? = null,
    val emailTag: String? = null,
    val wwwTag: String? = null,
    val position: Int = 0,
)

/**
 * The event tags the editor offers, mirroring FamilyGem's menus so an imported tree
 * never shows a tag the UI cannot name.
 */
object EventCatalog {

    /** Offered first for a person, at the top of the "add event" menu. */
    val PRIMARY_PERSON_TAGS = listOf("BIRT", "CHR", "RESI", "OCCU", "DEAT", "BURI")

    /** The rest of the person tags, shown under "Other". */
    val OTHER_PERSON_TAGS = listOf(
        "CREM", "ADOP", "BAPM", "BARM", "BATM", "BLES", "CONF", "FCOM", "ORDN",
        "NATU", "EMIG", "IMMI", "CENS", "PROB", "WILL", "GRAD", "RETI", "EVEN",
        "CAST", "DSCR", "EDUC", "NATI", "NCHI", "PROP", "RELI", "SSN", "TITL", "_MILT",
    )

    /** Offered first for a family. */
    val PRIMARY_FAMILY_TAGS = listOf("MARR", "DIV")

    /** The rest of the family tags. */
    val OTHER_FAMILY_TAGS = listOf(
        "ANUL", "CENS", "DIVF", "ENGA", "MARB", "MARC", "MARL", "MARS",
        "RESI", "EVEN", "NCHI",
    )

    /** Tags whose `VALUE` slot is meaningless — the editor hides the field for these. */
    val VALUELESS_TAGS = setOf(
        "BIRT", "CHR", "DEAT", "BURI", "CREM", "ADOP", "BAPM", "BARM", "BATM",
        "BLES", "CONF", "FCOM", "ORDN", "NATU", "EMIG", "IMMI", "CENS", "PROB",
        "WILL", "GRAD", "RETI", "MARR", "DIV", "ANUL", "DIVF", "ENGA", "MARB",
        "MARC", "MARL", "MARS", "RESI", "SEX",
    )

    /** Death-implying tags — a person carrying any of these is treated as deceased. */
    val DEATH_TAGS = setOf("DEAT", "BURI", "CREM")

    /** Birth-implying tags, in the order used when picking a person's "first date". */
    val BIRTH_TAGS = listOf("BIRT", "CHR", "BAPM")

    /** Tags that mean a couple is married. */
    val MARRIAGE_TAGS = setOf("MARR", "MARB", "MARC", "MARL", "MARS")

    val ALL_PERSON_TAGS: List<String> get() = PRIMARY_PERSON_TAGS + OTHER_PERSON_TAGS
    val ALL_FAMILY_TAGS: List<String> get() = PRIMARY_FAMILY_TAGS + OTHER_FAMILY_TAGS
}
