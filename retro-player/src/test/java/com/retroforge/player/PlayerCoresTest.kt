package com.retroforge.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlayerCoresTest {
    @Test
    fun cartridgeGamesUseTheBuiltInPlayer() {
        assertEquals("mgba", PlayerCores.forGame("gba", "Pokemon Emerald.gba")?.id)
        assertEquals("mgba", PlayerCores.forGame("gba", "Advance Wars.zip")?.id)
        assertEquals("snes9x", PlayerCores.forGame("snes", "Super Metroid.sfc")?.id)
        assertEquals("desmume", PlayerCores.forGame("nds", "Mario Kart DS.nds")?.id)
    }

    @Test
    fun discsAndUnknownSystemsDoNot() {
        assertNull(PlayerCores.forGame("pcengine", "Ys Book I & II.cue"))
        assertNull(PlayerCores.forGame("psx", "Crash.chd"))
        assertNull(PlayerCores.forGame("unknown", "game.bin"))
    }
}
