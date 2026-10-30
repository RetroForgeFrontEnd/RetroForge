package com.retroforge.library.manage

import com.retroforge.library.SystemRules
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import kotlin.random.Random

class RvzTest {

    @get:Rule val tmp = TemporaryFolder()

    /** Fills [disc] from [from] to [to] with junk the way mastering tools do: a fresh seed for every 32 KiB block. */
    private fun junk(disc: ByteArray, from: Int, to: Int, random: Random) {
        var at = from
        while (at < to) {
            val blockStart = at / Rvz.BLOCK * Rvz.BLOCK
            val end = minOf(to, blockStart + Rvz.BLOCK)
            val lfg = Lfg()
            lfg.setSeed(IntArray(Lfg.SEED_WORDS) { random.nextInt() })
            lfg.skip((at - blockStart).toLong())
            lfg.fill(disc, at, end - at)
            at = end
        }
    }

    /** A small GameCube image: header, a file table with two files, junk around them and a block of zeros. */
    private fun disc(): ByteArray {
        val random = Random(3)
        val size = 0xA6000
        val disc = ByteArray(size)
        "GTEST01".toByteArray().copyInto(disc)
        ByteBuffer.wrap(disc).putInt(0x1C, 0xC2339F3D.toInt()).putInt(0x424, 0x2000).putInt(0x428, 36)
        val fst = ByteBuffer.wrap(disc, 0x2000, 36)
        fst.putInt(0x01000000).putInt(0).putInt(3)
        fst.putInt(0).putInt(0x11000).putInt(0x5123)
        fst.putInt(0).putInt(0x30000).putInt(0x9000)
        random.nextBytes(disc, 0x11000, 0x16123)
        random.nextBytes(disc, 0x30000, 0x39000)
        junk(disc, 0x16140, 0x30000, random)
        junk(disc, 0x39000, 0x60000, random)
        // 0x60000-0x80000 stays zero (a whole group); junk again to the end.
        junk(disc, 0x80000, size, random)
        return disc
    }

    @Test
    fun lfgFindsTheSeedOfJunk() {
        val data = ByteArray(Rvz.BLOCK)
        junk(data, 0x100, Rvz.BLOCK, Random(9))
        val seed = IntArray(Lfg.SEED_WORDS)
        assertEquals(Rvz.BLOCK - 0x101, Lfg.findSeed(data, 0x101, Rvz.BLOCK - 0x101, 0x101, seed))
        val again = ByteArray(Rvz.BLOCK - 0x100)
        Lfg().apply { setSeed(seed); skip(0x100) }.fill(again, 0, again.size)
        assertArrayEquals(data.copyOfRange(0x100, Rvz.BLOCK), again)
    }

    @Test
    fun roundTripsAGameCubeDisc() {
        val iso = disc()
        val isoFile = tmp.newFile("Game.iso").apply { writeBytes(iso) }
        val rvzFile = tmp.newFile("Game.rvz")
        val written = RandomAccessFile(rvzFile, "rw").channel.use { out ->
            RandomAccessFile(isoFile, "r").channel.use { input -> Rvz.write(out, input, iso.size.toLong()) }
        }
        assertEquals(rvzFile.length(), written)
        // The random file data doesn't shrink; nearly everything else is junk or zeros and should.
        assertTrue("RVZ is ${rvzFile.length()} bytes", rvzFile.length() < 0x18000)
        RandomAccessFile(rvzFile, "r").channel.use { RvzVerifier.verify(it, isoFile.inputStream()) }

        val damaged = iso.copyOf().also { it[0x45000] = (it[0x45000] + 1).toByte() }
        try {
            RandomAccessFile(rvzFile, "r").channel.use { RvzVerifier.verify(it, damaged.inputStream()) }
            fail("a different disc passed the check")
        } catch (_: IOException) {
        }
    }

    @Test
    fun romManagerConvertsGameCubeAndLeavesWii() {
        val root = tmp.newFolder("roms")
        File(root, "gamecube").mkdirs()
        File(root, "wii").mkdirs()
        File(root, "gamecube/Game.iso").writeBytes(disc())
        File(root, "wii/Other.iso").writeBytes(ByteArray(0x40000).also { ByteBuffer.wrap(it).putInt(0x18, 0x5D1C9EA3) })
        val files = JvmCollectionFiles(root)
        val games = listOf(
            CompressCandidate("gamecube", "gamecube/Game.iso", "gamecube", "Game", 0xA6000),
            CompressCandidate("wii", "wii/Other.iso", "wii", "Other", 0x40000),
        )
        val (jobs, skipped) = CompressPlanner.plan(games, files, SystemRules.bundled())
        assertEquals(listOf(CompressFormat.RVZ), jobs.map { it.format })
        assertEquals(listOf("wii/Other.iso"), skipped.map { it.path })
        val result = Compressor(files).run(jobs, Journal(OperationLog("1", "compress", 1, 0, "t")) {})
        assertEquals(emptyList<String>(), result.problems)
        assertTrue(File(root, "gamecube/Game.rvz").exists())
        assertTrue(File(root, "_originals/gamecube/Game.iso").exists())
    }
}
