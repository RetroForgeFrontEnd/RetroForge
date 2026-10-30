package com.retroforge.stream

import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Base64
import com.limelight.Game
import com.limelight.binding.PlatformBinding
import com.limelight.computers.IdentityManager
import com.limelight.nvstream.http.ComputerDetails
import com.limelight.nvstream.http.HostHttpResponseException
import com.limelight.nvstream.http.NvHTTP
import com.limelight.nvstream.http.PairingManager
import com.limelight.nvstream.mdns.JmDNSDiscoveryAgent
import com.limelight.nvstream.mdns.MdnsComputer
import com.limelight.nvstream.mdns.MdnsDiscoveryListener
import com.limelight.nvstream.mdns.NsdManagerDiscoveryAgent
import com.limelight.preferences.StreamSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate

/** A PC the user streams from: Sunshine, or NVIDIA GameStream on older GeForce Experience. */
data class StreamHost(
    val uuid: String,
    val name: String,
    val address: String,
    val port: Int = NvHTTP.DEFAULT_HTTP_PORT,
    val httpsPort: Int = 0,
    /** The PC's certificate, saved when pairing worked; null until then. */
    val serverCert: String? = null,
) {
    val paired: Boolean get() = serverCert != null
}

data class StreamApp(val id: Int, val name: String, val hdr: Boolean)

/** What the PC said when asked just now. */
data class HostStatus(val online: Boolean, val paired: Boolean, val runningAppId: Int, val problem: String? = null)

/** A PC found on the network by mDNS (Sunshine and GameStream announce themselves as _nvstream._tcp). */
data class FoundHost(val name: String, val address: String, val port: Int)

/**
 * RetroForge's side of streaming from the user's own PC, over Moonlight's protocol code (com.limelight): finding PCs,
 * pairing with a PIN, listing their apps, and the intent for Moonlight's full-screen stream.
 */
class PcStreaming(context: Context) {
    private val context = context.applicationContext
    private val prefs = context.getSharedPreferences("pc_stream_hosts", Context.MODE_PRIVATE)
    private val crypto by lazy { PlatformBinding.getCryptoProvider(this.context) }
    private val uniqueId by lazy { IdentityManager(this.context).uniqueId }

    fun hosts(): List<StreamHost> {
        val array = runCatching { JSONArray(prefs.getString("hosts", "[]")) }.getOrElse { JSONArray() }
        return (0 until array.length()).map { i ->
            val o = array.getJSONObject(i)
            StreamHost(
                uuid = o.getString("uuid"),
                name = o.getString("name"),
                address = o.getString("address"),
                port = o.optInt("port", NvHTTP.DEFAULT_HTTP_PORT),
                httpsPort = o.optInt("httpsPort", 0),
                serverCert = o.optString("cert").ifBlank { null },
            )
        }
    }

    private fun save(hosts: List<StreamHost>) {
        val array = JSONArray()
        hosts.forEach { h ->
            array.put(
                JSONObject().put("uuid", h.uuid).put("name", h.name).put("address", h.address).put("port", h.port)
                    .put("httpsPort", h.httpsPort).put("cert", h.serverCert.orEmpty()),
            )
        }
        prefs.edit().putString("hosts", array.toString()).apply()
    }

    private fun put(host: StreamHost) = save(hosts().filterNot { it.uuid == host.uuid } + host)

    fun remove(host: StreamHost) = save(hosts().filterNot { it.uuid == host.uuid })

    /** Listens for PCs announcing themselves on the local network for [millis], then returns what answered. */
    suspend fun discover(millis: Long = 4000): List<FoundHost> {
        val found = java.util.concurrent.ConcurrentHashMap<String, FoundHost>()
        val listener = object : MdnsDiscoveryListener {
            override fun notifyComputerAdded(computer: MdnsComputer) {
                val address = computer.localAddress?.hostAddress ?: computer.ipv6Address?.hostAddress ?: return
                found[address] = FoundHost(computer.name, address, computer.port)
            }

            override fun notifyDiscoveryFailure(e: Exception) {}
        }
        // Same split as Moonlight: NsdManager handles mDNS properly from Android 14, jmDNS before that.
        val agent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) NsdManagerDiscoveryAgent(context, listener)
        else JmDNSDiscoveryAgent(context, listener)
        withContext(Dispatchers.IO) { agent.startDiscovery(1000) }
        try {
            delay(millis)
        } finally {
            withContext(Dispatchers.IO) { agent.stopDiscovery() }
        }
        return found.values.sortedBy { it.name }
    }

    private fun http(host: StreamHost) = NvHTTP(
        ComputerDetails.AddressTuple(host.address, host.port), host.httpsPort, uniqueId, host.serverCert?.let(::decodeCert), crypto,
    )

    /**
     * Asks the PC at [address] (an IP or name, optionally with :port) who it is and keeps it in the list. Throws if
     * nothing answers there.
     */
    suspend fun add(address: String): StreamHost = withContext(Dispatchers.IO) {
        val (hostPart, port) = splitAddress(address.trim())
        val http = NvHTTP(ComputerDetails.AddressTuple(hostPart, port), 0, uniqueId, null, crypto)
        val details = http.getComputerDetails(true)
        val existing = hosts().firstOrNull { it.uuid == details.uuid }
        val host = StreamHost(details.uuid, details.name, hostPart, port, details.httpsPort, existing?.serverCert)
        put(host)
        host
    }

    suspend fun status(host: StreamHost): HostStatus = withContext(Dispatchers.IO) {
        runCatching {
            val http = http(host)
            val info = http.getServerInfo(true)
            val details = http.getComputerDetails(info)
            if (details.httpsPort != 0 && details.httpsPort != host.httpsPort) put(host.copy(httpsPort = details.httpsPort))
            HostStatus(true, host.paired && details.pairState == PairingManager.PairState.PAIRED, details.runningGameId)
        }.getOrElse { e ->
            // 401 means the PC no longer accepts this phone's pairing (unpaired on the PC, or reinstalled).
            if (e is HostHttpResponseException && e.errorCode == 401) HostStatus(true, false, 0)
            else HostStatus(false, false, 0, e.message)
        }
    }

    /**
     * Pairs with the PC. The user types [pin] into Sunshine's web page (PIN tab) or GeForce Experience's prompt while
     * this waits; it returns when they do, or when the PC refuses.
     */
    suspend fun pair(host: StreamHost, pin: String): PairingManager.PairState = withContext(Dispatchers.IO) {
        val http = NvHTTP(ComputerDetails.AddressTuple(host.address, host.port), host.httpsPort, uniqueId, null, crypto)
        val manager = http.pairingManager
        val result = manager.pair(http.getServerInfo(true), pin)
        if (result == PairingManager.PairState.PAIRED) {
            put(host.copy(serverCert = Base64.encodeToString(manager.pairedCert.encoded, Base64.NO_WRAP)))
        }
        result
    }

    /** Forgets the pairing on this phone and asks the PC to forget it too (if it's reachable). */
    suspend fun unpair(host: StreamHost) = withContext(Dispatchers.IO) {
        runCatching { http(host).unpair() }
        put(host.copy(serverCert = null))
    }

    suspend fun apps(host: StreamHost): List<StreamApp> = withContext(Dispatchers.IO) {
        http(host).appList.map { StreamApp(it.appId, it.appName, it.isHdrSupported) }
    }

    /** The app's box art from the PC (Sunshine has one for each app), or null. */
    suspend fun boxArt(host: StreamHost, app: StreamApp): ByteArray? = withContext(Dispatchers.IO) {
        runCatching { http(host).getBoxArt(com.limelight.nvstream.http.NvApp(app.name, app.id, app.hdr)).use { it.readBytes() } }.getOrNull()
    }

    /** Ends whatever game the PC is streaming (only one started from a Moonlight-protocol client). */
    suspend fun quit(host: StreamHost): Boolean = withContext(Dispatchers.IO) { http(host).quitApp() }

    /** Moonlight's full-screen stream of [app]; back (or the controller's quit combo) ends it and returns here. */
    fun streamIntent(host: StreamHost, app: StreamApp): Intent = Intent(context, Game::class.java)
        .putExtra(Game.EXTRA_HOST, host.address)
        .putExtra(Game.EXTRA_PORT, host.port)
        .putExtra(Game.EXTRA_HTTPS_PORT, host.httpsPort)
        .putExtra(Game.EXTRA_APP_NAME, app.name)
        .putExtra(Game.EXTRA_APP_ID, app.id)
        .putExtra(Game.EXTRA_APP_HDR, app.hdr)
        .putExtra(Game.EXTRA_UNIQUEID, uniqueId)
        .putExtra(Game.EXTRA_PC_UUID, host.uuid)
        .putExtra(Game.EXTRA_PC_NAME, host.name)
        .putExtra(Game.EXTRA_SERVER_CERT, host.serverCert?.let { Base64.decode(it, Base64.NO_WRAP) })

    /** Moonlight's stream settings: resolution, frame rate, bitrate, codec, on-screen controls, mouse and so on. */
    fun settingsIntent(): Intent = Intent(context, StreamSettings::class.java)

    companion object {
        fun newPin(): String = PairingManager.generatePinString()

        internal fun splitAddress(text: String): Pair<String, Int> {
            // [v6]:port, v4:port, or a bare address/name.
            if (text.startsWith("[")) {
                val end = text.indexOf(']')
                val port = text.substring(end + 1).removePrefix(":").toIntOrNull() ?: NvHTTP.DEFAULT_HTTP_PORT
                return text.substring(1, end) to port
            }
            val colon = text.lastIndexOf(':')
            if (colon > 0 && text.indexOf(':') == colon) {
                text.substring(colon + 1).toIntOrNull()?.let { return text.substring(0, colon) to it }
            }
            return text to NvHTTP.DEFAULT_HTTP_PORT
        }

        private fun decodeCert(base64: String): X509Certificate =
            CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(Base64.decode(base64, Base64.NO_WRAP))) as X509Certificate
    }
}
