package com.retroforge.app.ui.tournament

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.retroforge.app.R
import com.retroforge.app.tournament.Match
import com.retroforge.app.tournament.Score
import com.retroforge.app.tournament.Slot
import com.retroforge.app.tournament.Tournament
import com.retroforge.app.tournament.TournamentFormat
import com.retroforge.app.tournament.TournamentStore
import com.retroforge.app.tournament.Tournaments
import com.retroforge.app.ui.CardBody
import com.retroforge.app.ui.CardTitle
import com.retroforge.app.ui.ChipRow
import com.retroforge.app.ui.CloseStep
import com.retroforge.app.ui.ScreenFrame
import com.retroforge.core.ui.ControllerTextField
import com.retroforge.core.ui.FocusCard
import com.retroforge.core.ui.FocusChip
import com.retroforge.core.ui.StatusPill
import com.retroforge.core.ui.StatusTone
import com.retroforge.core.ui.pluralStr
import java.io.File
import java.text.DateFormat
import java.util.Date

/** Local tournaments: a list of them with the winners so far, a form for a new one, and each one's bracket or table. */
@Composable
fun TournamentScreen() {
    val context = LocalContext.current
    val store = remember { TournamentStore(File(context.filesDir, "tournaments.json")) }
    val all by store.all.collectAsStateWithLifecycle()
    var open by remember { mutableStateOf<String?>(null) }
    var creating by remember { mutableStateOf(false) }
    CloseStep(enabled = open != null || creating) { open = null; creating = false }

    val current = open?.let { id -> all.firstOrNull { it.id == id } }
    when {
        creating -> NewTournament(onStart = { store.save(it); creating = false; open = it.id })
        current != null -> TournamentDetail(current, onChange = store::save, onDelete = { store.delete(current.id); open = null })
        else -> ScreenFrame(title = stringResource(R.string.tournament_title), subtitle = stringResource(R.string.tournament_subtitle)) {
            FocusCard(modifier = Modifier.fillMaxWidth(), onClick = { creating = true }) {
                CardTitle(stringResource(R.string.tournament_new))
                CardBody(stringResource(R.string.tournament_new_body))
            }
            val (finished, going) = all.partition { it.finishedAt != null }
            going.forEach { t ->
                FocusCard(modifier = Modifier.fillMaxWidth(), onClick = { open = t.id }) {
                    CardTitle(t.name, status = { StatusPill(stringResource(R.string.tournament_in_progress), StatusTone.CAUTION) })
                    CardBody(summary(t))
                }
            }
            if (finished.isNotEmpty()) {
                FocusCard(modifier = Modifier.fillMaxWidth()) {
                    CardTitle(stringResource(R.string.tournament_winners))
                    finished.forEach { t ->
                        val winner = Tournaments.champion(t)?.let { t.players[it] } ?: "?"
                        CardBody(stringResource(R.string.tournament_winner_line, winner, t.name, date(t.finishedAt ?: t.createdAt)))
                    }
                }
                finished.forEach { t -> FocusChip(t.name, onClick = { open = t.id }) }
            }
        }
    }
}

@Composable
private fun summary(t: Tournament): String = listOf(
    t.game.ifBlank { null },
    formatName(t.format),
    pluralStr(R.plurals.tournament_players, t.players.size, t.players.size),
).filterNotNull().joinToString(" · ")

@Composable
private fun formatName(f: TournamentFormat) = when (f) {
    TournamentFormat.KNOCKOUT -> stringResource(R.string.tournament_knockout)
    TournamentFormat.LEAGUE -> stringResource(R.string.tournament_league)
}

private fun date(at: Long) = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(at))

@Composable
private fun NewTournament(onStart: (Tournament) -> Unit) {
    var name by remember { mutableStateOf("") }
    var game by remember { mutableStateOf("") }
    var rules by remember { mutableStateOf("") }
    var format by remember { mutableStateOf(TournamentFormat.KNOCKOUT) }
    var player by remember { mutableStateOf("") }
    var players by remember { mutableStateOf(listOf<String>()) }
    fun addPlayer() {
        val p = player.trim()
        if (p.isNotEmpty() && players.none { it.equals(p, ignoreCase = true) }) players = players + p
        player = ""
    }
    ScreenFrame(title = stringResource(R.string.tournament_new)) {
        ControllerTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.tournament_name)) })
        ControllerTextField(game, { game = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.tournament_game)) })
        ControllerTextField(rules, { rules = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.tournament_rules)) }, singleLine = false)
        CardTitle(stringResource(R.string.tournament_format))
        ChipRow {
            TournamentFormat.entries.forEach { f ->
                FocusChip((if (f == format) "● " else "○ ") + formatName(f), onClick = { format = f })
            }
        }
        CardBody(
            when (format) {
                TournamentFormat.KNOCKOUT -> stringResource(R.string.tournament_knockout_body)
                TournamentFormat.LEAGUE -> stringResource(R.string.tournament_league_body)
            },
        )
        CardTitle(pluralStr(R.plurals.tournament_players, players.size, players.size))
        ControllerTextField(player, { player = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.tournament_player_name)) })
        ChipRow {
            FocusChip(stringResource(R.string.tournament_add_player), onClick = ::addPlayer, enabled = player.isNotBlank())
            FocusChip(stringResource(R.string.tournament_shuffle), onClick = { players = players.shuffled() }, enabled = players.size > 1)
        }
        if (players.isNotEmpty()) {
            CardBody(stringResource(R.string.tournament_players_hint))
            ChipRow { players.forEachIndexed { i, p -> FocusChip("${i + 1}. $p ✕", onClick = { players = players - p }) } }
        }
        val ready = name.isNotBlank() && players.size >= 2
        FocusChip(stringResource(R.string.tournament_start), enabled = ready, onClick = {
            onStart(Tournament(TournamentStore.newId(), name.trim(), game.trim(), format, players, rules.trim(), createdAt = System.currentTimeMillis()))
        })
        if (!ready) CardBody(stringResource(R.string.tournament_need_two))
    }
}

@Composable
private fun TournamentDetail(t: Tournament, onChange: (Tournament) -> Unit, onDelete: () -> Unit) {
    var editing by remember(t.id) { mutableStateOf<String?>(null) }
    var askDelete by remember(t.id) { mutableStateOf(false) }
    val rounds = Tournaments.matches(t)
    val champion = Tournaments.champion(t)
    ScreenFrame(title = t.name, subtitle = summary(t)) {
        champion?.let {
            FocusCard(modifier = Modifier.fillMaxWidth()) {
                StatusPill(stringResource(R.string.tournament_champion), StatusTone.GOOD)
                Text("🏆 " + t.players[it], style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
            }
        }
        if (t.rules.isNotBlank()) FocusCard(modifier = Modifier.fillMaxWidth()) {
            CardTitle(stringResource(R.string.tournament_rules))
            CardBody(t.rules)
        }
        if (t.format == TournamentFormat.LEAGUE) FocusCard(modifier = Modifier.fillMaxWidth()) {
            CardTitle(stringResource(R.string.tournament_table))
            CardBody(stringResource(R.string.tournament_table_heading))
            Tournaments.table(t).forEachIndexed { i, r ->
                val diff = if (r.difference > 0) "+${r.difference}" else r.difference.toString()
                CardBody(stringResource(R.string.tournament_table_row, i + 1, t.players[r.player], r.played, r.won, r.drawn, r.lost, diff, r.points))
            }
        }
        rounds.forEachIndexed { i, round ->
            CardTitle(roundName(t.format, i, rounds.size))
            round.forEach { m ->
                if (editing == m.key) ScoreEditor(t, m, onDone = { score ->
                    editing = null
                    onChange(Tournaments.withResult(t, m.key, score))
                }, onCancel = { editing = null })
                else FocusCard(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = if (m.playable) ({ editing = m.key }) else null,
                ) { Text(matchLine(t, m), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface) }
            }
        }
        if (t.format == TournamentFormat.KNOCKOUT && champion == null) CardBody(stringResource(R.string.tournament_knockout_hint))
        if (askDelete) {
            FocusCard(modifier = Modifier.fillMaxWidth()) {
                CardTitle(stringResource(R.string.tournament_delete_title, t.name))
                ChipRow {
                    FocusChip(stringResource(R.string.tournament_delete), onClick = onDelete)
                    FocusChip(stringResource(R.string.tournament_keep), onClick = { askDelete = false })
                }
            }
        } else FocusChip(stringResource(R.string.tournament_delete_ask), onClick = { askDelete = true })
    }
}

@Composable
private fun roundName(format: TournamentFormat, i: Int, count: Int) = when {
    format == TournamentFormat.KNOCKOUT && i == count - 1 -> stringResource(R.string.tournament_final)
    format == TournamentFormat.KNOCKOUT && i == count - 2 -> stringResource(R.string.tournament_semi_finals)
    format == TournamentFormat.KNOCKOUT && i == count - 3 -> stringResource(R.string.tournament_quarter_finals)
    else -> stringResource(R.string.tournament_round, i + 1)
}

@Composable
private fun slotName(t: Tournament, s: Slot) = when (s) {
    is Slot.Player -> t.players[s.index]
    Slot.Bye -> stringResource(R.string.tournament_bye)
    Slot.Pending -> stringResource(R.string.tournament_to_come)
}

@Composable
private fun matchLine(t: Tournament, m: Match): String {
    val a = slotName(t, m.a)
    val b = slotName(t, m.b)
    val line = m.score?.let { "$a  ${it.a} – ${it.b}  $b" } ?: "$a  v  $b"
    return if (m.winner is Slot.Player && t.format == TournamentFormat.KNOCKOUT) "$line  ✓ ${slotName(t, m.winner)}" else line
}

@Composable
private fun ScoreEditor(t: Tournament, m: Match, onDone: (Score?) -> Unit, onCancel: () -> Unit) {
    var a by remember(m.key) { mutableStateOf(m.score?.a ?: 0) }
    var b by remember(m.key) { mutableStateOf(m.score?.b ?: 0) }
    FocusCard(modifier = Modifier.fillMaxWidth(), selected = true) {
        CardTitle(stringResource(R.string.tournament_enter_result))
        listOf(Triple(slotName(t, m.a), a, { v: Int -> a = v }), Triple(slotName(t, m.b), b, { v: Int -> b = v })).forEach { (name, value, set) ->
            Text("$name: $value", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            ChipRow {
                FocusChip("−", onClick = { set(maxOf(0, value - 1)) }, enabled = value > 0)
                FocusChip("+", onClick = { set(value + 1) })
            }
        }
        if (t.format == TournamentFormat.KNOCKOUT && a == b) CardBody(stringResource(R.string.tournament_no_draws))
        ChipRow {
            FocusChip(stringResource(R.string.tournament_save_result), onClick = { onDone(Score(a, b)) })
            if (m.score != null) FocusChip(stringResource(R.string.tournament_clear_result), onClick = { onDone(null) })
            FocusChip(stringResource(R.string.tournament_cancel), onClick = onCancel)
        }
    }
}
