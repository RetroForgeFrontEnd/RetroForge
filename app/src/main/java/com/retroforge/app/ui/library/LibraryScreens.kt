package com.retroforge.app.ui.library

import android.text.format.Formatter
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.retroforge.app.ui.CardBody
import com.retroforge.app.ui.CardTitle
import com.retroforge.app.ui.ScreenFrame
import com.retroforge.core.library.GameEntity
import com.retroforge.core.library.ScanState
import com.retroforge.core.ui.FocusCard
import com.retroforge.core.ui.FocusChip
import com.retroforge.core.ui.StatusPill
import com.retroforge.core.ui.StatusTone
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RetroScreen(
    viewModel: LibraryViewModel,
    onOpenFolders: () -> Unit,
    onOpenSystem: (String) -> Unit,
    onOpenGame: (Long) -> Unit,
    onOpenPatcher: () -> Unit,
) {
    val roots by viewModel.roots.collectAsStateWithLifecycle()
    val systems by viewModel.systems.collectAsStateWithLifecycle()
    val scans by viewModel.scans.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.searchResults.collectAsStateWithLifecycle()
    val recent by viewModel.recentlyPlayed.collectAsStateWithLifecycle()
    val rootsById = remember(roots) { roots.orEmpty().associateBy { it.id } }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let(viewModel::addFolder) }

    ScreenFrame(title = "Retro", subtitle = "Your consoles, arcade and computer games.") {
        val running = scans.values.filterIsInstance<ScanState.Running>().firstOrNull()
        when {
            roots == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
            roots!!.isEmpty() -> FocusCard(modifier = Modifier.fillMaxWidth(), onClick = { pickFolder.launch(null) }) {
                CardTitle("Add your game folder")
                CardBody(
                    "Select to choose the folder that holds your system folders, such as an existing \"roms\" folder from another frontend " +
                        "(with snes, nes, psx … inside), or a single system folder. RetroForge only reads it: nothing is renamed, moved or written.",
                )
            }
            else -> FocusCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenFolders) {
                CardTitle("Game folders")
                CardBody("${plural(systems.sumOf { it.games }, "game")} in ${plural(systems.size, "system")} from ${plural(roots!!.size, "folder")}.")
                if (running != null) {
                    StatusPill("Scanning ${running.progress.currentSystem ?: "…"}", StatusTone.CAUTION)
                    if (running.progress.systemsTotal > 0) {
                        LinearProgressIndicator(
                            progress = { running.progress.systemsDone.toFloat() / running.progress.systemsTotal },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                    }
                } else {
                    CardBody("Select to add folders, rescan or see problems.")
                }
            }
        }

        if (systems.isNotEmpty()) {
            OutlinedTextField(
                value = query,
                onValueChange = { viewModel.query.value = it },
                singleLine = true,
                label = { Text("Search your games") },
                modifier = Modifier.fillMaxWidth(),
            )
            if (query.trim().length >= 2) {
                if (results.isEmpty()) CardBody("No games match \"${query.trim()}\".")
                results.take(30).forEach { game ->
                    FocusCard(modifier = Modifier.fillMaxWidth(), onClick = { onOpenGame(game.id) }) {
                        Text(game.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        CardBody(listOfNotNull(viewModel.systemName(game.system), tagLine(game)).joinToString(" · "))
                        // With several folders (e.g. internal storage and an SD card), show which one this copy is in.
                        if (rootsById.size > 1) rootsById[game.rootId]?.let { CardBody("In ${it.name}") }
                    }
                }
                if (results.size > 30) CardBody("Showing 30 of ${results.size}+ matches. Type more to narrow it down.")
            }

            if (recent.isNotEmpty()) {
                CardTitle("Recently played")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    rowItems(recent, key = { it.id }) { game ->
                        GameTile(game, viewModel.documentUri(rootsById, game.rootId, game.imageDocumentId), onClick = { onOpenGame(game.id) }, modifier = Modifier.width(190.dp))
                    }
                }
            }

            CardTitle("Systems")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                systems.forEach { system ->
                    FocusCard(modifier = Modifier.width(220.dp).heightIn(min = 110.dp), onClick = { onOpenSystem(system.id) }) {
                        Text(system.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 2)
                        CardBody(plural(system.games, "game") + if (system.hacks > 0) " · ${plural(system.hacks, "hack")}" else "")
                    }
                }
            }
        }

        FocusCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenPatcher) {
            CardTitle("ROM Patcher")
            CardBody("Apply IPS, BPS or UPS patches (ROM hacks, translations) to make a new copy. Originals are never changed.")
        }

    }
}

@Composable
fun LibraryFoldersScreen(viewModel: LibraryViewModel) {
    val roots by viewModel.roots.collectAsStateWithLifecycle()
    val scans by viewModel.scans.collectAsStateWithLifecycle()
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let(viewModel::addFolder) }

    ScreenFrame(title = "Game folders", subtitle = "RetroForge keeps its own index of these folders. Your files are only read, never changed.") {
        roots.orEmpty().forEach { root ->
            val state = scans[root.id]
            val problems by remember(root.id) { viewModel.problems(root.id) }.collectAsState(initial = emptyList())
            val access = viewModel.hasAccess(root)
            FocusCard(modifier = Modifier.fillMaxWidth()) {
                CardTitle(root.name)
                when {
                    !access -> StatusPill("Access lost", StatusTone.BAD)
                    state is ScanState.Running -> StatusPill("Scanning", StatusTone.CAUTION)
                    state is ScanState.Failed -> StatusPill("Scan failed", StatusTone.BAD)
                    root.lastScanAt != null -> StatusPill(plural(root.gameCount, "game"), StatusTone.GOOD)
                    else -> StatusPill("Not scanned yet", StatusTone.NEUTRAL)
                }
                when (state) {
                    is ScanState.Running -> {
                        val p = state.progress
                        CardBody("System ${minOf(p.systemsDone + 1, maxOf(p.systemsTotal, 1))} of ${maxOf(p.systemsTotal, 1)}: ${p.currentSystem ?: "starting"} · ${p.filesSeen} games found so far")
                        if (p.systemsTotal > 0) LinearProgressIndicator(progress = { p.systemsDone.toFloat() / p.systemsTotal }, modifier = Modifier.fillMaxWidth())
                    }
                    is ScanState.Failed -> CardBody(state.reason)
                    else -> Unit
                }
                if (!access) CardBody("Android no longer lets RetroForge read this folder (the drive may have been removed, or access was withdrawn). Forget it and add it again.")
                root.lastScanAt?.let { CardBody("Last scanned ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it))}") }
                if (root.layout == "UNRECOGNISED") CardBody("No system folders were found here. Choose the folder that contains snes, nes, psx …, or a single system folder.")
                val unrecognised = root.unrecognisedFolders.lines().filter { it.isNotBlank() }
                if (unrecognised.isNotEmpty()) {
                    CardBody("${plural(unrecognised.size, "folder")} not recognised as a system and skipped: ${unrecognised.take(12).joinToString()}${if (unrecognised.size > 12) " …" else ""}")
                }
                if (problems.isNotEmpty()) {
                    CardBody("${problems.size} problem${if (problems.size == 1) "" else "s"}:")
                    problems.take(5).forEach { CardBody("• ${it.path}: ${it.message}") }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FocusChip("Rescan", onClick = { viewModel.rescan(root) }, enabled = access && state !is ScanState.Running)
                FocusChip("Forget folder", onClick = { viewModel.forget(root) })
            }
        }
        FocusCard(modifier = Modifier.fillMaxWidth(), onClick = { pickFolder.launch(null) }) {
            CardTitle("Add a folder")
            CardBody("A \"roms\" folder from another frontend, a single system folder, or a folder on an SD card or USB drive. \"Forget folder\" removes only RetroForge's index.")
        }
    }
}

@Composable
fun SystemGamesScreen(viewModel: LibraryViewModel, system: String, onOpenGame: (Long) -> Unit) {
    val games by remember(system) { viewModel.games(system) }.collectAsStateWithLifecycle(initialValue = null)
    val roots by viewModel.roots.collectAsStateWithLifecycle()
    val rootsById = remember(roots) { roots.orEmpty().associateBy { it.id } }
    var filter by remember { mutableStateOf("") }
    var hideCopies by remember { mutableStateOf(false) }
    val shown = remember(games, filter, hideCopies) {
        val seenCopies = mutableSetOf<String>()
        games.orEmpty()
            .filter { filter.isBlank() || it.title.contains(filter.trim(), ignoreCase = true) }
            .filter { g -> !hideCopies || g.duplicateKey == null || seenCopies.add(g.duplicateKey!!) }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(170.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(viewModel.systemName(system), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
                if (games == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                else CardBody("${shown.size} of ${games!!.size} games")
                OutlinedTextField(value = filter, onValueChange = { filter = it }, singleLine = true, label = { Text("Filter") }, modifier = Modifier.fillMaxWidth())
                if (games.orEmpty().any { it.duplicateKey != null }) {
                    FocusChip(if (hideCopies) "• Showing one copy of each game" else "Show one copy of each game", onClick = { hideCopies = !hideCopies })
                }
            }
        }
        items(shown, key = { it.id }) { game ->
            GameTile(game, viewModel.documentUri(rootsById, game.rootId, game.imageDocumentId), onClick = { onOpenGame(game.id) })
        }
    }
}

@Composable
fun GameDetailScreen(viewModel: LibraryViewModel, gameId: Long, onOpenGame: (Long) -> Unit) {
    val game by remember(gameId) { viewModel.game(gameId) }.collectAsStateWithLifecycle(initialValue = null)
    val roots by viewModel.roots.collectAsStateWithLifecycle()
    val rootsById = remember(roots) { roots.orEmpty().associateBy { it.id } }
    var duplicates by remember { mutableStateOf<List<GameEntity>>(emptyList()) }
    LaunchedEffect(game) { duplicates = game?.let { viewModel.duplicatesOf(it) }.orEmpty() }
    val context = LocalContext.current

    val g = game ?: run {
        ScreenFrame(title = "Game") { CardBody("Loading…") }
        return
    }
    ScreenFrame(title = g.title, subtitle = viewModel.systemName(g.system)) {
        GameArt(g, viewModel.documentUri(rootsById, g.rootId, g.imageDocumentId), Modifier.fillMaxWidth().heightIn(max = 320.dp).padding(bottom = 4.dp))
        PlaySection(viewModel, g, rootsById[g.rootId])
        FocusCard(modifier = Modifier.fillMaxWidth()) {
            g.description?.let { CardBody(it) }
            listOfNotNull(
                g.releaseDate?.let { "Released: $it" },
                g.developer?.let { "Developer: $it" },
                g.publisher?.let { "Publisher: $it" },
                g.genre?.let { "Genre: $it" },
                g.players?.let { "Players: $it" },
                g.rating?.let { "Rating: ${"%.1f".format(it * 5)} / 5" },
                g.regions.ifEmpty { null }?.let { "Region: $it" },
                g.discs?.let { "Discs: $it" },
                g.tags.lines().filter { it.isNotBlank() && it != g.regions }.ifEmpty { null }?.let { "Tags: ${it.joinToString()}" },
            ).forEach { CardBody(it) }
            if (g.description == null && g.developer == null) CardBody("No gamelist.xml details for this game; the title comes from its file name.")
        }
        if (g.isHack) {
            FocusCard(modifier = Modifier.fillMaxWidth()) {
                StatusPill("ROM hack", StatusTone.CAUTION)
                CardBody(g.hackFolder?.let { "From your \"$it\" folder." } ?: "Marked as a hack in its file name.")
            }
        }
        FocusCard(modifier = Modifier.fillMaxWidth()) {
            CardTitle("File")
            CardBody(g.fileName)
            CardBody("In ${rootsById[g.rootId]?.name ?: "your folder"}: ${g.relativePath}")
            if (g.sizeBytes > 0) CardBody(Formatter.formatFileSize(context, g.sizeBytes))
        }
        if (duplicates.isNotEmpty()) {
            FocusCard(modifier = Modifier.fillMaxWidth()) {
                CardTitle("Other copies in your library")
                CardBody("Same game and tags, different file. RetroForge doesn't delete anything; this is for your information.")
            }
            duplicates.forEach { other -> FocusChip(other.relativePath, onClick = { onOpenGame(other.id) }) }
        }
    }
}

fun plural(count: Int, noun: String): String = if (count == 1) "1 $noun" else "$count ${noun}s"
