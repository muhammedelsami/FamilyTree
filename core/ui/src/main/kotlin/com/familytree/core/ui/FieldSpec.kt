package com.familytree.core.ui

import androidx.annotation.StringRes
import com.familytree.core.model.Address
import com.familytree.core.model.Event
import com.familytree.core.model.MediaObject
import com.familytree.core.model.Note
import com.familytree.core.model.PersonName
import com.familytree.core.model.Repository
import com.familytree.core.model.Source
import com.familytree.core.model.Submitter

/**
 * How one field of a record is read, written and rendered.
 *
 * This replaces the reflection FamilyGem used to drive its generic record editor
 * (`object.getClass().getMethod("get" + name).invoke(object)`). A lambda pair does the
 * same job while being checked by the compiler, safe under R8 without keep rules, and
 * free of the per-field reflection cost the original itself flagged as a problem.
 */
data class FieldSpec<T>(
    @param:StringRes val labelRes: Int,
    val kind: FieldKind,
    val get: (T) -> String?,
    val set: (T, String?) -> T,
    /** Hidden unless expert mode is on — GEDCOM plumbing most users never need. */
    val expertOnly: Boolean = false,
)

enum class FieldKind {
    TEXT,
    MULTILINE,
    /** Rendered with the GEDCOM date picker and validity warning. */
    DATE,
    /** Offers places already used in this tree. */
    PLACE,
}

private fun String?.orNull(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

/**
 * The editable fields of each record type.
 *
 * Read-only identity fields — a person's `SEX`, an event's tag — are deliberately
 * absent: they are chosen elsewhere, and offering them as free text is how trees end up
 * with `SEX` set to "male?".
 */
object RecordFields {

    val event: List<FieldSpec<Event>> = listOf(
        FieldSpec(R.string.field_date, FieldKind.DATE, { it.date }, { r, v -> r.copy(date = v.orNull()) }),
        FieldSpec(R.string.field_place, FieldKind.PLACE, { it.place }, { r, v -> r.copy(place = v.orNull()) }),
        FieldSpec(R.string.field_value, FieldKind.TEXT, { it.value }, { r, v -> r.copy(value = v.orNull()) }),
        FieldSpec(R.string.field_type, FieldKind.TEXT, { it.type }, { r, v -> r.copy(type = v.orNull()) }),
        FieldSpec(R.string.field_cause, FieldKind.TEXT, { it.cause }, { r, v -> r.copy(cause = v.orNull()) }),
        FieldSpec(R.string.field_phone, FieldKind.TEXT, { it.phone }, { r, v -> r.copy(phone = v.orNull()) }),
        FieldSpec(R.string.field_email, FieldKind.TEXT, { it.email }, { r, v -> r.copy(email = v.orNull()) }),
        FieldSpec(R.string.field_www, FieldKind.TEXT, { it.www }, { r, v -> r.copy(www = v.orNull()) }),
        FieldSpec(R.string.field_fax, FieldKind.TEXT, { it.fax }, { r, v -> r.copy(fax = v.orNull()) }, expertOnly = true),
        FieldSpec(R.string.field_rin, FieldKind.TEXT, { it.rin }, { r, v -> r.copy(rin = v.orNull()) }, expertOnly = true),
        FieldSpec(R.string.field_uid, FieldKind.TEXT, { it.uid }, { r, v -> r.copy(uid = v.orNull()) }, expertOnly = true),
    )

    val name: List<FieldSpec<PersonName>> = listOf(
        FieldSpec(R.string.field_given, FieldKind.TEXT, { it.given }, { r, v -> r.copy(given = v.orNull()) }),
        FieldSpec(R.string.field_surname, FieldKind.TEXT, { it.surname }, { r, v -> r.copy(surname = v.orNull()) }),
        FieldSpec(R.string.field_nickname, FieldKind.TEXT, { it.nickname }, { r, v -> r.copy(nickname = v.orNull()) }),
        FieldSpec(R.string.field_prefix, FieldKind.TEXT, { it.prefix }, { r, v -> r.copy(prefix = v.orNull()) }),
        FieldSpec(R.string.field_suffix, FieldKind.TEXT, { it.suffix }, { r, v -> r.copy(suffix = v.orNull()) }),
        FieldSpec(R.string.field_married_name, FieldKind.TEXT, { it.marriedName }, { r, v -> r.copy(marriedName = v.orNull()) }),
        FieldSpec(R.string.field_aka, FieldKind.TEXT, { it.alsoKnownAs }, { r, v -> r.copy(alsoKnownAs = v.orNull()) }),
        FieldSpec(R.string.field_name_type, FieldKind.TEXT, { it.type }, { r, v -> r.copy(type = v.orNull()) }, expertOnly = true),
        // The slashed value is regenerated from the pieces on save, so it is only shown
        // to experts who may need to correct an unusual form by hand.
        FieldSpec(R.string.field_value, FieldKind.TEXT, { it.value }, { r, v -> r.copy(value = v.orNull()) }, expertOnly = true),
        FieldSpec(R.string.field_surname_prefix, FieldKind.TEXT, { it.surnamePrefix }, { r, v -> r.copy(surnamePrefix = v.orNull()) }, expertOnly = true),
        FieldSpec(R.string.field_phonetic, FieldKind.TEXT, { it.phonetic }, { r, v -> r.copy(phonetic = v.orNull()) }, expertOnly = true),
        FieldSpec(R.string.field_romanised, FieldKind.TEXT, { it.romanised }, { r, v -> r.copy(romanised = v.orNull()) }, expertOnly = true),
    )

    val note: List<FieldSpec<Note>> = listOf(
        FieldSpec(R.string.field_text, FieldKind.MULTILINE, { it.value }, { r, v -> r.copy(value = v.orEmpty()) }),
        FieldSpec(R.string.field_rin, FieldKind.TEXT, { it.rin }, { r, v -> r.copy(rin = v.orNull()) }, expertOnly = true),
    )

    val source: List<FieldSpec<Source>> = listOf(
        FieldSpec(R.string.field_title, FieldKind.TEXT, { it.title }, { r, v -> r.copy(title = v.orNull()) }),
        FieldSpec(R.string.field_author, FieldKind.TEXT, { it.author }, { r, v -> r.copy(author = v.orNull()) }),
        FieldSpec(R.string.field_abbreviation, FieldKind.TEXT, { it.abbreviation }, { r, v -> r.copy(abbreviation = v.orNull()) }),
        FieldSpec(R.string.field_publication, FieldKind.MULTILINE, { it.publication }, { r, v -> r.copy(publication = v.orNull()) }),
        FieldSpec(R.string.field_text, FieldKind.MULTILINE, { it.text }, { r, v -> r.copy(text = v.orNull()) }),
        FieldSpec(R.string.field_date, FieldKind.DATE, { it.date }, { r, v -> r.copy(date = v.orNull()) }),
        FieldSpec(R.string.field_call_number, FieldKind.TEXT, { it.callNumber }, { r, v -> r.copy(callNumber = v.orNull()) }),
        FieldSpec(R.string.field_media_type, FieldKind.TEXT, { it.mediaType }, { r, v -> r.copy(mediaType = v.orNull()) }, expertOnly = true),
        FieldSpec(R.string.field_reference_number, FieldKind.TEXT, { it.referenceNumber }, { r, v -> r.copy(referenceNumber = v.orNull()) }, expertOnly = true),
        FieldSpec(R.string.field_rin, FieldKind.TEXT, { it.rin }, { r, v -> r.copy(rin = v.orNull()) }, expertOnly = true),
    )

    val repository: List<FieldSpec<Repository>> = listOf(
        FieldSpec(R.string.field_name, FieldKind.TEXT, { it.name }, { r, v -> r.copy(name = v.orNull()) }),
        FieldSpec(R.string.field_value, FieldKind.MULTILINE, { it.value }, { r, v -> r.copy(value = v.orNull()) }),
        FieldSpec(R.string.field_phone, FieldKind.TEXT, { it.phone }, { r, v -> r.copy(phone = v.orNull()) }),
        FieldSpec(R.string.field_email, FieldKind.TEXT, { it.email }, { r, v -> r.copy(email = v.orNull()) }),
        FieldSpec(R.string.field_www, FieldKind.TEXT, { it.www }, { r, v -> r.copy(www = v.orNull()) }),
        FieldSpec(R.string.field_fax, FieldKind.TEXT, { it.fax }, { r, v -> r.copy(fax = v.orNull()) }, expertOnly = true),
        FieldSpec(R.string.field_rin, FieldKind.TEXT, { it.rin }, { r, v -> r.copy(rin = v.orNull()) }, expertOnly = true),
    )

    val submitter: List<FieldSpec<Submitter>> = listOf(
        FieldSpec(R.string.field_name, FieldKind.TEXT, { it.name }, { r, v -> r.copy(name = v.orNull()) }),
        FieldSpec(R.string.field_value, FieldKind.MULTILINE, { it.value }, { r, v -> r.copy(value = v.orNull()) }),
        FieldSpec(R.string.field_phone, FieldKind.TEXT, { it.phone }, { r, v -> r.copy(phone = v.orNull()) }),
        FieldSpec(R.string.field_email, FieldKind.TEXT, { it.email }, { r, v -> r.copy(email = v.orNull()) }),
        FieldSpec(R.string.field_www, FieldKind.TEXT, { it.www }, { r, v -> r.copy(www = v.orNull()) }),
        FieldSpec(R.string.field_language, FieldKind.TEXT, { it.language }, { r, v -> r.copy(language = v.orNull()) }, expertOnly = true),
        FieldSpec(R.string.field_rin, FieldKind.TEXT, { it.rin }, { r, v -> r.copy(rin = v.orNull()) }, expertOnly = true),
    )

    val media: List<FieldSpec<MediaObject>> = listOf(
        FieldSpec(R.string.field_title, FieldKind.TEXT, { it.title }, { r, v -> r.copy(title = v.orNull()) }),
        FieldSpec(R.string.field_file, FieldKind.TEXT, { it.file }, { r, v -> r.copy(file = v.orNull()) }),
        FieldSpec(R.string.field_format, FieldKind.TEXT, { it.format }, { r, v -> r.copy(format = v.orNull()) }, expertOnly = true),
        FieldSpec(R.string.field_media_type, FieldKind.TEXT, { it.mediaType }, { r, v -> r.copy(mediaType = v.orNull()) }, expertOnly = true),
    )

    val address: List<FieldSpec<Address>> = listOf(
        FieldSpec(R.string.field_place_name, FieldKind.TEXT, { it.name }, { r, v -> r.copy(name = v.orNull()) }),
        FieldSpec(R.string.field_address_line, FieldKind.TEXT, { it.line1 }, { r, v -> r.copy(line1 = v.orNull()) }),
        FieldSpec(R.string.field_city, FieldKind.TEXT, { it.city }, { r, v -> r.copy(city = v.orNull()) }),
        FieldSpec(R.string.field_postal_code, FieldKind.TEXT, { it.postalCode }, { r, v -> r.copy(postalCode = v.orNull()) }),
        FieldSpec(R.string.field_state, FieldKind.TEXT, { it.state }, { r, v -> r.copy(state = v.orNull()) }),
        FieldSpec(R.string.field_country, FieldKind.TEXT, { it.country }, { r, v -> r.copy(country = v.orNull()) }),
        FieldSpec(R.string.field_value, FieldKind.MULTILINE, { it.value }, { r, v -> r.copy(value = v.orNull()) }, expertOnly = true),
    )
}
