package dev.wakin.microuter

import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeCatalogTest {
    @Test
    fun catalogExposesTheElevenRequestedPalettesInDisplayOrder() {
        assertEquals(
            listOf(
                "green",
                "teal",
                "cyan",
                "blue",
                "indigo",
                "purple",
                "lavender",
                "rose",
                "orange",
                "sand",
                "lime",
            ),
            ThemePalette.entries.map { it.storageKey },
        )
    }

    @Test
    fun storedLegacyPaletteNamesRemainReadable() {
        assertEquals(ThemePalette.Blue, ThemePalette.fromStoredValue("blue"))
        assertEquals(ThemePalette.Purple, ThemePalette.fromStoredValue("PURPLE"))
        assertEquals(ThemePalette.Green, ThemePalette.fromStoredValue("green"))
        assertEquals(ThemePalette.Orange, ThemePalette.fromStoredValue("orange"))
        assertEquals(ThemePalette.Rose, ThemePalette.fromStoredValue("rose"))
    }

    @Test
    fun unknownStoredPaletteFallsBackToBlue() {
        assertEquals(ThemePalette.Blue, ThemePalette.fromStoredValue("unknown"))
    }
}
