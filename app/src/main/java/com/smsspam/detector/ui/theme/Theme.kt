package com.smsspam.detector.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColors = lightColorScheme(
    primary = Green40,
    secondary = GreenGrey40,
    tertiary = Teal40,
    error = Red40,
)

private val DarkColors = darkColorScheme(
    primary = Green80,
    secondary = GreenGrey80,
    tertiary = Teal80,
    error = Red80,
)

private val BlueLight = lightColorScheme(
    primary = Blue40,
    secondary = BlueGrey40,
    tertiary = Cyan40,
    error = Red40,
)

private val BlueDark = darkColorScheme(
    primary = Blue80,
    secondary = BlueGrey80,
    tertiary = Cyan80,
    error = Red80,
)

private val PurpleLight = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pinkish40,
    error = Red40,
)

private val PurpleDark = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pinkish80,
    error = Red80,
)

private val TealLight = lightColorScheme(
    primary = Tealish40,
    secondary = TealGrey40,
    tertiary = Indigo40,
    error = Red40,
)

private val TealDark = darkColorScheme(
    primary = Tealish80,
    secondary = TealGrey80,
    tertiary = Indigo80,
    error = Red80,
)

private val OrangeLight = lightColorScheme(
    primary = Orange40,
    secondary = OrangeGrey40,
    tertiary = Olive40,
    error = Red40,
)

private val OrangeDark = darkColorScheme(
    primary = Orange80,
    secondary = OrangeGrey80,
    tertiary = Olive80,
    error = Red80,
)

private val PinkLight = lightColorScheme(
    primary = Pink40,
    secondary = PinkGrey40,
    tertiary = Rose40,
    error = Red40,
)

private val PinkDark = darkColorScheme(
    primary = Pink80,
    secondary = PinkGrey80,
    tertiary = Rose80,
    error = Red80,
)

/** Light color scheme for a fixed [ThemePalette]; [ThemePalette.DYNAMIC] falls back to green. */
private fun ThemePalette.lightScheme(): ColorScheme = when (this) {
    ThemePalette.DYNAMIC, ThemePalette.GREEN -> LightColors
    ThemePalette.BLUE -> BlueLight
    ThemePalette.PURPLE -> PurpleLight
    ThemePalette.TEAL -> TealLight
    ThemePalette.ORANGE -> OrangeLight
    ThemePalette.PINK -> PinkLight
}

/** Dark color scheme for a fixed [ThemePalette]; [ThemePalette.DYNAMIC] falls back to green. */
private fun ThemePalette.darkScheme(): ColorScheme = when (this) {
    ThemePalette.DYNAMIC, ThemePalette.GREEN -> DarkColors
    ThemePalette.BLUE -> BlueDark
    ThemePalette.PURPLE -> PurpleDark
    ThemePalette.TEAL -> TealDark
    ThemePalette.ORANGE -> OrangeDark
    ThemePalette.PINK -> PinkDark
}

/**
 * Turns a dark scheme into a "pure dark" one for AMOLED panels: the window
 * background and surface become true black, while the container levels keep a
 * slight lift so cards and sheets stay legible.
 */
private fun ColorScheme.pureBlack(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = Color(0xFF070707),
    surfaceContainer = Color(0xFF0C0C0C),
    surfaceContainerHigh = Color(0xFF141414),
    surfaceContainerHighest = Color(0xFF1B1B1B),
)

@Composable
fun SpamGuardTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    palette: ThemePalette = ThemePalette.DYNAMIC,
    pureDark: Boolean = false,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val dynamic = palette == ThemePalette.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val context = LocalContext.current
    val base = when {
        dynamic -> if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> palette.darkScheme()
        else -> palette.lightScheme()
    }
    val colorScheme = if (darkTheme && pureDark) base.pureBlack() else base

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content,
    )
}
