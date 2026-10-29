package com.retroforge.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PortsTest {
    private val ports = GamePorts.bundled()
    private fun port(id: String) = ports.first { it.id == id }

    @Test
    fun bundledListLoads() {
        assertTrue(ports.size >= 25)
        assertEquals(ports.size, ports.map { it.id }.toSet().size)
        assertTrue(ports.all { it.repo.startsWith("https://github.com/") && it.needs.isNotBlank() })
    }

    @Test
    fun matchesOwnedGames() {
        assertTrue(port("ghostship").matches("n64", "Super Mario 64"))
        assertFalse(port("ghostship").matches("n64", "Super Mario 64 - Star Road"))
        assertTrue(port("shipwright").matches("n64", "The Legend of Zelda: Ocarina of Time"))
        assertFalse(port("shipwright").matches("snes", "The Legend of Zelda: Ocarina of Time"))
        assertTrue(port("zelda3").matches("snes", "Legend of Zelda, The - A Link to the Past"))
        assertTrue(port("banjo-kazooie").matches("n64", "Banjo-Kazooie"))
        assertFalse(port("banjo-kazooie").matches("n64", "Banjo-Tooie"))
        assertFalse(port("openmw").matches("n64", "Morrowind"))
    }
}
