package com.retroforge.app.player

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

class PeerLinkTest {
    @get:Rule val temp = TemporaryFolder()

    /** A device: its save folders, the devices it's paired with and its server (on a free port). */
    private inner class Device(val id: String) {
        val base: File = temp.newFolder(id)
        val folders = mapOf("saves" to File(base, "saves").apply { mkdirs() })
        val peers = mutableMapOf<String, Peer>()
        val server = PeerLink.Server(id, "Device $id", { FilesTarget(folders) }, { peers[it]?.secret }, { peers[it.id] = it }, port = 0, askPort = 0)

        fun save(path: String, text: String, time: Long) = File(base, path).apply { parentFile?.mkdirs(); writeText(text); setLastModified(time) }

        fun syncWith(other: Device): SaveSync.Report = PeerLink.Remote("127.0.0.1", other.server.port, id, peers.getValue(other.id).secret).use {
            SaveSync(folders, it, File(base, "manifest-${other.id}.txt"), File(base, "conflicts")).sync()
        }
    }

    private fun paired(): Pair<Device, Device> {
        val a = Device("a")
        val b = Device("b")
        b.server.pairCode = "123456"
        val peer = PeerLink.pair("127.0.0.1", b.server.port, "123456", a.id, "Device a")
        assertNotNull(peer)
        a.peers[b.id] = peer!!
        assertEquals("b", peer.id)
        assertEquals(peer.secret, b.peers.getValue("a").secret)
        return a to b
    }

    @Test
    fun pairedDevicesSwapTheirNewerSavesBothWays() {
        val (a, b) = paired()
        a.save("saves/snes/Zelda.srm", "from a", 1_000_000)
        b.save("saves/psx/FF7.srm", "from b", 2_000_000)
        assertEquals(SaveSync.Report(pulled = 1, pushed = 1, conflicts = 0), a.syncWith(b))
        assertEquals("from a", File(b.base, "saves/snes/Zelda.srm").readText())
        assertEquals("from b", File(a.base, "saves/psx/FF7.srm").readText())

        b.save("saves/psx/FF7.srm", "played on b", System.currentTimeMillis())
        assertEquals(SaveSync.Report(pulled = 1, pushed = 0, conflicts = 0), a.syncWith(b))
        assertEquals("played on b", File(a.base, "saves/psx/FF7.srm").readText())
        assertEquals(SaveSync.Report(0, 0, 0), a.syncWith(b))
        a.server.close(); b.server.close()
    }

    @Test
    fun aWrongCodeDoesntPairAndTheCodeIsUsedUpOnce() {
        val b = Device("b")
        b.server.pairCode = "111111"
        assertNull(PeerLink.pair("127.0.0.1", b.server.port, "222222", "x", "X"))
        assertNotNull(PeerLink.pair("127.0.0.1", b.server.port, "111111", "x", "X"))
        assertNull(PeerLink.pair("127.0.0.1", b.server.port, "111111", "y", "Y"))
        assertEquals(setOf("x"), b.peers.keys)
        b.server.close()
    }

    @Test
    fun devicesThatArentPairedAreTurnedAway() {
        val (a, b) = paired()
        val result = runCatching { PeerLink.Remote("127.0.0.1", b.server.port, "a", "not the secret") }
        assertTrue(result.exceptionOrNull() is NotPairedException)
        b.peers.clear()
        assertTrue(runCatching { a.syncWith(b) }.exceptionOrNull() is NotPairedException)
        a.server.close(); b.server.close()
    }

    @Test
    fun pathsOutsideTheSaveFoldersAreRefused() {
        val (a, b) = paired()
        PeerLink.Remote("127.0.0.1", b.server.port, a.id, a.peers.getValue("b").secret).use { remote ->
            assertTrue(runCatching { remote.write("saves/../escaped.txt", byteArrayOf(1)) }.exceptionOrNull() is IOException)
            assertTrue(runCatching { remote.read("other/x") }.exceptionOrNull() is IOException)
            remote.write("saves/gb/Pokemon.srm", byteArrayOf(1, 2, 3))
            assertArrayEquals(byteArrayOf(1, 2, 3), remote.read("saves/gb/Pokemon.srm"))
        }
        assertTrue(!File(b.base, "escaped.txt").exists())
        a.server.close(); b.server.close()
    }
}
