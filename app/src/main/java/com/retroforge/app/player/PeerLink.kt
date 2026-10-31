package com.retroforge.app.player

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.concurrent.thread

/** A device paired for swapping saves: its id, its name and the secret both devices keep. */
data class Peer(val id: String, val name: String, val secret: String)

/** A paired device's RetroForge seen on the network. */
data class FoundPeer(val id: String, val name: String, val host: String, val port: Int)

/** The other device said no: it isn't paired with this one (any more). */
class NotPairedException : IOException("not paired")

/** The built-in player's own save folders, by their name in [SaveSync], as a [SyncTarget] for another device. */
class FilesTarget(private val folders: Map<String, File>) : SyncTarget {
    override fun list(): Map<String, SyncedFile> = folders.flatMap { (name, root) ->
        root.walkTopDown().onEnter { it == root || it.name !in SaveSync.SKIP_FOLDERS }.filter { it.isFile }
            .map { "$name/" + it.relativeTo(root).invariantSeparatorsPath to SyncedFile(it.length(), it.lastModified()) }.toList()
    }.toMap()

    /** The file for [path]; never one outside the save folders. */
    private fun file(path: String): File = folders[path.substringBefore('/')]?.let { root ->
        File(root, path.substringAfter('/')).takeIf { it.canonicalPath.startsWith(root.canonicalPath + File.separator) }
    } ?: throw FileNotFoundException(path)

    override fun read(path: String) = file(path).readBytes()

    override fun write(path: String, bytes: ByteArray): Long {
        val file = file(path)
        file.parentFile?.mkdirs()
        val part = File(file.path + ".part")
        part.writeBytes(bytes)
        if (!part.renameTo(file)) {
            file.delete()
            if (!part.renameTo(file)) throw IOException(path)
        }
        return file.lastModified()
    }
}

/**
 * Saves swapped between two RetroForges on the same Wi-Fi, with no network drive in between. Each device answers
 * "who's there?" broadcasts and serves its saves over TCP. Two devices pair once with a short code and keep a shared
 * secret; after that every connection proves itself with an HMAC of a fresh nonce, so nobody else on the network can
 * read or change the saves. The other device's saves are a [SyncTarget], so [SaveSync] does the swapping.
 */
object PeerLink {
    const val PORT = 55470
    const val ASK_PORT = 55471
    private const val ASK = "RFPS?"
    private const val ANSWER = "RFPS!"
    private const val HELLO = "RFPEER1"
    private const val MAX_FILE = 64 * 1024 * 1024
    private val random = SecureRandom()

    fun hmac(secret: String, nonce: ByteArray): ByteArray =
        Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(secret.toByteArray(), "HmacSHA256")) }.doFinal(nonce)

    fun newSecret(): String = ByteArray(32).also(random::nextBytes).joinToString("") { "%02x".format(it) }

    /** A six-digit pairing code. */
    fun newCode(): String = "%06d".format(random.nextInt(1_000_000))

    /**
     * Serves this device's saves ([files]) to paired devices ([secretOf] gives a paired device's secret by its id) and
     * answers "who's there?" on [askPort]. While [pairCode] is set, a device that sends it is paired: [onPaired] gets
     * it, and the code is used up. Five wrong codes close pairing.
     */
    class Server(
        private val myId: String,
        private val myName: String,
        private val files: () -> SyncTarget,
        private val secretOf: (String) -> String?,
        private val onPaired: (Peer) -> Unit,
        port: Int = PORT,
        private val askPort: Int = ASK_PORT,
    ) : Closeable {
        @Volatile var pairCode: String? = null
            set(value) { field = value; wrong = 0 }
        @Volatile private var wrong = 0
        @Volatile private var running = true
        private val server = runCatching { ServerSocket(port) }.getOrElse { ServerSocket(0) }
        private var asks: DatagramSocket? = null
        val port: Int get() = server.localPort

        init {
            thread(name = "peer-sync-server", isDaemon = true) {
                while (running) {
                    val socket = runCatching { server.accept() }.getOrNull() ?: continue
                    thread(name = "peer-sync-client", isDaemon = true) { runCatching { serve(socket) } }
                }
            }
            thread(name = "peer-sync-announcer", isDaemon = true) {
                runCatching {
                    DatagramSocket(null).use { s ->
                        asks = s
                        s.reuseAddress = true
                        s.bind(InetSocketAddress(askPort))
                        val buffer = ByteArray(64)
                        while (running) {
                            val packet = DatagramPacket(buffer, buffer.size)
                            s.receive(packet)
                            if (String(packet.data, 0, packet.length) != ASK) continue
                            val answer = listOf(ANSWER, port.toString(), myId, myName).joinToString("\n").toByteArray()
                            s.send(DatagramPacket(answer, answer.size, packet.address, packet.port))
                        }
                    }
                }
            }
        }

        private fun serve(socket: Socket) = socket.use { s ->
            s.soTimeout = 30_000
            val input = DataInputStream(BufferedInputStream(s.getInputStream()))
            val out = DataOutputStream(BufferedOutputStream(s.getOutputStream()))
            val nonce = ByteArray(16).also(random::nextBytes)
            out.writeUTF(HELLO)
            out.write(nonce)
            out.flush()
            when (input.readUTF()) {
                "PAIR" -> {
                    val code = input.readUTF()
                    val peer = Peer(input.readUTF(), input.readUTF(), newSecret())
                    val wanted = pairCode
                    if (wanted == null || !MessageDigest.isEqual(code.toByteArray(), wanted.toByteArray())) {
                        if (++wrong >= 5) pairCode = null
                        out.writeBoolean(false)
                        out.flush()
                        return
                    }
                    pairCode = null
                    onPaired(peer)
                    out.writeBoolean(true)
                    out.writeUTF(myId)
                    out.writeUTF(myName)
                    out.writeUTF(peer.secret)
                    out.flush()
                }
                "AUTH" -> {
                    val id = input.readUTF()
                    val mac = ByteArray(32).also(input::readFully)
                    val secret = secretOf(id)
                    val ok = secret != null && MessageDigest.isEqual(mac, hmac(secret, nonce))
                    out.writeBoolean(ok)
                    out.flush()
                    if (!ok) return
                    val target = files()
                    while (true) {
                        when (input.readUTF()) {
                            "LIST" -> {
                                val all = target.list()
                                out.writeInt(all.size)
                                all.forEach { (path, f) -> out.writeUTF(path); out.writeLong(f.size); out.writeLong(f.modified) }
                            }
                            "READ" -> {
                                val path = input.readUTF()
                                val bytes = runCatching { target.read(path) }.getOrNull()
                                out.writeInt(bytes?.size ?: -1)
                                bytes?.let(out::write)
                            }
                            "WRITE" -> {
                                val path = input.readUTF()
                                val size = input.readInt()
                                if (size !in 0..MAX_FILE) return
                                val bytes = ByteArray(size).also(input::readFully)
                                out.writeLong(runCatching { target.write(path, bytes) }.getOrDefault(-1L))
                            }
                            else -> return
                        }
                        out.flush()
                    }
                }
            }
        }

        override fun close() {
            running = false
            runCatching { server.close() }
            runCatching { asks?.close() }
        }
    }

    /** Pairs with the device at [host] using the [code] it shows; its [Peer] (with the new shared secret), or null if the code was wrong. */
    fun pair(host: String, port: Int, code: String, myId: String, myName: String): Peer? = Socket().use { s ->
        s.connect(InetSocketAddress(host, port), 3000)
        s.soTimeout = 15_000
        val input = DataInputStream(BufferedInputStream(s.getInputStream()))
        val out = DataOutputStream(BufferedOutputStream(s.getOutputStream()))
        if (input.readUTF() != HELLO) throw IOException("not RetroForge")
        input.readFully(ByteArray(16))
        out.writeUTF("PAIR")
        out.writeUTF(code.trim())
        out.writeUTF(myId)
        out.writeUTF(myName)
        out.flush()
        if (!input.readBoolean()) return null
        Peer(input.readUTF(), input.readUTF(), input.readUTF())
    }

    /** A paired device's saves, over one connection. Close it when done. */
    class Remote(host: String, port: Int, myId: String, secret: String) : SyncTarget, Closeable {
        private val socket = Socket().apply { connect(InetSocketAddress(host, port), 3000); soTimeout = 30_000 }
        private val input = DataInputStream(BufferedInputStream(socket.getInputStream()))
        private val out = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))

        init {
            try {
                if (input.readUTF() != HELLO) throw IOException("not RetroForge")
                val nonce = ByteArray(16).also(input::readFully)
                out.writeUTF("AUTH")
                out.writeUTF(myId)
                out.write(hmac(secret, nonce))
                out.flush()
                if (!input.readBoolean()) throw NotPairedException()
            } catch (e: IOException) {
                close()
                throw e
            }
        }

        override fun list(): Map<String, SyncedFile> {
            out.writeUTF("LIST")
            out.flush()
            return (0 until input.readInt()).associate { input.readUTF() to SyncedFile(input.readLong(), input.readLong()) }
        }

        override fun read(path: String): ByteArray {
            out.writeUTF("READ")
            out.writeUTF(path)
            out.flush()
            val size = input.readInt()
            if (size < 0) throw FileNotFoundException(path)
            return ByteArray(size).also(input::readFully)
        }

        override fun write(path: String, bytes: ByteArray): Long {
            out.writeUTF("WRITE")
            out.writeUTF(path)
            out.writeInt(bytes.size)
            out.write(bytes)
            out.flush()
            return input.readLong().also { if (it < 0) throw IOException(path) }
        }

        override fun close() {
            runCatching { out.writeUTF("BYE"); out.flush() }
            runCatching { socket.close() }
        }
    }

    /** Asks the network which RetroForges are there and collects the answers for [waitMs], leaving out [myId]. Call off the main thread. */
    fun find(myId: String, waitMs: Int = 1500, askPort: Int = ASK_PORT): List<FoundPeer> {
        val found = linkedMapOf<String, FoundPeer>()
        runCatching {
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
                        targets.forEach { runCatching { s.send(DatagramPacket(ask, ask.size, it, askPort)) } }
                        lastAsk = System.currentTimeMillis()
                    }
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        s.receive(packet)
                        val parts = String(packet.data, 0, packet.length).split("\n")
                        if (parts.size >= 4 && parts[0] == ANSWER && parts[2] != myId) {
                            found[parts[2]] = FoundPeer(parts[2], parts[3], packet.address.hostAddress.orEmpty(), parts[1].toIntOrNull() ?: PORT)
                        }
                    } catch (_: SocketTimeoutException) {
                    }
                }
            }
        }
        return found.values.toList()
    }
}
