package com.retroforge.pcplayer.net

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * Play together over the internet: a virtual LAN between RetroForge devices, so a Windows game's LAN mode finds the
 * other players as if they were on the same network. No VPN: the game's sockets are redirected by libpcexec.so
 * (pcnet.c) to this relay on 127.0.0.1, and the relay carries the traffic to the others.
 *
 * - Addresses: 10.77.0.1 is the host, 10.77.0.2–9 the players who joined. Windows sees an adapter "rf0" with this
 *   device's address (and only that, so games advertise it); the relay's settings are in [configFile], which pcnet.c
 *   reads.
 * - Between devices: one TCP connection from each player to the host (control, TCP streams, and UDP until the UDP
 *   path works) and UDP packets on the same port. The host forwards between players. The host's router port is
 *   opened with UPnP, as the retro player's netplay does.
 * - With the game: UDP packets with a 12-byte header (who it's for / who it's from); TCP connections the game makes
 *   to 10.77.0.x come to the relay's TCP port, after pcnet.c has said where each was going; ones from other players
 *   are made to the game from 127.0.0.1, and pcnet.c asks which virtual address stands behind each.
 */
class PcLan private constructor(private val configFile: File, private val myName: String, private val hosting: Boolean) : Closeable {

    data class Member(val index: Int, val name: String)

    sealed interface Status {
        data class Starting(val step: String) : Status
        /** Up: this device's virtual address, and everyone in the game (this device included). */
        data class Up(val address: String, val members: List<Member>) : Status
        data class Failed(val message: String) : Status
    }

    private val _status = MutableStateFlow<Status>(Status.Starting(if (hosting) "Starting" else "Connecting"))
    val status: StateFlow<Status> = _status

    @Volatile private var me = if (hosting) HOST else 0
    @Volatile private var closed = false
    private val names = ConcurrentHashMap<Int, String>()

    // Between devices.
    private var server: ServerSocket? = null
    private val links = ConcurrentHashMap<Int, Link>()   // the host's players, by index
    @Volatile private var hostLink: Link? = null          // a player's link to the host
    private val tunnel = DatagramSocket(null)
    private val playerUdp = ConcurrentHashMap<Int, InetSocketAddress>()  // host: where each player's UDP comes from
    @Volatile private var hostUdp: InetSocketAddress? = null
    @Volatile private var hostUdpWorks = false

    // With the game.
    // UDP and TCP on the same port number (pcnet.c knows just the one).
    private val relaySockets = relayPorts()
    private val relayUdp: DatagramSocket = relaySockets.first
    private val relayTcp: ServerSocket = relaySockets.second
    private data class Target(val member: Int, val port: Int)
    /** pcnet.c's 'C' notes: the game's local port (TCP or UDP) → where it was connecting. */
    private val outbound = ConcurrentHashMap<Int, Target>()
    /** Connections the relay made to the game for other players: the relay's local port → who it stands for. */
    private val inbound = ConcurrentHashMap<Int, Target>()
    private class Stream(val socket: Socket, val peer: Int, val localPort: Int)
    private val streams = ConcurrentHashMap<Long, Stream>()
    private val nextStream = AtomicInteger(1)

    val relayPort: Int get() = relayUdp.localPort

    /** A connection to another device, carrying frames: length, type, from, to, body. */
    private inner class Link(val socket: Socket) {
        private val output = DataOutputStream(socket.getOutputStream().buffered())
        private val input = DataInputStream(socket.getInputStream().buffered())
        @Volatile var index = 0

        fun send(type: Char, from: Int, to: Int, body: ByteArray, offset: Int = 0, length: Int = body.size) {
            runCatching {
                synchronized(output) {
                    output.writeInt(length + 3)
                    output.writeByte(type.code)
                    output.writeByte(from)
                    output.writeByte(to)
                    output.write(body, offset, length)
                    output.flush()
                }
            }.onFailure { runCatching { socket.close() } }
        }

        /** Reads one frame: (type, from, to, body). Throws at the end of the connection. */
        fun read(): Frame {
            val length = input.readInt()
            if (length < 3 || length > MAX_FRAME) throw EOFException("Bad frame")
            val type = input.readUnsignedByte().toChar()
            val from = input.readUnsignedByte()
            val to = input.readUnsignedByte()
            val body = ByteArray(length - 3)
            input.readFully(body)
            return Frame(type, from, to, body)
        }

        fun close() = runCatching { socket.close() }
    }

    private class Frame(val type: Char, val from: Int, val to: Int, val body: ByteArray)

    // ---- Starting ----

    private fun startHost(port: Int) {
        val listener = ServerSocket()
        listener.reuseAddress = true
        listener.bind(InetSocketAddress(port))
        server = listener
        tunnel.reuseAddress = true
        tunnel.bind(InetSocketAddress(port))
        names[HOST] = myName
        writeConfig()
        startRelay()
        thread(name = "pclan-tunnel", isDaemon = true) { readTunnel() }
        thread(name = "pclan-accept", isDaemon = true) {
            while (!closed) {
                val socket = runCatching { listener.accept() }.getOrNull() ?: break
                thread(name = "pclan-player", isDaemon = true) { welcome(socket) }
            }
        }
        publish()
    }

    private fun welcome(socket: Socket) {
        val link = Link(socket)
        try {
            socket.tcpNoDelay = true
            socket.soTimeout = 8000
            val hello = link.read()
            val lines = String(hello.body).lines()
            if (hello.type != 'H' || lines.firstOrNull() != VERSION) return
            socket.soTimeout = 0
            // A player coming back after its connection dropped gets its old address again. If the old connection
            // still looks open here (a dropped Wi-Fi isn't noticed at this end), the same device takes its place.
            val want = lines.getOrNull(2)?.toIntOrNull() ?: 0
            val index = synchronized(links) {
                val old = links[want]
                val free = want in (HOST + 1)..MAX_MEMBER && (old == null || old.socket.inetAddress == socket.inetAddress)
                if (free) {
                    old?.close()
                    dropStreams(want)
                }
                (if (free) want else ((HOST + 1)..MAX_MEMBER).firstOrNull { !links.containsKey(it) })?.also { links[it] = link }
            } ?: return
            link.index = index
            names[index] = lines.getOrNull(1).orEmpty().take(40).ifBlank { "Player $index" }
            link.send('W', HOST, index, ByteArray(0))
            publish()
            readLink(link)
        } catch (_: Exception) {
            // The player left (or never said hello).
        } finally {
            link.close()
            // (Unless the same player has already come back on a new connection.)
            if (link.index != 0 && links.remove(link.index, link)) {
                names.remove(link.index)
                playerUdp.remove(link.index)
                dropStreams(link.index)
                publish()
            }
        }
    }

    /** Connects to the host and says hello (asking for [want] back after a dropped connection). Returns the link. */
    private fun connectToHost(host: InetAddress, port: Int, want: Int): Link {
        val socket = Socket()
        socket.connect(InetSocketAddress(host, port), 8000)
        socket.tcpNoDelay = true
        socket.soTimeout = 8000
        val link = Link(socket)
        link.send('H', 0, HOST, "$VERSION\n$myName\n$want".toByteArray())
        val welcome = link.read()
        if (welcome.type != 'W' || welcome.to !in (HOST + 1)..MAX_MEMBER) {
            link.close()
            throw EOFException("The host didn't let this device in")
        }
        socket.soTimeout = 0
        link.index = welcome.to
        return link
    }

    private fun startJoin(address: String, port: Int) {
        val host = InetAddress.getByName(address)
        val link = connectToHost(host, port, 0)
        me = link.index
        hostLink = link
        hostUdp = InetSocketAddress(host, port)
        tunnel.bind(InetSocketAddress(0))
        writeConfig()
        startRelay()
        thread(name = "pclan-tunnel", isDaemon = true) { readTunnel() }
        thread(name = "pclan-keepalive", isDaemon = true) {
            // Keeps the UDP path open through this side's router, and tells the host where it comes from.
            while (!closed) {
                sendTunnel(hostUdp ?: break, 'K', me, HOST, ByteArray(0))
                Thread.sleep(if (hostUdpWorks) 5000 else 1000)
            }
        }
        thread(name = "pclan-host", isDaemon = true) {
            var current = link
            while (!closed) {
                try {
                    readLink(current)
                } catch (_: Exception) {
                }
                current.close()
                dropStreams(HOST)
                if (closed) break
                // Wi-Fi dropped, the router restarted, the host's phone was busy: keep trying for a while, as the same
                // player (the game keeps its address, so it can carry on once the link is back).
                hostUdpWorks = false
                _status.value = Status.Starting("Connection lost. Reconnecting")
                val giveUp = System.currentTimeMillis() + RECONNECT_MS
                var back: Link? = null
                while (!closed && back == null && System.currentTimeMillis() < giveUp) {
                    back = runCatching { connectToHost(host, port, me) }.getOrNull()
                    if (back == null) Thread.sleep(2000)
                }
                if (back == null || back.index != me) {
                    back?.close()
                    if (!closed) _status.value = Status.Failed("The connection to the host was lost")
                    break
                }
                current = back
                hostLink = back
                publish()
            }
        }
    }

    private fun writeConfig() {
        configFile.parentFile?.mkdirs()
        val part = File(configFile.path + ".part")
        part.writeText("vip=${address(me)}\nport=$relayPort\nhide=1\n")
        part.renameTo(configFile)
    }

    /** Tells everyone (and this device's menu) who's in the game. */
    private fun publish() {
        val list = names.entries.sortedBy { it.key }.map { Member(it.key, it.value) }
        if (hosting) {
            val body = list.joinToString("\n") { "${it.index}:${it.name}" }.toByteArray()
            links.values.forEach { it.send('M', HOST, BROADCAST, body) }
        }
        if (!closed) _status.value = Status.Up(address(me), list)
    }

    // ---- Between devices ----

    private fun readLink(link: Link) {
        while (!closed) {
            val frame = link.read()
            // A player can only speak for itself.
            if (hosting && frame.from != link.index) continue
            route(frame.type, frame.from, frame.to, frame.body)
        }
    }

    /** A frame from another device: forwarded (the host, for other players), handled here, or both (broadcasts). */
    private fun route(type: Char, from: Int, to: Int, body: ByteArray) {
        if (hosting && to != HOST) {
            if (to == BROADCAST) links.forEach { (index, link) -> if (index != from) forward(link, type, from, to, body) }
            else links[to]?.let { forward(it, type, from, to, body) }
            if (to != BROADCAST) return
        }
        handle(type, from, body)
    }

    private fun forward(link: Link, type: Char, from: Int, to: Int, body: ByteArray) {
        val udp = playerUdp[link.index]
        if (type == 'U' && udp != null) sendTunnel(udp, type, from, to, body) else link.send(type, from, to, body)
    }

    /** Sends a frame towards [to] (a member or [BROADCAST]). UDP traffic goes by UDP once that path works. */
    private fun send(type: Char, to: Int, body: ByteArray) {
        if (hosting) {
            if (to == BROADCAST) links.values.forEach { forward(it, type, me, to, body) } else links[to]?.let { forward(it, type, me, to, body) }
        } else {
            val udp = hostUdp
            if (type == 'U' && hostUdpWorks && udp != null) sendTunnel(udp, type, me, to, body)
            else hostLink?.send(type, me, to, body)
        }
    }

    private fun handle(type: Char, from: Int, body: ByteArray) {
        when (type) {
            'U' -> if (body.size >= 4) deliverUdp(from, u16(body, 0), u16(body, 2), body, 4)
            'O' -> if (body.size >= 8) openInbound(from, body.int(0), u16(body, 4), u16(body, 6))
            'S' -> if (body.size >= 5) streams[key(body[0].toInt() and 0xFF, body.int(1))]?.let { s ->
                runCatching { s.socket.getOutputStream().apply { write(body, 5, body.size - 5); flush() } }
                    .onFailure { closeStream(key(body[0].toInt() and 0xFF, body.int(1)), tell = true) }
            }
            'C' -> if (body.size >= 5) closeStream(key(body[0].toInt() and 0xFF, body.int(1)), tell = false)
            'M' -> if (!hosting) {
                names.clear()
                String(body).lines().forEach { line ->
                    line.substringBefore(':').toIntOrNull()?.let { names[it] = line.substringAfter(':') }
                }
                publish()
            }
        }
    }

    private fun sendTunnel(to: InetSocketAddress, type: Char, from: Int, dest: Int, body: ByteArray) {
        val packet = ByteArray(7 + body.size)
        TUNNEL_MAGIC.copyInto(packet)
        packet[4] = type.code.toByte()
        packet[5] = from.toByte()
        packet[6] = dest.toByte()
        body.copyInto(packet, 7)
        runCatching { tunnel.send(DatagramPacket(packet, packet.size, to)) }
    }

    private fun readTunnel() {
        val buffer = ByteArray(65536)
        while (!closed) {
            val packet = DatagramPacket(buffer, buffer.size)
            try {
                tunnel.receive(packet)
            } catch (e: Exception) {
                if (closed) return
                continue
            }
            if (packet.length < 7 || !buffer.startsWith(TUNNEL_MAGIC)) continue
            val type = buffer[4].toInt().toChar()
            val from = buffer[5].toInt() and 0xFF
            val to = buffer[6].toInt() and 0xFF
            val sender = packet.socketAddress as InetSocketAddress
            if (hosting) {
                // Only from a player that's connected, from the same address as its connection.
                val link = links[from] ?: continue
                if (link.socket.inetAddress != sender.address) continue
                playerUdp[from] = sender
                if (type == 'K') sendTunnel(sender, 'K', HOST, from, ByteArray(0))
            } else {
                if (sender.address != hostUdp?.address) continue
                if (type == 'K') hostUdpWorks = true
            }
            if (type == 'U') route(type, from, to, buffer.copyOfRange(7, packet.length))
        }
    }

    // ---- With the game ----

    private fun startRelay() {
        thread(name = "pclan-relay-udp", isDaemon = true) {
            val buffer = ByteArray(65536)
            while (!closed) {
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    relayUdp.receive(packet)
                } catch (e: Exception) {
                    if (closed) break
                    continue
                }
                if (!packet.address.isLoopbackAddress) continue
                fromGame(buffer, packet.length, packet.port)
            }
        }
        thread(name = "pclan-relay-tcp", isDaemon = true) {
            while (!closed) {
                val socket = runCatching { relayTcp.accept() }.getOrNull() ?: break
                val target = outbound[outKey(TCP, socket.port)]
                if (target == null) {
                    runCatching { socket.close() }
                    continue
                }
                socket.tcpNoDelay = true
                val id = nextStream.getAndIncrement()
                val key = key(me, id)
                streams[key] = Stream(socket, target.member, socket.localPort)
                send('O', target.member, ByteArray(8).also { it.putInt(0, id); put16(it, 4, socket.port); put16(it, 6, target.port) })
                pump(key, me, id)
            }
        }
    }

    private fun fromGame(data: ByteArray, length: Int, port: Int) {
        val tagged = length >= 6 && data.startsWith(MAGIC)
        val type = if (tagged) data[4].toInt().toChar() else ' '
        when {
            // A packet for the virtual network: [RFVL D 0][to: 4][port: 2][data].
            tagged && type == 'D' && length >= HEADER -> {
                val to = member(data, 6) ?: return
                sendUdp(to, port, u16(data, 10), data, HEADER, length)
            }
            // "Local port L is connecting to 10.77.0.x:p": [RFVL C T|U][ip: 4][p: 2][L: 2].
            tagged && type == 'C' && length >= 14 -> {
                val proto = data[5].toInt().toChar()
                val to = member(data, 6)
                if (to == null || to == BROADCAST) return answer(port, null)
                outbound[outKey(proto, u16(data, 12))] = Target(to, u16(data, 10))
                answer(port, Target(to, u16(data, 10)))
            }
            // Who's behind our own relayed socket (O) or a connection the relay made to the game (I): [RFVL O|I T|U][port: 2].
            tagged && (type == 'O' || type == 'I') && length >= 8 -> {
                val p = u16(data, 6)
                answer(port, if (type == 'O') outbound[outKey(data[5].toInt().toChar(), p)] else inbound[p])
            }
            // Sent on a UDP socket the game connected to 10.77.0.x (no header: where it goes was noted at connect).
            else -> outbound[outKey(UDP, port)]?.let { sendUdp(it.member, port, it.port, data, 0, length) }
        }
    }

    private fun answer(port: Int, target: Target?) {
        val reply = ByteArray(12)
        MAGIC.copyInto(reply)
        reply[4] = (if (target != null) 'A' else 'N').code.toByte()
        if (target != null) {
            ipBytes(target.member).copyInto(reply, 6)
            put16(reply, 10, target.port)
        }
        runCatching { relayUdp.send(DatagramPacket(reply, reply.size, InetSocketAddress(LOOPBACK, port))) }
    }

    private fun sendUdp(to: Int, fromPort: Int, toPort: Int, data: ByteArray, start: Int, end: Int) {
        val body = ByteArray(4 + end - start)
        put16(body, 0, fromPort)
        put16(body, 2, toPort)
        data.copyInto(body, 4, start, end)
        send('U', to, body)
    }

    /** A packet from another player to the game: [RFVL D 0][from: 4][port: 2][data], from the relay's port. */
    private fun deliverUdp(from: Int, fromPort: Int, toPort: Int, body: ByteArray, start: Int) {
        val packet = ByteArray(HEADER + body.size - start)
        MAGIC.copyInto(packet)
        packet[4] = 'D'.code.toByte()
        ipBytes(from).copyInto(packet, 6)
        put16(packet, 10, fromPort)
        body.copyInto(packet, HEADER, start)
        runCatching { relayUdp.send(DatagramPacket(packet, packet.size, InetSocketAddress(LOOPBACK, toPort))) }
    }

    /** Another player's game connecting to a TCP port of this one's: the relay connects to it on their behalf. */
    private fun openInbound(from: Int, id: Int, fromPort: Int, toPort: Int) {
        val key = key(from, id)
        val socket = Socket()
        try {
            socket.bind(InetSocketAddress(LOOPBACK, 0))
            inbound[socket.localPort] = Target(from, fromPort)
            socket.connect(InetSocketAddress(LOOPBACK, toPort), 2000)
            socket.tcpNoDelay = true
            streams[key] = Stream(socket, from, socket.localPort)
            pump(key, from, id)
        } catch (_: Exception) {
            inbound.remove(socket.localPort)
            runCatching { socket.close() }
            send('C', from, ByteArray(5).also { it[0] = from.toByte(); it.putInt(1, id) })
        }
    }

    /** Copies what the game writes on a relayed TCP connection to the other end (on a thread of its own). */
    private fun pump(key: Long, opener: Int, id: Int) {
        val stream = streams[key] ?: return
        thread(name = "pclan-stream", isDaemon = true) {
            val buffer = ByteArray(16384 + 5)
            buffer[0] = opener.toByte()
            buffer.putInt(1, id)
            try {
                val input = stream.socket.getInputStream()
                while (true) {
                    val n = input.read(buffer, 5, buffer.size - 5)
                    if (n < 0) break
                    send('S', stream.peer, buffer.copyOf(n + 5))
                }
            } catch (_: Exception) {
            }
            closeStream(key, tell = true)
        }
    }

    private fun closeStream(key: Long, tell: Boolean) {
        val stream = streams.remove(key) ?: return
        inbound.remove(stream.localPort)
        runCatching { stream.socket.close() }
        if (tell) send('C', stream.peer, ByteArray(5).also { it[0] = (key ushr 32).toByte(); it.putInt(1, key.toInt()) })
    }

    private fun dropStreams(member: Int) {
        streams.filterValues { it.peer == member }.keys.forEach { closeStream(it, tell = false) }
    }

    /** Which member a virtual address means: 1–9, or [BROADCAST] for 10.77.0.255 and 255.255.255.255. */
    private fun member(data: ByteArray, at: Int): Int? {
        val a = IntArray(4) { data[at + it].toInt() and 0xFF }
        if (a.all { it == 255 }) return BROADCAST
        if (a[0] != 10 || a[1] != 77 || a[2] != 0) return null
        return when (a[3]) {
            255 -> BROADCAST
            in HOST..MAX_MEMBER -> a[3].takeIf { it != me }
            else -> null
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        configFile.delete()
        runCatching { server?.close() }
        links.values.forEach { it.close() }
        hostLink?.close()
        streams.keys.toList().forEach { closeStream(it, tell = false) }
        runCatching { tunnel.close() }
        runCatching { relayUdp.close() }
        runCatching { relayTcp.close() }
    }

    companion object {
        /** The port between devices (TCP and UDP). The retro player's netplay uses 55450–55451. */
        const val PORT = 55460
        const val HOST = 1
        private const val MAX_MEMBER = 9
        /** How long a player keeps trying to get back to the host after the connection drops. */
        private const val RECONNECT_MS = 3 * 60_000L
        private const val BROADCAST = 255
        private const val VERSION = "RFLAN1"
        private const val HEADER = 12
        private const val MAX_FRAME = 1 shl 20
        private const val TCP = 'T'
        private const val UDP = 'U'
        private val MAGIC = "RFVL".toByteArray()
        private val TUNNEL_MAGIC = "RFT1".toByteArray()
        private val LOOPBACK: InetAddress = InetAddress.getByAddress(byteArrayOf(127, 0, 0, 1))

        /** Hosts a virtual LAN on [PORT] (call off the main thread). Throws when the port can't be used. */
        fun host(configFile: File, name: String, port: Int = PORT): PcLan =
            PcLan(configFile, name, hosting = true).apply {
                try {
                    startHost(port)
                } catch (e: Exception) {
                    close()
                    throw e
                }
            }

        /** Joins the virtual LAN hosted at [address] (call off the main thread). Throws when it can't connect. */
        fun join(configFile: File, name: String, address: String, port: Int = PORT): PcLan =
            PcLan(configFile, name, hosting = false).apply {
                try {
                    startJoin(address, port)
                    publish()
                } catch (e: Exception) {
                    close()
                    throw e
                }
            }

        /** Where the virtual LAN's settings for libpcexec.so live (named in Wine's environment as PCNET_FILE). */
        fun configFile(runtimeRoot: File) = File(runtimeRoot, "pcnet.cfg")

        fun address(member: Int) = "10.77.0.$member"

        private fun relayPorts(): Pair<DatagramSocket, ServerSocket> {
            repeat(20) {
                val udp = DatagramSocket(InetSocketAddress(LOOPBACK, 0))
                try {
                    return udp to ServerSocket(udp.localPort, 50, LOOPBACK)
                } catch (_: Exception) {
                    udp.close()
                }
            }
            throw java.io.IOException("No free port for the relay")
        }
        private fun ipBytes(member: Int) = byteArrayOf(10, 77, 0, member.toByte())
        private fun key(opener: Int, id: Int) = (opener.toLong() shl 32) or (id.toLong() and 0xFFFFFFFFL)
        private fun outKey(proto: Char, port: Int) = (proto.code shl 16) or port
        private fun u16(b: ByteArray, at: Int) = ((b[at].toInt() and 0xFF) shl 8) or (b[at + 1].toInt() and 0xFF)
        private fun put16(b: ByteArray, at: Int, v: Int) {
            b[at] = (v shr 8).toByte()
            b[at + 1] = v.toByte()
        }
        private fun ByteArray.int(at: Int) = (u16(this, at) shl 16) or u16(this, at + 2)
        private fun ByteArray.putInt(at: Int, v: Int) {
            put16(this, at, v ushr 16)
            put16(this, at + 2, v)
        }
        private fun ByteArray.startsWith(prefix: ByteArray) = size >= prefix.size && prefix.indices.all { this[it] == prefix[it] }
    }
}
