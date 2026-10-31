package com.retroforge.app.ui.library

import com.retroforge.app.R
import com.retroforge.core.ui.str
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.retroforge.app.player.Playtime
import com.retroforge.app.ui.CardBody
import com.retroforge.app.ui.CardTitle
import com.retroforge.app.ui.ScreenFrame
import com.retroforge.app.ui.countOf
import com.retroforge.core.library.BacklogGame
import com.retroforge.core.library.BacklogStatus
import com.retroforge.core.library.GameEntity
import com.retroforge.core.library.PlayedGame
import com.retroforge.core.ui.FocusCard
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The gaming passport: your play history in one place, from the backlog, the built-in player's playtime (per game and
 * per week) and the games' last-played dates. Worked out each time it opens; nothing is stored.
 */
object Passport {
    /** A system, genre, developer or series: how many of its games you've played, and for how long. */
    data class Stat(val key: String, val games: Int, val millis: Long)

    /** One year: time played (from the weekly records) and the games finished in it. */
    data class Year(val year: Int, val millis: Long, val finished: List<GameEntity>)

    enum class Kind { FINISHED, HOURS, SYSTEMS, GAMES }

    /** A milestone of [kind] at [target]: the highest [reached], or the next one, [value] of the way there. */
    data class Milestone(val kind: Kind, val target: Int, val value: Int, val reached: Boolean)

    data class Summary(
        val millis: Long,
        val gamesPlayed: Int,
        val systems: List<Stat>,
        val genres: List<Stat>,
        val developers: List<Stat>,
        val series: List<Stat>,
        val finished: List<GameEntity>,
        val playing: List<GameEntity>,
        val milestones: List<Milestone>,
        val years: List<Year>,
    )

    const val TOP = 5
    private val targets = mapOf(
        Kind.FINISHED to listOf(1, 5, 10, 25, 50, 100, 250),
        Kind.HOURS to listOf(1, 10, 50, 100, 250, 500, 1000),
        Kind.SYSTEMS to listOf(1, 3, 5, 10, 20, 30),
        Kind.GAMES to listOf(1, 10, 25, 50, 100, 250, 500),
    )
    private val numbering = Regex("^(?:[0-9]+|[IVX]+)$")

    /**
     * The series a title belongs to, by its name: "Final Fantasy VII (USA)" → "Final Fantasy", "Zelda II: The
     * Adventure of Link" → "Zelda", "Sonic the Hedgehog 2" → "Sonic the Hedgehog". A guess, but most series name
     * themselves this way.
     */
    fun series(title: String): String? {
        val name = title.replace(Regex("\\s*[(\\[].*?[)\\]]"), "").split(':', '–').first().split(" - ").first().trim()
        val words = name.split(' ').filter { it.isNotBlank() }.toMutableList()
        while (words.size > 1 && numbering.matches(words.last())) words.removeAt(words.size - 1)
        return words.joinToString(" ").takeIf { it.length > 2 }
    }

    /** The title without its region and version marks, so copies of one game on several systems count once. */
    private fun plainTitle(title: String) = title.replace(Regex("\\s*[(\\[].*?[)\\]]"), "").trim().lowercase()

    fun build(
        games: List<GameEntity>,
        backlog: List<BacklogGame>,
        played: List<PlayedGame>,
        playtime: Map<Pair<String, String>, Long>,
        weeks: Map<LocalDate, Long>,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Summary {
        val key = { g: GameEntity -> g.rootId to g.relativePath }
        val status = backlog.associate { key(it.game) to it.backlogStatus }
        val startedKeys = played.map { key(it.game) }.toSet()
        val time = { g: GameEntity -> playtime[g.system to g.fileName] ?: 0L }
        // Played: started from RetroForge, timed by the built-in player, or marked playing or finished.
        val yours = games.filter { g ->
            key(g) in startedKeys || time(g) > 0 || status[key(g)] == BacklogStatus.PLAYING || status[key(g)] == BacklogStatus.FINISHED
        }
        fun stats(by: (GameEntity) -> String?, min: Int = 1): List<Stat> = yours.groupBy(by).filterKeys { it != null }
            .map { (k, list) -> Stat(k!!, list.distinctBy { plainTitle(it.title) }.size, list.sumOf(time)) }
            .filter { it.games >= min }
            .sortedWith(compareByDescending<Stat> { it.millis }.thenByDescending { it.games }.thenBy { it.key })

        val finishedGames = backlog.filter { it.backlogStatus == BacklogStatus.FINISHED }
        val millis = yours.sumOf(time)
        val systems = stats({ it.system })
        val gamesPlayed = yours.distinctBy { plainTitle(it.title) }.size
        val values = mapOf(
            Kind.FINISHED to finishedGames.size, Kind.HOURS to (millis / 3_600_000L).toInt(),
            Kind.SYSTEMS to systems.size, Kind.GAMES to gamesPlayed,
        )
        // The highest milestone reached of each kind, then the next one.
        val milestones = Kind.entries.flatMap { kind ->
            val value = values.getValue(kind)
            val list = targets.getValue(kind)
            listOfNotNull(
                list.lastOrNull { it <= value }?.let { Milestone(kind, it, value, true) },
                list.firstOrNull { it > value }?.let { Milestone(kind, it, value, false) },
            )
        }

        val yearOf = { at: Long -> Instant.ofEpochMilli(at).atZone(zone).year }
        val timeByYear = weeks.entries.groupBy({ it.key.year }, { it.value }).mapValues { it.value.sum() }
        val finishedByYear = finishedGames.groupBy({ yearOf(it.updatedAt) }, { it.game })
        val years = (timeByYear.keys + finishedByYear.keys).sortedDescending().map { y ->
            Year(y, timeByYear[y] ?: 0L, finishedByYear[y].orEmpty().sortedBy { it.sortKey })
        }

        return Summary(
            millis = millis,
            gamesPlayed = gamesPlayed,
            systems = systems,
            genres = stats({ SmartCollections.genre(it.genre) }).take(TOP),
            developers = stats({ it.developer?.trim()?.takeIf { d -> d.length > 1 } }).take(TOP),
            series = stats({ series(it.title) }, min = 2).take(TOP),
            finished = finishedGames.sortedByDescending { it.updatedAt }.map { it.game },
            playing = backlog.filter { it.backlogStatus == BacklogStatus.PLAYING }.sortedByDescending { it.updatedAt }.map { it.game },
            milestones = milestones,
            years = years,
        )
    }
}

private fun Passport.Milestone.label(): String = str(
    when (kind) {
        Passport.Kind.FINISHED -> R.string.passport_m_finished
        Passport.Kind.HOURS -> R.string.passport_m_hours
        Passport.Kind.SYSTEMS -> R.string.passport_m_systems
        Passport.Kind.GAMES -> R.string.passport_m_games
    },
    target,
)

@Composable
fun PassportScreen(viewModel: LibraryViewModel, onOpenGame: (Long) -> Unit) {
    val context = LocalContext.current
    val games by viewModel.everyGame.collectAsStateWithLifecycle()
    val backlog by viewModel.backlog.collectAsStateWithLifecycle()
    val played by viewModel.playedGames.collectAsStateWithLifecycle()
    val playtimeFile = remember { Playtime(File(context.filesDir, "player/playtime.properties")) }
    val records by produceState<Pair<Map<Pair<String, String>, Long>, Map<LocalDate, Long>>?>(null) {
        value = withContext(Dispatchers.IO) { playtimeFile.all() to playtimeFile.weeks() }
    }
    val summary by produceState<Passport.Summary?>(null, games, backlog, played, records) {
        val (playtime, weeks) = records ?: return@produceState
        value = withContext(Dispatchers.Default) { Passport.build(games, backlog, played, playtime, weeks) }
    }
    ScreenFrame(title = stringResource(R.string.passport_title), subtitle = stringResource(R.string.passport_subtitle)) {
        val s = summary ?: return@ScreenFrame
        if (s.gamesPlayed == 0) {
            FocusCard(modifier = Modifier.fillMaxWidth()) { CardBody(stringResource(R.string.passport_empty)) }
            return@ScreenFrame
        }
        FocusCard(modifier = Modifier.fillMaxWidth()) {
            Text(
                if (s.millis > 0) stringResource(R.string.passport_time, Playtime.format(s.millis)) else stringResource(R.string.passport_no_time),
                style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface,
            )
            CardBody(
                listOf(
                    stringResource(R.string.passport_played, countOf(s.gamesPlayed, "game")),
                    countOf(s.systems.size, "system"),
                    stringResource(R.string.passport_finished_n, s.finished.size),
                    stringResource(R.string.passport_playing_n, s.playing.size),
                ).joinToString(" · "),
            )
        }

        CardTitle(stringResource(R.string.passport_milestones))
        FocusCard(modifier = Modifier.fillMaxWidth()) {
            val reached = s.milestones.filter { it.reached }
            CardBody(if (reached.isEmpty()) stringResource(R.string.passport_no_milestones) else reached.joinToString("\n") { "✓ " + it.label() })
            s.milestones.filter { !it.reached }.forEach { m ->
                Text(stringResource(R.string.passport_next, m.label(), m.value, m.target), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
        }

        // Bars measure time where the built-in player has timed something, and games where it hasn't.
        @Composable
        fun chart(title: Int, stats: List<Passport.Stat>, name: (String) -> String = { it }) {
            if (stats.isEmpty()) return
            CardTitle(stringResource(title))
            val byTime = stats.any { it.millis > 0 }
            val max = stats.maxOf { if (byTime) it.millis else it.games.toLong() }
            stats.forEach { st ->
                Bar(
                    name(st.key), if (byTime) st.millis else st.games.toLong(), max,
                    listOfNotNull(st.millis.takeIf { it > 0 }?.let { Playtime.format(it) }, countOf(st.games, "game")).joinToString(" · "),
                )
            }
        }
        chart(R.string.passport_systems, s.systems, viewModel::systemName)
        chart(R.string.passport_genres, s.genres)
        chart(R.string.passport_series, s.series)
        chart(R.string.passport_developers, s.developers)
        if (s.genres.isEmpty() && s.developers.isEmpty()) CardBody(stringResource(R.string.passport_need_details))

        @Composable
        fun gameList(title: Int, list: List<GameEntity>) {
            if (list.isEmpty()) return
            CardTitle(stringResource(title))
            list.forEach { g ->
                FocusCard(modifier = Modifier.fillMaxWidth(), onClick = { onOpenGame(g.id) }) {
                    Text(g.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    CardBody(viewModel.systemName(g.system))
                }
            }
        }
        gameList(R.string.backlog_playing, s.playing)
        gameList(R.string.backlog_finished, s.finished)

        if (s.years.isNotEmpty()) {
            CardTitle(stringResource(R.string.passport_years))
            s.years.forEach { y ->
                FocusCard(modifier = Modifier.fillMaxWidth()) {
                    Text(y.year.toString(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    CardBody(
                        listOfNotNull(
                            y.millis.takeIf { it > 0 }?.let { stringResource(R.string.passport_time, Playtime.format(it)) },
                            y.finished.takeIf { it.isNotEmpty() }?.let { f -> stringResource(R.string.passport_finished_games, f.joinToString { it.title }) },
                        ).joinToString("\n"),
                    )
                }
            }
        }
    }
}
