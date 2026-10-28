package com.retroforge.app.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class SaveBackupTest {
    @get:Rule
    val tmp = TemporaryFolder()

    private fun folders(root: File) = mapOf("saves" to File(root, "s"), "states" to File(root, "t"))

    @Test
    fun exportsAndRestores() {
        val from = tmp.newFolder("from")
        File(from, "s/snes").mkdirs(); File(from, "s/snes/Mario.srm").writeText("sram")
        File(from, "t/snes").mkdirs(); File(from, "t/snes/Mario.snes9x.auto.state").writeText("state")
        val zip = ByteArrayOutputStream()
        assertEquals(2, SaveBackup(folders(from)).export(zip))

        val to = tmp.newFolder("to")
        File(to, "s/nes").mkdirs(); File(to, "s/nes/Kept.srm").writeText("kept")
        assertEquals(2, SaveBackup(folders(to)).restore(ByteArrayInputStream(zip.toByteArray())))
        assertEquals("sram", File(to, "s/snes/Mario.srm").readText())
        assertEquals("state", File(to, "t/snes/Mario.snes9x.auto.state").readText())
        assertEquals("kept", File(to, "s/nes/Kept.srm").readText())
    }

    @Test
    fun skipsEntriesOutsideTheFolders() {
        val bytes = ByteArrayOutputStream().also { out ->
            ZipOutputStream(out).use { zip ->
                listOf("saves/../../evil.txt", "other/x.txt").forEach {
                    zip.putNextEntry(ZipEntry(it)); zip.write(1); zip.closeEntry()
                }
            }
        }.toByteArray()
        val to = tmp.newFolder("to")
        assertEquals(0, SaveBackup(folders(to)).restore(ByteArrayInputStream(bytes)))
        assertFalse(File(tmp.root, "evil.txt").exists())
    }
}
