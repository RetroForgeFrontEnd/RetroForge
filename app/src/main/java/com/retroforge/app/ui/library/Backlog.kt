package com.retroforge.app.ui.library

import com.retroforge.app.R
import com.retroforge.core.ui.str
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

fun BacklogStatus.label(): String = str(
    when (this) {
        BacklogStatus.WANT -> R.string.backlog_want
        BacklogStatus.PLAYING -> R.string.backlog_playing
        BacklogStatus.FINISHED -> R.string.backlog_finished
        BacklogStatus.DROPPED -> R.string.backlog_dropped
    },
)

/** The built-in player's play time, read off the main thread (the player process writes it). */
@Composable
private fun rememberPlaytime(): Playtime {
    val context = LocalContext.current
    return remember { Playtime(File(context.filesDir, "player/playtime.properties")) }
}

private fun day(at: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(at))

/** On a game's page: its backlog status, and the choice of another. */
@Composable
fun BacklogChips(game: GameEntity, viewModel: LibraryViewModel) {
    val status by remember(game.id) { viewModel.backlogStatus(game) }.collectAsStateWithLifecycle(initialValue = null)
    var choosing by remember(game.id) { mutableStateOf(false) }
    FocusChip(
        status?.let { stringResource(R.string.backlog_chip, it.label()) } ?: stringResource(R.string.backlog_add),
        onClick = { choosing = !choosing },
    )
    if (choosing) {
        ChipRow {
            BacklogStatus.entries.forEach { s ->
                FocusChip((if (s == status) "☑ " else "☐ ") + s.label(), onClick = { viewModel.setBacklog(game, s); choosing = false })
            }
            if (status != null) FocusChip(stringResource(R.string.backlog_remove), onClick = { viewModel.setBacklog(game, null); choosing = false })
        }
    }
}

/** A game worth playing next, and why. */
data class Suggestion(val game: GameEntity, val why: String)

/**
 * What to play next: the games being played (most recently played first), then the want-to-play games, those in the
 * genre of games already finished or on the system played most coming first, and otherwise the longest waiting.
 */
fun suggestNext(
    backlog: List<BacklogGame>,
    played: List<PlayedGame>,
    playtime: Map<Pair<String, String>, Long>,
    systemName: (String) -> String,
    limit: Int = 4,
): List<Suggestion> {
    val lastPlayed = played.associate { (it.game.rootId to it.game.relativePath) to it.lastPlayedAt }
    val playing = backlog.filter { it.backlogStatus == BacklogStatus.PLAYING }
        .sortedByDescending { lastPlayed[it.game.rootId to it.game.relativePath] ?: it.updatedAt }
        .take(2)
        .map { b ->
            val at = lastPlayed[b.game.rootId to b.game.relativePath]
            Suggestion(b.game, if (at != null) str(R.string.backlog_why_carry_on, day(at)) else str(R.string.backlog_why_carry_on_new))
        }
    // The system played most: built-in player time first, else how often games were started.
    val bySystem = playtime.entries.groupBy({ it.key.first }, { it.value }).mapValues { it.value.sum() }
    val topSystem = bySystem.maxByOrNull { it.value }?.key
        ?: played.groupingBy { it.game.system }.fold(0) { n, p -> n + p.playCount }.maxByOrNull { it.value }?.key
    val finishedGenres = backlog.filter { it.backlogStatus == BacklogStatus.FINISHED }.mapNotNull { it.game.genre?.trim()?.lowercase()?.ifBlank { null } }.toSet()
    val want = backlog.filter { it.backlogStatus == BacklogStatus.WANT }
        .sortedWith(
            compareByDescending<BacklogGame> { (if (it.game.genre?.trim()?.lowercase() in finishedGenres) 2 else 0) + (if (it.game.system == topSystem) 1 else 0) }
                .thenBy { it.updatedAt },
        )
        .map { b ->
            val why = when {
                b.game.genre?.trim()?.lowercase() in finishedGenres -> str(R.string.backlog_why_genre, b.game.genre!!.trim())
                b.game.system == topSystem -> str(R.string.backlog_why_system, systemName(b.game.system))
                else -> str(R.string.backlog_why_waiting, day(b.updatedAt))
            }
            Suggestion(b.game, why)
        }
    return (playing + want).take(limit)
}

@Composable
fun BacklogScreen(viewModel: LibraryViewModel, onOpenGame: (Long) -> Unit) {
    val backlog by viewModel.backlog.collectAsStateWithLifecycle()
    val played by viewModel.playedGames.collectAsStateWithLifecycle()
    val playtimeFile = rememberPlaytime()
    val playtime by produceState(emptyMap<Pair<String, String>, Long>()) { value = withContext(Dispatchers.IO) { playtimeFile.all() } }
    ScreenFrame(title = stringResource(R.string.backlog_title), subtitle = stringResource(R.string.backlog_subtitle)) {
        if (backlog.isEmpty()) {
            FocusCard(modifier = Modifier.fillMaxWidth()) { CardBody(stringResource(R.string.backlog_empty)) }
            return@ScreenFrame
        }
        CardTitle(stringResource(R.string.backlog_play_next))
        val suggestions = remember(backlog, played, playtime) { suggestNext(backlog, played, playtime, viewModel::systemName) }
        if (suggestions.isEmpty()) CardBody(stringResource(R.string.backlog_nothing_to_suggest))
        suggestions.forEach { s ->
            FocusCard(modifier = Modifier.fillMaxWidth(), onClick = { onOpenGame(s.game.id) }) {
                Text(s.game.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                CardBody(viewModel.systemName(s.game.system))
                Text(s.why, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
        var shown by remember { mutableStateOf(BacklogStatus.PLAYING) }
        val byStatus = backlog.groupBy { it.backlogStatus }
        ChipRow {
            BacklogStatus.entries.forEach { s ->
                FocusChip((if (s == shown) "● " else "") + s.label() + " · " + (byStatus[s]?.size ?: 0), onClick = { shown = s })
            }
        }
        val games = byStatus[shown].orEmpty().sortedBy { it.game.sortKey }
        if (games.isEmpty()) CardBody(stringResource(R.string.backlog_no_games_here))
        games.forEach { b ->
            FocusCard(modifier = Modifier.fillMaxWidth(), onClick = { onOpenGame(b.game.id) }) {
                Text(b.game.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                val time = playtime[b.game.system to b.game.fileName]
                CardBody(listOfNotNull(viewModel.systemName(b.game.system), time?.let { Playtime.format(it) }).joinToString(" · "))
            }
        }
    }
}

/** One labelled bar of a chart, its length [value] out of [max]. */
@Composable
internal fun Bar(label: String, value: Long, max: Long, valueText: String, onClick: (() -> Unit)? = null) {
    FocusCard(modifier = Modifier.fillMaxWidth(), onClick = onClick, contentPadding = 12.dp) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box(Modifier.padding(top = 6.dp).fillMaxWidth().height(10.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(5.dp))) {
            Box(Modifier.fillMaxWidth((value.toFloat() / max.coerceAtLeast(1)).coerceIn(0.02f, 1f)).fillMaxHeight().background(MaterialTheme.colorScheme.primary, RoundedCornerShape(5.dp)))
        }
    }
}

private enum class PlaytimeView { GAME, SYSTEM, WEEK }

@Composable
fun PlaytimeScreen(viewModel: LibraryViewModel, onOpenGame: (Long) -> Unit) {
    val played by viewModel.playedGames.collectAsStateWithLifecycle()
    val playtimeFile = rememberPlaytime()
    val data by produceState<Pair<Map<Pair<String, String>, Long>, Map<java.time.LocalDate, Long>>?>(null) {
        value = withContext(Dispatchers.IO) { playtimeFile.all() to playtimeFile.weeks() }
    }
    var view by remember { mutableStateOf(PlaytimeView.GAME) }
    ScreenFrame(title = stringResource(R.string.playtime_title), subtitle = stringResource(R.string.playtime_card_body)) {
        val (games, weeks) = data ?: return@ScreenFrame
        val total = games.values.sum()
        FocusCard(modifier = Modifier.fillMaxWidth()) {
            CardBody(if (total > 0) stringResource(R.string.playtime_total, Playtime.format(total)) else stringResource(R.string.playtime_none))
        }
        ChipRow {
            listOf(PlaytimeView.GAME to R.string.playtime_per_game, PlaytimeView.SYSTEM to R.string.playtime_per_system, PlaytimeView.WEEK to R.string.playtime_per_week).forEach { (v, label) ->
                FocusChip((if (v == view) "● " else "") + stringResource(label), onClick = { view = v })
            }
        }
        // Games are named from the library; a file no longer in it keeps its file name.
        val known by produceState(emptyMap<Pair<String, String>, GameEntity>(), games) {
            value = games.keys.mapNotNull { key -> viewModel.gameFor(key.first, key.second)?.let { key to it } }.toMap()
        }
        when (view) {
            PlaytimeView.GAME -> {
                val top = games.entries.sortedByDescending { it.value }.take(25)
                top.forEach { (key, millis) ->
                    val game = known[key]
                    Bar(
                        game?.title ?: key.second.substringBeforeLast('.'), millis, top.first().value,
                        Playtime.format(millis), onClick = game?.let { { onOpenGame(it.id) } },
                    )
                }
                // Games started in other emulators: RetroForge only knows how often.
                val others = played.filter { (it.game.system to it.game.fileName) !in games }.take(25)
                if (others.isNotEmpty()) {
                    CardTitle(stringResource(R.string.playtime_other_emulators))
                    CardBody(stringResource(R.string.playtime_other_emulators_body))
                    val most = others.maxOf { it.playCount }.toLong()
                    others.sortedByDescending { it.playCount }.forEach { p ->
                        Bar(p.game.title, p.playCount.toLong(), most, str(R.string.playtime_started_last, countOf(p.playCount, "time"), day(p.lastPlayedAt)), onClick = { onOpenGame(p.game.id) })
                    }
                }
            }
            PlaytimeView.SYSTEM -> {
                val bySystem = games.entries.groupBy({ it.key.first }, { it.value }).mapValues { it.value.sum() }.entries.sortedByDescending { it.value }
                bySystem.forEach { (system, millis) -> Bar(viewModel.systemName(system), millis, bySystem.first().value, Playtime.format(millis)) }
            }
            PlaytimeView.WEEK -> {
                CardBody(stringResource(R.string.playtime_weeks_note))
                WeekChart(weeks)
            }
        }
    }
}

/** The last 8 weeks as columns, this week on the right. */
@Composable
private fun WeekChart(weeks: Map<java.time.LocalDate, Long>) {
    val thisWeek = Playtime.weekOf(java.time.LocalDate.now())
    val shown = (7 downTo 0).map { thisWeek.minusWeeks(it.toLong()) }
    val max = shown.maxOf { weeks[it] ?: 0L }.coerceAtLeast(1)
    val short = java.time.format.DateTimeFormatter.ofPattern("d/M")
    FocusCard(modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(160.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.Bottom) {
            shown.forEach { week ->
                val millis = weeks[week] ?: 0L
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Bottom, horizontalAlignment = Alignment.CenterHorizontally) {
                    if (millis > 0) Text(if (millis >= 3_600_000) "${millis / 3_600_000}h" else "${millis / 60_000}m", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, softWrap = false)
                    Box(
                        Modifier.fillMaxWidth().fillMaxHeight((millis.toFloat() / max * 0.8f).coerceAtLeast(0.01f))
                            .background(if (week == thisWeek) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.6f), RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            shown.forEach { week ->
                Text(week.format(short), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, softWrap = false, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.weight(1f))
            }
        }
    }
    shown.reversed().filter { (weeks[it] ?: 0L) > 0 }.forEach { week ->
        CardBody(str(R.string.playtime_week_of, day(week.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli())) + ": " + Playtime.format(weeks[week]!!))
    }
}
