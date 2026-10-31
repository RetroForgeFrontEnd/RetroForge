package com.retroforge.app.player

import com.retroforge.player.CoreOption
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinkCableTest {
    /** Gambatte's link options as it lists them. */
    private val gambatte = listOf(
        CoreOption("gambatte_gb_link_mode", "GameBoy Link Mode", listOf("Not Connected", "Network Server", "Network Client"), "Not Connected"),
        CoreOption("gambatte_gb_link_network_port", "Network Link Port", (56400..56420).map { "$it" }, "56400"),
    )

    @Test
    fun addressesBecomeTwelveDigits() {
        assertEquals(listOf(1, 9, 2, 1, 6, 8, 0, 0, 1, 0, 2, 3), LinkCable.ipDigits("192.168.1.23"))
        assertEquals(listOf(0, 1, 0, 0, 0, 0, 0, 0, 2, 0, 0, 2), LinkCable.ipDigits(" 10.0.2.2 "))
        assertNull(LinkCable.ipDigits("192.168.1"))
        assertNull(LinkCable.ipDigits("300.1.1.1"))
        assertNull(LinkCable.ipDigits("phone.local"))
    }

    @Test
    fun joiningSetsTheAddressBeforeTheMode() {
        val options = LinkCable.joinOptions(gambatte, "10.0.2.2")!!
        assertEquals("Network Client", options.values.last())
        assertEquals("gambatte_gb_link_mode", options.keys.last())
        assertEquals("2", options["gambatte_gb_link_network_server_ip_12"])
        assertEquals("1", options["gambatte_gb_link_network_server_ip_2"])
        assertEquals("56400", options["gambatte_gb_link_network_port"])
        assertEquals(mapOf("gambatte_gb_link_network_port" to "56400", "gambatte_gb_link_mode" to "Network Server"), LinkCable.hostOptions(gambatte))
        assertEquals(mapOf("gambatte_gb_link_mode" to "Not Connected"), LinkCable.unplugOptions(gambatte))
    }

    @Test
    fun coresWithoutTheCableSayNo() {
        assertNull(LinkCable.hostOptions(emptyList()))
        assertNull(LinkCable.joinOptions(emptyList(), "10.0.2.2"))
    }
}
