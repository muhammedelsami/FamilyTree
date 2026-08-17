package com.familytree.core.model

/**
 * An individual (`INDI`).
 *
 * Names, events, notes, media and citations live in their own tables and are attached
 * through [OwnerType.PERSON]; [PersonDetails] carries the fully loaded aggregate.
 */
data class Person(
    val id: Long = 0L,
    val treeId: Long,
    /** GEDCOM cross-reference id, e.g. `I12`. Assigned on export if still null. */
    val gedcomId: String? = null,
    val sex: Sex = Sex.NONE,
    val sortOrder: Int = 0,
    /** `_UID`/`UID` — a stable identifier other programs use to match records. */
    val uid: String? = null,
    /** Which spelling the source file used, so export reproduces it exactly. */
    val uidTag: String? = null,
    val rin: String? = null,
    /** `REFN` values, newline separated. */
    val referenceNumbers: String? = null,
    val addressId: Long? = null,
    val phone: String? = null,
    val fax: String? = null,
    val email: String? = null,
    val emailTag: String? = null,
    val www: String? = null,
    val wwwTag: String? = null,
    val changeDate: String? = null,
    /** Timezone the change date was recorded in — FamilyGem's `zone` extension. */
    val changeZone: String? = null,
    val updatedAt: Long = 0L,
)

/**
 * Biological/administrative sex as recorded in the `SEX` fact.
 *
 * [NONE] means no `SEX` fact exists at all, which is different from [UNDEFINED]
 * (`SEX U`) — the diagram draws them with different card borders.
 */
enum class Sex(val gedcomValue: String?) {
    NONE(null),
    MALE("M"),
    FEMALE("F"),
    UNDEFINED("U"),
    OTHER("X"),
    ;

    val isMale: Boolean get() = this == MALE
    val isFemale: Boolean get() = this == FEMALE

    companion object {
        fun fromGedcom(value: String?): Sex = when (value?.trim()?.uppercase()) {
            null, "" -> NONE
            "M" -> MALE
            "F" -> FEMALE
            "U" -> UNDEFINED
            else -> OTHER
        }
    }
}

/**
 * One `NAME` structure. A person may have several (birth name, married name, aka…).
 *
 * [value] is the raw GEDCOM form with the surname delimited by slashes
 * (`John /Smith/`); the individual pieces are also stored so the UI can edit them
 * without re-parsing. On export, [value] is regenerated from the pieces when blank.
 */
data class PersonName(
    val id: Long = 0L,
    val personId: Long,
    val position: Int = 0,
    val value: String? = null,
    val prefix: String? = null,
    val given: String? = null,
    val surnamePrefix: String? = null,
    val surname: String? = null,
    val suffix: String? = null,
    val nickname: String? = null,
    /** `TYPE` sub-tag: aka, birth, immigrant, maiden, married… */
    val type: String? = null,
    val typeTag: String? = null,
    /** `_MARRNM` — the married surname, a common non-standard extension. */
    val marriedName: String? = null,
    val marriedNameTag: String? = null,
    val alsoKnownAsTag: String? = null,
    /** `_AKA` — an alternative name, another widespread extension. */
    val alsoKnownAs: String? = null,
    /** `FONE` — phonetic variation. */
    val phonetic: String? = null,
    /** `ROMN` — romanised variation. */
    val romanised: String? = null,
) {
    /**
     * The name to show.
     *
     * The `NAME` value wins over the separate pieces, because GEDCOM treats it as the
     * primary form and the pieces as optional refinements. Files in the wild very often
     * carry `NAME Ayşe /Yılmaz/` with only `SURN` broken out — preferring the pieces
     * there would silently drop the given name.
     */
    fun display(): String {
        val fromValue = value?.replace("/", " ")?.collapseSpaces()
        if (!fromValue.isNullOrBlank()) return fromValue
        return listOfNotNull(
            prefix?.takeIf { it.isNotBlank() },
            given?.takeIf { it.isNotBlank() },
            surnamePrefix?.takeIf { it.isNotBlank() },
            surname?.takeIf { it.isNotBlank() },
            suffix?.takeIf { it.isNotBlank() },
        ).joinToString(" ")
    }

    /** The given name, read from the value when it was never broken out. */
    fun effectiveGiven(): String? =
        given?.takeIf { it.isNotBlank() }
            ?: value?.substringBefore('/')?.collapseSpaces()?.takeIf { it.isNotBlank() }

    /** The surname, read from between the slashes when it was never broken out. */
    fun effectiveSurname(): String? =
        surname?.takeIf { it.isNotBlank() }
            ?: value?.takeIf { it.contains('/') }
                ?.substringAfter('/')?.substringBefore('/')
                ?.collapseSpaces()?.takeIf { it.isNotBlank() }
}

private fun String.collapseSpaces(): String = trim().replace("\\s+".toRegex(), " ")

/** A person with everything needed to render a profile screen. */
data class PersonDetails(
    val person: Person,
    val names: List<PersonName> = emptyList(),
    val events: List<Event> = emptyList(),
    val media: List<MediaObject> = emptyList(),
    val notes: List<Note> = emptyList(),
    val citations: List<SourceCitation> = emptyList(),
    val extensions: List<GedcomExtension> = emptyList(),
)
