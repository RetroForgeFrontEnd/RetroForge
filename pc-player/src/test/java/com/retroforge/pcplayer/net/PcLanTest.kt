package com.retroforge.pcplayer.net

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files

/**
 * Two virtual LAN members on this machine, with "games" that speak to their relays the way pcnet.c does: UDP with
 * the 12-byte header, 'C' before a TCP connect, 'I' to learn who's behind an accepted connection.
 */
class PcLanTest {
    private val loopback = InetAddress.getByName("127.0.0.1")
    private lateinit var dir: File
    private lateinit var host: PcLan
    private lateinit var guest: PcLan

    @Before
    fun setUp() {
        dir = Files.createTempDirectory("pclan").toFile()
        val port = ServerSocket(0).use { it.localPort }
        host = PcLan.host(File(dir, "host.cfg"), "Host", port)
        guest = PcLan.join(File(dir, "guest.cfg"), "Guest", "127.0.0.1", port)
        waitFor { (host.status.value as? PcLan.Status.Up)?.members?.size == 2 && (guest.status.value as? PcLan.Status.Up)?.members?.size == 2 }
    }

    @After
    fun tearDown() {
        guest.close()
        host.close()
        dir.deleteRecursively()
    }

    private fun waitFor(condition: () -> Boolean) {
        val end = System.currentTimeMillis() + 5000
        while (!condition()) {
            check(System.currentTimeMillis() < end) { "Timed out" }
            Thread.sleep(20)
        }
    }

    private fun config(file: String) = File(dir, file).readLines().associate { it.substringBefore('=') to it.substringAfter('=') }

    private fun header(type: Char, ip: String, port: Int, rest: ByteArray = ByteArray(0)): ByteArray {
        val b = ByteArray(12 + rest.size)
        "RFVL".toByteArray().copyInto(b)
        b[4] = type.code.toByte()
        InetAddress.getByName(ip).address.copyInto(b, 6)
        b[10] = (port shr 8).toByte()
        b[11] = port.toByte()
        rest.copyInto(b, 12)
        return b
    }

    private fun receive(socket: DatagramSocket): Pair<String, String> {
        val buffer = ByteArray(2048)
        val packet = DatagramPacket(buffer, buffer.size)
        socket.soTimeout = 3000
        socket.receive(packet)
        val from = InetAddress.getByAddress(buffer.copyOfRange(6, 10)).hostAddress + ":" +
            (((buffer[10].toInt() and 0xFF) shl 8) or (buffer[11].toInt() and 0xFF))
        return from to String(buffer, 12, packet.length - 12)
    }

    @Test
    fun configFilesNameEachDevicesAddressAndRelay() {
        assertEquals("10.77.0.1", config("host.cfg")["vip"])
        assertEquals("10.77.0.2", config("guest.cfg")["vip"])
        assertEquals(host.relayPort.toString(), config("host.cfg")["port"])
    }

    @Test
    fun udpBroadcastAndReply() {
        val hostGame = DatagramSocket(InetSocketAddress(loopback, 0))
        val guestGame = DatagramSocket(InetSocketAddress(loopback, 0))
        // The guest's game broadcasts to the host game's port; the host's game sees it from 10.77.0.2.
        val hello = header('D', "255.255.255.255", hostGame.localPort, "hello".toByteArray())
        guestGame.send(DatagramPacket(hello, hello.size, loopback, guest.relayPort))
        val (from, text) = receive(hostGame)
        assertEquals("10.77.0.2:${guestGame.localPort}", from)
        assertEquals("hello", text)
        // And answers it directly.
        val reply = header('D', "10.77.0.2", guestGame.localPort, "welcome".toByteArray())
        hostGame.send(DatagramPacket(reply, reply.size, loopback, host.relayPort))
        assertEquals("10.77.0.1:${hostGame.localPort}" to "welcome", receive(guestGame))
    }

    @Test
    fun tcpConnectionThroughTheRelay() {
        val hostGame = ServerSocket(0, 5, loopback)
        val client = Socket()
        client.bind(InetSocketAddress(loopback, 0))
        // pcnet.c's connect(): 'C' with where it's going and its local port, then connect to the relay.
        val ask = DatagramSocket(InetSocketAddress(loopback, 0))
        val portBytes = byteArrayOf((client.localPort shr 8).toByte(), client.localPort.toByte())
        val c = header('C', "10.77.0.1", hostGame.localPort, portBytes).also { it[5] = 'T'.code.toByte() }
        ask.send(DatagramPacket(c, c.size, loopback, guest.relayPort))
        val answer = ByteArray(16)
        ask.soTimeout = 3000
        ask.receive(DatagramPacket(answer, answer.size))
        assertEquals('A', answer[4].toInt().toChar())
        client.connect(InetSocketAddress(loopback, guest.relayPort), 3000)
        hostGame.soTimeout = 3000
        val accepted = hostGame.accept()
        client.getOutputStream().write("ping".toByteArray())
        val got = ByteArray(4)
        accepted.getInputStream().read(got)
        assertEquals("ping", String(got))
        accepted.getOutputStream().write("pong".toByteArray())
        client.getInputStream().read(got)
        assertEquals("pong", String(got))
        // pcnet.c's accept(): who stands behind the relay's 127.0.0.1 port? The guest, from its own port.
        val i = ByteArray(8)
        "RFVL".toByteArray().copyInto(i)
        i[4] = 'I'.code.toByte()
        i[5] = 'T'.code.toByte()
        i[6] = (accepted.port shr 8).toByte()
        i[7] = accepted.port.toByte()
        ask.send(DatagramPacket(i, i.size, loopback, host.relayPort))
        ask.receive(DatagramPacket(answer, answer.size))
        assertEquals('A', answer[4].toInt().toChar())
        assertEquals("10.77.0.2", InetAddress.getByAddress(answer.copyOfRange(6, 10)).hostAddress)
        assertEquals(client.localPort, ((answer[10].toInt() and 0xFF) shl 8) or (answer[11].toInt() and 0xFF))
        // Closing one end closes the other.
        client.close()
        accepted.soTimeout = 3000
        assertTrue(accepted.getInputStream().read() < 0)
    }
}
