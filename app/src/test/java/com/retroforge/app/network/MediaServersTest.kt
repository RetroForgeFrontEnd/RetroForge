package com.retroforge.app.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaServersTest {
    @Test
    fun readsTheLocationOfAnSsdpAnswer() {
        val answer = "HTTP/1.1 200 OK\r\nCACHE-CONTROL: max-age=1800\r\nLocation: http://192.168.1.5:32469/DeviceDescription.xml\r\nST: urn:schemas-upnp-org:device:MediaServer:1\r\n\r\n"
        assertEquals("http://192.168.1.5:32469/DeviceDescription.xml", MediaServers.location(answer))
        assertNull(MediaServers.location("HTTP/1.1 200 OK\r\n\r\n"))
    }

    @Test
    fun listsFoldersAndVideosButNotMusic() {
        val didl = """<DIDL-Lite xmlns="urn:schemas-upnp-org:metadata-1-0/DIDL-Lite/" xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:upnp="urn:schemas-upnp-org:metadata-1-0/upnp/">
<container id="1" parentID="0" childCount="12"><dc:title>Films</dc:title><upnp:class>object.container.storageFolder</upnp:class></container>
<container id="2" parentID="0"><dc:title>An album</dc:title><upnp:class>object.container.album.musicAlbum</upnp:class></container>
<item id="3" parentID="0"><dc:title>Avatar 3D</dc:title><upnp:class>object.item.videoItem.movie</upnp:class>
<upnp:albumArtURI>http://s/art.jpg</upnp:albumArtURI>
<res protocolInfo="http-get:*:video/x-matroska:*">http://192.168.1.5:32469/object/3/file.mkv</res></item>
<item id="4" parentID="0"><dc:title>A song</dc:title><upnp:class>object.item.audioItem.musicTrack</upnp:class>
<res protocolInfo="http-get:*:audio/mpeg:*">http://s/song.mp3</res></item>
</DIDL-Lite>"""
        val list = MediaServers.didl(didl)
        assertEquals(listOf("Films", "Avatar 3D"), list.map { it.title })
        assertEquals(12, list[0].count)
        assertEquals("http://192.168.1.5:32469/object/3/file.mkv", list[1].url)
        assertEquals("http://s/art.jpg", list[1].art)
    }

    @Test
    fun linksGoToTheAddressTheServerWasReachedAt() {
        val server = MediaServer("Plex", "http://10.120.223.248:32469/DeviceDescription.xml", "http://10.120.223.248:32469/ContentDirectory/x/control.xml")
        assertEquals("http://10.120.223.248:32469/object/3/file.mkv", MediaServers.reachable("http://192.168.1.5:32469/object/3/file.mkv", server))
        assertEquals("http://10.120.223.248:32469/a.mkv", MediaServers.reachable("http://10.120.223.248:32469/a.mkv", server))
        // Links to the internet are left alone.
        assertEquals("https://image.example.com/p.jpg", MediaServers.reachable("https://image.example.com/p.jpg", server))
    }
}
