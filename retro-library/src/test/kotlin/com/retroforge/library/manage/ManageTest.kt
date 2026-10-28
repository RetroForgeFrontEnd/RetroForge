package com.retroforge.library.manage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ManageTest {

    @get:Rule val tmp = TemporaryFolder()

    private val gamelist = """
        |<?xml version="1.0"?>
        |<gameList>
        |	<game id="1">
        |		<path>./Super Mario World (USA).7z</path>
        |		<name>Super Mario World</name>
        |	</game>
        |	<game>
        |		<path>./Super Mario World (USA).zip</path>
        |		<name>Super Mario World</name>
        |	</game>
        |	<game>
        |		<path>./Tom &amp; Jerry (USA).7z</path>
        |	</game>
        |</gameList>
        |""".trimMargin()

    @Test
    fun gamelistRemovesEntryWhenKeptCopyHasOne() {
        val (out, change) = GamelistEditor.retarget(gamelist, "Super Mario World (USA).7z", "Super Mario World (USA).zip")
        assertTrue(change is TextChange.RemovedEntry)
        assertFalse(out.contains(".7z</path>\n\t\t<name>Super"))
        assertEquals(gamelist, GamelistEditor.undo(out, change!!))
    }

    @Test
    fun gamelistRepointsEntryWhenKeptCopyHasNone() {
        val (out, change) = GamelistEditor.retarget(gamelist, "Tom & Jerry (USA).7z", "Tom & Jerry (USA).zip")
        assertEquals(TextChange.PathChanged("./Tom &amp; Jerry (USA).7z", "./Tom &amp; Jerry (USA).zip"), change)
        assertTrue(out.contains("<path>./Tom &amp; Jerry (USA).zip</path>"))
        assertEquals(gamelist.length + 1, out.length) // only the extension changed
        assertEquals(gamelist, GamelistEditor.undo(out, change!!))
    }

    @Test
    fun gamelistLeavesUnknownGamesAlone() {
        assertNull(GamelistEditor.retarget(gamelist, "Nope.zip", "Nope.7z").second)
    }

    @Test
    fun playlistRetargetsDiscLines() {
        val m3u = "Resident Evil 2 (Disc 1).cue\r\n.\\discs\\Resident Evil 2 (Disc 2).cue\r\n"
        val (out, changes) = PlaylistEditor.retarget(m3u, "discs/Resident Evil 2 (Disc 2).cue", "discs/Resident Evil 2 (Disc 2).chd")
        assertEquals("Resident Evil 2 (Disc 1).cue\r\n.\\discs\\Resident Evil 2 (Disc 2).chd\r\n".replace(".\\discs", "discs"), out.replace(".\\discs", "discs").replace("./discs", "discs"))
        assertEquals(1, changes.size)
        assertEquals(m3u, PlaylistEditor.undo(out, changes.single() as TextChange.LineChanged))
    }

    private fun cand(path: String, size: Long = 100, key: String = "k", art: Boolean = true) =
        DuplicateCandidate("snes", path, "snes", "Game", size, key, hasMetadata = art, hasArt = art)

    @Test
    fun plannerSkipsCompanionsAndSameNames() {
        val (sets, skipped) = DuplicatePlanner.plan(
            listOf(
                cand("snes/A.7z", key = "a"), cand("snes/A.zip", key = "a"),
                cand("megadrive-msu/B/B.cue", key = "b"), cand("megadrive-msu/B/B.md", key = "b"),
                cand("quake2/baseq2/pak0.pak", key = "c"), cand("quake2/rogue/pak0.pak", key = "c"),
                cand("_duplicates/snes/D.7z", key = "d"), cand("snes/D.zip", key = "d"),
            ),
        )
        assertEquals(listOf(listOf("snes/A.7z", "snes/A.zip")), sets.map { s -> s.files.map { it.relativePath } })
        assertEquals(2, skipped.size)
    }

    @Test
    fun keepRules() {
        val set = DuplicateSet("snes", "A", listOf(cand("snes/A.7z", 90), cand("snes/A.zip", 100), cand("snes/sub/A.sfc", 500)))
        assertEquals("snes/sub/A.sfc", set.keep(KeepRule.MOST_COMPATIBLE).relativePath)
        assertEquals("snes/A.7z", set.keep(KeepRule.SMALLEST).relativePath)
        val withArt = DuplicateSet("snes", "A", listOf(cand("snes/A.7z", 90, art = false), cand("snes/A.zip", 100)))
        assertEquals("snes/A.zip", withArt.keep(KeepRule.SMALLEST).relativePath)
    }

    @Test
    fun fingerprintMatchesRawAndZipped() {
        val rom = ByteArray(5000) { (it * 7).toByte() }
        val zip = ByteArrayOutputStream().also { b ->
            ZipOutputStream(b).use { z -> z.putNextEntry(ZipEntry("A.sfc")); z.write(rom); z.putNextEntry(ZipEntry("readme.txt")); z.write(1) }
        }.toByteArray()
        val raw = ContentFingerprint.of("A.sfc", rom.size.toLong()) { rom.inputStream() }
        val zipped = ContentFingerprint.of("A.zip", zip.size.toLong()) { zip.inputStream() }
        assertEquals(raw, zipped)
        val other = ContentFingerprint.of("B.sfc", 10) { ByteArray(10).inputStream() }
        assertEquals(Sameness.SAME_DATA, ContentFingerprint.sameness(listOf(raw, zipped)))
        assertEquals(Sameness.DIFFERENT_DATA, ContentFingerprint.sameness(listOf(raw, other)))
    }

    @Test
    fun moveDuplicatesThenUndo() {
        val root = tmp.newFolder("roms")
        File(root, "snes").mkdirs()
        File(root, "snes/Super Mario World (USA).7z").writeText("7z")
        File(root, "snes/Super Mario World (USA).zip").writeText("zip")
        File(root, "snes/gamelist.xml").writeText(gamelist)
        val files = JvmCollectionFiles(root)
        val logs = mutableListOf<OperationLog>()
        val journal = Journal(OperationLog("1", "duplicates", 1, 0, "test")) { logs += it }
        val a = DuplicateCandidate("snes", "snes/Super Mario World (USA).7z", "snes", "SMW", 2, "k", true, true)
        val b = a.copy(relativePath = "snes/Super Mario World (USA).zip", size = 3)
        val set = DuplicateSet("snes", "SMW", listOf(a, b))

        val result = DuplicateMover(files).run(listOf(set to b), journal)
        assertEquals(1, result.moved)
        assertEquals(1, result.gamelistsUpdated)
        assertTrue(File(root, "_duplicates/snes/Super Mario World (USA).7z").isFile)
        assertFalse(File(root, "snes/Super Mario World (USA).7z").exists())
        assertEquals(gamelist, File(root, "snes/gamelist.xml$BACKUP_SUFFIX").readText())
        assertFalse(File(root, "snes/gamelist.xml").readText().contains("World (USA).7z"))

        // The log round-trips through JSON and undoes exactly.
        val saved = OperationLog.decode(logs.last().encode())
        assertEquals(emptyList<String>(), Undo.run(files, saved))
        assertTrue(File(root, "snes/Super Mario World (USA).7z").isFile)
        assertEquals(gamelist, File(root, "snes/gamelist.xml").readText())
    }

    @Test
    fun undoNeverOverwrites() {
        val root = tmp.newFolder("r")
        File(root, "_duplicates/snes").mkdirs()
        File(root, "_duplicates/snes/A.7z").writeText("old")
        File(root, "snes").mkdirs()
        File(root, "snes/A.7z").writeText("new")
        val log = OperationLog("1", "duplicates", 1, 0, "t", listOf(Step.Moved("snes/A.7z", "_duplicates/snes/A.7z")))
        assertEquals(1, Undo.run(JvmCollectionFiles(root), log).size)
        assertEquals("new", File(root, "snes/A.7z").readText())
    }

    @Test
    fun cueTracks() {
        val cue = "FILE \"T1.bin\" BINARY\n  TRACK 01 MODE2/2352\n    INDEX 01 00:00:00\nFILE \"T2.bin\" BINARY\n  TRACK 02 AUDIO\n    INDEX 00 00:00:00\n    INDEX 01 00:02:00\n"
        val tracks = CueSheet.tracks(cue) { if (it == "T1.bin") 2352L * 1000 else 2352L * 300 }
        assertEquals(listOf(1000, 300), tracks.map { it.frames })
        assertEquals(listOf(0, 150), tracks.map { it.pregap })
        assertEquals("TRACK:2 TYPE:AUDIO SUBTYPE:NONE FRAMES:300 PREGAP:150 PGTYPE:VAUDIO PGSUB:NONE POSTGAP:0\u0000", String(Chd.cdTrackMetadata(1, tracks[1]).data))
        assertEquals((1000L + 300) * Chd.CD_FRAME, CueSheet.logicalBytes(tracks))
    }
}

class MultiDiscTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test
    fun plansPlaylists() {
        val root = tmp.newFolder("roms")
        File(root, "psx").mkdirs()
        File(root, "psx/Taken.m3u").writeText("x")
        fun g(path: String) = CompressCandidate(path.substringBefore('/'), path, path.substringBefore('/'), nameOf(path).substringBefore(" ("), 1)
        val games = listOf(
            g("psx/Command & Conquer (USA) (Disc 1) (GDI).chd"), g("psx/Command & Conquer (USA) (Disc 2) (NOD).chd"),
            g("psx/Gap (USA) (Disc 1).chd"), g("psx/Gap (USA) (Disc 3).chd"),
            g("psx/Taken (Disc 1).chd"), g("psx/Taken (Disc 2).chd"),
            g("amstradcpc/Bard's Tale (1988)(Electronic Arts)(Disk 2 of 2).zip"), g("amstradcpc/Bard's Tale (1988)(Electronic Arts)(Disk 1 of 2).zip"),
            g("snes/Not Multi (USA) (Disc 1).sfc"), g("snes/Not Multi (USA) (Disc 2).sfc"),
        )
        val (jobs, skipped) = MultiDiscPlanner.plan(games, JvmCollectionFiles(root), com.retroforge.library.SystemRules.bundled())
        assertEquals(
            listOf(
                PlaylistJob("amstradcpc", "Bard's Tale", "amstradcpc/Bard's Tale (1988)(Electronic Arts).m3u",
                    listOf("Bard's Tale (1988)(Electronic Arts)(Disk 1 of 2).zip", "Bard's Tale (1988)(Electronic Arts)(Disk 2 of 2).zip")),
                PlaylistJob("psx", "Command & Conquer", "psx/Command & Conquer (USA).m3u",
                    listOf("Command & Conquer (USA) (Disc 1) (GDI).chd", "Command & Conquer (USA) (Disc 2) (NOD).chd")),
            ),
            jobs,
        )
        assertEquals(setOf("Disc 2 is missing.", "Taken.m3u already exists."), skipped.map { it.reason }.toSet())

        PlaylistMaker(JvmCollectionFiles(root)).run(jobs.takeLast(1), Journal(OperationLog("1", "playlists", 1, 0, "t")) {})
        assertEquals("Command & Conquer (USA) (Disc 1) (GDI).chd\nCommand & Conquer (USA) (Disc 2) (NOD).chd\n", File(root, "psx/Command & Conquer (USA).m3u").readText())
    }
}
