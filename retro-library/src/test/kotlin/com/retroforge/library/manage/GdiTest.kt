package com.retroforge.library.manage

import com.retroforge.library.SystemRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.RandomAccessFile
import kotlin.random.Random

class GdiTest {

    @get:Rule val tmp = TemporaryFolder()

    private val sheet = "3\r\n1 0 4 2352 track01.bin 0\r\n2 600 0 2352 \"track 02.raw\" 0\r\n3 45000 4 2048 track03.iso 0\r\n"

    @Test
    fun laysOutTracksLikeChdman() {
        val sizes = mapOf("track01.bin" to 450L * 2352, "track 02.raw" to 300L * 2352, "track03.iso" to 1000L * 2048)
        val tracks = GdiSheet.tracks(sheet) { sizes[it] }
        assertEquals(listOf(TrackType.MODE1_RAW, TrackType.AUDIO, TrackType.MODE1), tracks.map { it.type })
        // Each gap becomes padding at the end of the track before it, so track 3 starts at 45000.
        assertEquals(listOf(600, 45000 - 600, 1000), tracks.map { it.frames })
        assertEquals(listOf(150, 45000 - 900, 0), tracks.map { it.padFrames })
        assertEquals("TRACK:2 TYPE:AUDIO SUBTYPE:NONE FRAMES:44400 PAD:44100 PREGAP:0 PGTYPE:MODE1 PGSUB:NONE POSTGAP:0\u0000",
            String(Chd.gdTrackMetadata(1, tracks[1]).data, Charsets.US_ASCII))
    }

    @Test
    fun parityIsRebuiltExactly() {
        // A mode 1 sector: sync, address, data, then parity made by the generator; clearing and rebuilding gives it back.
        val sector = ByteArray(2352)
        Random(2).nextBytes(sector, 16, 2064)
        sector[15] = 1
        CdEcc.generate(sector, 0)
        assertTrue(CdEcc.verify(sector, 0))
        val copy = sector.copyOf()
        CdEcc.clear(copy, 0)
        CdEcc.generate(copy, 0)
        assertTrue(copy.contentEquals(sector))
        sector[100] = (sector[100] + 1).toByte()
        assertTrue(!CdEcc.verify(sector, 0))
    }

    @Test
    fun romManagerCompressesGdiAndLeavesCdi() {
        val root = tmp.newFolder("roms")
        val dc = File(root, "dreamcast/Game").apply { mkdirs() }
        File(dc, "Game.gdi").writeText(sheet)
        val random = Random(4)
        File(dc, "track01.bin").writeBytes(ByteArray(450 * 2352).also { b ->
            for (f in 0 until 450) { random.nextBytes(b, f * 2352 + 16, f * 2352 + 2064); b[f * 2352 + 15] = 1; CdEcc.generate(b, f * 2352) }
        })
        File(dc, "track 02.raw").writeBytes(ByteArray(300 * 2352) { (it % 7).toByte() })
        File(dc, "track03.iso").writeBytes(ByteArray(1000 * 2048) { (it / 2048).toByte() })
        File(root, "dreamcast/Other.cdi").writeBytes(ByteArray(4096))
        val files = JvmCollectionFiles(root)
        val games = listOf(
            CompressCandidate("dreamcast", "dreamcast/Game/Game.gdi", "dreamcast", "Game", 1),
            CompressCandidate("dreamcast", "dreamcast/Other.cdi", "dreamcast", "Other", 4096),
        )
        val (jobs, skipped) = CompressPlanner.plan(games, files, SystemRules.bundled())
        assertEquals(listOf(CompressFormat.GD_CHD), jobs.map { it.format })
        assertEquals(4, jobs[0].inputs.size)
        assertEquals(listOf("dreamcast/Other.cdi"), skipped.map { it.path })
        val result = Compressor(files).run(jobs, Journal(OperationLog("1", "compress", 1, 0, "t")) {})
        assertEquals(emptyList<String>(), result.problems)
        val chd = File(root, "dreamcast/Game/Game.chd")
        assertTrue(chd.exists())
        // Rebuildable parity is left out, so the random data track shrinks by about its parity.
        assertTrue("CHD is ${chd.length()} bytes", chd.length() < 450L * 2352 - 450L * 250)
        RandomAccessFile(chd, "r").channel.use { ChdVerifier.verify(it) }
        assertTrue(File(root, "_originals/dreamcast/Game/track 02.raw").exists())
    }
}
