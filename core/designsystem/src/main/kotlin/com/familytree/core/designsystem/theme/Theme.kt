package com.familytree.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily

/**
 * @param dynamicColor follow the wallpaper palette on Android 12+. The genealogy
 *   accents stay on the brand palette either way — they encode meaning (sex, tree
 *   state), so letting the wallpaper recolour them would make them unreadable.
 * @param fontFamily the typeface for the whole type scale. Takes a Compose type rather
 *   than the settings enum on purpose: this module knows nothing about the domain, which
 *   is what keeps it usable from previews and tests. `FamilyTreeFonts` holds the bundled
 *   families.
 */
@Composable
fun FamilyTreeTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    fontFamily: FontFamily = FamilyTreeFonts.Alexandria,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        darkTheme -> DarkColors
        else -> LightColors
    }
    val genealogyColors = if (darkTheme) DarkGenealogyColors else LightGenealogyColors
    // Fifteen TextStyles, rebuilt only when the family actually changes rather than on
    // every recomposition of the whole app.
    val typography = remember(fontFamily) { familyTreeTypography(fontFamily) }

    CompositionLocalProvider(LocalGenealogyColors provides genealogyColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            shapes = FamilyTreeShapes,
            content = content,
        )
    }
}

/** Shorthand mirroring `MaterialTheme.colorScheme`. */
object FtTheme {
    val genealogyColors: GenealogyColors
        @Composable get() = LocalGenealogyColors.current
}
