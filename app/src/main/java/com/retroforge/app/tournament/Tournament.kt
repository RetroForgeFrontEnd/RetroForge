package com.retroforge.app.tournament

/** How a tournament is played. */
enum class TournamentFormat { KNOCKOUT, LEAGUE }

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
            a is Slot.Player && b == Slot.Bye -> a
            b is Slot.Player && a == Slot.Bye -> b
            !playable || score == null || score.a == score.b -> Slot.Pending
            score.a > score.b -> a
            else -> b
        }
}

/** A league table row; [player] is the player's place in the tournament's list. */
data class TableRow(val player: Int, val played: Int, val won: Int, val drawn: Int, val lost: Int, val scored: Int, val conceded: Int) {
    val points get() = won * 3 + drawn
    val difference get() = scored - conceded
}

object Tournaments {
    /**
     * The knockout bracket, round by round. The draw is padded to a power of two with byes, placed by the usual
     * seeding (1 v 8, 4 v 5, 2 v 7, 3 v 6…) so the first players entered get the byes and meet the latest.
     */
    fun knockout(t: Tournament): List<List<Match>> {
        val n = t.players.size
        if (n < 2) return emptyList()
        var size = 1
        while (size < n) size *= 2
        var slots: List<Slot> = seeding(size).map { seed -> if (seed <= n) Slot.Player(seed - 1) else Slot.Bye }
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

    /** Seed numbers in bracket order for a draw of [size] (a power of two). */
    internal fun seeding(size: Int): List<Int> {
        var order = listOf(1)
        while (order.size < size) {
            val next = order.size * 2
            order = order.flatMap { listOf(it, next + 1 - it) }
        }
        return order
    }

    /** Everyone plays everyone once, in rounds where nobody plays twice (the circle method; an odd player out rests). */
    fun leagueRounds(t: Tournament): List<List<Match>> {
        val n = t.players.size
        if (n < 2) return emptyList()
        val ring: MutableList<Int?> = (0 until n).toMutableList<Int?>().also { if (n % 2 == 1) it += null }
        val rounds = mutableListOf<List<Match>>()
        repeat(ring.size - 1) { round ->
            val matches = (0 until ring.size / 2).mapNotNull { i ->
                val x = ring[i] ?: return@mapNotNull null
                val y = ring[ring.size - 1 - i] ?: return@mapNotNull null
                val (a, b) = if (x < y) x to y else y to x
                val key = "l-$a-$b"
                Match(key, round, Slot.Player(a), Slot.Player(b), t.results[key])
            }
            rounds += matches
            // Keep the first place fixed and turn the rest one step.
            ring.add(1, ring.removeAt(ring.size - 1))
        }
        return rounds
    }

    /** The league table: 3 points a win, 1 a draw; ties split by score difference, then scored, then name. */
    fun table(t: Tournament): List<TableRow> {
        val rows = t.players.indices.associateWith { TableRow(it, 0, 0, 0, 0, 0, 0) }.toMutableMap()
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
        for (m in leagueRounds(t).flatten()) {
            val s = m.score ?: continue
            add((m.a as Slot.Player).index, s.a, s.b)
            add((m.b as Slot.Player).index, s.b, s.a)
        }
        return rows.values.sortedWith(
            compareByDescending<TableRow> { it.points }.thenByDescending { it.difference }.thenByDescending { it.scored }
                .thenBy { t.players[it.player].lowercase() },
        )
    }

    fun matches(t: Tournament): List<List<Match>> = when (t.format) {
        TournamentFormat.KNOCKOUT -> knockout(t)
        TournamentFormat.LEAGUE -> leagueRounds(t)
    }

    /** The winner, once every match that decides it has a result; null before then. */
    fun champion(t: Tournament): Int? = when (t.format) {
        TournamentFormat.KNOCKOUT -> (knockout(t).lastOrNull()?.single()?.winner as? Slot.Player)?.index
        TournamentFormat.LEAGUE -> {
            val all = leagueRounds(t).flatten()
            if (all.isEmpty() || all.any { it.score == null }) null else table(t).first().player
        }
    }

    /**
     * Enters or clears a result. In a knockout, changing a result clears the later results that depended on it,
     * since the players in those matches may have changed.
     */
    fun withResult(t: Tournament, key: String, score: Score?): Tournament {
        val results = t.results.toMutableMap()
        if (score == null) results.remove(key) else results[key] = score
        var next = t.copy(results = results)
        if (t.format == TournamentFormat.KNOCKOUT) {
            val before = knockout(t).flatten().associateBy { it.key }
            val stale = knockout(next).flatten().filter { m -> before[m.key]?.let { it.a != m.a || it.b != m.b } == true }.map { it.key }
            if (stale.isNotEmpty()) next = withResults(next, next.results - stale.toSet())
        }
        return next
    }

    private fun withResults(t: Tournament, results: Map<String, Score>): Tournament {
        // Clearing a stale result can change the matches after it in turn.
        val cleaned = t.copy(results = results)
        val now = knockout(cleaned).flatten()
        val stale = now.filter { it.score != null && !it.playable }.map { it.key }
        return if (stale.isEmpty()) cleaned else withResults(cleaned, results - stale.toSet())
    }
}
