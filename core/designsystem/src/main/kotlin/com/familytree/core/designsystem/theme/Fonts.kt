package com.familytree.core.designsystem.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.familytree.core.designsystem.R

/**
 * The bundled typefaces.
 *
 * Both are shipped as *variable* fonts — one file carrying a continuous weight axis rather
 * than one file per weight. That is how a family costs ~300–600 KB instead of four times
 * that, and it is why every weight below is cut from the same file. Android has supported
 * variable fonts since API 26, comfortably under this app's minimum of 28.
 *
 * Only the weights the type scale actually asks for are declared. Anything else is
 * synthesised by the platform, and a weight nobody uses is dead bytes in the atlas.
 */
object FamilyTreeFonts {

    val Cairo: FontFamily = variableFamily(R.font.cairo)

    val Alexandria: FontFamily = variableFamily(R.font.alexandria)

    /** What the app used before the setting existed: whatever the device provides. */
    val System: FontFamily = FontFamily.Default
}

private val usedWeights = listOf(
    FontWeight.Normal,
    FontWeight.Medium,
    FontWeight.SemiBold,
    FontWeight.Bold,
)

// The variable-font overload of `Font` is still marked experimental, though the underlying
// platform support has been stable since API 26. The opt-in is scoped to this one function
// so the marker stays where the risk is.
@OptIn(ExperimentalTextApi::class)
private fun variableFamily(resId: Int): FontFamily = FontFamily(
    usedWeights.map { weight ->
        Font(
            resId = resId,
            weight = weight,
            // Declaring the weight alone is not enough for a variable font: without the
            // axis setting the renderer takes the file's default instance and every weight
            // comes out identical, which reads as "the font did not apply".
            variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
        )
    },
)
