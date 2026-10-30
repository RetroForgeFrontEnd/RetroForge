package com.retroforge.app.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkDrivesTest {
    @Test
    fun knowsTailscaleAddresses() {
        assertTrue(NetworkDrives.isTailscale("100.101.102.103"))
        assertTrue(NetworkDrives.isTailscale("100.64.0.1"))
        assertTrue(NetworkDrives.isTailscale("100.127.255.254"))
        assertTrue(NetworkDrives.isTailscale("gaming-pc.tail1234.ts.net"))
        assertFalse(NetworkDrives.isTailscale("100.128.0.1"))
        assertFalse(NetworkDrives.isTailscale("100.63.0.1"))
        assertFalse(NetworkDrives.isTailscale("192.168.0.5"))
        assertFalse(NetworkDrives.isTailscale("gaming-pc"))
    }

    @Test
    fun aShareOnATailscaleAddressSaysSo() {
        assertTrue(NetworkShare("1", "PC", "100.90.1.2:445", "Games", "", "").viaTailscale)
        assertFalse(NetworkShare("1", "PC", "192.168.0.5", "Games", "", "").viaTailscale)
    }
}
