package com.retroforge.library.manage

import com.retroforge.library.SystemRules
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.RandomAccessFile
import java.util.zip.ZipFile
import kotlin.random.Random

class CompressorTest {

    @get:Rule val tmp = TemporaryFolder()
    private val rules = SystemRules.bundled()

    private fun collection(): File {
        val root = tmp.newFolder("roms")
        File(root, "psx/discs").mkdirs()
        File(root, "snes").mkdirs()
        val random = Random(7)
        File(root, "psx/discs/Game (Disc 1).bin").writeBytes(random.nextBytes(2352 * 40))
        File(root, "psx/discs/Game (Disc 1) (Track 2).bin").writeBytes(ByteArray(2352 * 20) { (it % 13).toByte() })
        File(root, "psx/discs/Game (Disc 1).cue").writeText(
            "FILE \"Game (Disc 1).bin\" BINARY\n  TRACK 01 MODE2/2352\n    INDEX 01 00:00:00\n" +
                "FILE \"Game (Disc 1) (Track 2).bin\" BINARY\n  TRACK 02 AUDIO\n    INDEX 00 00:00:00\n    INDEX 01 00:00:10\n",
        )
        File(root, "psx/Game.m3u").writeText("discs/Game (Disc 1).cue\n")
        File(root, "snes/Rom (USA).sfc").writeBytes(ByteArray(64 * 1024) { (it / 100).toByte() })
        File(root, "snes/gamelist.xml").writeText("<gameList>\n\t<game>\n\t\t<path>./Rom (USA).sfc</path>\n\t</game>\n</gameList>\n")
        return root
    }

    private val games = listOf(
        CompressCandidate("psx", "psx/Game.m3u", "psx", "Game", 24),
        CompressCandidate("snes", "snes/Rom (USA).sfc", "snes", "Rom", 64 * 1024),
        CompressCandidate("snes", "snes/Already (USA).zip", "snes", "Already", 10),
        CompressCandidate("megadrive-msu", "megadrive-msu/X/X.md", "megadrive-msu", "X", 10),
    )

    @Test
    fun compressesThenUndoes() {
        val root = collection()
        val files = JvmCollectionFiles(root)
        val (jobs, skipped) = CompressPlanner.plan(games, files, rules)
        assertEquals(listOf(CompressFormat.CD_CHD, CompressFormat.ZIP), jobs.map { it.format })
        assertEquals(3, jobs[0].inputs.size)
        assertEquals(listOf("megadrive-msu/X/X.md"), skipped.map { it.path })

        val sfc = File(root, "snes/Rom (USA).sfc").readBytes()
        var log: OperationLog? = null
        val result = Compressor(files).run(jobs, Journal(OperationLog("1", "compress", 1, 0, "t")) { log = it })
        assertEquals(emptyList<String>(), result.problems)
        assertEquals(2, result.converted)
        assertEquals(chdAndZipSize(root), result.bytesAfter)

        val chd = File(root, "psx/discs/Game (Disc 1).chd")
        RandomAccessFile(chd, "r").channel.use { ChdVerifier.verify(it) }
        assertEquals("discs/Game (Disc 1).chd\n", File(root, "psx/Game.m3u").readText())
        assertTrue(File(root, "_originals/psx/discs/Game (Disc 1) (Track 2).bin").isFile)
        assertFalse(File(root, "psx/discs/Game (Disc 1).cue").exists())
        ZipFile(File(root, "snes/Rom (USA).zip")).use { z -> assertArrayEquals(sfc, z.getInputStream(z.getEntry("Rom (USA).sfc")).readBytes()) }
        assertTrue(File(root, "snes/gamelist.xml").readText().contains("<path>./Rom (USA).zip</path>"))
        assertFalse(root.walk().any { it.name.endsWith(PARTIAL_SUFFIX) })

        assertEquals(emptyList<String>(), Undo.run(files, OperationLog.decode(log!!.encode())))
        assertArrayEquals(sfc, File(root, "snes/Rom (USA).sfc").readBytes())
        assertTrue(File(root, "psx/discs/Game (Disc 1).cue").isFile)
        assertEquals("discs/Game (Disc 1).cue\n", File(root, "psx/Game.m3u").readText())
        assertTrue(File(root, "snes/gamelist.xml").readText().contains("<path>./Rom (USA).sfc</path>"))
        // The new files aren't deleted: they're set aside.
        assertTrue(File(root, "_duplicates/psx/discs/Game (Disc 1).chd").isFile)
        assertFalse(File(root, "snes/Rom (USA).zip").exists())
    }

    private fun chdAndZipSize(root: File) = File(root, "psx/discs/Game (Disc 1).chd").length() + File(root, "snes/Rom (USA).zip").length()

    @Test
    fun failedConversionLeavesOriginalsAlone() {
        val root = collection()
        File(root, "psx/discs/Game (Disc 1) (Track 2).bin").writeBytes(ByteArray(100)) // not whole sectors
        val files = JvmCollectionFiles(root)
        val (jobs, skipped) = CompressPlanner.plan(games.take(1), files, rules)
        assertTrue(jobs.isEmpty())
        assertTrue(skipped.single().reason.contains("whole number of sectors"))
    }
}
