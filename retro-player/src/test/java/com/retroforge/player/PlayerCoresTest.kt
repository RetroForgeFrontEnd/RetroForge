package com.retroforge.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PlayerCoresTest {
    @get:Rule val temp = TemporaryFolder()

    @Test
    fun cartridgeGamesUseTheBuiltInPlayer() {
        assertEquals("mgba", PlayerCores.forGame("gba", "Pokemon Emerald.gba")?.id)
        assertEquals("mgba", PlayerCores.forGame("gba", "Advance Wars.zip")?.id)
        assertEquals("snes9x", PlayerCores.forGame("snes", "Super Metroid.sfc")?.id)
        assertEquals("desmume", PlayerCores.forGame("nds", "Mario Kart DS.nds")?.id)
        // .bin is a cartridge dump on cartridge systems, not a disc track.
        assertEquals(PlayerCores.forSystem("megadrive")?.id, PlayerCores.forGame("megadrive", "Sonic the Hedgehog.bin")?.id)
        assertEquals(PlayerCores.forSystem("atari2600")?.id, PlayerCores.forGame("atari2600", "Adventure.bin")?.id)
    }

    @Test
    fun discsAndUnknownSystemsDoNot() {
        assertNull(PlayerCores.forGame("pcengine", "Ys Book I & II.cue"))
        assertNull(PlayerCores.forGame("unknown", "game.bin"))
    }

    @Test
    fun playStationDiscsUseTheBuiltInPlayer() {
        assertEquals("pcsx_rearmed", PlayerCores.forGame("psx", "Crash.chd")?.id)
        assertEquals("pcsx_rearmed", PlayerCores.forGame("psx", "Final Fantasy VII (Disc 1).cue")?.id)
        assertEquals(0x205, PlayerCores.portDevice("pcsx_rearmed"))
    }

    @Test
    fun resolutionsRunFromNativeUpwardsWhateverTheDefault() {
        // Mupen64Plus-Next's plain option list: its default, 640x480, comes first.
        assertEquals(
            listOf("320x240", "640x480", "960x720", "1280x960"),
            PlayerCores.resolutionChoices("mupen64plus_next_gles3", listOf("640x480", "320x240", "960x720", "1280x960")),
        )
        // Flycast starts at the Dreamcast's own 640x480, not its smaller 320x240.
        assertEquals(
            listOf("640x480", "800x600", "960x720"),
            PlayerCores.resolutionChoices("flycast", listOf("640x480", "320x240", "800x600", "960x720")),
        )
        assertEquals(listOf("1x (Native)", "2x", "10x"), PlayerCores.resolutionChoices("citra", listOf("1x (Native)", "2x", "10x")))
        // PCSX ReARMed's Enhanced Resolution: off (native), then on (double).
        assertEquals(listOf("disabled", "enabled"), PlayerCores.resolutionChoices("pcsx_rearmed", listOf("enabled", "disabled")))
        assertEquals("pcsx_rearmed_neon_enhancement_enable", PlayerCores.resolutionOption("pcsx_rearmed")?.first)
        // PCSX ReARMed only doubles from the next start, so a new resolution restarts the game.
        assertTrue(PlayerCores.resolutionAtStart("pcsx_rearmed"))
    }

    @Test
    fun aChosenEmulatorIsUsedOnlyWhenTheSystemHasIt() {
        assertEquals("gpsp", PlayerCores.forGame("gba", "Advance Wars.gba", preferred = "gpsp")?.id)
        assertEquals("mgba", PlayerCores.forGame("gba", "Advance Wars.gba", preferred = "snes9x")?.id)
    }

    @Test
    fun choicesAreRememberedInAFile() {
        val file = File(temp.root, "player/cores.properties")
        CoreChoices(file).set("gba", "gpsp")
        assertEquals("gpsp", CoreChoices(file).get("gba"))
        CoreChoices(file).set("gba", null)
        assertNull(CoreChoices(file).get("gba"))
    }
}
