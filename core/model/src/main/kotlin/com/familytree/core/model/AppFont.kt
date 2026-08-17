package com.familytree.core.model

/**
 * The typeface the interface is drawn in.
 *
 * Both bundled families were picked for the same reason: they are Arabic-first designs that
 * also carry Latin Extended, so Turkish and Arabic are drawn by the same hand instead of
 * Arabic falling back to whatever the device has. A Latin-only font would have been the
 * wrong trade — see the note in `Type.kt`.
 *
 * @property endonym the family's own name, left untranslated the way a typeface name is.
 *   Empty for [SYSTEM], which is labelled with a translated string instead.
 */
enum class AppFont(val endonym: String) {
    /** The device's own font, which is what the app used before this setting existed. */
    SYSTEM(""),
    CAIRO("Cairo"),
    ALEXANDRIA("Alexandria"),
}
