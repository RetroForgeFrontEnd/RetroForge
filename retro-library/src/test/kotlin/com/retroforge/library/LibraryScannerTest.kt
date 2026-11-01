package com.retroforge.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryScannerTest {

    private val rules = SystemRules.bundled()
    private val scanner = LibraryScanner(rules)

    private val gamelist = """
        <?xml version="1.0"?>
        <gameList>
          <game id="19196" source="ScreenScraper.fr">
            <path>./Star Fox (USA).zip</path>
            <name>Star Fox</name>
            <desc>Fly the Arwing.</desc>
            <image>./mixart/Star Fox (USA).png</image>
            <releasedate>19930301T000000</releasedate>
            <developer>Nintendo</developer>
            <rating>0.8</rating>
          </game>
          <game><path>./SMW HACKS/Kaizo.smc</path><name>Kaizo Mario</name></game>
          <game><path>./Missing Game (USA).zip</path><name>Not on disk</name></game>
        </gameList>
    """.trimIndent()

    private fun retrobatLikeRoms() = dir(
        "roms",
        dir(
            "snes",
            file("gamelist.xml", gamelist),
            file("gamelist.xml.old"),
            file("_info.txt"),
            file("Star Fox (USA).zip"),
            file("Super Mario World (USA).zip"),
            file("Super Mario World (USA).7z"),
            file("Legend of Zelda, The - A Link to the Past (USA).sfc"),
            file("notes.pdf"),
            dir("mixart", file("Star Fox (USA).png"), file("Super Mario World (USA).png")),
            dir("videos", file("Star Fox (USA).mp4")),
            dir("SMW HACKS", file("Kaizo.smc"), file("Readme.txt")),
            dir("SNES HACKS", file("DKC Mania.zip")),
        ),
        dir(
            "psx",
            file("Final Fantasy VII (USA).m3u", "Final Fantasy VII (USA) (Disc 1).chd\nFinal Fantasy VII (USA) (Disc 2).chd\nFinal Fantasy VII (USA) (Disc 3).chd\n"),
            file("Final Fantasy VII (USA) (Disc 1).chd"),
            file("Final Fantasy VII (USA) (Disc 2).chd"),
            file("Final Fantasy VII (USA) (Disc 3).chd"),
            file("Crash Bandicoot (USA).cue", "FILE \"Crash Bandicoot (USA).bin\" BINARY\n  TRACK 01 MODE2/2352\n"),
            file("Crash Bandicoot (USA).bin"),
            file("Spyro (USA).chd"),
        ),
        dir("not-a-system", file("whatever.zip")),
        MemNode("nes", kids = emptyList(), denied = true),
    )

    @Test
    fun scansARetroBatStyleCollection() {
        val result = scanner.scan(retrobatLikeRoms())
        assertEquals(RootLayout.COLLECTION, result.layout)
        assertEquals(listOf("not-a-system"), result.unrecognisedFolders)

        val snes = result.games.filter { it.system == "snes" }.map { it.relativePath }.sorted()
        assertEquals(
            listOf(
                "snes/Legend of Zelda, The - A Link to the Past (USA).sfc",
                "snes/SMW HACKS/Kaizo.smc",
                "snes/SNES HACKS/DKC Mania.zip",
                "snes/Star Fox (USA).zip",
                "snes/Super Mario World (USA).7z",
                "snes/Super Mario World (USA).zip",
            ),
            snes,
        )
    }

    /** A param.sfo with UTF-8 text entries, as PS4 dumps have. */
    private fun paramSfo(vararg entries: Pair<String, String>): ByteArray {
        val keys = java.io.ByteArrayOutputStream()
        val data = java.io.ByteArrayOutputStream()
        val index = java.nio.ByteBuffer.allocate(entries.size * 16).order(java.nio.ByteOrder.LITTLE_ENDIAN)
        for ((key, value) in entries) {
            val bytes = value.toByteArray() + 0
            index.putShort(keys.size().toShort()).putShort(0x0204).putInt(bytes.size).putInt(bytes.size).putInt(data.size())
            keys.write(key.toByteArray() + 0)
            data.write(bytes)
        }
        val keyStart = 20 + entries.size * 16
        val header = java.nio.ByteBuffer.allocate(20).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            .putInt(0x46535000).putInt(0x101).putInt(keyStart).putInt(keyStart + keys.size()).putInt(entries.size)
        return header.array() + index.array() + keys.toByteArray() + data.toByteArray()
    }

    @Test
    fun ps4GamesAreDumpedFoldersWithTheirTitleFromParamSfo() {
        val sfo = paramSfo("CATEGORY" to "gd", "TITLE" to "Sonic Mania", "TITLE_ID" to "CUSA07023")
        val roms = dir(
            "roms",
            dir(
                "ps4",
                dir("CUSA07023", file("eboot.bin"), dir("sce_sys", binary("param.sfo", sfo))),
                dir("CUSA07023-UPDATE", file("eboot.bin"), dir("sce_sys")),
                dir("Bloodborne", file("EBOOT.BIN"), dir("sce_sys")),
                dir("Some DLC", dir("sce_sys", file("param.sfo"))),
            ),
        )
        val games = scanner.scan(roms).games.filter { it.system == "ps4" }.sortedBy { it.relativePath }
        assertEquals(listOf("ps4/Bloodborne", "ps4/CUSA07023"), games.map { it.relativePath })
        assertEquals("Sonic Mania", games.single { it.fileName == "CUSA07023" }.displayTitle)
        assertEquals("Bloodborne", games.single { it.fileName == "Bloodborne" }.displayTitle)
    }

    @Test
    fun ps5GamesAreDumpedFoldersWithTheirTitleFromParamJson() {
        val param = """
            {"contentId": "XX0000-PPSA99999_00-0000000000000000", "titleId": "PPSA99999",
             "localizedParameters": {"defaultLanguage": "en-GB", "en-US": {"titleName": "Test Game US"},
                                     "en-GB": {"titleName": "Test Game"}}}
        """.trimIndent()
        val roms = dir(
            "roms",
            dir(
                "ps5",
                dir("PPSA99999", file("eboot.bin"), dir("sce_sys", file("param.json", param))),
                dir("PPSA99999-patch", file("eboot.bin"), dir("sce_sys")),
                dir("PPSA99998", file("eboot.bin"), dir("sce_sys", file("param.json", "not json"))),
                dir("Not a game", dir("sce_sys", file("param.json", param))),
            ),
        )
        val games = scanner.scan(roms).games.filter { it.system == "ps5" }.sortedBy { it.relativePath }
        assertEquals(listOf("ps5/PPSA99998", "ps5/PPSA99999"), games.map { it.relativePath })
        assertEquals("Test Game", games.single { it.fileName == "PPSA99999" }.displayTitle)
        assertEquals("PPSA99998", games.single { it.fileName == "PPSA99998" }.displayTitle)
    }

    @Test
    fun paramSfoReadsTextAndNumbersAndShrugsOffJunk() {
        val sfo = ParamSfo.parse(paramSfo("TITLE" to "Gravity Rush", "TITLE_ID" to "CUSA01113"))
        assertEquals("Gravity Rush", sfo["TITLE"])
        assertEquals("CUSA01113", sfo["TITLE_ID"])
        assertTrue(ParamSfo.parse(ByteArray(10)).isEmpty())
        assertTrue(ParamSfo.parse("not a param.sfo at all, just text".toByteArray()).isEmpty())
    }

    @Test
    fun deniedFolderIsReportedAndTheRestStillScans() {
        val result = scanner.scan(retrobatLikeRoms())
        assertTrue(result.problems.any { it.path == "nes" && "permission denied" in it.message })
        assertTrue(result.games.isNotEmpty())
    }

    @Test
    fun gamelistMetadataAndArtworkAreAttached() {
        val starFox = scanner.scan(retrobatLikeRoms()).games.single { it.fileName == "Star Fox (USA).zip" }
        val meta = assertNotNull(starFox.metadata).let { starFox.metadata!! }
        assertEquals("Star Fox", starFox.displayTitle)
        assertEquals("1993-03-01", meta.releaseDate)
        assertEquals("Nintendo", meta.developer)
        assertEquals(0.8f, meta.rating)
        assertNotNull(starFox.imageNodeId)
        // A ROM without a gamelist entry falls back to its file name.
        val zelda = scanner.scan(retrobatLikeRoms()).games.single { it.fileName.startsWith("Legend") }
        assertNull(zelda.metadata)
        assertEquals("The Legend of Zelda - A Link to the Past", zelda.displayTitle)
    }

    @Test
    fun hackFoldersAreRecognised() {
        val games = scanner.scan(retrobatLikeRoms()).games
        assertEquals("SMW HACKS", games.single { it.fileName == "Kaizo.smc" }.hackFolder)
        assertEquals("Kaizo Mario", games.single { it.fileName == "Kaizo.smc" }.displayTitle)
        assertEquals("SNES HACKS", games.single { it.fileName == "DKC Mania.zip" }.hackFolder)
        assertTrue(games.single { it.fileName == "Star Fox (USA).zip" }.hackFolder == null)
    }

    @Test
    fun playlistsHideTheirDiscsAndTracks() {
        val psx = scanner.scan(retrobatLikeRoms()).games.filter { it.system == "psx" }
        assertEquals(listOf("Crash Bandicoot (USA).cue", "Final Fantasy VII (USA).m3u", "Spyro (USA).chd"), psx.map { it.fileName }.sorted())
        assertEquals(3, psx.single { it.fileName.endsWith(".m3u") }.discs)
    }

    @Test
    fun zipAnd7zCopiesOfTheSameGameAreMarkedAsDuplicates() {
        val games = scanner.scan(retrobatLikeRoms()).games
        val smw = games.filter { it.name.title == "Super Mario World" }
        assertEquals(2, smw.size)
        assertTrue(smw.all { it.duplicateKey != null } && smw.map { it.duplicateKey }.distinct().size == 1)
        assertNull(games.single { it.fileName == "Star Fox (USA).zip" }.duplicateKey)
    }

    @Test
    fun singleSystemFolderAndParentOfRomsAreUnderstood() {
        val snesOnly = scanner.scan(dir("snes", file("Star Fox (USA).zip")))
        assertEquals(RootLayout.SINGLE_SYSTEM, snesOnly.layout)
        assertEquals("Star Fox (USA).zip", snesOnly.games.single().relativePath)

        // RetroBat's own "screenshots" folder is named like a system; its roms folder must still win.
        val retrobat = scanner.scan(dir("Retrobat", dir("emulators"), dir("screenshots", file("shot.png")), dir("roms", dir("nes", file("Super Mario Bros. (World).zip")))))
        assertEquals(RootLayout.COLLECTION, retrobat.layout)
        assertEquals("roms/nes/Super Mario Bros. (World).zip", retrobat.games.single().relativePath)
    }

    @Test
    fun systemFoldersOneLevelDownAreFound() {
        // A PC's "Games" share holding a collection folder, as on a network drive.
        val result = scanner.scan(
            dir("Games", dir("Ps Vita"), dir("RetroForge test", dir("snes", file("Super Mario World (USA).zip")), dir("psx", file("Batman Forever (USA).chd")))),
        )
        assertEquals(RootLayout.COLLECTION, result.layout)
        assertEquals(
            listOf("RetroForge test/psx/Batman Forever (USA).chd", "RetroForge test/snes/Super Mario World (USA).zip"),
            result.games.map { it.relativePath }.sorted(),
        )
        assertTrue(result.problems.isEmpty()) // the nested system folders' zip and chd aren't "loose" too
    }

    @Test
    fun foldersNamedLikeGamesAreOneGameAndNotEntered() {
        val result = scanner.scan(
            dir(
                "dos",
                dir("XCOM - UFO Defense.pc", dir("EXTRAS", file("BIN.ZIP")), file("UFO.EXE")),
                file("Doom (1993).zip"),
            ),
        )
        assertEquals(listOf("Doom (1993).zip", "XCOM - UFO Defense.pc"), result.games.map { it.fileName }.sorted())
        assertEquals("XCOM - UFO Defense", result.games.single { it.fileName.endsWith(".pc") }.displayTitle)
    }

    @Test
    fun unrelatedFolderIsReportedNotSilentlyEmpty() {
        val result = scanner.scan(dir("Photos", file("cat.jpg")))
        assertEquals(RootLayout.UNRECOGNISED, result.layout)
        assertTrue(result.games.isEmpty())
        assertTrue(result.problems.single().message.startsWith("No system folders found"))
    }

    @Test
    fun scanReadsOnlyGamelistsAndPlaylists() {
        val rom = file("Star Fox (USA).zip")
        scanner.scan(dir("snes", rom, file("gamelist.xml", "<gameList/>")))
        assertEquals(0, rom.reads)
    }

    @Test
    fun cancellationStopsTheScan() {
        var calls = 0
        val result = runCatching { scanner.scan(retrobatLikeRoms(), isCancelled = { ++calls > 3 }) }
        assertTrue(result.exceptionOrNull() is ScanCancelled)
    }

    @Test
    fun gamelistWithDoctypeIsRefusedButScanContinues() {
        val evil = """<?xml version="1.0"?><!DOCTYPE x [<!ENTITY e SYSTEM "file:///etc/passwd">]><gameList><game><path>./A.zip</path><name>&e;</name></game></gameList>"""
        val result = scanner.scan(dir("snes", file("gamelist.xml", evil), file("A.zip")))
        assertEquals("A", result.games.single().displayTitle)
        assertTrue(result.problems.single().message.startsWith("Couldn't read gamelist.xml"))
    }

    @Test
    fun findsLooseGamesByFileType() {
        val root = dir(
            "Downloads",
            file("Pokemon Emerald (USA).gba"),
            file("Something.zip"),
            dir("My stuff", file("Super Mario 64 (USA).z64"), file("holiday.jpg")),
        )
        val result = scanner.scan(root)
        assertEquals(setOf("gba" to "Pokemon Emerald (USA).gba", "n64" to "Super Mario 64 (USA).z64"), result.games.map { it.system to it.fileName }.toSet())
        assertEquals("My stuff/Super Mario 64 (USA).z64", result.games.first { it.system == "n64" }.relativePath)
        assertTrue(result.problems.any { it.message.contains("could be for several systems") })
        assertTrue(result.problems.none { it.message.startsWith("No system folders found") })
    }

    @Test
    fun looseGamesBesideSystemFolders() {
        val root = dir("roms", dir("snes", file("Star Fox (USA).sfc")), dir("New stuff", file("Tetris (World).gb")))
        val result = scanner.scan(root)
        assertEquals(setOf("snes", "gb"), result.games.map { it.system }.toSet())
        assertTrue("New stuff" !in result.unrecognisedFolders)
    }
}

