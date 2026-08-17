package com.familytree.core.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * One radius step per size, each a clean multiple of 4dp.
 *
 * Regular steps are what make rounding read as a system rather than as a series of
 * decisions: a chip inside a card inside a sheet nests without any two curves looking
 * like they were meant to match and missed.
 */
internal val FamilyTreeShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

/**
 * Dimensions shared across screens, so spacing stays consistent without magic numbers.
 *
 * The [space1]…[space8] scale is the base: every gap in the app should be one of these,
 * and the named tokens below are the scale under the name of the job they do. Reach for
 * the named one where it fits — changing what "the padding around a screen" means then
 * stays a single edit.
 */
object FtDimens {
    // --- The 4dp scale ---
    val space1 = 4.dp
    val space2 = 8.dp
    val space3 = 12.dp
    val space4 = 16.dp
    val space5 = 20.dp
    val space6 = 24.dp
    val space8 = 32.dp

    // --- Named for their job ---
    val screenPadding = space4
    val listItemSpacing = space2
    val sectionSpacing = space6
    val cardPadding = space4
    /** Between a group of settings and the next heading, where the divider used to be. */
    val groupSpacing = space8

    // --- Portraits ---
    val portraitSmall = 40.dp
    val portraitMedium = 56.dp
    val portraitLarge = 96.dp

    /** Android's minimum, and the floor for any row the user can tap. */
    val minTouchTarget = 48.dp

    /**
     * One device pixel would disappear on a 3x screen and two would look drawn on, so
     * separators and card outlines are a hairline at 1dp everywhere.
     */
    val hairline = 1.dp
}
