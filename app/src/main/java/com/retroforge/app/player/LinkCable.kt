package com.retroforge.app.player

import android.content.Context
import android.net.wifi.WifiManager
import com.retroforge.player.CoreOption
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import kotlin.concurrent.thread

/** A Game Boy offering the link cable on the network: where it is, its game and who's playing. */
data class LinkHost(val address: String, val title: String, val player: String)

/**
 * The Game Boy link cable between two devices on the same Wi-Fi, for trading and battling (Pokémon Red and Blue,
 * Tetris head-to-head). Gambatte has the cable built in: one side is the host (it listens), the other joins its
 * address. The two games needn't be the same. RetroForge finds hosts with a broadcast, as for netplay, and sets
 * Gambatte's link options; they aren't saved, so a game always starts unplugged.
 */
object LinkCable {
    /** Gambatte's own default port for the cable. */
    const val PORT = 56400
    private const val ASK_PORT = 55481
    private const val ASK = "RFLC?"
    private const val ANSWER = "RFLC!"
    private const val MODE = "gambatte_gb_link_mode"
    private const val PORT_KEY = "gambatte_gb_link_network_port"
    private const val IP_KEY = "gambatte_gb_link_network_server_ip_"

    fun supports(system: String, coreId: String) = system in setOf("gb", "gbc") && coreId == "gambatte"

    /** The core's choice for the link mode containing [word] ("Server", "Client", "Not"), as the core spells it. */
    private fun mode(options: List<CoreOption>, word: String): String? =
        options.firstOrNull { it.key == MODE }?.choices?.firstOrNull { it.contains(word, ignoreCase = true) }

    /** Gambatte's options for hosting, or null if this Gambatte has no network cable. */
    fun hostOptions(options: List<CoreOption>): Map<String, String>? = mode(options, "Server")?.let { linkedMapOf(PORT_KEY to "$PORT", MODE to it) }

    /** Gambatte's options for joining the host at [address] (an IPv4 address), or null if it isn't one or the core can't. */
    fun joinOptions(options: List<CoreOption>, address: String): Map<String, String>? {
        val digits = ipDigits(address) ?: return null
        val client = mode(options, "Client") ?: return null
        // The address goes in as twelve digits, each its own option: 192.168.1.23 is 192 168 001 023. Mode last, so
        // the core connects once with everything set.
        return linkedMapOf<String, String>().apply {
            put(PORT_KEY, "$PORT")
            digits.forEachIndexed { i, d -> put("$IP_KEY${i + 1}", d.toString()) }
            put(MODE, client)
        }
    }

    fun unplugOptions(options: List<CoreOption>): Map<String, String>? = mode(options, "Not")?.let { mapOf(MODE to it) }

    /** "192.168.1.23" → 1 9 2 1 6 8 0 0 1 0 2 3; null if it isn't an IPv4 address. */
    fun ipDigits(address: String): List<Int>? {
        val parts = address.trim().split('.').map { it.toIntOrNull() }
        if (parts.size != 4 || parts.any { it == null || it !in 0..255 }) return null
        return parts.joinToString("") { "%03d".format(it) }.map { it - '0' }
    }

    /** Answers "who's got a link cable?" with this game until [stop] is called. */
    class Announcer(context: Context, private val title: String, private val player: String) {
        @Volatile private var running = true
        private var socket: DatagramSocket? = null
        // Some phones drop broadcasts unless an app holds a multicast lock.
        private val lock = (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)
            ?.createMulticastLock("retroforge-link-cable")?.apply { setReferenceCounted(false); runCatching { acquire() } }

        init {
            thread(name = "link-cable-announcer", isDaemon = true) {
                runCatching {
                    DatagramSocket(null).use { s ->
                        socket = s
                        s.reuseAddress = true
                        s.bind(InetSocketAddress(ASK_PORT))
                        val buffer = ByteArray(64)
                        while (running) {
                            val packet = DatagramPacket(buffer, buffer.size)
                            s.receive(packet)
                            if (String(packet.data, 0, packet.length) != ASK) continue
                            val answer = listOf(ANSWER, title, player).joinToString("\n").toByteArray()
                            s.send(DatagramPacket(answer, answer.size, packet.address, packet.port))
                        }
                    }
                }
            }
        }

        fun stop() {
            running = false
            runCatching { socket?.close() }
            runCatching { lock?.release() }
        }
    }

    /** Asks the network which Game Boys are hosting a cable and collects the answers for [waitMs]. Call off the main thread. */
    fun find(context: Context, waitMs: Int = 1500): List<LinkHost> {
        val lock = (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)
            ?.createMulticastLock("retroforge-link-cable-find")?.apply { setReferenceCounted(false); runCatching { acquire() } }
        val found = linkedMapOf<String, LinkHost>()
        try {
            DatagramSocket().use { s ->
                s.broadcast = true
                s.soTimeout = 250
                val ask = ASK.toByteArray()
                val targets = buildSet {
                    add(InetAddress.getByName("255.255.255.255"))
                    runCatching {
                        NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
                            .flatMap { it.interfaceAddresses }.mapNotNull { it.broadcast }.forEach { add(it) }
                    }
                }
                val end = System.currentTimeMillis() + waitMs
                var lastAsk = 0L
                val buffer = ByteArray(512)
                while (System.currentTimeMillis() < end) {
                    if (System.currentTimeMillis() - lastAsk > 500) {
                        targets.forEach { runCatching { s.send(DatagramPacket(ask, ask.size, it, ASK_PORT)) } }
                        lastAsk = System.currentTimeMillis()
                    }
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        s.receive(packet)
                        val parts = String(packet.data, 0, packet.length).split("\n")
                        if (parts.size >= 3 && parts[0] == ANSWER) {
                            val address = packet.address.hostAddress.orEmpty()
                            found[address] = LinkHost(address, parts[1], parts[2])
                        }
                    } catch (_: SocketTimeoutException) {
                    }
                }
            }
        } catch (_: Exception) {
        } finally {
            runCatching { lock?.release() }
        }
        return found.values.toList()
    }
}
