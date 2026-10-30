package com.retroforge.app.player

import com.retroforge.app.EnglishStrings
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Before
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PlaytimeTest {
    @Before
    fun englishTexts() = EnglishStrings.install()

    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun addsUpPerGame() {
        val playtime = Playtime(File(tmp.root, "player/playtime.properties"))
        playtime.add("snes", "Super Mario World (USA).zip", 90_000)
        playtime.add("snes", "Super Mario World (USA).zip", 30_000)
        playtime.add("nes", "Other.nes", 5_000)
        assertEquals(120_000, playtime.get("snes", "Super Mario World (USA).zip"))
        assertEquals(0, playtime.get("gba", "Missing.gba"))
    }

    @Test
    fun formats() {
        assertEquals("under a minute", Playtime.format(59_000))
        assertEquals("12 min", Playtime.format(12 * 60_000L))
        assertEquals("2 h 5 min", Playtime.format(125 * 60_000L))
    }
}
