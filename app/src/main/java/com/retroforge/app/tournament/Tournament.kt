package com.retroforge.app.tournament

/** How a tournament is played. */
enum class TournamentFormat { KNOCKOUT, DOUBLE_KNOCKOUT, LEAGUE, GROUPS }

/** A match's result: games or goals won by each side. */
data class Score(val a: Int, val b: Int)

/**
 * A local tournament: players entered by name, results entered by hand. Players are referred to by their place in
 * [players]; results are kept by match key, so the bracket or table is always worked out afresh from them.
 */
data class Tournament(
    val id: String,
    val name: String,
    val game: String,
    val format: TournamentFormat,
    val players: List<String>,
    val rules: String = "",
    val results: Map<String, Score> = emptyMap(),
    val createdAt: Long = 0,
    val finishedAt: Long? = null,
)

/** One side of a match: a player, a bye (nobody, so the other side goes through) or not known yet. */
sealed interface Slot {
    data class Player(val index: Int) : Slot
    data object Bye : Slot
    data object Pending : Slot
}

data class Match(val key: String, val round: Int, val a: Slot, val b: Slot, val score: Score?) {
    /** Both sides are players, so a result can be entered. */
    val playable get() = a is Slot.Player && b is Slot.Player

    /** Who goes through: decided by the score (a draw decides nothing), or the player facing a bye. */
    val winner: Slot
        get() = when {
            a == Slot.Bye && b == Slot.Bye -> Slot.Bye
            a is Slot.Player && b == Slot.Bye -> a
            b is Slot.Player && a == Slot.Bye -> b
            !playable || score == null || score.a == score.b -> Slot.Pending
            score.a > score.b -> a
            else -> b
        }

    /** Who is knocked out: the other side once there's a winner; nobody (a bye) when a side had no opponent. */
    val loser: Slot
        get() = when {
            a == Slot.Bye || b == Slot.Bye -> if (a is Slot.Pending || b is Slot.Pending) Slot.Pending else Slot.Bye
            winner == Slot.Pending -> Slot.Pending
            winner == a -> b
            else -> a
        }
}

/** A league table row; [player] is the player's place in the tournament's list. */
data class TableRow(val player: Int, val played: Int, val won: Int, val drawn: Int, val lost: Int, val scored: Int, val conceded: Int) {
    val points get() = won * 3 + drawn
    val difference get() = scored - conceded
}

object Tournaments {
    /** Fewest players for groups then a knockout: two groups of three. */
    const val MIN_FOR_GROUPS = 6
    /** How many from each group go through to the knockout. */
    const val QUALIFY_PER_GROUP = 2

    /**
     * The knockout bracket, round by round: of all the players, or of those through from the groups (not known until
     * each group has finished). The draw is padded to a power of two with byes, placed by the usual seeding (1 v 8,
     * 4 v 5, 2 v 7, 3 v 6…) so the first entrants get the byes and meet the latest.
     */
    fun knockout(t: Tournament): List<List<Match>> {
        val entrants: List<Slot> = when (t.format) {
            TournamentFormat.KNOCKOUT, TournamentFormat.DOUBLE_KNOCKOUT -> t.players.indices.map { Slot.Player(it) }
            TournamentFormat.GROUPS -> qualifiers(t)
            TournamentFormat.LEAGUE -> return emptyList()
        }
        val n = entrants.size
        if (n < 2) return emptyList()
        var size = 1
        while (size < n) size *= 2
        var slots: List<Slot> = seeding(size).map { seed -> entrants.getOrNull(seed - 1) ?: Slot.Bye }
        val rounds = mutableListOf<List<Match>>()
        var round = 0
        while (slots.size > 1) {
            val matches = slots.chunked(2).mapIndexed { i, (a, b) ->
                val key = "k-$round-$i"
                Match(key, round, a, b, t.results[key])
            }
            rounds += matches
            slots = matches.map { it.winner }
            round++
        }
        return rounds
    }

    /**
     * A double knockout's losers' bracket: the first round's losers play each other, then each later round of the
     * winners' bracket drops its losers in (in reverse order, to put off rematches), with the survivors playing each
     * other in between. Losing here is the second defeat.
     */
    fun losersBracket(t: Tournament): List<List<Match>> {
        if (t.format != TournamentFormat.DOUBLE_KNOCKOUT) return emptyList()
        val winners = knockout(t)
        if (winners.isEmpty()) return emptyList()
        val rounds = mutableListOf<List<Match>>()
        fun play(pairs: List<Pair<Slot, Slot>>): List<Slot> {
            val round = rounds.size
            val matches = pairs.mapIndexed { i, (a, b) -> "lb-$round-$i".let { Match(it, round, a, b, t.results[it]) } }
            rounds += matches
            return matches.map { it.winner }
        }
        var alive = winners[0].map { it.loser }
        if (alive.size > 1) alive = play(alive.chunked(2).map { (a, b) -> a to b })
        for (w in 1 until winners.size) {
            alive = play(alive.zip(winners[w].map { it.loser }.reversed()))
            if (alive.size > 1) alive = play(alive.chunked(2).map { (a, b) -> a to b })
        }
        return rounds
    }

    /** A double knockout's last match: the winners' bracket champion against the losers' bracket one. */
    fun grandFinal(t: Tournament): Match? {
        if (t.format != TournamentFormat.DOUBLE_KNOCKOUT) return null
        val top = knockout(t).lastOrNull()?.single()?.winner ?: return null
        val bottom = losersBracket(t).lastOrNull()?.single()?.winner
            ?: knockout(t).last().single().loser // two players: the final's loser gets a second go
        return Match("gf", 0, top, bottom, t.results["gf"])
    }

    /** Seed numbers in bracket order for a draw of [size] (a power of two). */
    internal fun seeding(size: Int): List<Int> {
        var order = listOf(1)
        while (order.size < size) {
            val next = order.size * 2
            order = order.flatMap { listOf(it, next + 1 - it) }
        }
        return order
    }

    /**
     * The groups, as players' places in the list: groups of about four, dealt out in a snake (A B C C B A…) so the
     * first players entered are spread between them.
     */
    fun groups(t: Tournament): List<List<Int>> {
        if (t.format != TournamentFormat.GROUPS || t.players.size < MIN_FOR_GROUPS) return emptyList()
        val count = maxOf(2, t.players.size / 4)
        val groups = List(count) { mutableListOf<Int>() }
        t.players.indices.forEach { i ->
            val row = i / count
            val col = i % count
            groups[if (row % 2 == 0) col else count - 1 - col] += i
        }
        return groups
    }

    /** A group's matches; group [g]'s keys start "g<g>-" so they don't clash with another group's. */
    fun groupRounds(t: Tournament, g: Int): List<List<Match>> = roundRobin(t, groups(t)[g], "g$g-")

    fun groupTable(t: Tournament, g: Int): List<TableRow> = table(t, groups(t)[g], "g$g-")

    /**
     * Who is through from the groups: the group winners as the top seeds (A, B, C…), then the runners-up, placed so
     * each winner's first match is against the next group's runner-up rather than their own group's (seed k meets
     * seed 2G+1-k). Pending for a group that hasn't finished.
     */
    internal fun qualifiers(t: Tournament): List<Slot> {
        val places = groups(t).indices.map { g ->
            val done = groupRounds(t, g).flatten().all { it.score != null }
            val table = groupTable(t, g)
            List(QUALIFY_PER_GROUP) { place -> if (done) Slot.Player(table[place].player) else Slot.Pending }
        }
        val count = places.size
        val winners = places.map { it[0] }
        // Winner i meets runners-up place count-1-i: put group i+1's runner-up there.
        val runnersUp = List(count) { j -> places[(count - j) % count][1] }
        return winners + runnersUp
    }

    /** Everyone plays everyone once, in rounds where nobody plays twice (the circle method; an odd player out rests). */
    fun leagueRounds(t: Tournament): List<List<Match>> = roundRobin(t, t.players.indices.toList(), "l-")

    private fun roundRobin(t: Tournament, members: List<Int>, prefix: String): List<List<Match>> {
        if (members.size < 2) return emptyList()
        val ring: MutableList<Int?> = members.toMutableList<Int?>().also { if (it.size % 2 == 1) it += null }
        val rounds = mutableListOf<List<Match>>()
        repeat(ring.size - 1) { round ->
            val matches = (0 until ring.size / 2).mapNotNull { i ->
                val x = ring[i] ?: return@mapNotNull null
                val y = ring[ring.size - 1 - i] ?: return@mapNotNull null
                val (a, b) = if (x < y) x to y else y to x
                val key = "$prefix$a-$b"
                Match(key, round, Slot.Player(a), Slot.Player(b), t.results[key])
            }
            rounds += matches
            // Keep the first place fixed and turn the rest one step.
            ring.add(1, ring.removeAt(ring.size - 1))
        }
        return rounds
    }

    /** The league table: 3 points a win, 1 a draw; ties split by score difference, then scored, then name. */
    fun table(t: Tournament): List<TableRow> = table(t, t.players.indices.toList(), "l-")

    private fun table(t: Tournament, members: List<Int>, prefix: String): List<TableRow> {
        val rows = members.associateWith { TableRow(it, 0, 0, 0, 0, 0, 0) }.toMutableMap()
        fun add(p: Int, mine: Int, theirs: Int) {
            val r = rows.getValue(p)
            rows[p] = r.copy(
                played = r.played + 1,
                won = r.won + if (mine > theirs) 1 else 0,
                drawn = r.drawn + if (mine == theirs) 1 else 0,
                lost = r.lost + if (mine < theirs) 1 else 0,
                scored = r.scored + mine,
                conceded = r.conceded + theirs,
            )
        }
        for (m in roundRobin(t, members, prefix).flatten()) {
            val s = m.score ?: continue
            add((m.a as Slot.Player).index, s.a, s.b)
            add((m.b as Slot.Player).index, s.b, s.a)
        }
        return rows.values.sortedWith(
            compareByDescending<TableRow> { it.points }.thenByDescending { it.difference }.thenByDescending { it.scored }
                .thenBy { t.players[it.player].lowercase() },
        )
    }

    /** The knockout or league matches; for groups, the knockout after them (see [groupRounds] for the groups). */
    fun matches(t: Tournament): List<List<Match>> = when (t.format) {
        TournamentFormat.KNOCKOUT, TournamentFormat.DOUBLE_KNOCKOUT, TournamentFormat.GROUPS -> knockout(t)
        TournamentFormat.LEAGUE -> leagueRounds(t)
    }

    private fun allMatches(t: Tournament): List<Match> = matches(t).flatten() +
        groups(t).indices.flatMap { groupRounds(t, it).flatten() } + losersBracket(t).flatten() + listOfNotNull(grandFinal(t))

    /** The winner, once every match that decides it has a result; null before then. */
    fun champion(t: Tournament): Int? = when (t.format) {
        TournamentFormat.KNOCKOUT, TournamentFormat.GROUPS -> (knockout(t).lastOrNull()?.single()?.winner as? Slot.Player)?.index
        TournamentFormat.DOUBLE_KNOCKOUT -> (grandFinal(t)?.winner as? Slot.Player)?.index
        TournamentFormat.LEAGUE -> {
            val all = leagueRounds(t).flatten()
            if (all.isEmpty() || all.any { it.score == null }) null else table(t).first().player
        }
    }

    /**
     * Enters or clears a result. Later results whose players changed because of it (a knockout winner, or who came
     * through a group) are cleared, since they were played by someone else.
     */
    fun withResult(t: Tournament, key: String, score: Score?): Tournament {
        val results = t.results.toMutableMap()
        if (score == null) results.remove(key) else results[key] = score
        val before = allMatches(t).associateBy { it.key }
        val next = t.copy(results = results)
        val stale = allMatches(next).filter { m -> m.key != key && before[m.key]?.let { it.a != m.a || it.b != m.b } == true }.map { it.key }
        return withResults(next, next.results - stale.toSet())
    }

    private fun withResults(t: Tournament, results: Map<String, Score>): Tournament {
        // Clearing a stale result can leave the matches after it without their players in turn.
        val cleaned = t.copy(results = results)
        val stale = allMatches(cleaned).filter { it.score != null && !it.playable }.map { it.key }
        return if (stale.isEmpty()) cleaned else withResults(cleaned, results - stale.toSet())
    }
}
