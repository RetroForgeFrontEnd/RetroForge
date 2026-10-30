package com.retroforge.library

import org.junit.Assert.assertEquals
import org.junit.Test

class ArcadeNamesTest {
    @Test
    fun `arcade short names get their full titles`() {
        assertEquals("Street Fighter II: The World Warrior", ArcadeNames.named("fbneo", "sf2.zip", RomNames.parse("sf2.zip")).title)
        assertEquals("Metal Slug - Super Vehicle-001", ArcadeNames.named("neogeo", "mslug.zip", RomNames.parse("mslug.zip")).title)
    }

    @Test
    fun `other systems and full names are left alone`() {
        assertEquals("sf2", ArcadeNames.named("snes", "sf2.zip", RomNames.parse("sf2.zip")).title)
        val full = RomNames.parse("Street Fighter II (USA).zip")
        assertEquals(full, ArcadeNames.named("fbneo", "Street Fighter II (USA).zip", full))
        assertEquals("unknownset", ArcadeNames.named("mame", "unknownset.zip", RomNames.parse("unknownset.zip")).title)
    }
}
