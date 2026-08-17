package com.familytree.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

/**
 * Readable names for GEDCOM event tags.
 *
 * An unknown tag falls back to itself rather than to a generic "event": a file may
 * carry a vendor tag this app has never seen, and showing `_MILT` at least tells the
 * user what the record actually says.
 */
private val LABELS: Map<String, Int> = mapOf(
    "BIRT" to R.string.event_birt,
    "CHR" to R.string.event_chr,
    "RESI" to R.string.event_resi,
    "OCCU" to R.string.event_occu,
    "DEAT" to R.string.event_deat,
    "BURI" to R.string.event_buri,
    "CREM" to R.string.event_crem,
    "ADOP" to R.string.event_adop,
    "BAPM" to R.string.event_bapm,
    "BARM" to R.string.event_barm,
    "BATM" to R.string.event_batm,
    "BLES" to R.string.event_bles,
    "CONF" to R.string.event_conf,
    "FCOM" to R.string.event_fcom,
    "ORDN" to R.string.event_ordn,
    "NATU" to R.string.event_natu,
    "EMIG" to R.string.event_emig,
    "IMMI" to R.string.event_immi,
    "CENS" to R.string.event_cens,
    "PROB" to R.string.event_prob,
    "WILL" to R.string.event_will,
    "GRAD" to R.string.event_grad,
    "RETI" to R.string.event_reti,
    "EVEN" to R.string.event_even,
    "CAST" to R.string.event_cast,
    "DSCR" to R.string.event_dscr,
    "EDUC" to R.string.event_educ,
    "NATI" to R.string.event_nati,
    "NCHI" to R.string.event_nchi,
    "PROP" to R.string.event_prop,
    "RELI" to R.string.event_reli,
    "SSN" to R.string.event_ssn,
    "TITL" to R.string.event_titl,
    "_MILT" to R.string.event_milt,
    "MARR" to R.string.event_marr,
    "DIV" to R.string.event_div,
    "ANUL" to R.string.event_anul,
    "DIVF" to R.string.event_divf,
    "ENGA" to R.string.event_enga,
    "MARB" to R.string.event_marb,
    "MARC" to R.string.event_marc,
    "MARL" to R.string.event_marl,
    "MARS" to R.string.event_mars,
    "SEX" to R.string.event_sex,
)

@Composable
fun eventLabel(tag: String): String {
    val resource = LABELS[tag.uppercase()] ?: return tag
    return stringResource(resource)
}

/** Whether the app has a name for this tag, used when offering tags to add. */
fun isKnownEventTag(tag: String): Boolean = LABELS.containsKey(tag.uppercase())
