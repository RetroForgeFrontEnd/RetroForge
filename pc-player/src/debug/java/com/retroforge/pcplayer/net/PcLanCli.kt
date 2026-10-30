package com.retroforge.pcplayer.net

import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import kotlin.concurrent.thread

/**
 * Debug builds only: [PcLan] from a command line, for testing the virtual LAN without the player.
 *
 * - On a phone (as the app, without installing): `run-as com.retroforge.app app_process -cp <app-debug.apk> /
 *   com.retroforge.pcplayer.net.PcLanCli join <host> <config file>`, then run a game or tools/lantest in Wine with
 *   PCNET_FILE=<config file>.
 * - On a PC (java with the module's classes, Kotlin's and coroutines' jars): `PcLanCli host <config file> answer`.
 *   "answer" adds a stand-in for tools/lantest on 10.77.0.1, speaking the relay's protocol: it answers hellos on
 *   UDP 47777 and TCP connections on 47778, printing the addresses it sees.
 */
object PcLanCli {
    @JvmStatic
    fun main(args: Array<String>) {
        val lan = when (args.getOrNull(0)) {
            "host" -> PcLan.host(File(args[1]), "PC test")
            "join" -> PcLan.join(File(args[2]), "Phone test", args[1])
            else -> {
                println("usage: host <config> [answer] | join <host> <config>")
                return
            }
        }
        thread(isDaemon = true) {
            var last: Any? = null
            while (true) {
                val status = lan.status.value
                if (status != last) println("status: $status")
                last = status
                Thread.sleep(200)
            }
        }
        if (args.getOrNull(2) == "answer") answer(lan.relayPort)
        Runtime.getRuntime().addShutdownHook(Thread { lan.close() })
        Thread.sleep(Long.MAX_VALUE)
    }

    private val loopback: InetAddress = InetAddress.getByName("127.0.0.1")

    private fun answer(relay: Int) {
        thread(isDaemon = true) {
            DatagramSocket(InetSocketAddress(loopback, 47777)).use { udp ->
                val buffer = ByteArray(2048)
                while (true) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    udp.receive(packet)
                    if (packet.length < 12) continue
                    val from = InetAddress.getByAddress(buffer.copyOfRange(6, 10))
                    val port = ((buffer[10].toInt() and 0xFF) shl 8) or (buffer[11].toInt() and 0xFF)
                    println("UDP from ${from.hostAddress}:$port: ${String(buffer, 12, packet.length - 12)}")
                    val hello = "hello PC-stand-in".toByteArray()
                    val reply = ByteArray(12 + hello.size)
                    "RFVL".toByteArray().copyInto(reply)
                    reply[4] = 'D'.code.toByte()
                    from.address.copyInto(reply, 6)
                    reply[10] = (port shr 8).toByte()
                    reply[11] = port.toByte()
                    hello.copyInto(reply, 12)
                    udp.send(DatagramPacket(reply, reply.size, loopback, relay))
                }
            }
        }
        thread(isDaemon = true) {
            ServerSocket(47778, 5, loopback).use { server ->
                while (true) {
                    server.accept().use { s ->
                        val buffer = ByteArray(512)
                        val n = s.getInputStream().read(buffer)
                        println("TCP from relay port ${s.port}: \"${if (n > 0) String(buffer, 0, n) else ""}\"")
                        s.getOutputStream().write("tcp reply from PC-stand-in".toByteArray())
                    }
                }
            }
        }
    }
}
