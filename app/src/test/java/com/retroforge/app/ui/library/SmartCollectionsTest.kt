package com.retroforge.app.ui.library

import com.retroforge.core.library.BacklogGame
import com.retroforge.core.library.BacklogStatus
import com.retroforge.core.library.GameEntity
import com.retroforge.core.library.PlayedGame
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartCollectionsTest {
    private var nextId = 1L
    private fun game(title: String, genre: String? = null, date: String? = null, developer: String? = null, players: String? = null, rating: Float? = null, system: String = "snes") = GameEntity(
        id = nextId++, rootId = 1, system = system, relativePath = "$system/$title.sfc", fileName = "$title.sfc", documentId = "d$title", title = title,
        sortKey = title.lowercase(), tags = "", regions = "", isHack = false, hackFolder = null, sizeBytes = 1, modifiedAt = 0, duplicateKey = null,
        discs = null, description = null, releaseDate = date, developer = developer, publisher = null, genre = genre, players = players, rating = rating, imageDocumentId = null,
    )

    private val label: (Int, Any?) -> String = { _, arg -> "label${arg ?: ""}" }
    private val now = 1_800_000_000_000L
    private val day = 24 * 60 * 60 * 1000L

    @Test
    fun detailsAreReadHoweverTheyreWritten() {
        assertEquals("Role playing game", SmartCollections.genre("ROLE PLAYING GAME / Action"))
        assertEquals("Platform", SmartCollections.genre("platform, action"))
        assertNull(SmartCollections.genre(" "))
        assertEquals(1991, SmartCollections.year("19910815T000000"))
        assertEquals(2004, SmartCollections.year("2004-03-01"))
        assertNull(SmartCollections.year("unknown"))
        assertEquals(4, SmartCollections.maxPlayers("1-4"))
        assertEquals(2, SmartCollections.maxPlayers("2"))
    }

    @Test
    fun genresDecadesAndDevelopersNeedTwoGamesAndTheBestComeFirst() {
        val zelda = game("Zelda", "Action / Adventure", "19911121", "Nintendo", rating = 0.9f)
        val mario = game("Mario World", "Platform", "1990", "Nintendo", "1-2", 0.95f)
        val dkc = game("Donkey Kong Country", "platform", "1994", "Rare", "1-2", 0.8f)
        val lone = game("Lone RPG", "Role playing game", "2001")
        val list = SmartCollections.build(listOf(zelda, mario, dkc, lone), emptyList(), emptyList(), emptyMap(), now, label)
        val byId = list.associateBy { it.id }
        assertEquals(listOf("Mario World", "Donkey Kong Country"), byId.getValue("genre:platform").games.map { it.title })
        assertTrue("genre:role playing game" !in byId)
        assertEquals(listOf("Mario World", "Zelda", "Donkey Kong Country"), byId.getValue("decade:1990").games.map { it.title })
        assertTrue("decade:2000" !in byId)
        assertEquals(listOf("Mario World", "Zelda"), byId.getValue("developer:nintendo").games.map { it.title })
        assertEquals(2, byId.getValue("players:2").games.size)
        assertTrue("players:4" !in byId)
        // Your play first, then genres, decades, developers, players.
        assertEquals(SmartCollections.Group.PLAY, list.first().group)
        assertEquals(SmartCollections.Group.PLAYERS, list.last().group)
    }

    @Test
    fun playCollectionsFollowBacklogPlaytimeAndLastPlayed() {
        val a = game("A")
        val b = game("B")
        val c = game("C")
        val d = game("D")
        val backlog = listOf(BacklogGame(a, BacklogStatus.FINISHED.name, 0))
        val played = listOf(PlayedGame(b, now - 2 * day, 3), PlayedGame(c, now - 400 * day, 1))
        val playtime = mapOf(("snes" to "B.sfc") to 90_000L, ("snes" to "A.sfc") to 10_000L)
        val byId = SmartCollections.build(listOf(a, b, c, d), backlog, played, playtime, now, label).associateBy { it.id }
        assertEquals(listOf("A"), byId.getValue("backlog:FINISHED").games.map { it.title })
        assertTrue("backlog:WANT" !in byId)
        assertEquals(listOf("B"), byId.getValue("recent").games.map { it.title })
        assertEquals(listOf("B", "A"), byId.getValue("most").games.map { it.title })
        assertEquals(listOf("C"), byId.getValue("forgotten").games.map { it.title })
        assertEquals(listOf("D"), byId.getValue("unplayed").games.map { it.title })
    }
}
