package com.retroforge.app.player

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SaveSyncTest {
    @get:Rule val tmp = TemporaryFolder()

    /** A sync folder in memory; each write moves its clock on, as a real folder's modified times would. */
    private class MemoryTarget : SyncTarget {
        val files = mutableMapOf<String, Pair<ByteArray, Long>>()
        var clock = 1_000_000L
        override fun list() = files.mapValues { SyncedFile(it.value.first.size.toLong(), it.value.second) }
        override fun read(path: String) = files.getValue(path).first
        override fun write(path: String, bytes: ByteArray): Long { clock += 1000; files[path] = bytes to clock; return clock }
    }

    private fun device(name: String, target: SyncTarget): Pair<File, SaveSync> {
        val root = tmp.newFolder(name)
        val saves = File(root, "saves").apply { mkdirs() }
        return saves to SaveSync(mapOf("saves" to saves), target, File(root, "manifest.json"), File(root, "conflicts"))
    }

    @Test
    fun `a save made on one device reaches the other`() {
        val target = MemoryTarget()
        val (phone, phoneSync) = device("phone", target)
        val (handheld, handheldSync) = device("handheld", target)
        File(phone, "snes/Zelda.srm").apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(1, 2, 3)) }

        assertEquals(1, phoneSync.sync().pushed)
        assertEquals(1, handheldSync.sync().pulled)
        assertArrayEquals(byteArrayOf(1, 2, 3), File(handheld, "snes/Zelda.srm").readBytes())
        // Nothing changed since: nothing moves.
        assertEquals(SaveSync.Report(0, 0, 0), phoneSync.sync())
        assertEquals(SaveSync.Report(0, 0, 0), handheldSync.sync())
    }

    @Test
    fun `a newer save replaces the older one, and a clash keeps both`() {
        val target = MemoryTarget()
        val (phone, phoneSync) = device("phone", target)
        val (handheld, handheldSync) = device("handheld", target)
        val phoneSave = File(phone, "nes/Metroid.srm").apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(1)) }
        phoneSync.sync()
        handheldSync.sync()

        // Played on the handheld: the phone gets it.
        File(handheld, "nes/Metroid.srm").apply { writeBytes(byteArrayOf(2)); setLastModified(lastModified() + 5000) }
        handheldSync.sync()
        assertEquals(1, phoneSync.sync().pulled)
        assertArrayEquals(byteArrayOf(2), phoneSave.readBytes())

        // Played on both before syncing: the newer wins, the other is kept.
        phoneSave.apply { writeBytes(byteArrayOf(3)); setLastModified(lastModified() + 60_000) }
        File(handheld, "nes/Metroid.srm").apply { writeBytes(byteArrayOf(4)); setLastModified(lastModified() + 10_000) }
        handheldSync.sync()
        val report = phoneSync.sync()
        assertEquals(1, report.conflicts)
        assertArrayEquals(byteArrayOf(3), target.read("saves/nes/Metroid.srm"))
        assertTrue(File(tmp.root, "phone/conflicts").walkTopDown().any { it.isFile && it.readBytes().contentEquals(byteArrayOf(4)) })
    }

    @Test
    fun `emulator working folders and one system only`() {
        val target = MemoryTarget()
        val (phone, phoneSync) = device("phone", target)
        File(phone, "c64/TEMP/game.d64").apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(9)) }
        File(phone, "c64/game.srm").writeBytes(byteArrayOf(1))
        File(phone, "snes/other.srm").apply { parentFile!!.mkdirs(); writeBytes(byteArrayOf(1)) }

        assertEquals(1, phoneSync.sync(only = listOf("saves/c64/")).pushed)
        assertEquals(setOf("saves/c64/game.srm"), target.files.keys)
    }
}
