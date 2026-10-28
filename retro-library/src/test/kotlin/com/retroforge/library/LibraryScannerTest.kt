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

        val retrobat = scanner.scan(dir("Retrobat", dir("emulators"), dir("roms", dir("nes", file("Super Mario Bros. (World).zip")))))
        assertEquals(RootLayout.COLLECTION, retrobat.layout)
        assertEquals("roms/nes/Super Mario Bros. (World).zip", retrobat.games.single().relativePath)
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
}
