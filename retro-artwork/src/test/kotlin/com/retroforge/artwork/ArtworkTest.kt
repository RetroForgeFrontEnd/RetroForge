package com.retroforge.artwork

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtworkTest {

    private val md = "Sega - Mega Drive - Genesis"

    // Real names from the server's Mega Drive index (2026-10-28).
    private val sonics = listOf(
        "Sonic The Hedgehog (Japan) (En) (Virtual Console)",
        "Sonic The Hedgehog (Japan, Europe, Korea) (En)",
        "Sonic The Hedgehog (Japan, Europe, Korea)",
        "Sonic The Hedgehog (USA)",
        "Sonic The Hedgehog (USA, Europe) (Sonic Classic Collection)",
        "Sonic The Hedgehog (USA, Europe)",
        "Sonic The Hedgehog (World) (Sonic Mega Collection)",
        "Sonic The Hedgehog 2 (USA)",
        "Streets of Rage (World) (Rev 1)",
    )

    @Test
    fun exactNoIntroNameWins() {
        val best = LibretroThumbnails.match(sonics, md, ThumbnailType.BOXART, "Sonic The Hedgehog (USA, Europe).zip").first()
        assertTrue(best.exact)
        assertEquals("Sonic The Hedgehog (USA, Europe)", best.name)
    }

    @Test
    fun goodToolsNameFromTheUsersCollectionStillFindsTheGame() {
        val matches = LibretroThumbnails.match(sonics, md, ThumbnailType.BOXART, "Sonic The Hedgehog (W) (REV00) [!].zip")
        assertTrue(matches.isNotEmpty())
        assertTrue(matches.none { it.name.startsWith("Sonic The Hedgehog 2") })
        // (W) = World: a plain release beats the Virtual Console / collection editions.
        assertFalse(matches.first().name.contains("Collection") || matches.first().name.contains("Virtual Console"))
    }

    @Test
    fun regionPreferenceIsUsed() {
        val best = LibretroThumbnails.match(sonics, md, ThumbnailType.BOXART, "Sonic The Hedgehog (USA) [b1].zip").first()
        assertEquals("Sonic The Hedgehog (USA)", best.name)
    }

    @Test
    fun differentGamesNeverMatch() {
        assertTrue(LibretroThumbnails.match(sonics, md, ThumbnailType.BOXART, "Golden Axe (World).zip").isEmpty())
    }

    @Test
    fun urlsAreEncodedLikeTheServer() {
        assertEquals(
            "https://thumbnails.libretro.com/Sega%20-%20Mega%20Drive%20-%20Genesis/Named_Boxarts/Sonic%20The%20Hedgehog%20%28USA%2C%20Europe%29.png",
            LibretroThumbnails.imageUrl(md, ThumbnailType.BOXART, "Sonic The Hedgehog (USA, Europe)"),
        )
        assertEquals("Q_bert _ Pac_Man", LibretroThumbnails.sanitise("Q*bert & Pac?Man"))
    }

    @Test
    fun indexPageIsParsed() {
        val html = """<a href="../">..</a><a href="Sonic%20The%20Hedgehog%20(USA).png">x</a><a href="3%20Ninjas%20Kick%20Back%20(USA).png">y</a>"""
        assertEquals(listOf("Sonic The Hedgehog (USA)", "3 Ninjas Kick Back (USA)"), LibretroThumbnails.parseIndex(html))
    }

    @Test
    fun steamGridDbParsesAndFiltersAndReportsBadKeys() {
        val requests = mutableListOf<Pair<String, Map<String, String>>>()
        val http = HttpGet { url, headers, _ ->
            requests += url to headers
            when {
                url.contains("/search/") -> HttpResponse(200, """{"success":true,"data":[{"id":5254,"name":"Sonic the Hedgehog","verified":true,"types":["steam"]}]}""".toByteArray())
                else -> HttpResponse(200, """{"success":true,"data":[{"id":1,"score":0,"style":"alternate","url":"https://cdn2.steamgriddb.com/grid/a.png","thumb":"https://cdn2.steamgriddb.com/thumb/a.png","width":600,"height":900,"author":{"name":"Artist"}}]}""".toByteArray())
            }
        }
        val sgdb = SteamGridDb(http) { "my-key" }
        val games = (sgdb.search("Sonic The Hedgehog") as SgdbResult.Ok).value
        assertEquals(5254L, games.single().id)
        val images = (sgdb.images(5254) as SgdbResult.Ok).value
        assertEquals("Artist", images.single().author?.name)
        assertEquals("Bearer my-key", requests.first().second["Authorization"])
        assertTrue(requests.first().first.endsWith("/search/autocomplete/Sonic%20The%20Hedgehog"))
        assertTrue(requests.last().first.contains("nsfw=false") && requests.last().first.contains("dimensions=600x900"))

        assertEquals(SgdbResult.InvalidKey, SteamGridDb(http) { "  " }.search("x"))
        assertEquals(SgdbResult.InvalidKey, SteamGridDb({ _, _, _ -> HttpResponse(401, ByteArray(0)) }) { "bad" }.search("x"))
    }

    @Test
    fun imageSniffing() {
        assertTrue(looksLikeImage(byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D)))
        assertTrue(looksLikeImage(byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())))
        assertFalse(looksLikeImage("<html>".toByteArray()))
    }
}
