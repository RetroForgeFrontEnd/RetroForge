package com.retroforge.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RomNamesTest {

    @Test
    fun noIntroNames() {
        val n = RomNames.parse("Legend of Zelda, The - A Link to the Past (USA) (Rev 1).sfc")
        assertEquals("The Legend of Zelda - A Link to the Past", n.title)
        assertEquals(listOf("USA"), n.regions)
        assertEquals("Rev 1", n.revision)
        assertEquals("legend of zelda a link to the past", n.sortKey)

        val multi = RomNames.parse("Sonic The Hedgehog (USA, Europe).md")
        assertEquals(listOf("USA", "Europe"), multi.regions)
    }

    @Test
    fun goodToolsTagsFromTheUsersCollection() {
        val n = RomNames.parse("Sonic The Hedgehog (W) (REV00) [!].zip")
        assertEquals("Sonic The Hedgehog", n.title)
        assertEquals(listOf("World"), n.regions)
        assertTrue(RomFlag.VERIFIED in n.flags)

        val kirby = RomNames.parse("Kirby - Nightmare in Dreamland (U) [!].zip")
        assertEquals(listOf("USA"), kirby.regions)
    }

    @Test
    fun flags() {
        assertTrue(RomFlag.BETA in RomNames.parse("Game (USA) (Beta).zip").flags)
        assertTrue(RomFlag.PROTO in RomNames.parse("Game (Japan) (Proto 2).zip").flags)
        assertTrue(RomFlag.HACK in RomNames.parse("Game (Hack).zip").flags)
        assertTrue(RomFlag.HACK in RomNames.parse("Game [h1C].zip").flags)
        assertTrue(RomFlag.TRANSLATION in RomNames.parse("Game (J) [T+Eng1.0].zip").flags)
        assertTrue(RomFlag.BAD_DUMP in RomNames.parse("Game [b1].zip").flags)
    }

    @Test
    fun plainNamesWithoutTags() {
        val n = RomNames.parse("DonkeyKongClassic (Shiru).smc")
        assertEquals("DonkeyKongClassic", n.title)
        assertEquals(listOf("Shiru"), n.tags)
        assertEquals("100 Rooms of Enemies", RomNames.parse("100 Rooms of Enemies.zip").title)
        assertEquals("'the", RomNames.parse("'the.smc").title)
    }

    @Test
    fun patchNameInBracketsIsAHack() {
        val hack = RomNames.parse("Super Mario Bros. (World) [Hard Mode].nes")
        assertEquals("Hard Mode", hack.hackName)
        assertTrue(RomFlag.HACK in hack.flags)
        assertEquals(null, RomNames.parse("Super Mario Bros. (World) [!].nes").hackName)
        assertEquals(null, RomNames.parse("Game (USA) [b1].nes").hackName)
        assertEquals(null, RomNames.parse("Game (Japan) [T+Eng1.0].nes").hackName)
    }

    @Test
    fun catalogueTagsAreNotHacks() {
        for (name in listOf(
            "Xenon 2 (1989)(Image Works)[cr Elite].st", "Barbarian (1987)(Psygnosis)[cr Replicants][t +2].st",
            "Aleste (1988)(Compile)(JP)[GoodMSX].rom", "Zork (1983)(Infocom)[CPM version].dsk",
            "Blazing Chrome [0100C2700C252000][v0].nsp", "Game (1990)(Pub)[one disk].st", "Elite (1984)(Acornsoft)[bootfile].ssd",
            "Game (Europe) [Disc 1].chd", "Game [SLUS-01234].chd", "Game (1986)(Pub)[aka Other Name].st", "Game (19xx)[req joystick].d88",
            "Game (USA) [Europe].zip", "Game (1990)(Pub)[a2][f NTSC].st", "Game (1990)(Pub)[tr Fre].dsk",
        )) assertEquals(name, null, RomNames.parse(name).hackName)
        assertTrue(RomFlag.HACK in RomNames.parse("Game (1990)(Pub)[h TSTH].st").flags)
    }
}
