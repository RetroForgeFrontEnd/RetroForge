package com.retroforge.stream

import org.junit.Assert.assertEquals
import org.junit.Test

class PcStreamingTest {
    @Test
    fun addressesWithAndWithoutPorts() {
        assertEquals("192.168.1.20" to 47989, PcStreaming.splitAddress("192.168.1.20"))
        assertEquals("192.168.1.20" to 50000, PcStreaming.splitAddress("192.168.1.20:50000"))
        assertEquals("gaming-pc.local" to 47989, PcStreaming.splitAddress("gaming-pc.local"))
        assertEquals("fe80::1" to 47989, PcStreaming.splitAddress("fe80::1"))
        assertEquals("fe80::1" to 50000, PcStreaming.splitAddress("[fe80::1]:50000"))
    }
}
