package com.smsspam.detector.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * How the app decides between a light and a dark color scheme.
 * Persisted by id in [com.smsspam.detector.data.SettingsStore].
 */
enum class ThemeMode(val id: String, val label: String) {
    /** Follow the system's dark-theme setting (the default). */
    SYSTEM("system", "System"),

    /** Always use the light scheme. */
    LIGHT("light", "Light"),

    /** Always use the dark ("night") scheme. */
    DARK("dark", "Dark"),
    ;

    companion object {
        fun fromId(id: String?): ThemeMode = entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}

/**
 * The accent color palette. [DYNAMIC] follows the system Material You color
 * (Android 12+); every other entry is a fixed brand palette. Persisted by id
 * in [com.smsspam.detector.data.SettingsStore].
 */
enum class ThemePalette(
    val id: String,
    val label: String,
    /** Representative color shown as the swatch in Settings. */
    val swatch: Color,
) {
    DYNAMIC("dynamic", "System", Color(0xFF6750A4)),
    GREEN("green", "Green", Green40),
    BLUE("blue", "Blue", Blue40),
    PURPLE("purple", "Purple", Purple40),
    TEAL("teal", "Teal", Tealish40),
    ORANGE("orange", "Orange", Orange40),
    PINK("pink", "Pink", Pink40),
    ;

    companion object {
        fun fromId(id: String?): ThemePalette = entries.firstOrNull { it.id == id } ?: DYNAMIC
    }
}
