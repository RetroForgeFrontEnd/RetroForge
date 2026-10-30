package com.retroforge.app.player

import androidx.compose.ui.Alignment
import com.retroforge.player.Pad
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PadLayoutTest {
    private val systems = listOf(
        "nes", "fds", "gb", "gbc", "gba", "snes", "sfc", "nds", "megadrive", "genesis", "sega32x", "megacd", "saturn", "n64",
        "psp", "dreamcast", "naomi", "atomiswave", "virtualboy", "mastersystem", "sg1000", "gamegear", "pcengine", "fbneo",
        "neogeo", "cps1", "mame", "atari2600", "channelf", "vectrex", "pokemini", "gamecube", "gc", "wii", "3ds", "psx", "ps2",
        "some-unknown-system",
    )

    @Test
    fun everyLayoutHasFaceButtonsTheScreenCanDraw() {
        systems.forEach { system ->
            val layout = PadLayout.forSystem(system)
            // FaceButtons draws 4 as a diamond, 6 as two rows, anything else as one row; the landscape row fits 3.
            assertTrue(system, layout.face.size in setOf(1, 2, 3, 4, 6))
            assertTrue(system, layout.face.all { it.first.isNotBlank() })
            assertTrue(system, layout.shoulders.size <= 2 && layout.triggers.size <= 2)
        }
    }

    @Test
    fun fourButtonPadsPutEachButtonInItsOwnPlace() {
        systems.map { PadLayout.forSystem(it) to it }.filter { it.first.face.size == 4 }.forEach { (layout, system) ->
            assertEquals(system, 4, layout.faceSpots.distinct().size)
            assertTrue(system, Alignment.Center !in layout.faceSpots)
        }
    }

    @Test
    fun remappedButtonsStayWhereTheyAre() {
        val snes = PadLayout.forSystem("snes")
        // On-screen Y now presses X and A presses Start: they keep their places, they don't pile onto X or the centre.
        val mine = snes.remapped(mapOf("Y" to Pad.X, "A" to Pad.START))
        assertEquals(snes.faceSpots, mine.faceSpots)
        assertEquals(listOf("Y" to Pad.X, "X" to Pad.X, "B" to Pad.B, "A" to Pad.START), mine.face)
    }

    @Test
    fun remappedKeepsTheConsolesOwnPlaces() {
        val gameCube = PadLayout.forSystem("gamecube")
        val mine = gameCube.remapped(mapOf("A" to Pad.B, "B" to Pad.A, "Z" to Pad.L))
        assertEquals(gameCube.faceSpots, mine.faceSpots)
        assertEquals("Z" to Pad.L, mine.shoulders.single())
    }

    @Test
    fun noChoicesChangesNothing() {
        val psx = PadLayout.forSystem("psx")
        val same = psx.remapped(emptyMap())
        assertEquals(psx.face, same.face)
        assertEquals(psx.faceSpots, same.faceSpots)
    }

    @Test
    fun coreNamedButtonsBecomeAPad() {
        val layout = PadLayout.forSystem("doom", mapOf(Pad.B to "Fire", Pad.A to "Use", Pad.Y to "Strafe", Pad.L to "Previous weapon"))
        assertEquals(listOf("Strafe" to Pad.Y, "Fire" to Pad.B, "Use" to Pad.A), layout.face)
        assertEquals(listOf("Prev" to Pad.L), layout.shoulders)
        assertEquals(false, layout.hasSelect)
    }
}
