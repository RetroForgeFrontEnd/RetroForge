package com.retroforge.library.manage

import com.retroforge.library.SystemRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.random.Random

class Z3dsTest {

    @get:Rule val tmp = TemporaryFolder()

    /** A small cartridge image: NCSD header with one NCCH partition at 0x4000, some random data, then padding. */
    private fun cartridge(): ByteArray {
        val cart = ByteArray(0x9_3210)
        val b = ByteBuffer.wrap(cart).order(ByteOrder.LITTLE_ENDIAN)
        "NCSD".toByteArray().copyInto(cart, 0x100)
        b.putInt(0x120, 0x4000 / 0x200)
        "NCCH".toByteArray().copyInto(cart, 0x4100)
        cart[0x418F] = 4 // NoCrypto: decrypted
        b.putInt(0x4104, 0x400).putShort(0x4112, 3).putLong(0x4118, 0x0004000000055D00L)
        Random(5).nextBytes(cart, 0x4200, 0x30000)
        cart.fill(0xFF.toByte(), 0x60000, cart.size)
        return cart
    }

    @Test
    fun roundTripsACartridge() {
        val cart = cartridge()
        val src = tmp.newFile("Game.3ds").apply { writeBytes(cart) }
        val out = tmp.newFile("Game.zcci")
        assertEquals(Z3ds.Kind.NCSD, Z3ds.kindOf(cart))
        val written = RandomAccessFile(out, "rw").channel.use { o -> RandomAccessFile(src, "r").channel.use { Z3ds.write(o, it, cart.size.toLong(), Z3ds.Kind.NCSD) } }
        assertEquals(out.length(), written)
        assertTrue("Z3DS is ${out.length()} bytes", out.length() < 0x40000)
        val head = out.readBytes()
        assertEquals("Z3DSNCSD", String(head, 0, 8))
        assertTrue(String(head, 0x20, 0x80, Charsets.ISO_8859_1).contains("titleinfo"))
        RandomAccessFile(out, "r").channel.use { Z3dsVerifier.verify(it, src.inputStream()) }
        val damaged = cart.copyOf().also { it[0x50000] = 1 }
        try {
            RandomAccessFile(out, "r").channel.use { Z3dsVerifier.verify(it, damaged.inputStream()) }
            fail("a different file passed the check")
        } catch (_: IOException) {
        }
    }

    @Test
    fun romManagerCompresses3dsGames() {
        val root = tmp.newFolder("roms")
        File(root, "3ds").mkdirs()
        File(root, "3ds/Game.3ds").writeBytes(cartridge())
        File(root, "3ds/Odd.3ds").writeBytes(ByteArray(0x1000))
        File(root, "3ds/Locked.3ds").writeBytes(cartridge().also { it[0x418F] = 0 })
        val files = JvmCollectionFiles(root)
        val games = listOf(
            CompressCandidate("3ds", "3ds/Game.3ds", "3ds", "Game", 0x9_3210),
            CompressCandidate("3ds", "3ds/Odd.3ds", "3ds", "Odd", 0x1000),
            CompressCandidate("3ds", "3ds/Locked.3ds", "3ds", "Locked", 0x9_3210),
        )
        val (jobs, skipped) = CompressPlanner.plan(games, files, SystemRules.bundled())
        assertEquals(listOf("3ds/Game.zcci"), jobs.map { it.output })
        assertEquals(listOf("3ds/Odd.3ds", "3ds/Locked.3ds"), skipped.map { it.path })
        assertTrue(skipped[1].reason.startsWith("Encrypted"))
        val result = Compressor(files).run(jobs, Journal(OperationLog("1", "compress", 1, 0, "t")) {})
        assertEquals(emptyList<String>(), result.problems)
        assertTrue(File(root, "3ds/Game.zcci").exists())
        assertTrue(File(root, "_originals/3ds/Game.3ds").exists())
    }

    /** Opt-in: RETROFORGE_Z3DS_TEST names a 3DS file, RETROFORGE_Z3DS_OUT a scratch output. */
    @Test
    fun convertScratchFile() {
        val src = System.getenv("RETROFORGE_Z3DS_TEST")?.let(::File)
        val out = System.getenv("RETROFORGE_Z3DS_OUT")?.let(::File)
        assumeTrue(src != null && src.isFile && out != null)
        out!!.delete()
        val kind = RandomAccessFile(src, "r").channel.use { Z3ds.kindOf(Rvz.read(it, 0, 0x200)) }!!
        val start = System.nanoTime()
        RandomAccessFile(out, "rw").channel.use { o -> RandomAccessFile(src, "r").channel.use { Z3ds.write(o, it, src!!.length(), kind) } }
        val secs = (System.nanoTime() - start) / 1e9
        RandomAccessFile(out, "r").channel.use { Z3dsVerifier.verify(it, src!!.inputStream()) }
        println("Z3DS ${src!!.name}: ${src.length()} -> ${out.length()} bytes in %.1fs, checked".format(secs))
    }
}
