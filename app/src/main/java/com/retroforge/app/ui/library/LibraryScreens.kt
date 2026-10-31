package com.retroforge.app.ui.library

import com.retroforge.app.ui.countOf
import com.retroforge.core.ui.str
import com.retroforge.app.R

import androidx.compose.ui.res.stringResource

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
import com.retroforge.core.ui.ControllerTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.retroforge.app.ui.CardBody
import com.retroforge.app.ui.CardTitle
import com.retroforge.app.ui.ScreenFrame
import com.retroforge.core.library.CoverBatch
import com.retroforge.core.library.GameEntity
import java.io.File
import com.retroforge.core.library.ScanState
import com.retroforge.core.ui.FocusCard
import com.retroforge.core.ui.LocalThemeSpec
import com.retroforge.theme.GameView
import com.retroforge.core.ui.FocusChip
import com.retroforge.core.ui.StatusPill
import com.retroforge.core.ui.StatusTone
import java.text.DateFormat
import kotlinx.coroutines.launch
import java.util.Date

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RetroScreen(
    viewModel: LibraryViewModel,
    onOpenFolders: () -> Unit,
    onOpenSystem: (String) -> Unit,
    onOpenGame: (Long) -> Unit,
    onOpenPatcher: () -> Unit,
    onOpenManager: () -> Unit,
    onOpenFiles: () -> Unit = {},
    onOpenTournaments: () -> Unit = {},
    onOpenPlaylist: (Long) -> Unit,
    onOpenBacklog: () -> Unit = {},
    onOpenPlaytime: () -> Unit = {},
    onOpenSmart: () -> Unit = {},
    onOpenPassport: () -> Unit = {},
) {
    val roots by viewModel.roots.collectAsStateWithLifecycle()
    val systems by viewModel.systems.collectAsStateWithLifecycle()
    val scans by viewModel.scans.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val results by viewModel.searchResults.collectAsStateWithLifecycle()
    val recent by viewModel.recentlyPlayed.collectAsStateWithLifecycle()
    val rootsById = remember(roots) { roots.orEmpty().associateBy { it.id } }
    val chosen by viewModel.chosenCovers.collectAsStateWithLifecycle()
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let(viewModel::addFolder) }

    val systemArt by viewModel.systemArt.collectAsStateWithLifecycle()
    val indexingArt by viewModel.indexingArt.collectAsStateWithLifecycle()
    val pickArt = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let(viewModel::useArtFolder) }

    ScreenFrame(title = stringResource(R.string.home_retro), subtitle = stringResource(R.string.library_your_consoles_arcade_and_computer_games)) {
        // Systems, canvas style: canvas-es artwork (downloaded for the library's systems) or the user's own theme
        // folder, drawn in RetroForge's colours.
        if (systems.isNotEmpty()) {
            val style by viewModel.tileStyle.collectAsStateWithLifecycle()
            val ownArt by viewModel.ownArt.collectAsStateWithLifecycle()
            CanvasSystemsView(systems.map { SystemSummary(it.id, it.name, it.games, it.hacks) }, systemArt, style, onOpenSystem)
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FocusChip(stringResource(R.string.library_tiles, style.shownLabel()), onClick = viewModel::nextTileStyle)
                val layout by viewModel.gameLayout.collectAsStateWithLifecycle()
                FocusChip(stringResource(R.string.library_games, layout.shownLabel()), onClick = viewModel::nextGameLayout)
                FocusChip(
                    if (ownArt == null) str(R.string.library_use_my_own_theme_folder) else str(R.string.library_artwork_change, ownArt!!.themeName.ifBlank { str(R.string.library_your_theme) }),
                    onClick = { pickArt.launch(null) },
                )
                if (ownArt != null) FocusChip(stringResource(R.string.library_back_to_built_in_artwork), onClick = viewModel::clearArtFolder)
            }
            if (indexingArt) LinearProgressIndicator(Modifier.fillMaxWidth())
        }

        // Find a system or a game by typing, rather than swiping through a hundred systems.
        if (systems.isNotEmpty()) {
            var systemQuery by androidx.compose.runtime.saveable.rememberSaveable { androidx.compose.runtime.mutableStateOf("") }
            ControllerTextField(
                value = systemQuery,
                onValueChange = { systemQuery = it },
                singleLine = true,
                label = { Text(stringResource(R.string.library_find_a_system)) },
                modifier = Modifier.fillMaxWidth(),
            )
            val wanted = systemQuery.trim()
            if (wanted.isNotEmpty()) {
                val matches = systems.filter { it.name.contains(wanted, ignoreCase = true) || it.id.contains(wanted, ignoreCase = true) }
                if (matches.isEmpty()) CardBody(stringResource(R.string.library_no_systems_match, wanted))
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    matches.take(12).forEach { s -> FocusChip("${s.name} · ${s.games}", onClick = { onOpenSystem(s.id) }) }
                }
            }
            ControllerTextField(
                value = query,
                onValueChange = { viewModel.query.value = it },
                singleLine = true,
                label = { Text(stringResource(R.string.library_search_your_games)) },
                modifier = Modifier.fillMaxWidth(),
            )
            if (query.trim().length >= 2) {
                if (results.isEmpty()) CardBody(stringResource(R.string.library_no_games_match, query.trim()))
                results.take(30).forEach { game ->
                    FocusCard(modifier = Modifier.fillMaxWidth(), onClick = { onOpenGame(game.id) }) {
                        Text(game.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                        CardBody(listOfNotNull(viewModel.systemName(game.system), tagLine(game)).joinToString(" · "))
                        // With several folders (e.g. internal storage and an SD card), show which one this copy is in.
                        if (rootsById.size > 1) rootsById[game.rootId]?.let { CardBody(stringResource(R.string.library_in, it.name)) }
                    }
                }
                if (results.size > 30) CardBody(stringResource(R.string.library_showing_30_of_matches_type_more, results.size))
            }
        }
        val running = scans.values.filterIsInstance<ScanState.Running>().firstOrNull()
        when {
            roots == null -> LinearProgressIndicator(Modifier.fillMaxWidth())
            roots!!.isEmpty() -> FocusCard(modifier = Modifier.fillMaxWidth(), onClick = { pickFolder.launch(null) }) {
                CardTitle(stringResource(R.string.library_add_your_game_folder))
                CardBody(
                    stringResource(R.string.library_select_to_choose_the_folder_that),
                )
            }
            else -> FocusCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenFolders) {
                CardTitle(stringResource(R.string.library_game_folders))
                CardBody(stringResource(R.string.library_game_in_from, plural(systems.sumOf { it.games }, "game"), plural(systems.size, "system"), plural(roots!!.size, "folder")))
                if (running != null) {
                    val percent = if (running.progress.systemsTotal > 0) " · ${running.progress.systemsDone * 100 / running.progress.systemsTotal}%" else ""
                    StatusPill(str(R.string.library_scanning_2, running.progress.currentSystem ?: "…", percent), StatusTone.CAUTION)
                    if (running.progress.systemsTotal > 0) {
                        LinearProgressIndicator(
                            progress = { running.progress.systemsDone.toFloat() / running.progress.systemsTotal },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                    }
                } else {
                    CardBody(stringResource(R.string.library_select_to_add_folders_rescan_or))
                }
            }
        }

        if (systems.isNotEmpty()) PlayTogetherCard(viewModel, onOpenGame)

        if (systems.isNotEmpty()) {
            if (recent.isNotEmpty()) {
                CardTitle(stringResource(R.string.library_recently_played))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    rowItems(recent, key = { it.id }) { game ->
                        GameTile(game, viewModel.art(rootsById, chosen, game), onClick = { onOpenGame(game.id) }, modifier = Modifier.width(190.dp))
                    }
                }
            }

            FavouritesAndPlaylistsSection(viewModel, rootsById, onOpenGame, onOpenPlaylist)

            FocusCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenSmart) {
                CardTitle(stringResource(R.string.smart_title))
                CardBody(stringResource(R.string.smart_card_body))
            }
            FocusCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenPassport) {
                CardTitle(stringResource(R.string.passport_title))
                CardBody(stringResource(R.string.passport_card_body))
            }
            FocusCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenBacklog) {
                CardTitle(stringResource(R.string.backlog_title))
                CardBody(stringResource(R.string.backlog_card_body))
            }
            FocusCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenPlaytime) {
                CardTitle(stringResource(R.string.playtime_title))
                CardBody(stringResource(R.string.playtime_card_body))
            }

        }

        FocusCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenPatcher) {
            CardTitle(stringResource(R.string.library_rom_patcher))
            CardBody(stringResource(R.string.library_apply_ips_bps_or_ups_patches))
        }

        if (!roots.isNullOrEmpty()) {
            FocusCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenManager) {
                CardTitle(stringResource(R.string.library_rom_manager))
                CardBody(stringResource(R.string.library_tidy_duplicate_copies_and_compress_games))
            }
        }

        FocusCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenFiles) {
            CardTitle(stringResource(R.string.files_title))
            CardBody(stringResource(R.string.files_card_body))
        }

        FocusCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenTournaments) {
            CardTitle(stringResource(R.string.tournament_title))
            CardBody(stringResource(R.string.tournament_card_body))
        }

    }
}

@Composable
fun LibraryFoldersScreen(viewModel: LibraryViewModel) {
    val roots by viewModel.roots.collectAsStateWithLifecycle()
    val scans by viewModel.scans.collectAsStateWithLifecycle()
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let(viewModel::addFolder) }

    ScreenFrame(title = stringResource(R.string.library_game_folders), subtitle = stringResource(R.string.library_retroforge_keeps_its_own_index_of)) {
        roots.orEmpty().forEach { root ->
            val state = scans[root.id]
            val problems by remember(root.id) { viewModel.problems(root.id) }.collectAsState(initial = emptyList())
            val access = viewModel.hasAccess(root)
            FocusCard(modifier = Modifier.fillMaxWidth()) {
                CardTitle(root.name)
                when {
                    !access -> StatusPill(stringResource(R.string.library_access_lost), StatusTone.BAD)
                    state is ScanState.Running -> StatusPill(stringResource(R.string.library_scanning), StatusTone.CAUTION)
                    state is ScanState.Failed -> StatusPill(stringResource(R.string.library_scan_failed), StatusTone.BAD)
                    root.lastScanAt != null -> StatusPill(plural(root.gameCount, "game"), StatusTone.GOOD)
                    else -> StatusPill(stringResource(R.string.library_not_scanned_yet), StatusTone.NEUTRAL)
                }
                when (state) {
                    is ScanState.Running -> {
                        val p = state.progress
                        val percent = if (p.systemsTotal > 0) "${p.systemsDone * 100 / p.systemsTotal}% · " else ""
                        CardBody(stringResource(R.string.library_system_of_games_found_so_far, percent, minOf(p.systemsDone + 1, maxOf(p.systemsTotal, 1)), maxOf(p.systemsTotal, 1), p.currentSystem ?: "starting", p.filesSeen))
                        if (p.systemsTotal > 0) LinearProgressIndicator(progress = { p.systemsDone.toFloat() / p.systemsTotal }, modifier = Modifier.fillMaxWidth())
                    }
                    is ScanState.Failed -> CardBody(state.reason)
                    else -> Unit
                }
                if (!access) CardBody(stringResource(R.string.library_android_no_longer_lets_retroforge_read))
                root.lastScanAt?.let { CardBody(stringResource(R.string.library_last_scanned, DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(it)))) }
                if (root.layout == "UNRECOGNISED") CardBody(stringResource(R.string.library_no_system_folders_were_found_here))
                val unrecognised = root.unrecognisedFolders.lines().filter { it.isNotBlank() }
                if (unrecognised.isNotEmpty()) {
                    CardBody(stringResource(R.string.library_with_no_games_retroforge_recognises_not, plural(unrecognised.size, "folder"), unrecognised.take(12).joinToString(), if (unrecognised.size > 12) " …" else ""))
                }
                if (problems.isNotEmpty()) {
                    CardBody(countOf(problems.size, "problem") + ":")
                    problems.take(5).forEach { CardBody("• ${it.path}: ${it.message}") }
                }
            }
            var renaming by remember(root.id) { mutableStateOf<String?>(null) }
            renaming?.let { draft ->
                ControllerTextField(draft, { renaming = it.take(60) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.library_name_shown_in_retroforge)) })
            }
            com.retroforge.app.ui.ChipRow {
                if (renaming == null) {
                    FocusChip(stringResource(R.string.library_scan_for_new_games), onClick = { viewModel.rescan(root) }, enabled = access && state !is ScanState.Running)
                    FocusChip(stringResource(R.string.library_rename), onClick = { renaming = root.name })
                    FocusChip(stringResource(R.string.library_forget_folder), onClick = { viewModel.forget(root) })
                } else {
                    FocusChip(stringResource(R.string.library_save_name), enabled = !renaming.isNullOrBlank(), onClick = { viewModel.renameFolder(root, renaming.orEmpty()); renaming = null })
                    FocusChip(stringResource(R.string.network_drives_cancel), onClick = { renaming = null })
                }
            }
        }
        FocusCard(modifier = Modifier.fillMaxWidth(), onClick = { pickFolder.launch(null) }) {
            CardTitle(stringResource(R.string.library_add_a_folder))
            CardBody(stringResource(R.string.library_a_roms_folder_from_another_frontend))
        }
    }
}

@Composable
fun SystemGamesScreen(viewModel: LibraryViewModel, system: String, onOpenGame: (Long) -> Unit) {
    val games by remember(system) { viewModel.games(system) }.collectAsStateWithLifecycle(initialValue = null)
    val roots by viewModel.roots.collectAsStateWithLifecycle()
    val rootsById = remember(roots) { roots.orEmpty().associateBy { it.id } }
    val chosen by viewModel.chosenCovers.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf("") }
    var hideCopies by remember { mutableStateOf(false) }
    // Two tabs when the system has ROM hacks: the original games, and the hacks on their own.
    var showHacks by remember(system) { mutableStateOf(false) }
    val hackCount = games.orEmpty().count { it.isHack }
    val shown = remember(games, filter, hideCopies, showHacks) {
        val seenCopies = mutableSetOf<String>()
        games.orEmpty()
            .filter { hackCount == 0 || it.isHack == showHacks }
            .filter { filter.isBlank() || it.title.contains(filter.trim(), ignoreCase = true) }
            .filter { g -> !hideCopies || g.duplicateKey == null || seenCopies.add(g.duplicateKey!!) }
    }

    // The player's choice (Retro tab → Games), else the theme's: one game per row (two on wide screens), or a grid of tiles.
    val layout by viewModel.gameLayout.collectAsStateWithLifecycle()
    val asList = when (layout) {
        GameLayout.LIST -> true
        GameLayout.GRID, GameLayout.SHELF -> false
        GameLayout.THEME -> LocalThemeSpec.current.gameView == GameView.LIST
    }
    // 3D box renders for the bookshelf (ScreenScraper), looked for again whenever more arrive.
    val box3dSeen by viewModel.covers.box3dChanges.collectAsStateWithLifecycle()
    // Bookshelf: whole shelves, as many boxes as fit across (at least three).
    val perShelf = maxOf(3, (androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp - 48) / 130)
    // The system's name fades in over the games while they scroll, and away once they stop.
    val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()
    Box(Modifier.fillMaxSize()) {
    LazyVerticalGrid(
        state = gridState,
        columns = if (asList) GridCells.Adaptive(360.dp) else GridCells.Adaptive(170.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp, vertical = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(viewModel.systemName(system), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
                if (hackCount > 0) {
                    com.retroforge.app.ui.ChipRow {
                        FocusChip((if (!showHacks) "● " else "") + stringResource(R.string.library_games_2, games.orEmpty().size - hackCount), onClick = { showHacks = false })
                        FocusChip((if (showHacks) "● " else "") + stringResource(R.string.library_rom_hacks, hackCount), onClick = { showHacks = true })
                    }
                }
                if (games == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                else CardBody("${shown.size} of ${if (hackCount == 0) games!!.size else if (showHacks) hackCount else games!!.size - hackCount} ${if (showHacks) "hacks" else "games"}")
                FocusChip(stringResource(R.string.library_show_as, if (layout == GameLayout.SHELF) stringResource(R.string.library_as_bookshelf) else if (asList) stringResource(R.string.library_as_list) else stringResource(R.string.library_as_grid)), onClick = {
                    viewModel.setGameLayout(if (layout == GameLayout.SHELF) GameLayout.LIST else if (asList) GameLayout.GRID else GameLayout.SHELF)
                })
                if (layout == GameLayout.SHELF) Box3dBatch(viewModel, shown)
                ControllerTextField(value = filter, onValueChange = { filter = it }, singleLine = true, label = { Text(stringResource(R.string.library_filter)) }, modifier = Modifier.fillMaxWidth())
                SystemCoverBatch(viewModel, system, games.orEmpty(), chosen)
                val apps = remember(system) { viewModel.standaloneOptions(system).map { (_, app) -> app.id to app.name } }
                com.retroforge.app.ui.settings.SystemDriverCard(system, remember(system) { viewModel.systemCore(system) }, apps)
                if (games.orEmpty().any { it.duplicateKey != null }) {
                    FocusChip(if (hideCopies) stringResource(R.string.library_showing_one_copy_of_each_game) else stringResource(R.string.library_show_one_copy_of_each_game), onClick = { hideCopies = !hideCopies })
                }
            }
        }
        if (layout == GameLayout.SHELF) {
            items(shown.chunked(perShelf), key = { "shelf-" + it.first().id }, span = { GridItemSpan(maxLineSpan) }) { shelf ->
                GameShelf(shelf.map { Triple(it, viewModel.art(rootsById, chosen, it), box3dSeen.let { _ -> viewModel.covers.box3d(it) }) }, perShelf) { onOpenGame(it.id) }
            }
        } else items(shown, key = { it.id }) { game ->
            if (asList) GameRow(game, viewModel.art(rootsById, chosen, game), onClick = { onOpenGame(game.id) })
            else GameTile(game, viewModel.art(rootsById, chosen, game), onClick = { onOpenGame(game.id) })
        }
    }
    if (gridState.firstVisibleItemIndex > 0) {
        ScrollNamePopup(viewModel.systemName(system), gridState.firstVisibleItemIndex, gridState.isScrollInProgress, Modifier.align(Alignment.TopCenter).padding(top = 12.dp))
    }
    }
}

@Composable
fun GameDetailScreen(
    viewModel: LibraryViewModel,
    gameId: Long,
    onOpenGame: (Long) -> Unit,
    onChangeCover: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onEditDetails: (Long) -> Unit = {},
) {
    val game by remember(gameId) { viewModel.game(gameId) }.collectAsStateWithLifecycle(initialValue = null)
    val roots by viewModel.roots.collectAsStateWithLifecycle()
    val rootsById = remember(roots) { roots.orEmpty().associateBy { it.id } }
    val chosen by viewModel.chosenCovers.collectAsStateWithLifecycle()
    var duplicates by remember { mutableStateOf<List<GameEntity>>(emptyList()) }
    LaunchedEffect(game) { duplicates = game?.let { viewModel.duplicatesOf(it) }.orEmpty() }
    // Marked as a ROM hack (or not) by hand; null when its name decides.
    val hackMark by remember(game?.rootId, game?.relativePath) { game?.let { viewModel.hackMark(it) } ?: kotlinx.coroutines.flow.flowOf(null) }
        .collectAsStateWithLifecycle(initialValue = null)
    val context = LocalContext.current

    val g = game ?: run {
        ScreenFrame(title = stringResource(R.string.library_game)) { CardBody(stringResource(R.string.cover_picker_loading)) }
        return
    }
    // Dual-screen devices: the second screen shows this game while its page is open.
    val art = viewModel.art(rootsById, chosen, g)
    androidx.compose.runtime.DisposableEffect(g.id, art) {
        com.retroforge.app.ui.SecondScreen.shown = com.retroforge.app.ui.SecondScreen.Shown(g.title, viewModel.systemName(g.system), g.description, art)
        onDispose { com.retroforge.app.ui.SecondScreen.shown = null }
    }
    ScreenFrame(title = null) {
        GameHero(g, art, eyebrow = viewModel.systemName(g.system), subtitle = tagLine(g), actionLabel = null, onAction = null, bigArt = true)
        // Play first, so it's in reach without scrolling; then RetroBat's video and manual, when the folder has them.
        PlaySection(viewModel, g, rootsById[g.rootId])
        val media by androidx.compose.runtime.produceState(GameMedia(null, null), g.id) { value = findGameMedia(context.contentResolver, rootsById[g.rootId], g) }
        var manualOpen by remember { mutableStateOf(false) }
        media.manual?.let { manual ->
            FocusChip(stringResource(R.string.library_read_the_manual), onClick = { manualOpen = true })
            if (manualOpen) ManualViewer(manual, g.title, onClose = { manualOpen = false })
        }
        media.video?.let { GameVideo(it) }
        CoverControls(g, viewModel, onChangeCover = { onChangeCover(g.id) })
        FocusChip(stringResource(R.string.edit_details_edit_details), onClick = { onEditDetails(g.id) })
        FavouriteAndPlaylists(g, viewModel)
        BacklogChips(g, viewModel)
        GameNotesCard(g, viewModel.documentUri(rootsById, g.rootId, g.documentId))
        com.retroforge.app.ui.settings.GameTexturePacks(g.system, g.title)
        GameDlcCard(g.system)
        FocusChip(stringResource(R.string.library_delete_this_game), onClick = { onDelete(g.id) })
        FocusCard(modifier = Modifier.fillMaxWidth()) {
            g.description?.let { CardBody(it) }
            listOfNotNull(
                g.releaseDate?.let { "Released: $it" },
                g.developer?.let { "Developer: $it" },
                g.publisher?.let { "Publisher: $it" },
                g.genre?.let { "Genre: $it" },
                g.players?.let { "Players: $it" },
                g.rating?.let { str(R.string.library_rating_5, "%.1f".format(it * 5)) },
                g.regions.ifEmpty { null }?.let { "Region: $it" },
                g.discs?.let { "Discs: $it" },
                g.tags.lines().filter { it.isNotBlank() && it != g.regions }.ifEmpty { null }?.let { "Tags: ${it.joinToString()}" },
            ).forEach { CardBody(it) }
            if (g.description == null && g.developer == null) CardBody(stringResource(if (g.system == "ps4" && !g.relativePath.endsWith(".pkg", true)) R.string.library_no_gamelist_xml_title_from_param_sfo else R.string.library_no_gamelist_xml_details_for_this))
        }
        media.patch?.let { patch ->
            FocusCard(modifier = Modifier.fillMaxWidth()) {
                StatusPill(stringResource(R.string.library_soft_patch), StatusTone.GOOD)
                CardBody(stringResource(R.string.library_soft_patch_body, patch))
            }
        }
        if (g.isHack) {
            FocusCard(modifier = Modifier.fillMaxWidth()) {
                StatusPill(stringResource(R.string.library_rom_hack), StatusTone.CAUTION)
                CardBody(
                    when {
                        hackMark == true -> stringResource(R.string.library_marked_as_a_hack_by_you)
                        else -> g.hackFolder?.let { stringResource(R.string.library_from_your_folder, it) } ?: stringResource(R.string.library_marked_as_a_hack_in_its)
                    },
                )
                FocusChip(stringResource(R.string.library_not_a_rom_hack), onClick = { viewModel.setHackMark(g, if (hackMark == true) null else false) })
            }
        }
        FocusCard(modifier = Modifier.fillMaxWidth()) {
            CardTitle(stringResource(R.string.library_file))
            CardBody(g.fileName)
            CardBody(stringResource(R.string.library_in_2, rootsById[g.rootId]?.name ?: stringResource(R.string.library_your_folder), g.relativePath))
            if (g.sizeBytes > 0) CardBody(Formatter.formatFileSize(context, g.sizeBytes))
            // For hacks whose name doesn't say so: moves the game to the system's ROM hacks tab.
            if (!g.isHack) FocusChip(stringResource(R.string.library_this_is_a_rom_hack), onClick = { viewModel.setHackMark(g, if (hackMark == false) null else true) })
        }
        if (duplicates.isNotEmpty()) {
            FocusCard(modifier = Modifier.fillMaxWidth()) {
                CardTitle(stringResource(R.string.library_other_copies_in_your_library))
                CardBody(stringResource(R.string.library_same_game_and_tags_different_file))
            }
            duplicates.forEach { other -> FocusChip(other.relativePath, onClick = { onOpenGame(other.id) }) }
        }
    }
}

fun plural(count: Int, noun: String): String = com.retroforge.app.ui.countOf(count, noun)

@Composable
private fun SystemCoverBatch(viewModel: LibraryViewModel, system: String, games: List<GameEntity>, chosen: Map<Pair<Long, String>, File>) {
    val batch by viewModel.covers.batch.collectAsStateWithLifecycle()
    val missing = games.count { it.imageDocumentId == null && (it.rootId to it.relativePath) !in chosen }
    val mine = when (val b = batch) {
        is CoverBatch.Running -> b.system == system
        is CoverBatch.Finished -> b.system == system
        is CoverBatch.Failed -> b.system == system
        null -> false
    }
    when (val b = if (mine) batch else null) {
        is CoverBatch.Running -> {
            CardBody(stringResource(R.string.library_downloading_box_art_of_found, b.done, b.total, plural(b.found, "cover")))
            LinearProgressIndicator(progress = { if (b.total == 0) 0f else b.done.toFloat() / b.total }, modifier = Modifier.fillMaxWidth())
            FocusChip(stringResource(R.string.library_stop), onClick = viewModel.covers::cancelBatch)
        }
        is CoverBatch.Finished -> CardBody(
            str(R.string.library_box_art_added, plural(b.found, "cover")) +
                (if (b.missing > 0) str(R.string.library_not_found, b.missing) else "") +
                (if (b.failed > 0) str(R.string.library_failed, b.failed) else "") + ".",
        )
        is CoverBatch.Failed -> CardBody(b.reason)
        null -> if (missing > 0 && viewModel.covers.hasLibretroSource(system)) {
            FocusChip(stringResource(R.string.library_download_box_art_for_without_artwork, plural(missing, "game")), onClick = { viewModel.covers.downloadMissing(system, games) })
        }
    }
}

/** Netplay from the Retro tab: games friends are hosting on this Wi-Fi, each opening your copy of that game. */
@Composable
private fun PlayTogetherCard(viewModel: LibraryViewModel, onOpenGame: (Long) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var hosts by remember { mutableStateOf<List<com.retroforge.app.player.NetplayHost>?>(null) }
    var looking by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    val localNetwork = com.retroforge.app.player.rememberLocalNetwork()
    FocusCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle(stringResource(R.string.settings_play_together_netplay))
        CardBody(
            stringResource(R.string.library_two_players_two_devices_the_same),
        )
        FocusChip(if (looking) stringResource(R.string.library_looking) else stringResource(R.string.library_find_games_on_this_wi_fi), enabled = !looking, onClick = {
            localNetwork {
                looking = true
                message = null
                scope.launch {
                    hosts = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { com.retroforge.app.player.Netplay.find(context) }
                    looking = false
                }
            }
        })
        hosts?.let { found ->
            if (found.isEmpty()) CardBody(stringResource(R.string.library_nobody_s_hosting_on_this_wi))
            found.forEach { host ->
                FocusChip("${host.player}: ${host.title}", onClick = {
                    scope.launch {
                        val id = viewModel.gameIdForNetplay(host.gameId)
                        if (id != null) {
                            message = null
                            onOpenGame(id)
                        } else {
                            message = str(R.string.library_isn_t_in_your_library_you, host.title)
                        }
                    }
                })
            }
            if (found.isNotEmpty()) CardBody(stringResource(R.string.library_pick_one_to_open_your_copy))
        }
        message?.let { CardBody(it) }
    }
}

/** Bookshelf view: fetches ScreenScraper's 3D box renders for these games, with how far it has got. */
@Composable
private fun Box3dBatch(viewModel: LibraryViewModel, games: List<GameEntity>) {
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var done by remember { mutableStateOf<Int?>(null) }
    var result by remember { mutableStateOf<String?>(null) }
    val running = done != null
    FocusChip(if (running) stringResource(R.string.library_getting_3d_boxes) else stringResource(R.string.library_3d_boxes_from_screenscraper), enabled = !running, onClick = {
        if (!viewModel.covers.screenScraper.available) {
            result = str(R.string.library_3d_boxes_come_from_screenscraper_which)
            return@FocusChip
        }
        done = 0
        result = null
        scope.launch {
            result = viewModel.covers.fetchBox3d(games) { n -> done = n }.fold(
                onSuccess = { if (it == 0) str(R.string.library_screenscraper_had_no_new_3d_boxes) else str(R.string.library_added, plural(it, "3D box")) },
                onFailure = { str(R.string.library_couldn_t_get_3d_boxes, it.message) },
            )
            done = null
        }
    })
    done?.let { n ->
        val p = if (games.isEmpty()) 1f else n.toFloat() / games.size
        CardBody(stringResource(R.string.library_of_games, (p * 100).toInt(), n, games.size))
        LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
    }
    result?.let { CardBody(it) }
}
