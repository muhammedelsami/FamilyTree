package com.familytree.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

/*
 * The brand palette: one deep evergreen over near-achromatic neutrals.
 *
 * The design is deliberately quiet. A family tree is dense with names, dates and lines,
 * and the screen has to stay readable when a hundred of them are on it, so colour is
 * spent on meaning rather than decoration: the green marks what is actionable, the
 * neutrals carry everything else, and the two supporting hues — a muted sand and a dusty
 * clay — sit low enough in chroma to read as warm greys until they are needed. What makes
 * this feel modern is restraint plus contrast, not more colour.
 *
 * The **full surface ramp is defined here**, not just the accents. Material's
 * `surfaceContainer*` roles default to its baseline purple-tinted neutrals when left
 * unset, and cards, sheets and menus all draw from them — leaving them out puts an
 * off-brand grey under every card in the app while the accents look correct.
 *
 * Tones follow the Material tonal convention (the number is roughly L*), so the light and
 * dark schemes are the same palette read from opposite ends.
 */

// --- Primary: evergreen. Desaturated on purpose; at 14sp it should read as ink with a
// --- cast of green rather than as a colour.
private val Pine10 = Color(0xFF072114)
private val Pine20 = Color(0xFF123526)
private val Pine30 = Color(0xFF1C4B37)
private val Pine40 = Color(0xFF2A654B)
private val Pine80 = Color(0xFF9CCFB3)
private val Pine90 = Color(0xFFBCE9CE)

// --- Secondary: sand. A warm grey, and barely more than that on purpose. Material spends
// --- `secondaryContainer` on the selected pill in the navigation bar, so any real
// --- saturation here puts a second brand colour on every screen, sitting under the tab
// --- the user is looking at and arguing with the green. At this chroma it reads as a
// --- highlight rather than as a colour.
private val Sand10 = Color(0xFF1C1A16)
private val Sand20 = Color(0xFF31302B)
private val Sand30 = Color(0xFF484640)
private val Sand40 = Color(0xFF605D55)
private val Sand80 = Color(0xFFCDCAC3)
private val Sand90 = Color(0xFFE9E7E1)

// --- Tertiary: clay. Used for the rare third signal; never for emphasis on its own.
private val Clay10 = Color(0xFF2A1512)
private val Clay20 = Color(0xFF422925)
private val Clay30 = Color(0xFF5B3E39)
private val Clay40 = Color(0xFF76564E)
private val Clay80 = Color(0xFFE3BDB4)
private val Clay90 = Color(0xFFFFDBD3)

// --- Error. The one place saturation is welcome: it has to interrupt.
private val Red10 = Color(0xFF410002)
private val Red20 = Color(0xFF690005)
private val Red30 = Color(0xFF93000A)
private val Red40 = Color(0xFFB3261E)
private val Red80 = Color(0xFFFFB4AB)
private val Red90 = Color(0xFFFFDAD6)

/*
 * Neutrals. Almost achromatic — a trace of the primary's hue keeps them from looking
 * blue next to the green, and nothing more than that. The steps between the container
 * tones are small so stacked surfaces separate without any of them looking tinted.
 */
private val LightSurface = Color(0xFFFCFDFC)
private val LightSurfaceContainerLowest = Color(0xFFFFFFFF)
private val LightSurfaceContainerLow = Color(0xFFF7F9F7)
private val LightSurfaceContainer = Color(0xFFF1F4F2)
private val LightSurfaceContainerHigh = Color(0xFFEBEEEC)
private val LightSurfaceContainerHighest = Color(0xFFE5E9E6)
private val LightSurfaceDim = Color(0xFFDBDFDC)
private val LightSurfaceVariant = Color(0xFFE2E7E3)
private val LightOnSurface = Color(0xFF121614)
private val LightOnSurfaceVariant = Color(0xFF414942)
private val LightOutline = Color(0xFF717A74)
private val LightOutlineVariant = Color(0xFFC5CEC7)

private val DarkSurface = Color(0xFF0B0F0D)
private val DarkSurfaceContainerLowest = Color(0xFF060908)
private val DarkSurfaceContainerLow = Color(0xFF121614)
private val DarkSurfaceContainer = Color(0xFF171B19)
private val DarkSurfaceContainerHigh = Color(0xFF212623)
private val DarkSurfaceContainerHighest = Color(0xFF2C312E)
private val DarkSurfaceBright = Color(0xFF313632)
private val DarkSurfaceVariant = Color(0xFF414942)
private val DarkOnSurface = Color(0xFFE1E4E0)
private val DarkOnSurfaceVariant = Color(0xFFBFC9C2)
private val DarkOutline = Color(0xFF8A938C)
private val DarkOutlineVariant = Color(0xFF414942)

internal val LightColors = lightColorScheme(
    primary = Pine40,
    onPrimary = Color.White,
    primaryContainer = Pine90,
    onPrimaryContainer = Pine10,
    secondary = Sand40,
    onSecondary = Color.White,
    secondaryContainer = Sand90,
    onSecondaryContainer = Sand10,
    tertiary = Clay40,
    onTertiary = Color.White,
    tertiaryContainer = Clay90,
    onTertiaryContainer = Clay10,
    error = Red40,
    onError = Color.White,
    errorContainer = Red90,
    onErrorContainer = Red10,
    background = LightSurface,
    onBackground = LightOnSurface,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    surfaceContainerLowest = LightSurfaceContainerLowest,
    surfaceContainerLow = LightSurfaceContainerLow,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    surfaceContainerHighest = LightSurfaceContainerHighest,
    surfaceDim = LightSurfaceDim,
    surfaceBright = LightSurface,
    surfaceTint = Pine40,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    inverseSurface = Color(0xFF2E322F),
    inverseOnSurface = Color(0xFFF0F2EE),
    inversePrimary = Pine80,
    scrim = Color(0xFF000000),
)

internal val DarkColors = darkColorScheme(
    primary = Pine80,
    onPrimary = Pine20,
    primaryContainer = Pine30,
    onPrimaryContainer = Pine90,
    secondary = Sand80,
    onSecondary = Sand20,
    secondaryContainer = Sand30,
    onSecondaryContainer = Sand90,
    tertiary = Clay80,
    onTertiary = Clay20,
    tertiaryContainer = Clay30,
    onTertiaryContainer = Clay90,
    error = Red80,
    onError = Red20,
    errorContainer = Red30,
    onErrorContainer = Red90,
    background = DarkSurface,
    onBackground = DarkOnSurface,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    surfaceContainerLowest = DarkSurfaceContainerLowest,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest,
    surfaceDim = DarkSurface,
    surfaceBright = DarkSurfaceBright,
    surfaceTint = Pine80,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    inverseSurface = DarkOnSurface,
    inverseOnSurface = Color(0xFF2C312E),
    inversePrimary = Pine40,
    scrim = Color(0xFF000000),
)
