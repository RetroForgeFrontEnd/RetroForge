package com.retroforge.library.manage

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class HeaderFixTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun gameBoy(): ByteArray = ByteArray(0x8000) { (it * 13).toByte() }.also { b ->
        b[0x14D] = HeaderFixer.gbHeader(b)
        HeaderFixer.gbGlobal(b).let { b[0x14E] = (it shr 8).toByte(); b[0x14F] = it.toByte() }
    }

    private fun megaDrive(): ByteArray = ByteArray(0x4000) { (it * 7).toByte() }.also { b ->
        "SEGA".forEachIndexed { i, c -> b[0x100 + i] = c.code.toByte() }
        HeaderFixer.mdChecksum(b).let { b[0x18E] = (it shr 8).toByte(); b[0x18F] = it.toByte() }
    }

    @Test fun findsAndFixesEachProblem() {
        val gb = gameBoy()
        assertEquals(emptyList<HeaderProblem>(), HeaderFixer.problems(gb, "gb", "a.gb"))
        val hacked = gb.copyOf().also { it[0x134] = 'X'.code.toByte() }
        val found = HeaderFixer.problems(hacked, "gb", "a.gb")
        assertTrue(HeaderProblem.GB_HEADER_CHECKSUM in found)
        assertEquals(emptyList<HeaderProblem>(), HeaderFixer.problems(HeaderFixer.fix(hacked, found), "gb", "a.gb"))

        val md = megaDrive().also { it[0x300] = 1 }
        assertEquals(listOf(HeaderProblem.MD_CHECKSUM), HeaderFixer.problems(md, "megadrive", "a.md"))
        assertEquals(emptyList<HeaderProblem>(), HeaderFixer.problems(HeaderFixer.fix(md, listOf(HeaderProblem.MD_CHECKSUM)), "megadrive", "a.md"))

        val snes = ByteArray(512 + 0x8000) { if (it < 512) 0 else 5 }
        val stripped = HeaderFixer.fix(snes, HeaderFixer.problems(snes, "snes", "a.smc"))
        assertEquals(0x8000, stripped.size)

        val nes = "NES\u001A".toByteArray() + byteArrayOf(2, 1, 0x10) + "DiskDude!".toByteArray() + ByteArray(0x8000)
        assertEquals(listOf(HeaderProblem.NES_HEADER_JUNK), HeaderFixer.problems(nes, "nes", "a.nes"))
        val clean = HeaderFixer.fix(nes, listOf(HeaderProblem.NES_HEADER_JUNK))
        assertArrayEquals(ByteArray(9), clean.copyOfRange(7, 16))
        assertEquals(0x10.toByte(), clean[6])
    }

    @Test fun aVerifiedGoodCopyKeepsItsChecksums() {
        val root = tmp.newFolder()
        val md = megaDrive().also { it[0x300] = 1 }
        File(root, "md").mkdirs()
        File(root, "md/game.md").writeBytes(md)
        val c = IdentifyCandidate("megadrive", "md/game.md", "md", "Game")
        val files = JvmCollectionFiles(root)
        assertNull(HeaderFixer.check(files, c) { _, _ -> true })
        assertTrue(HeaderFixer.check(files, c, null) is HeaderSkip)
        assertTrue(HeaderFixer.check(files, c) { _, _ -> false } is HeaderJob)
    }

    @Test fun fixesAZippedGameAndUndoPutsTheOriginalBack() {
        val root = tmp.newFolder()
        File(root, "gb").mkdirs()
        val hacked = gameBoy().also { it[0x134] = 'X'.code.toByte() }
        val zip = File(root, "gb/hack.zip")
        ZipOutputStream(zip.outputStream()).use { z -> z.putNextEntry(ZipEntry("hack.gb")); z.write(hacked); z.closeEntry() }
        val original = zip.readBytes()
        val files = JvmCollectionFiles(root)
        val job = HeaderFixer.check(files, IdentifyCandidate("gb", "gb/hack.zip", "gb", "Hack")) { _, _ -> false } as HeaderJob
        var log = OperationLog("t", "headers", 1, 0, "")
        val result = HeaderFixRunner(files).run(listOf(job), Journal(log) { log = it }) { false }
        assertEquals(emptyList<String>(), result.problems)
        val fixed = ZipInputStream(zip.inputStream()).use { z -> z.nextEntry!!.name to z.readBytes() }
        assertEquals("hack.gb", fixed.first)
        assertEquals(emptyList<HeaderProblem>(), HeaderFixer.problems(fixed.second, "gb", "hack.gb"))
        assertTrue(File(root, "_originals/gb/hack.zip").exists())

        assertEquals(emptyList<String>(), Undo.run(files, log))
        assertArrayEquals(original, zip.readBytes())
    }
}
