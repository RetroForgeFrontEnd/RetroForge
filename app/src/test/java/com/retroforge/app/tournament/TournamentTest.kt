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

    @Test fun groupsAreDealtInASnakeAndTheTopTwoGoThrough() {
        var t = Tournament("t", "Cup", "", TournamentFormat.GROUPS, listOf("A", "B", "C", "D", "E", "F", "G", "H"))
        assertEquals(listOf(listOf(0, 3, 4, 7), listOf(1, 2, 5, 6)), Tournaments.groups(t))
        // Nobody is through until a group is finished.
        assertTrue(Tournaments.knockout(t)[0].all { !it.playable })

        // The lower place in the list wins every group match.
        fun playGroups(t0: Tournament): Tournament {
            var x = t0
            Tournaments.groups(x).indices.forEach { g ->
                Tournaments.groupRounds(x, g).flatten().forEach { m -> x = Tournaments.withResult(x, m.key, Score(1, 0)) }
            }
            return x
        }
        t = playGroups(t)
        assertEquals(listOf(0, 3), Tournaments.groupTable(t, 0).take(2).map { it.player })
        assertEquals(listOf(1, 2), Tournaments.groupTable(t, 1).take(2).map { it.player })
        // Winners A and B, then runners-up the other way round: A meets C (B's runner-up), B meets D.
        val semis = Tournaments.knockout(t)[0]
        assertEquals(setOf(setOf(Slot.Player(0), Slot.Player(2)), setOf(Slot.Player(1), Slot.Player(3))), semis.map { setOf(it.a, it.b) }.toSet())

        semis.forEach { t = Tournaments.withResult(t, it.key, Score(2, 0)) }
        val final = Tournaments.knockout(t)[1][0]
        t = Tournaments.withResult(t, final.key, Score(3, 1))
        assertEquals((final.a as Slot.Player).index, Tournaments.champion(t))

        // Changing a group result that changes who came through clears the knockout results after it.
        val first = Tournaments.groupRounds(t, 0).flatten().first { it.a == Slot.Player(0) && it.b == Slot.Player(3) }
        t = Tournaments.withResult(t, first.key, Score(0, 5))
        t = Tournaments.withResult(t, Tournaments.groupRounds(t, 0).flatten().first { it.a == Slot.Player(0) && it.b == Slot.Player(4) }.key, Score(0, 5))
        assertTrue(t.results.keys.none { it.startsWith("k-") })
        assertNull(Tournaments.champion(t))
    }

    @Test fun aDoubleKnockoutNeedsTwoDefeats() {
        var t = Tournament("t", "Cup", "", TournamentFormat.DOUBLE_KNOCKOUT, listOf("A", "B", "C", "D"))
        assertEquals(listOf(1, 1), Tournaments.losersBracket(t).map { it.size })
        // The first-named side wins every match until the losers' bracket is decided.
        fun next(x: Tournament) = (Tournaments.knockout(x).flatten() + Tournaments.losersBracket(x).flatten() + listOfNotNull(Tournaments.grandFinal(x)))
            .firstOrNull { it.playable && it.score == null }
        while (true) {
            val m = next(t) ?: break
            // D beats A in the grand final, though A won the winners' bracket.
            t = Tournaments.withResult(t, m.key, if (m.key == "gf") Score(0, 1) else Score(1, 0))
        }
        val gf = Tournaments.grandFinal(t)!!
        assertEquals(Slot.Player(0), gf.a) // A won the winners' bracket
        // D lost to A first, then beat B and C in the losers' bracket.
        assertEquals(Slot.Player(3), gf.b)
        assertEquals(3, Tournaments.champion(t))
        // A v D, B v C, the winners' final, D v C, D v B and the grand final.
        assertEquals(6, t.results.size)
    }

    @Test fun byesInADoubleKnockoutPassStraightThrough() {
        val t = Tournament("t", "Cup", "", TournamentFormat.DOUBLE_KNOCKOUT, listOf("A", "B", "C", "D", "E"))
        assertEquals(4, Tournaments.losersBracket(t).size)
        // Three of the first round's matches were byes: their "losers" are byes too, and only D v E's is awaited.
        val first = Tournaments.losersBracket(t)[0]
        assertEquals(Slot.Pending, first[0].winner)
        assertEquals(Slot.Bye, first[1].winner)
        val de = Tournaments.knockout(t)[0].first { it.playable }
        val after5 = Tournaments.withResult(t, de.key, Score(1, 0))
        assertEquals(Slot.Player(4), Tournaments.losersBracket(after5)[0][0].winner)
        val two = Tournament("t", "Cup", "", TournamentFormat.DOUBLE_KNOCKOUT, listOf("A", "B"))
        val final = Tournaments.knockout(two)[0][0]
        val after = Tournaments.withResult(two, final.key, Score(2, 0))
        assertEquals(Slot.Player(1), Tournaments.grandFinal(after)!!.b)
    }

    @Test fun aLeagueHasNoChampionUntilEveryMatchIsPlayed() {
        val t = league("A", "B", "C")
        val m = Tournaments.leagueRounds(t).flatten().first()
        assertNull(Tournaments.champion(Tournaments.withResult(t, m.key, Score(1, 0))))
    }
}
