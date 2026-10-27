package com.retroforge.patch

import org.apache.commons.compress.archivers.sevenz.SevenZOutputFile
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ArchiveInputTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val romBytes = ByteArray(2048) { it.toByte() }

    private fun zip(vararg files: Pair<String, ByteArray>): ByteArray = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { zip ->
            for ((name, data) in files) {
                zip.putNextEntry(ZipEntry(name)); zip.write(data); zip.closeEntry()
            }
        }
    }.toByteArray()

    private fun sevenZip(vararg files: Pair<String, ByteArray>): ByteArray {
        val file = tmp.newFile("test.7z")
        SevenZOutputFile(file).use { archive ->
            for ((name, data) in files) {
                val entry = archive.createArchiveEntry(tmp.newFile(name.replace('/', '_')), name)
                archive.putArchiveEntry(entry); archive.write(data); archive.closeArchiveEntry()
            }
        }
        return file.readBytes()
    }

    @Test
    fun plainFilesPassThrough() {
        val result = ArchiveInput.readRom("Game.sfc", romBytes) as ArchiveResult.Ok
        assertEquals("Game.sfc", result.file.name)
    }

    @Test
    fun zipWithRomAndReadmePicksTheRom() {
        val result = ArchiveInput.readRom("Game.zip", zip("readme.txt" to "hi".toByteArray(), "Game.sfc" to romBytes))
        val ok = result as ArchiveResult.Ok
        assertEquals("Game.sfc", ok.file.name)
        assertArrayEquals(romBytes, ok.file.bytes)
    }

    @Test
    fun zipWithTwoRomsIsAmbiguous() {
        val result = ArchiveInput.readRom("Set.zip", zip("A.sfc" to romBytes, "B.sfc" to romBytes))
        assertEquals(listOf("A.sfc", "B.sfc"), (result as ArchiveResult.Ambiguous).candidates)
    }

    @Test
    fun patchZipYieldsThePatchOnly() {
        val patch = TestPatches.ips(0 to TestPatches.bytes(1))
        val result = ArchiveInput.readPatch("Hack.zip", zip("docs/readme.txt" to "x".toByteArray(), "docs/hack.ips" to patch))
        val ok = result as ArchiveResult.Ok
        assertEquals("hack.ips", ok.file.name)
        assertArrayEquals(patch, ok.file.bytes)
    }

    @Test
    fun patchArchiveReadmesAreReturnedForDisplay() {
        val patch = TestPatches.ips(0 to TestPatches.bytes(1))
        val readme = "Apply Giant Mario v1.2 first.\r\nBase: Super Mario Bros. (World).nes"
        val result = ArchiveInput.readPatch("Hack.zip", zip("Hack.txt" to readme.toByteArray(), "Hack.ips" to patch)) as ArchiveResult.Ok
        assertEquals(listOf(TextFile("Hack.txt", "Apply Giant Mario v1.2 first.\nBase: Super Mario Bros. (World).nes")), result.readmes)
    }

    @Test
    fun legacyReadmesFallBackToLatin1AndDropControlCharacters() {
        val dosText = byteArrayOf(0x43, 0x61, 0x66, 0xE9.toByte(), 0x07, 0x0A, 0x4F, 0x4B)
        assertEquals("Café\nOK", ArchiveInput.decodeText(dosText))
    }

    @Test
    fun aPatchCanBeChosenFromAnArchiveHoldingSeveral() {
        val a = TestPatches.ips(0 to TestPatches.bytes(1))
        val b = TestPatches.ips(0 to TestPatches.bytes(2))
        val archive = zip("Giant Mario.ips" to a, "Giant Mario (Level unchanged).ips" to b)
        val ambiguous = ArchiveInput.readPatch("GM.zip", archive) as ArchiveResult.Ambiguous
        assertEquals(listOf("Giant Mario.ips", "Giant Mario (Level unchanged).ips"), ambiguous.candidates)
        val chosen = ArchiveInput.readPatch("GM.zip", archive, entryName = "Giant Mario (Level unchanged).ips") as ArchiveResult.Ok
        assertArrayEquals(b, chosen.file.bytes)
    }

    @Test
    fun oversizedReadmeIsSkippedWithoutFailingThePatch() {
        val patch = TestPatches.ips(0 to TestPatches.bytes(1))
        val result = ArchiveInput.readPatch("Hack.zip", zip("huge.txt" to ByteArray(70_000) { 'a'.code.toByte() }, "Hack.ips" to patch))
        val ok = result as ArchiveResult.Ok
        assertArrayEquals(patch, ok.file.bytes)
        assertTrue(ok.readmes.isEmpty())
    }

    @Test
    fun romArchivesDoNotCollectReadmes() {
        val result = ArchiveInput.readRom("Game.zip", zip("readme.txt" to "x".toByteArray(), "Game.sfc" to romBytes)) as ArchiveResult.Ok
        assertTrue(result.readmes.isEmpty())
    }

    @Test
    fun sevenZipRomsAreRead() {
        val result = ArchiveInput.readRom("Game.7z", sevenZip("Game.sfc" to romBytes)) as ArchiveResult.Ok
        assertArrayEquals(romBytes, result.file.bytes)
    }

    @Test
    fun corruptArchiveIsReportedNotThrown() {
        assertTrue(ArchiveInput.readRom("Broken.7z", ByteArray(64) { 1 }) is ArchiveResult.Unreadable)
    }

    @Test
    fun readLimitedStopsAtTheLimit() {
        assertEquals(null, ArchiveInput.readLimited(ByteArray(1000).inputStream(), "x", limit = 999))
        assertEquals(1000, ArchiveInput.readLimited(ByteArray(1000).inputStream(), "x", limit = 1000)!!.size)
    }
}
