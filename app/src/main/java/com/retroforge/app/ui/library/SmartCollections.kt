package com.retroforge.app.ui.library

import com.retroforge.app.R
import com.retroforge.core.ui.str
import androidx.activity.compose.BackHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.retroforge.app.player.Playtime
import com.retroforge.app.ui.CardBody
import com.retroforge.app.ui.CardTitle
import com.retroforge.app.ui.ChipRow
import com.retroforge.app.ui.ScreenFrame
import com.retroforge.app.ui.countOf
import com.retroforge.core.library.BacklogGame
import com.retroforge.core.library.BacklogStatus
import com.retroforge.core.library.GameEntity
import com.retroforge.core.library.PlayedGame
import com.retroforge.core.ui.FocusCard
import com.retroforge.core.ui.FocusChip
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Collections that make and keep themselves up to date: from the games' own details (genre, year, developer, players,
 * as the scraper or gamelist.xml gave them) and from what you play (backlog, playtime, last played). Nothing is
 * stored; they're worked out again whenever the library or the play history changes.
 */
object SmartCollections {
    enum class Group { PLAY, GENRE, DECADE, DEVELOPER, PLAYERS }

    /** A collection: [id] stays the same while its games change, so it can be reopened. */
    data class Collection(val id: String, val group: Group, val title: String, val games: List<GameEntity>)

    /** What's known of each game's play: when it was last started, and the built-in player's time. */
    data class Play(val lastPlayedAt: Long?, val playtime: Long)

    /** Genres, developers and decades need this many games to make a collection. */
    const val MIN_GAMES = 2
    const val MAX_DEVELOPERS = 20
    private const val DAY = 24 * 60 * 60 * 1000L

    /** "Role Playing Game / Action" → "Role playing game": the first genre named, as one name whatever its case. */
    fun genre(raw: String?): String? = raw?.split('/', ',', ';', '|')?.firstOrNull()?.trim()?.takeIf { it.length > 1 }
        ?.lowercase()?.replaceFirstChar { it.titlecase() }

    /** The year of a release date as gamelist.xml ("19910815T000000"), scrapers ("1991-08-15") or a person ("Aug 1991") write it. */
    fun year(raw: String?): Int? = raw?.let { Regex("(19[5-9][0-9]|20[0-9]{2})").find(it)?.value?.toInt() }

    /** The most players a game takes: "1-4" → 4, "2" → 2. */
    fun maxPlayers(raw: String?): Int? = raw?.let { Regex("[0-9]+").findAll(it).mapNotNull { m -> m.value.toIntOrNull() }.maxOrNull() }

    private fun developer(raw: String?): String? = raw?.trim()?.takeIf { it.length > 1 }

    fun build(
        games: List<GameEntity>,
        backlog: List<BacklogGame>,
        played: List<PlayedGame>,
        playtime: Map<Pair<String, String>, Long>,
        now: Long = System.currentTimeMillis(),
        // A title from the app's strings: [label] (the string, and its one argument or null). Tests pass their own.
        label: (Int, Any?) -> String = { id, arg -> if (arg == null) str(id) else str(id, arg) },
    ): List<Collection> {
        val key = { g: GameEntity -> g.rootId to g.relativePath }
        val status = backlog.associate { key(it.game) to it.backlogStatus }
        val last = played.associate { key(it.game) to it.lastPlayedAt }
        val play = games.associateWith { Play(last[key(it)], playtime[it.system to it.fileName] ?: 0L) }
        // Best first where the details give a rating, then A to Z.
        val best = compareByDescending<GameEntity> { it.rating ?: -1f }.thenBy { it.sortKey }
        val out = mutableListOf<Collection>()
        fun add(id: String, group: Group, title: String, list: List<GameEntity>, min: Int = 1) {
            if (list.size >= min) out += Collection(id, group, title, list)
        }

        BacklogStatus.entries.forEach { s ->
            add("backlog:${s.name}", Group.PLAY, label(s.labelRes(), null), games.filter { status[key(it)] == s }.sortedBy { it.sortKey })
        }
        add("recent", Group.PLAY, label(R.string.smart_recent, null), games.filter { (play[it]?.lastPlayedAt ?: 0) > now - 30 * DAY }.sortedByDescending { play[it]?.lastPlayedAt })
        add("most", Group.PLAY, label(R.string.smart_most_played, null), games.filter { (play[it]?.playtime ?: 0) > 0 }.sortedByDescending { play[it]?.playtime }.take(25))
        add("forgotten", Group.PLAY, label(R.string.smart_forgotten, null), games.filter { (play[it]?.lastPlayedAt ?: now) < now - 180 * DAY }.sortedBy { play[it]?.lastPlayedAt })
        add("unplayed", Group.PLAY, label(R.string.smart_unplayed, null), games.filter { play[it]?.lastPlayedAt == null && (play[it]?.playtime ?: 0) == 0L && status[key(it)] == null }.sortedWith(best))

        games.groupBy { genre(it.genre) }.filterKeys { it != null }.toSortedMap(compareBy { it!! }).forEach { (g, list) ->
            add("genre:${g!!.lowercase()}", Group.GENRE, g, list.sortedWith(best), MIN_GAMES)
        }
        games.groupBy { year(it.releaseDate)?.let { y -> y / 10 * 10 } }.filterKeys { it != null }.toSortedMap(compareBy { it!! }).forEach { (d, list) ->
            add("decade:$d", Group.DECADE, label(R.string.smart_decade, d!!), list.sortedWith(best), MIN_GAMES)
        }
        // The developers with the most games, up to [MAX_DEVELOPERS], so a big library isn't a wall of them.
        games.groupBy { developer(it.developer)?.lowercase() }.filterKeys { it != null }.values
            .filter { it.size >= MIN_GAMES }.sortedByDescending { it.size }.take(MAX_DEVELOPERS).forEach { list ->
                val name = developer(list.first().developer)!!
                add("developer:${name.lowercase()}", Group.DEVELOPER, name, list.sortedWith(best))
            }
        val players = games.associateWith { maxPlayers(it.players) ?: 0 }
        add("players:2", Group.PLAYERS, label(R.string.smart_two_players, null), games.filter { (players[it] ?: 0) >= 2 }.sortedWith(best))
        add("players:4", Group.PLAYERS, label(R.string.smart_party, null), games.filter { (players[it] ?: 0) >= 4 }.sortedWith(best))
        return out
    }
}

private fun BacklogStatus.labelRes(): Int = when (this) {
    BacklogStatus.WANT -> R.string.backlog_want
    BacklogStatus.PLAYING -> R.string.backlog_playing
    BacklogStatus.FINISHED -> R.string.backlog_finished
    BacklogStatus.DROPPED -> R.string.backlog_dropped
}

private fun SmartCollections.Group.label(): String = str(
    when (this) {
        SmartCollections.Group.PLAY -> R.string.smart_group_play
        SmartCollections.Group.GENRE -> R.string.smart_group_genre
        SmartCollections.Group.DECADE -> R.string.smart_group_decade
        SmartCollections.Group.DEVELOPER -> R.string.smart_group_developer
        SmartCollections.Group.PLAYERS -> R.string.smart_group_players
    },
)

/** Inside a collection: all its games, or only those not played yet, being played or finished. */
private enum class SmartFilter { ALL, UNPLAYED, PLAYING, FINISHED }

@Composable
fun SmartCollectionsScreen(viewModel: LibraryViewModel, onOpenGame: (Long) -> Unit) {
    val context = LocalContext.current
    val games by viewModel.everyGame.collectAsStateWithLifecycle()
    val backlog by viewModel.backlog.collectAsStateWithLifecycle()
    val played by viewModel.playedGames.collectAsStateWithLifecycle()
    val playtimeFile = remember { Playtime(File(context.filesDir, "player/playtime.properties")) }
    val playtime by produceState(emptyMap<Pair<String, String>, Long>()) { value = withContext(Dispatchers.IO) { playtimeFile.all() } }
    val collections by produceState(emptyList<SmartCollections.Collection>(), games, backlog, played, playtime) {
        value = withContext(Dispatchers.Default) { SmartCollections.build(games, backlog, played, playtime) }
    }
    var openId by rememberSaveable { mutableStateOf<String?>(null) }
    val open = collections.firstOrNull { it.id == openId }
    BackHandler(enabled = openId != null) { openId = null }

    if (open == null) {
        ScreenFrame(title = stringResource(R.string.smart_title), subtitle = stringResource(R.string.smart_subtitle)) {
            if (collections.isEmpty()) {
                FocusCard(modifier = Modifier.fillMaxWidth()) { CardBody(stringResource(R.string.smart_empty)) }
                return@ScreenFrame
            }
            collections.groupBy { it.group }.forEach { (group, list) ->
                CardTitle(group.label())
                list.forEach { c ->
                    FocusCard(modifier = Modifier.fillMaxWidth(), onClick = { openId = c.id }) {
                        Text(c.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        CardBody(listOf(countOf(c.games.size, "game"), c.games.take(3).joinToString { it.title }).joinToString(" · "))
                    }
                }
            }
            if (collections.none { it.group != SmartCollections.Group.PLAY }) CardBody(stringResource(R.string.smart_need_details))
        }
        return
    }
    val status = remember(backlog) { backlog.associate { (it.game.rootId to it.game.relativePath) to it.backlogStatus } }
    val playedKeys = remember(played) { played.map { it.game.rootId to it.game.relativePath }.toSet() }
    var filter by rememberSaveable(openId) { mutableStateOf(SmartFilter.ALL) }
    ScreenFrame(title = open.title, subtitle = countOf(open.games.size, "game")) {
        ChipRow {
            listOf(
                SmartFilter.ALL to R.string.news_all, SmartFilter.UNPLAYED to R.string.smart_filter_unplayed,
                SmartFilter.PLAYING to R.string.backlog_playing, SmartFilter.FINISHED to R.string.backlog_finished,
            ).forEach { (f, label) -> FocusChip((if (f == filter) "● " else "") + stringResource(label), onClick = { filter = f }) }
            FocusChip(stringResource(R.string.smart_back), onClick = { openId = null })
        }
        val shown = open.games.filter { g ->
            val k = g.rootId to g.relativePath
            when (filter) {
                SmartFilter.ALL -> true
                SmartFilter.UNPLAYED -> k !in playedKeys && (playtime[g.system to g.fileName] ?: 0L) == 0L
                SmartFilter.PLAYING -> status[k] == BacklogStatus.PLAYING
                SmartFilter.FINISHED -> status[k] == BacklogStatus.FINISHED
            }
        }
        if (shown.isEmpty()) CardBody(stringResource(R.string.backlog_no_games_here))
        shown.forEach { g ->
            FocusCard(modifier = Modifier.fillMaxWidth(), onClick = { onOpenGame(g.id) }) {
                Text(g.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                val details = listOfNotNull(
                    viewModel.systemName(g.system), SmartCollections.year(g.releaseDate)?.toString(),
                    playtime[g.system to g.fileName]?.let { Playtime.format(it) }, status[g.rootId to g.relativePath]?.label(),
                )
                CardBody(details.joinToString(" · "))
            }
        }
    }
}
