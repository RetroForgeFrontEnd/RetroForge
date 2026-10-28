package com.retroforge.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StandaloneTest {
    private val all = StandaloneEmulators.bundled()
    private val vars = LaunchVars(
        romUri = "content://com.android.externalstorage.documents/tree/primary%3Aroms/document/primary%3Aroms%2Fpsx%2FGame.chd",
        romPath = "/storage/emulated/0/roms/psx/Game.chd",
        gameDirPath = "/storage/emulated/0/roms/psx",
        romsPath = "/storage/emulated/0/roms",
    )

    @Test
    fun bundledTableCoversMainSystems() {
        for (system in listOf("psx", "ps2", "psp", "gamecube", "wii", "3ds", "n64", "nds", "dreamcast", "saturn", "switch")) {
            assertTrue(system, all.optionsFor(system).isNotEmpty())
        }
        // One button per app and command, even where the source lists an app under two names.
        assertEquals(all.optionsFor("ps2").map { it.first.emulator to it.first.command }.distinct().size, all.optionsFor("ps2").size)
        assertTrue(all.emulators.values.all { it.page.startsWith("https://") && it.components.isNotEmpty() })
    }

    @Test
    fun modernEmulatorsCanBeAddedToObtainium() {
        for (id in listOf("DOLPHIN", "PPSSPP", "EDEN", "DUCKSTATION", "PLAY!", "NETHERSX2")) {
            val setup = all.emulators.getValue(id).obtainiumApp
            assertTrue(id, setup != null && setup.contains("\"url\""))
        }
    }

    @Test
    fun duckStation() {
        val (option, emu) = all.optionsFor("psx").first { it.second.name == "DuckStation" }
        val spec = LaunchCommand.parse(option.command, vars)
        assertEquals("com.github.stenzek.duckstation" to "com.github.stenzek.duckstation.EmulationActivity", emu.components.first())
        assertEquals(listOf(IntentExtra.Bool("resumeState", false), IntentExtra.Text("bootPath", vars.romUri)), spec.extras)
        assertTrue(spec.clearTask && spec.clearTop)
        assertEquals(setOf(vars.romUri), spec.grants)
    }

    @Test
    fun dolphinAndPpsspp() {
        val dolphin = LaunchCommand.parse(all.optionsFor("gamecube").first().first.command, vars)
        assertEquals("android.intent.action.MAIN", dolphin.action)
        assertEquals(listOf(IntentExtra.Text("AutoStartFile", vars.romUri)), dolphin.extras)
        val ppsspp = LaunchCommand.parse(all.optionsFor("psp").first().first.command, vars)
        assertEquals(vars.romUri, ppsspp.data)
        assertEquals("android.intent.action.VIEW", ppsspp.action)
    }

    @Test
    fun mameCommandLineWithPaths() {
        val (option, _) = all.optionsFor("amstradcpc").first()
        val spec = LaunchCommand.parse(option.command, vars)
        val cli = spec.extras.single { it.name == "cli_params" } as IntentExtra.Text
        assertEquals("-rompath '/storage/emulated/0/roms/psx;/storage/emulated/0/roms/amstradcpc' -flop1 '/storage/emulated/0/roms/psx/Game.chd'", cli.value)
        assertEquals("cpc6128", spec.data)
    }

    @Test(expected = UnsupportedCommandException::class)
    fun pathCommandsNeedLocalStorage() {
        val (option, _) = all.optionsFor("amstradcpc").first()
        LaunchCommand.parse(option.command, vars.copy(romPath = null, gameDirPath = null, romsPath = null))
    }
}
