package com.retroforge.app.ui.library

import com.retroforge.core.library.BacklogGame
import com.retroforge.core.library.BacklogStatus
import com.retroforge.core.library.GameEntity
import com.retroforge.core.library.PlayedGame
import java.time.LocalDate
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PassportTest {
    private var nextId = 1L
    private fun game(title: String, system: String = "snes", genre: String? = null, developer: String? = null) = GameEntity(
        id = nextId++, rootId = 1, system = system, relativePath = "$system/$title", fileName = "$title.rom", documentId = "d$system$title", title = title,
        sortKey = title.lowercase(), tags = "", regions = "", isHack = false, hackFolder = null, sizeBytes = 1, modifiedAt = 0, duplicateKey = null,
        discs = null, description = null, releaseDate = null, developer = developer, publisher = null, genre = genre, players = null, rating = null, imageDocumentId = null,
    )

    private val hour = 3_600_000L
    private fun at(year: Int) = LocalDate.of(year, 6, 1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test
    fun seriesAreReadFromTitles() {
        assertEquals("Final Fantasy", Passport.series("Final Fantasy VII (USA)"))
        assertEquals("Zelda", Passport.series("Zelda II: The Adventure of Link"))
        assertEquals("Sonic the Hedgehog", Passport.series("Sonic the Hedgehog 2 [!]"))
        assertEquals("Tetris", Passport.series("Tetris"))
        assertNull(Passport.series("X"))
    }

    @Test
    fun summaryCountsSystemsFavouritesMilestonesAndYears() {
        val ff6 = game("Final Fantasy VI", genre = "Role playing game", developer = "Square")
        val ff7 = game("Final Fantasy VII (USA)", "psx", "RPG", "Square")
        val mario = game("Super Mario World", genre = "Platform", developer = "Nintendo")
        val marioGba = game("Super Mario World (Europe)", "gba", "Platform", "Nintendo")
        val untouched = game("Never Played", "nes")
        val backlog = listOf(
            BacklogGame(ff6, BacklogStatus.FINISHED.name, at(2025)),
            BacklogGame(ff7, BacklogStatus.FINISHED.name, at(2026)),
            BacklogGame(mario, BacklogStatus.PLAYING.name, at(2026)),
            BacklogGame(untouched, BacklogStatus.WANT.name, at(2026)),
        )
        val played = listOf(PlayedGame(marioGba, at(2026), 2))
        val playtime = mapOf(("snes" to "Final Fantasy VI.rom") to 12 * hour, ("psx" to "Final Fantasy VII (USA).rom") to 30 * hour, ("gba" to "Super Mario World (Europe).rom") to hour)
        val weeks = mapOf(LocalDate.of(2025, 12, 29) to 12 * hour, LocalDate.of(2026, 3, 2) to 31 * hour)
        val s = Passport.build(listOf(ff6, ff7, mario, marioGba, untouched), backlog, played, playtime, weeks, ZoneOffset.UTC)

        assertEquals(43 * hour, s.millis)
        // The same game on two systems counts once; the want-to-play game isn't played.
        assertEquals(3, s.gamesPlayed)
        assertEquals(listOf("psx", "snes", "gba"), s.systems.map { it.key })
        assertEquals(listOf("Square", "Nintendo"), s.developers.map { it.key })
        assertEquals(listOf("Final Fantasy" to 2), s.series.map { it.key to it.games })
        assertEquals(listOf("Final Fantasy VII (USA)", "Final Fantasy VI"), s.finished.map { it.title })
        assertEquals(listOf("Super Mario World"), s.playing.map { it.title })

        val reached = s.milestones.filter { it.reached }.map { it.kind to it.target }
        assertTrue(Passport.Kind.HOURS to 10 in reached)
        assertTrue(Passport.Kind.SYSTEMS to 3 in reached)
        assertTrue(Passport.Kind.SYSTEMS to 1 !in reached)
        val next = s.milestones.filter { !it.reached }.associate { it.kind to (it.target to it.value) }
        assertEquals(5 to 2, next[Passport.Kind.FINISHED])
        assertEquals(50 to 43, next[Passport.Kind.HOURS])

        assertEquals(listOf(2026, 2025), s.years.map { it.year })
        assertEquals(31 * hour, s.years[0].millis)
        assertEquals(listOf("Final Fantasy VI"), s.years[1].finished.map { it.title })
    }
}
