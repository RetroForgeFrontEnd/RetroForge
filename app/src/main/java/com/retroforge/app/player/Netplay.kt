package com.retroforge.app.player

import android.content.Context
import android.net.wifi.WifiManager
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import kotlin.concurrent.thread

/** A game being hosted for netplay on this network: where, what, and by whom. */
data class NetplayHost(val address: String, val port: Int, val title: String, val gameId: String, val player: String)

/**
 * Netplay's settings and finding games on the same Wi-Fi. A host answers a small broadcast question ("who's hosting?")
 * with its game; joining then connects straight to it (netplay.cpp). Over the internet, players type the address.
 */
object Netplay {
    const val GAME_PORT = 55450
    private const val ASK_PORT = 55451
    private const val ASK = "RFNP?"
    private const val ANSWER = "RFNP!"

    private const val PREFS = "netplay"
    private const val NAME = "name"
    private const val DELAY = "delay"

    /** Frames between pressing a button and the game using it, on both devices. More copes with slower networks. */
    val delays = listOf(2, 3, 4, 5, 6)

    fun name(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(NAME, null)?.takeIf { it.isNotBlank() }
            ?: android.os.Build.MODEL

    fun delay(context: Context): Int = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt(DELAY, 3)

    fun setName(context: Context, name: String) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(NAME, name.trim()).apply()

    fun setDelay(context: Context, frames: Int) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(DELAY, frames).apply()

    /** The same game on both devices: system, file name and size. */
    fun gameId(system: String, fileName: String, size: Long) = "$system|$fileName|$size"

    /** This device's addresses on the local network, for the other player to type. */
    fun localAddresses(): List<String> = runCatching {
        NetworkInterface.getNetworkInterfaces().toList().filter { it.isUp && !it.isLoopback }
            .flatMap { it.inetAddresses.toList() }.filterIsInstance<Inet4Address>().map { it.hostAddress.orEmpty() }
    }.getOrDefault(emptyList()).filter { it.isNotBlank() }

    /** Answers "who's hosting?" with this game until [stop] is called. */
    class Announcer(context: Context, private val title: String, private val gameId: String, private val player: String) {
        @Volatile private var running = true
        private var socket: DatagramSocket? = null
        // Some phones drop broadcasts unless an app holds a multicast lock.
        private val lock = (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)
            ?.createMulticastLock("retroforge-netplay")?.apply { setReferenceCounted(false); runCatching { acquire() } }

        init {
            thread(name = "netplay-announcer", isDaemon = true) {
                runCatching {
                    DatagramSocket(null).use { s ->
                        socket = s
                        s.reuseAddress = true
                        s.bind(InetSocketAddress(ASK_PORT))
                        val buffer = ByteArray(256)
                        while (running) {
                            val packet = DatagramPacket(buffer, buffer.size)
                            s.receive(packet)
                            if (String(packet.data, 0, packet.length) != ASK) continue
                            val answer = listOf(ANSWER, GAME_PORT.toString(), title, gameId, player).joinToString("\n").toByteArray()
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

    /** Asks the network who's hosting and collects the answers for [waitMs]. Call off the main thread. */
    fun find(context: Context, waitMs: Int = 1500): List<NetplayHost> {
        val lock = (context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager)
            ?.createMulticastLock("retroforge-netplay-find")?.apply { setReferenceCounted(false); runCatching { acquire() } }
        val found = linkedMapOf<String, NetplayHost>()
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
                        if (parts.size >= 5 && parts[0] == ANSWER) {
                            val address = packet.address.hostAddress.orEmpty()
                            found[address] = NetplayHost(address, parts[1].toIntOrNull() ?: GAME_PORT, parts[2], parts[3], parts[4])
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
