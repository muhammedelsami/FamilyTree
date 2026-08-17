package com.familytree.core.model

/**
 * A note (`NOTE`).
 *
 * A note with a [gedcomId] is *shared*: it lives at the top level and several records
 * point at it. A note without one is *inline*, owned by exactly one parent.
 */
data class Note(
    val id: Long = 0L,
    val treeId: Long,
    val gedcomId: String? = null,
    val value: String = "",
    val rin: String? = null,
    val changeDate: String? = null,
    val changeZone: String? = null,
    val updatedAt: Long = 0L,
) {
    val isShared: Boolean get() = gedcomId != null
}

/**
 * A media object (`OBJE`) — a photo, video, PDF or any other attached file.
 *
 * [file] is stored exactly as GEDCOM recorded it, which in real-world files may be a
 * Windows path, an Android path, a bare filename or a URL. Resolving it to something
 * openable is `core:media`'s job.
 */
data class MediaObject(
    val id: Long = 0L,
    val treeId: Long,
    val gedcomId: String? = null,
    val file: String? = null,
    val title: String? = null,
    /** `FORM` — the file format, e.g. `jpg`. */
    val format: String? = null,
    val fileTag: String? = null,
    /** `_TYPE` — a semantic category such as `photo` or `certificate`. */
    val mediaType: String? = null,
    /** `_PRIM` — marks the portrait used on diagram cards and profile headers. */
    val isPrimary: Boolean = false,
    val changeDate: String? = null,
    val changeZone: String? = null,
    val updatedAt: Long = 0L,
) {
    val isShared: Boolean get() = gedcomId != null
}

/** A source (`SOUR`) — where a piece of information came from. */
data class Source(
    val id: Long = 0L,
    val treeId: Long,
    val gedcomId: String? = null,
    val title: String? = null,
    val author: String? = null,
    val abbreviation: String? = null,
    val publication: String? = null,
    val text: String? = null,
    val date: String? = null,
    val callNumber: String? = null,
    val mediaType: String? = null,
    val typeTag: String? = null,
    val uidTag: String? = null,
    val referenceNumber: String? = null,
    val rin: String? = null,
    val uid: String? = null,
    val changeDate: String? = null,
    val changeZone: String? = null,
    val updatedAt: Long = 0L,
)

/**
 * A citation of a source from somewhere in the tree (`SOUR` inside another record).
 *
 * [sourceId] is null for a "source note" — an inline citation with free text but no
 * source record behind it, which GEDCOM permits.
 */
data class SourceCitation(
    val id: Long = 0L,
    val treeId: Long,
    val sourceId: Long? = null,
    val ownerType: OwnerType,
    val ownerId: Long,
    val value: String? = null,
    val page: String? = null,
    val date: String? = null,
    val text: String? = null,
    /** `QUAY` — confidence, 0 (unreliable) to 3 (direct evidence). */
    val quality: String? = null,
    val position: Int = 0,
)

/** An archive holding sources (`REPO`). */
data class Repository(
    val id: Long = 0L,
    val treeId: Long,
    val gedcomId: String? = null,
    val name: String? = null,
    val value: String? = null,
    val addressId: Long? = null,
    val phone: String? = null,
    val fax: String? = null,
    val www: String? = null,
    val wwwTag: String? = null,
    val email: String? = null,
    val emailTag: String? = null,
    val rin: String? = null,
    val changeDate: String? = null,
    val changeZone: String? = null,
    val updatedAt: Long = 0L,
)

/** A source's pointer to the repository that holds it (`SOUR.REPO`). */
data class RepositoryRef(
    val id: Long = 0L,
    val treeId: Long,
    val sourceId: Long,
    val repositoryId: Long? = null,
    val value: String? = null,
    val callNumber: String? = null,
    val mediaType: String? = null,
)

/**
 * A submitter (`SUBM`) — a person who contributed the data.
 *
 * [passed] records that this submitter has already been handed on with a shared tree;
 * it drives the share lifecycle and prevents a submitter being credited twice.
 */
data class Submitter(
    val id: Long = 0L,
    val treeId: Long,
    val gedcomId: String? = null,
    val name: String? = null,
    val value: String? = null,
    val addressId: Long? = null,
    val phone: String? = null,
    val fax: String? = null,
    val www: String? = null,
    val wwwTag: String? = null,
    val email: String? = null,
    val emailTag: String? = null,
    val language: String? = null,
    val rin: String? = null,
    val passed: Boolean = false,
    val changeDate: String? = null,
    val changeZone: String? = null,
    val updatedAt: Long = 0L,
)

/** A postal address, attached to an event, repository or submitter. */
data class Address(
    val id: Long = 0L,
    val treeId: Long,
    val value: String? = null,
    val line1: String? = null,
    val line2: String? = null,
    val line3: String? = null,
    val city: String? = null,
    val state: String? = null,
    val postalCode: String? = null,
    val country: String? = null,
    /** `_NAME` — the name of the place, a common extension. */
    val name: String? = null,
) {
    fun format(singleLine: Boolean = false): String {
        val parts = listOfNotNull(
            name?.takeIf { it.isNotBlank() },
            line1?.takeIf { it.isNotBlank() },
            line2?.takeIf { it.isNotBlank() },
            line3?.takeIf { it.isNotBlank() },
            listOfNotNull(
                postalCode?.takeIf { it.isNotBlank() },
                city?.takeIf { it.isNotBlank() },
            ).joinToString(" ").takeIf { it.isNotBlank() },
            state?.takeIf { it.isNotBlank() },
            country?.takeIf { it.isNotBlank() },
        )
        val assembled = parts.joinToString(if (singleLine) ", " else "\n")
        return assembled.ifBlank { value.orEmpty() }
    }
}

/**
 * The GEDCOM file header (`HEAD`) — one per tree.
 *
 * Kept faithfully so a round-trip preserves which program wrote the file and in what
 * character set, which other genealogy software relies on when importing.
 */
data class Header(
    val treeId: Long,
    val generatorValue: String? = null,
    val generatorName: String? = null,
    val generatorVersion: String? = null,
    val corporation: String? = null,
    val destination: String? = null,
    val dateTime: String? = null,
    val submitterId: Long? = null,
    val file: String? = null,
    val copyright: String? = null,
    val gedcomVersion: String? = "5.5.1",
    val gedcomForm: String? = "LINEAGE-LINKED",
    val characterSet: String? = "UTF-8",
    val language: String? = null,
)
