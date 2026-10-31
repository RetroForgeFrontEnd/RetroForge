package com.retroforge.library.manage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class IdentifyTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun crc(b: ByteArray) = CRC32().also { it.update(b) }.value
    private fun hex(v: Long) = "%08X".format(v)

    private val gameData = ByteArray(32768) { (it * 7).toByte() }
    private val track1 = ByteArray(2352 * 20) { (it % 251).toByte() }

    private fun dat() = DatParser.parse(
        """
        clrmamepro (
        	name "Test - System"
        	version "2026.10.07"
        )

        game (
        	name "Space Game (USA)"
        	rom ( name "Space Game (USA).nes" size ${gameData.size} crc ${hex(crc(gameData))} sha1 0000 )
        )
        game (
        	name "Disc Game (Europe)"
        	rom ( name "Disc Game (Europe) (Track 01).bin" size ${track1.size} crc ${hex(crc(track1))} )
        )
        """.trimIndent(),
    )

    @Test fun readsBothListFormats() {
        val list = dat()
        assertEquals("Test - System", list.name)
        assertEquals("2026.10.07", list.version)
        assertEquals("Space Game (USA).nes", list.find(crc(gameData), gameData.size.toLong())?.fileName)
        val xml = DatParser.parse(
            """<?xml version="1.0"?><datafile><header><name>X</name><version>1</version></header>
            <game name="Tom &amp; Jerry (USA)"><rom name="Tom &amp; Jerry (USA).gb" size="4" crc="0a0b0c0d" sha1="00"/></game></datafile>""",
        )
        assertEquals("Tom & Jerry (USA).gb", xml.find(0x0A0B0C0DL, 4)?.fileName)
        assertEquals("Tom & Jerry (USA)", xml.roms.single().game)
    }

    @Test fun leavesOutAnInesHeaderAndFindsGamesInZips() {
        val root = tmp.newFolder()
        val files = JvmCollectionFiles(root)
        File(root, "nes").mkdirs()
        File(root, "nes/space.nes").writeBytes("NES\u001A".toByteArray() + ByteArray(12) + gameData)
        ZipOutputStream(File(root, "nes/zipped.zip").outputStream()).use { z -> z.putNextEntry(ZipEntry("whatever.nes")); z.write(gameData); z.closeEntry() }
        File(root, "nes/hack.nes").writeBytes(ByteArray(100))
        val list = dat()
        fun check(path: String) = Identifier.identify(IdentifyCandidate("nes", path, "nes", path), list, { RomChecksum.of(files, it, "nes") }, { "" })
        val plain = check("nes/space.nes")
        assertEquals(IdentifyOutcome.RENAME, plain.outcome)
        assertEquals("nes/Space Game (USA).nes", plain.newPath)
        assertEquals("nes/Space Game (USA).zip", check("nes/zipped.zip").newPath)
        assertEquals(IdentifyOutcome.UNKNOWN, check("nes/hack.nes").outcome)
        assertEquals(IdentifyOutcome.SKIPPED, check("nes/game.chd").outcome)
    }

    @Test fun summsN64GamesInTheListsByteOrder() {
        val z64 = ByteArray(4096) { it.toByte() }.also { it[0] = 0x80.toByte(); it[1] = 0x37; it[2] = 0x12; it[3] = 0x40 }
        val v64 = z64.copyOf().also { b -> for (i in b.indices step 2) { val t = b[i]; b[i] = b[i + 1]; b[i + 1] = t } }
        val sums = RomChecksum.sums(v64.inputStream(), v64.size.toLong(), "n64", null) { false }
        assertTrue(sums.any { it.crc == crc(z64) })
    }

    @Test fun renamesACueSetAndUndoPutsItBack() {
        val root = tmp.newFolder()
        val files = JvmCollectionFiles(root)
        File(root, "psx").mkdirs()
        File(root, "psx/disc (t1).bin").writeBytes(track1)
        File(root, "psx/disc (t2).bin").writeBytes(ByteArray(2352))
        File(root, "psx/disc.cue").writeText("FILE \"disc (t1).bin\" BINARY\r\n  TRACK 01 MODE2/2352\r\nFILE \"disc (t2).bin\" BINARY\r\n  TRACK 02 AUDIO\r\n")
        File(root, "psx/gamelist.xml").writeText("<gameList><game><path>./disc.cue</path><name>Disc</name></game></gameList>")
        File(root, "psx/Disc.m3u").writeText("disc.cue\n")
        val c = IdentifyCandidate("psx", "psx/disc.cue", "psx", "Disc")
        val found = Identifier.identify(c, dat(), { RomChecksum.of(files, it, "psx") }, { File(root, it).readText() })
        assertEquals(IdentifyOutcome.RENAME, found.outcome)
        assertEquals("psx/Disc Game (Europe).cue", found.newPath)

        var log = OperationLog("t", "rename", 1, 0, "")
        val journal = Journal(log) { log = it }
        val result = Renamer(files).run(listOf(found), journal) { false }
        assertEquals(emptyList<String>(), result.problems)
        assertTrue(File(root, "psx/Disc Game (Europe) (Track 01).bin").exists())
        assertTrue(File(root, "psx/Disc Game (Europe) (Track 02).bin").exists())
        val cue = File(root, "psx/Disc Game (Europe).cue").readText()
        assertTrue(cue, cue.contains("FILE \"Disc Game (Europe) (Track 02).bin\" BINARY\r\n"))
        assertTrue(File(root, "psx/gamelist.xml").readText().contains("./Disc Game (Europe).cue"))
        assertEquals("Disc Game (Europe).cue\n", File(root, "psx/Disc.m3u").readText())

        assertEquals(emptyList<String>(), Undo.run(files, log))
        assertTrue(File(root, "psx/disc (t1).bin").exists())
        assertEquals("FILE \"disc (t1).bin\" BINARY\r\n  TRACK 01 MODE2/2352\r\nFILE \"disc (t2).bin\" BINARY\r\n  TRACK 02 AUDIO\r\n", File(root, "psx/disc.cue").readText())
        assertTrue(File(root, "psx/gamelist.xml").readText().contains("./disc.cue"))
    }

    @Test fun aSecondCopyOfTheSameGameIsLeftAlone() {
        val a = Identified(IdentifyCandidate("nes", "nes/a.nes", "nes", "a"), IdentifyOutcome.RENAME, "G", "nes/G.nes", listOf("nes/a.nes" to "nes/G.nes"))
        val b = a.copy(candidate = a.candidate.copy(relativePath = "nes/b.nes"), renames = listOf("nes/b.nes" to "nes/G.nes"))
        val out = Identifier.resolveClashes(listOf(a, b)) { false }
        assertEquals(IdentifyOutcome.RENAME, out[0].outcome)
        assertEquals(IdentifyOutcome.SKIPPED, out[1].outcome)
    }

    @Test fun shortNamesLeaveOutRevisionsUnlessTwoVersionsMeet() {
        fun named(path: String) = Identified(IdentifyCandidate("gba", path, "gba", "g"), IdentifyOutcome.NAMED_RIGHT, "g")
        fun rename(from: String, to: String) = Identified(IdentifyCandidate("gba", from, "gba", "g"), IdentifyOutcome.RENAME, "g", to, listOf(from to to))
        val out = Identifier.withoutRevisions(
            listOf(
                named("gba/Advance Wars (USA) (Rev 1).zip"),
                rename("gba/fr.zip", "gba/Fire Red (USA, Europe) (Rev 1).zip"),
                named("gba/Ruby (USA) (Rev 2).zip"),
                named("gba/Ruby (USA).zip"),
                named("gba/Emerald (USA, Europe).zip"),
            ),
        ) { false }
        assertEquals("gba/Advance Wars (USA).zip", out[0].newPath)
        assertEquals(listOf("gba/Advance Wars (USA) (Rev 1).zip" to "gba/Advance Wars (USA).zip"), out[0].renames)
        assertEquals("gba/Fire Red (USA, Europe).zip", out[1].newPath)
        // Two versions of Ruby would both be "Ruby (USA)": both keep their names.
        assertEquals(IdentifyOutcome.NAMED_RIGHT, out[2].outcome)
        assertEquals(IdentifyOutcome.NAMED_RIGHT, out[3].outcome)
        assertEquals(IdentifyOutcome.NAMED_RIGHT, out[4].outcome)
        assertEquals("Game (Japan) (Beta).nes", Identifier.withoutRevision("Game (Japan) (v1.1) (Beta).nes"))
    }

    @Test fun trackNamesFollowTheList() {
        assertEquals(listOf("G (Track 1).bin", "G (Track 2).bin"), Identifier.trackNames("G", "G (Track 1).bin", 2))
        assertEquals(listOf("G.bin"), Identifier.trackNames("G", "G.bin", 1))
        assertEquals(listOf("G.bin"), Identifier.trackNames("G", "G (Track 01).bin", 1))
    }
}
