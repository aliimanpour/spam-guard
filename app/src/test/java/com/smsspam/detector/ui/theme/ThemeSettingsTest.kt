package com.smsspam.detector.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeSettingsTest {

    @Test
    fun themeModeFromIdFallsBackToSystem() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromId(null))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromId("nonsense"))
        assertEquals(ThemeMode.DARK, ThemeMode.fromId("dark"))
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromId("light"))
    }

    @Test
    fun paletteFromIdFallsBackToDynamic() {
        assertEquals(ThemePalette.DYNAMIC, ThemePalette.fromId(null))
        assertEquals(ThemePalette.DYNAMIC, ThemePalette.fromId("nonsense"))
        assertEquals(ThemePalette.GREEN, ThemePalette.fromId("green"))
        assertEquals(ThemePalette.PINK, ThemePalette.fromId("pink"))
    }

    @Test
    fun idsAreUnique() {
        assertEquals(ThemeMode.entries.size, ThemeMode.entries.map { it.id }.toSet().size)
        assertEquals(ThemePalette.entries.size, ThemePalette.entries.map { it.id }.toSet().size)
    }
}
