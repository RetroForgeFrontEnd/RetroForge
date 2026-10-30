package com.retroforge.app.network

import com.retroforge.app.R
import com.retroforge.core.ui.str
import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.net.DatagramPacket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.NetworkInterface
import java.net.SocketTimeoutException
import java.net.URL
import javax.xml.parsers.DocumentBuilderFactory

/** A DLNA/UPnP media server on the network (Plex, Jellyfin, Emby, Windows Media Player, a NAS …). */
data class MediaServer(val name: String, val location: String, val controlUrl: String)

/** A folder or a video on a media server. [url] is where a video streams from; folders have none. */
data class MediaEntry(val id: String, val title: String, val url: String?, val art: String?, val isFolder: Boolean, val count: Int? = null)

/**
 * Finds media servers and lists what they share, the way TVs and VR apps (Bigscreen) do: no account or sign-in, only
 * the server's own DLNA sharing. Plex shares this way when Settings → DLNA → "Enable the DLNA server" is on.
 */
object MediaServers {
    private const val SSDP = "239.255.255.250"
    private const val CONTENT_DIRECTORY = "urn:schemas-upnp-org:service:ContentDirectory:1"
    /** Plex's DLNA server, asked for directly when a network doesn't pass the search on (USB tethering, some routers). */
    private const val PLEX_DLNA_PORT = 32469

    suspend fun find(context: Context, timeoutMs: Int = 3000): List<MediaServer> = withContext(Dispatchers.IO) {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        val lock = runCatching { wifi?.createMulticastLock("retroforge-dlna")?.apply { setReferenceCounted(false); acquire() } }.getOrNull()
        try {
            coroutineScope {
                val searched = async { search(timeoutMs) }
                val plex = async { probePlex() }
                (searched.await() + plex.await()).distinct()
                    .map { location -> async { runCatching { describe(location) }.getOrNull() } }
                    .awaitAll().filterNotNull().distinctBy { it.controlUrl }.sortedBy { it.name.lowercase() }
            }
        } finally {
            runCatching { lock?.release() }
        }
    }

    /** SSDP M-SEARCH on every network this device is on; returns the description addresses that answered. */
    private fun search(timeoutMs: Int): List<String> {
        val message = (
            "M-SEARCH * HTTP/1.1\r\nHOST: $SSDP:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: urn:schemas-upnp-org:device:MediaServer:1\r\n\r\n"
            ).toByteArray()
        val found = mutableSetOf<String>()
        val sockets = NetworkSearch.ownAddresses().mapNotNull { own ->
            runCatching {
                MulticastSocket(InetSocketAddress(InetAddress.getByName(own), 0)).apply {
                    NetworkInterface.getByInetAddress(InetAddress.getByName(own))?.let { networkInterface = it }
                    timeToLive = 4
                    soTimeout = 300
                }
            }.getOrNull()
        }
        try {
            repeat(2) { sockets.forEach { s -> runCatching { s.send(DatagramPacket(message, message.size, InetAddress.getByName(SSDP), 1900)) } } }
            val end = System.currentTimeMillis() + timeoutMs
            val buffer = ByteArray(2048)
            while (System.currentTimeMillis() < end) {
                sockets.forEach { s ->
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        s.receive(packet)
                        location(String(packet.data, 0, packet.length))?.let(found::add)
                    } catch (_: SocketTimeoutException) {
                    } catch (_: Exception) {
                    }
                }
            }
        } finally {
            sockets.forEach { runCatching { it.close() } }
        }
        return found.toList()
    }

    /** The LOCATION header of an SSDP answer. */
    internal fun location(answer: String): String? =
        answer.lineSequence().firstOrNull { it.startsWith("LOCATION:", ignoreCase = true) }?.substringAfter(':')?.trim()?.ifEmpty { null }

    /** Every address on this device's networks with Plex's DLNA port open. */
    private suspend fun probePlex(): List<String> = coroutineScope {
        val own = NetworkSearch.ownAddresses()
        val gate = kotlinx.coroutines.sync.Semaphore(48)
        own.map { it.substringBeforeLast('.') }.distinct().flatMap { prefix -> (1..254).map { "$prefix.$it" } }
            .map { address ->
                async {
                    gate.acquire()
                    try {
                        val open = runCatching {
                            // The gateway (".1", or ".2" on the Android emulator, where it's the PC itself) gets longer to answer.
                            val patient = address.endsWith(".1") || address.endsWith(".2")
                            java.net.Socket().use { it.connect(InetSocketAddress(address, PLEX_DLNA_PORT), if (patient) 1500 else 500) }
                            true
                        }.getOrDefault(false)
                        if (open) "http://$address:$PLEX_DLNA_PORT/DeviceDescription.xml" else null
                    } finally {
                        gate.release()
                    }
                }
            }.awaitAll().filterNotNull()
    }

    /** Reads a server's description: its name and where to ask for its folders. Null when it isn't a media server. */
    private fun describe(location: String): MediaServer? {
        val xml = http(location) ?: return null
        val doc = parse(xml)
        val name = doc.getElementsByTagName("friendlyName").item(0)?.textContent?.trim() ?: return null
        val base = doc.getElementsByTagName("URLBase").item(0)?.textContent?.trim()?.ifEmpty { null } ?: location
        val services = doc.getElementsByTagName("service")
        for (i in 0 until services.length) {
            val service = services.item(i) as Element
            if (service.child("serviceType")?.startsWith("urn:schemas-upnp-org:service:ContentDirectory") != true) continue
            val control = service.child("controlURL") ?: continue
            return MediaServer(name, location, URL(URL(base), control).toString())
        }
        return null
    }

    /** What's in folder [id] ("0" is the top). */
    suspend fun browse(server: MediaServer, id: String = "0"): List<MediaEntry> = withContext(Dispatchers.IO) {
        val entries = mutableListOf<MediaEntry>()
        var start = 0
        while (true) {
            val body = """<?xml version="1.0" encoding="utf-8"?>
<s:Envelope xmlns:s="http://schemas.xmlsoap.org/soap/envelope/" s:encodingStyle="http://schemas.xmlsoap.org/soap/encoding/">
<s:Body><u:Browse xmlns:u="$CONTENT_DIRECTORY"><ObjectID>${escape(id)}</ObjectID><BrowseFlag>BrowseDirectChildren</BrowseFlag>
<Filter>*</Filter><StartingIndex>$start</StartingIndex><RequestedCount>200</RequestedCount><SortCriteria></SortCriteria></u:Browse></s:Body></s:Envelope>"""
            val answer = http(server.controlUrl, body, "\"$CONTENT_DIRECTORY#Browse\"") ?: error(str(R.string.media_servers_didn_t_answer, server.name))
            val doc = parse(answer)
            val result = doc.getElementsByTagName("Result").item(0)?.textContent ?: break
            val page = didl(result).map { it.copy(url = it.url?.let { u -> reachable(u, server) }, art = it.art?.let { u -> reachable(u, server) }) }
            entries += page
            val total = doc.getElementsByTagName("TotalMatches").item(0)?.textContent?.trim()?.toIntOrNull() ?: break
            start += doc.getElementsByTagName("NumberReturned").item(0)?.textContent?.trim()?.toIntOrNull() ?: page.size
            if (page.isEmpty() || start >= total || entries.size >= 5000) break
        }
        entries
    }

    /**
     * A server on a PC with several networks (Wi-Fi and USB tethering) can name its address on the other one in its
     * links; those go to the address this device reached it at instead (same machine, so the same port answers).
     */
    internal fun reachable(url: String, server: MediaServer): String = runCatching {
        val link = URL(url)
        val reached = URL(server.controlUrl).host
        val host = link.host
        val isPrivate = InetAddress.getByName(host).let { it.isSiteLocalAddress || it.isLoopbackAddress }
        if (host == reached || !isPrivate) url else url.replaceFirst("//$host", "//$reached")
    }.getOrDefault(url)

    /** Folders and videos from a DIDL-Lite listing (music and photos are left out). */
    internal fun didl(xml: String): List<MediaEntry> {
        val doc = parse(xml)
        val list = mutableListOf<MediaEntry>()
        val root = doc.documentElement
        val nodes = root.childNodes
        for (i in 0 until nodes.length) {
            val e = nodes.item(i) as? Element ?: continue
            val id = e.getAttribute("id")
            val title = e.child("title")?.trim().orEmpty().ifEmpty { str(R.string.media_servers_untitled) }
            val art = e.child("albumArtURI")
            when (e.localName ?: e.tagName.substringAfter(':')) {
                "container" -> {
                    val upnpClass = e.child("class").orEmpty()
                    if (upnpClass.contains("musicAlbum") || upnpClass.contains("photoAlbum") || upnpClass.contains("musicArtist")) continue
                    list += MediaEntry(id, title, null, art, isFolder = true, count = e.getAttribute("childCount").toIntOrNull())
                }
                "item" -> {
                    val resources = e.getElementsByTagNameNS("*", "res").let { r -> (0 until r.length).map { r.item(it) as Element } }
                    val video = resources.firstOrNull { it.getAttribute("protocolInfo").contains(":video/") }
                        ?: resources.takeIf { e.child("class").orEmpty().contains("videoItem") }?.firstOrNull()
                        ?: continue
                    list += MediaEntry(id, title, video.textContent.trim(), art, isFolder = false)
                }
            }
        }
        return list
    }

    private fun Element.child(name: String): String? {
        val nodes = childNodes
        for (i in 0 until nodes.length) {
            val n = nodes.item(i) as? Element ?: continue
            if ((n.localName ?: n.tagName.substringAfter(':')) == name) return n.textContent
        }
        return null
    }

    private fun parse(xml: String) = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        .newDocumentBuilder().parse(ByteArrayInputStream(xml.trim().toByteArray()))

    private fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    private fun http(url: String, soap: String? = null, action: String? = null): String? = runCatching {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 3000
        c.readTimeout = 15000
        if (soap != null) {
            c.requestMethod = "POST"
            c.doOutput = true
            c.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
            c.setRequestProperty("SOAPACTION", action)
            c.outputStream.use { it.write(soap.toByteArray()) }
        }
        try {
            if (c.responseCode !in 200..299) null else c.inputStream.use { it.readBytes().decodeToString() }
        } finally {
            c.disconnect()
        }
    }.getOrNull()
}
