package com.retroforge.app.tournament

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TournamentTest {
    private fun knockout(vararg names: String) = Tournament("t", "Cup", "Street Fighter II", TournamentFormat.KNOCKOUT, names.toList())
    private fun league(vararg names: String) = Tournament("t", "League", "ISS Deluxe", TournamentFormat.LEAGUE, names.toList())

    @Test fun seedingKeepsTheTopSeedsApart() {
        assertEquals(listOf(1, 8, 4, 5, 2, 7, 3, 6), Tournaments.seeding(8))
    }

    @Test fun fivePlayersGiveTheFirstThreeByes() {
        val rounds = Tournaments.knockout(knockout("A", "B", "C", "D", "E"))
        assertEquals(listOf(4, 2, 1), rounds.map { it.size })
        val first = rounds[0]
        assertEquals(1, first.count { it.playable }) // 4 v 5
        assertEquals(Slot.Player(0), first[0].winner) // A has a bye
        assertEquals(Slot.Pending, rounds[1][0].b) // waiting for D v E
    }

    @Test fun aKnockoutIsWonAndAChangedResultClearsWhatFollowed() {
        var t = knockout("A", "B", "C", "D")
        val r0 = Tournaments.knockout(t)[0]
        t = Tournaments.withResult(t, r0[0].key, Score(2, 1)) // A beats D
        t = Tournaments.withResult(t, r0[1].key, Score(0, 2)) // C beats B
        assertNull(Tournaments.champion(t))
        val final = Tournaments.knockout(t)[1][0]
        assertEquals(Slot.Player(0), final.a)
        assertEquals(Slot.Player(2), final.b)
        t = Tournaments.withResult(t, final.key, Score(1, 1))
        assertNull(Tournaments.champion(t)) // a draw decides nothing
        t = Tournaments.withResult(t, final.key, Score(1, 3))
        assertEquals(2, Tournaments.champion(t))

        // D wins the first match after all: the final had A in it, so its result goes.
        t = Tournaments.withResult(t, r0[0].key, Score(0, 2))
        assertTrue(final.key !in t.results)
        assertNull(Tournaments.champion(t))
    }

    @Test fun everyonePlaysEveryoneOnceAndTheTableAddsUp() {
        var t = league("A", "B", "C", "D", "E")
        val rounds = Tournaments.leagueRounds(t)
        val all = rounds.flatten()
        assertEquals(10, all.size)
        assertEquals(10, all.map { it.key }.toSet().size)
        rounds.forEach { round ->
            val ids = round.flatMap { listOf(it.a, it.b) }
            assertEquals(ids.size, ids.toSet().size)
        }
        all.forEach { m ->
            val a = (m.a as Slot.Player).index
            t = Tournaments.withResult(t, m.key, if (a == 0) Score(3, 0) else Score(1, 1))
        }
        val table = Tournaments.table(t)
        assertEquals(0, table.first().player)
        assertEquals(12, table.first().points)
        assertEquals(0, Tournaments.champion(t))
        assertEquals(20, table.sumOf { it.played })
    }

    @Test fun aLeagueHasNoChampionUntilEveryMatchIsPlayed() {
        val t = league("A", "B", "C")
        val m = Tournaments.leagueRounds(t).flatten().first()
        assertNull(Tournaments.champion(Tournaments.withResult(t, m.key, Score(1, 0))))
    }
}
