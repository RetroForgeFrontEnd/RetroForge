package com.retroforge.pcplayer.net

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.SocketTimeoutException
import java.net.URL

/**
 * Asks the home router (UPnP Internet Gateway Device) to send a port to this device, so friends on the internet can
 * join a netplay game (or a PC game played together) without anyone setting up port forwarding. Routers that have UPnP switched off just say no.
 * Mobile data and most hotel or office networks can't be opened this way.
 */
class Upnp private constructor(private val controlUrl: String, private val service: String) {

    /** The router's public address, for the other player to type. */
    fun externalAddress(): String? = soap("GetExternalIPAddress", "")?.let { Regex("<NewExternalIPAddress>([^<]+)<").find(it)?.groupValues?.get(1) }

    /** Sends [port] ([protocol]: TCP or UDP) from the internet to [localAddress]. True when the router agreed. */
    fun open(port: Int, localAddress: String, protocol: String = "TCP"): Boolean = soap(
        "AddPortMapping",
        "<NewRemoteHost></NewRemoteHost><NewExternalPort>$port</NewExternalPort><NewProtocol>$protocol</NewProtocol>" +
            "<NewInternalPort>$port</NewInternalPort><NewInternalClient>$localAddress</NewInternalClient>" +
            "<NewEnabled>1</NewEnabled><NewPortMappingDescription>RetroForge netplay</NewPortMappingDescription>" +
            "<NewLeaseDuration>7200</NewLeaseDuration>",
    ) != null

    fun close(port: Int, protocol: String = "TCP") {
        soap("DeletePortMapping", "<NewRemoteHost></NewRemoteHost><NewExternalPort>$port</NewExternalPort><NewProtocol>$protocol</NewProtocol>")
    }

    private fun soap(action: String, args: String): String? = runCatching {
        val body = "<?xml version=\"1.0\"?><s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" " +
            "s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body><u:$action xmlns:u=\"$service\">$args</u:$action></s:Body></s:Envelope>"
        val connection = URL(controlUrl).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 3000
        connection.readTimeout = 4000
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"")
        connection.setRequestProperty("SOAPAction", "\"$service#$action\"")
        connection.outputStream.use { it.write(body.toByteArray()) }
        if (connection.responseCode != 200) null else connection.inputStream.bufferedReader().use { it.readText() }
    }.getOrNull()

    companion object {
        private val services = listOf(
            "urn:schemas-upnp-org:service:WANIPConnection:2",
            "urn:schemas-upnp-org:service:WANIPConnection:1",
            "urn:schemas-upnp-org:service:WANPPPConnection:1",
        )

        /** Finds the router on this network (a second or two). Null when there's none that speaks UPnP. Off the main thread. */
        fun find(): Upnp? = runCatching {
            val location = DatagramSocket().use { s ->
                s.soTimeout = 1500
                val ask = ("M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 1\r\n" +
                    "ST: urn:schemas-upnp-org:device:InternetGatewayDevice:1\r\n\r\n").toByteArray()
                s.send(DatagramPacket(ask, ask.size, InetAddress.getByName("239.255.255.250"), 1900))
                val buffer = ByteArray(2048)
                var found: String? = null
                val end = System.currentTimeMillis() + 3000
                while (found == null && System.currentTimeMillis() < end) {
                    try {
                        val packet = DatagramPacket(buffer, buffer.size)
                        s.receive(packet)
                        found = String(packet.data, 0, packet.length).lines()
                            .firstOrNull { it.startsWith("LOCATION:", ignoreCase = true) }?.substringAfter(':')?.trim()
                    } catch (_: SocketTimeoutException) {
                        break
                    }
                }
                found
            } ?: return null
            val description = (URL(location).openConnection() as HttpURLConnection).run {
                connectTimeout = 3000
                readTimeout = 3000
                inputStream.bufferedReader().use { it.readText() }
            }
            // The control URL of the first WAN connection service the router has, made absolute.
            for (service in services) {
                val at = description.indexOf("<serviceType>$service</serviceType>")
                if (at < 0) continue
                val control = Regex("<controlURL>([^<]+)</controlURL>").find(description, at)?.groupValues?.get(1) ?: continue
                return Upnp(URL(URL(location), control).toString(), service)
            }
            null
        }.getOrNull()
    }
}
