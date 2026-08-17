package com.familytree.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

/**
 * The shipped default is Alexandria; the platform's own family is one of the choices
 * rather than the starting point, so the interface looks the same on every device. Both
 * bundled families are Arabic-first designs carrying Latin Extended as well — the thing to
 * avoid here is a Latin-first font, which would degrade Arabic badly.
 *
 * [LineHeightStyle] with `Trim.None` keeps the extra leading Arabic diacritics need
 * instead of clipping it, which is the usual cause of cut-off marks in Compose text.
 */
private val defaultLineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun style(
    family: FontFamily,
    size: Int,
    lineHeight: Int,
    weight: FontWeight = FontWeight.Normal,
    letterSpacing: Double = 0.0,
) = TextStyle(
    fontFamily = family,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = letterSpacing.sp,
    lineHeightStyle = defaultLineHeightStyle,
)

/**
 * The type scale, in [family].
 *
 * Sizes and spacing do not change with the typeface: the scale is the design, the family is
 * a preference within it.
 *
 * **Body and title tracking is zero, and that is not only a matter of taste.** Material's
 * baseline opens body text up by 0.25–0.5sp, which on a geometric face reads loose and
 * dated at these sizes — and in Arabic it is worse than loose: letter-spacing is inserted
 * between glyphs that are supposed to join, so a positive value visibly pulls a word apart
 * at the seams. Zero is the only setting that is right in all three of the shipped
 * languages. The large sizes take slightly *negative* tracking instead, which is what keeps
 * a 32sp heading from looking like it has drifted apart.
 *
 * Line heights are a little more generous than the baseline for the same reason the trim is
 * off: Arabic sits taller than Latin, and the difference shows first in the small styles.
 */
internal fun familyTreeTypography(family: FontFamily = FontFamily.Default) = Typography(
    displayLarge = style(family, 57, 64, FontWeight.Medium, (-1.0)),
    displayMedium = style(family, 45, 54, FontWeight.Medium, (-0.5)),
    displaySmall = style(family, 36, 44, FontWeight.Medium, (-0.25)),
    headlineLarge = style(family, 32, 40, FontWeight.Medium, (-0.5)),
    headlineMedium = style(family, 28, 36, FontWeight.Medium, (-0.25)),
    headlineSmall = style(family, 24, 32, FontWeight.Medium),
    titleLarge = style(family, 22, 28, FontWeight.SemiBold, (-0.2)),
    titleMedium = style(family, 16, 24, FontWeight.SemiBold),
    titleSmall = style(family, 14, 20, FontWeight.SemiBold),
    bodyLarge = style(family, 16, 26),
    bodyMedium = style(family, 14, 22),
    bodySmall = style(family, 12, 18),
    labelLarge = style(family, 14, 20, FontWeight.Medium),
    labelMedium = style(family, 12, 16, FontWeight.Medium),
    labelSmall = style(family, 11, 16, FontWeight.Medium),
)
