package com.familytree.core.ui

import androidx.compose.ui.text.font.FontFamily
import com.familytree.core.designsystem.theme.FamilyTreeFonts
import com.familytree.core.model.AppFont

/**
 * The chosen typeface as Compose sees it.
 *
 * Lives here because this is the module that knows both sides: `core:designsystem` holds
 * the font resources but no domain types, and `core:model` holds the preference but no
 * Compose. Two callers need it — the theme at the top of the app, and the picker in
 * settings that renders each option in its own face.
 */
fun AppFont.fontFamily(): FontFamily = when (this) {
    AppFont.SYSTEM -> FamilyTreeFonts.System
    AppFont.CAIRO -> FamilyTreeFonts.Cairo
    AppFont.ALEXANDRIA -> FamilyTreeFonts.Alexandria
}
