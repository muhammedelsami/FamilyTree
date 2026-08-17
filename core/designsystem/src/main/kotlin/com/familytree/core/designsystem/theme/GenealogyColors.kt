package com.familytree.core.designsystem.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Colours the diagram and record lists need that Material's roles do not cover.
 *
 * These live outside [androidx.compose.material3.ColorScheme] because they carry
 * domain meaning — "this card is a woman", "this tree has been consumed" — rather than
 * a UI role. They are exposed through [LocalGenealogyColors] so a screen reads them the
 * same way it reads `MaterialTheme.colorScheme`.
 */
@Immutable
data class GenealogyColors(
    val maleAccent: Color,
    val femaleAccent: Color,
    val undefinedAccent: Color,
    /** Cards for people who married into the family rather than being born into it. */
    val acquiredCard: Color,
    val deceasedIndicator: Color,
    val diagramLine: Color,
    val diagramBackLine: Color,
    val duplicateLineMale: Color,
    val duplicateLineFemale: Color,
    val duplicateLineUndefined: Color,
    /** Lines drawn onto exported PNG/PDF, which always sit on white. */
    val printLine: Color,
    /** A tree that came back from a share and derives from a local tree. */
    val treeDerived: Color,
    /** A tree whose updates have all been consumed. */
    val treeExhausted: Color,
)

/*
 * The sex accents are two hues an equal distance either side of neutral — a slate blue and
 * a dusty rose — at low chroma. The convention they follow is worth keeping, since a
 * genealogist reads it without being told, but the saturated blue/pink it usually arrives
 * in shouts on a screen holding fifty cards. Pulled down to this level they still separate
 * at a glance while the names stay the loudest thing on the diagram.
 */
internal val LightGenealogyColors = GenealogyColors(
    maleAccent = Color(0xFF47698C),
    femaleAccent = Color(0xFF8C4769),
    undefinedAccent = Color(0xFF7C847F),
    // Warm enough to separate from `surfaceContainerHigh` under the card next to it,
    // quiet enough that a row of in-laws does not stripe the diagram.
    acquiredCard = Color(0xFFEDE8DE),
    deceasedIndicator = Color(0xFF5A625D),
    // Hairlines. The diagram is mostly connections, so these sit close to the outline
    // tones: heavy enough to trace, light enough to disappear when reading names.
    diagramLine = Color(0xFF98A29B),
    diagramBackLine = Color(0xFFC5CEC7),
    duplicateLineMale = Color(0xFF47698C),
    duplicateLineFemale = Color(0xFF8C4769),
    duplicateLineUndefined = Color(0xFF98A29B),
    printLine = Color(0xFF414942),
    treeDerived = Color(0xFF635C4B),
    treeExhausted = Color(0xFF919A94),
)

internal val DarkGenealogyColors = GenealogyColors(
    maleAccent = Color(0xFFA8C4E0),
    femaleAccent = Color(0xFFE0A8C4),
    undefinedAccent = Color(0xFFA9B2AC),
    acquiredCard = Color(0xFF272420),
    deceasedIndicator = Color(0xFFA9B2AC),
    diagramLine = Color(0xFF6E7873),
    diagramBackLine = Color(0xFF3A403C),
    duplicateLineMale = Color(0xFFA8C4E0),
    duplicateLineFemale = Color(0xFFE0A8C4),
    duplicateLineUndefined = Color(0xFF6E7873),
    // Export always renders on a white page, so this stays dark in both themes.
    printLine = Color(0xFF414942),
    treeDerived = Color(0xFFCEC6B0),
    treeExhausted = Color(0xFF6E7873),
)

val LocalGenealogyColors = staticCompositionLocalOf { LightGenealogyColors }
