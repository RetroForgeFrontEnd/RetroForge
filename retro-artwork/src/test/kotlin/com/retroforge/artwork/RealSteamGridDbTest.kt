package com.retroforge.artwork

import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in: talks to the real SteamGridDB API with the key in SGDB_KEY (never stored in the project). */
class RealSteamGridDbTest {
    @Test
    fun searchAndCovers() {
        val key = System.getenv("SGDB_KEY")
        assumeTrue("SGDB_KEY not set", !key.isNullOrBlank())
        val sgdb = SteamGridDb(JvmHttpGet("RetroForge-test")) { key }
        for (title in listOf("Super Mario World", "Resident Evil 2", "Kaizo Mario World", "Sonic the Hedgehog 2")) {
            val found = sgdb.search(title)
            if (found !is SgdbResult.Ok) { println("SGDB search '$title': $found"); continue }
            println("SGDB search '$title': ${found.value.take(4).map { "${it.name} (${it.id})" }}")
            val first = found.value.firstOrNull() ?: continue
            val images = sgdb.images(first.id)
            if (images is SgdbResult.Ok) println("SGDB covers for ${first.name}: ${images.value.size}; first: ${images.value.firstOrNull()}")
            else println("SGDB covers for ${first.name}: $images")
        }
        val bad = SteamGridDb(JvmHttpGet("RetroForge-test")) { "0123456789abcdef0123456789abcdef" }.search("Mario")
        println("SGDB with a wrong key: $bad")
    }
}
