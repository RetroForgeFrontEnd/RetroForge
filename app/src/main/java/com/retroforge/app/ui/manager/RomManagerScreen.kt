package com.retroforge.app.ui.manager

import com.retroforge.app.R

import androidx.compose.ui.res.stringResource

import com.retroforge.app.ui.ChipRow
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.retroforge.app.ui.CardBody
import com.retroforge.app.ui.CardTitle
import com.retroforge.app.ui.ScreenFrame
import com.retroforge.app.ui.library.plural
import com.retroforge.core.library.ManagerRun
import com.retroforge.core.library.RomManagerRepository.Companion.formatBytes
import com.retroforge.core.library.RootEntity
import com.retroforge.library.manage.CompressFormat
import com.retroforge.library.manage.DELETED_FOLDER
import com.retroforge.library.manage.DUPLICATES_FOLDER
import com.retroforge.library.manage.KeepRule
import com.retroforge.library.manage.ORIGINALS_FOLDER
import com.retroforge.library.manage.Sameness
import com.retroforge.core.ui.FocusCard
import com.retroforge.core.ui.FocusChip
import com.retroforge.core.ui.StatusPill
import com.retroforge.core.ui.StatusTone
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RomManagerScreen(viewModel: RomManagerViewModel, deleteGameId: Long = -1) {
    LaunchedEffect(deleteGameId) { if (deleteGameId >= 0) viewModel.askDelete(deleteGameId) }
    val roots by viewModel.roots.collectAsStateWithLifecycle()
    val rootId by viewModel.rootId.collectAsStateWithLifecycle()
    val tab by viewModel.tab.collectAsStateWithLifecycle()
    val run by viewModel.run.collectAsStateWithLifecycle()
    val pending by viewModel.pending.collectAsStateWithLifecycle()

    LaunchedEffect(roots) { roots?.firstOrNull()?.let { if (viewModel.root() == null) viewModel.selectRoot(it) } }

    // Compressing a disc can take minutes; don't let the screen sleep (and the app be suspended) meanwhile.
    val view = LocalView.current
    val running = run is ManagerRun.Running
    DisposableEffect(running) {
        view.keepScreenOn = running
        onDispose { view.keepScreenOn = false }
    }

    ScreenFrame(
        title = stringResource(R.string.library_rom_manager),
        subtitle = stringResource(R.string.rom_manager_tidy_duplicates_and_compress_games_every),
    ) {
        val root = roots?.firstOrNull { it.id == rootId }
        if (roots?.isEmpty() == true) {
            CardBody(stringResource(R.string.rom_manager_add_a_game_folder_in_retro))
            return@ScreenFrame
        }
        if ((roots?.size ?: 0) > 1) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                roots.orEmpty().forEach { r -> FocusChip((if (r.id == rootId) "● " else "") + r.name, onClick = { viewModel.selectRoot(r) }) }
            }
        }
        if (root == null) return@ScreenFrame

        RunCard(run, viewModel)
        if (running) return@ScreenFrame

        pending?.let { p ->
            ConfirmCard(p, root, viewModel)
            return@ScreenFrame
        }

        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ManagerTab.entries.forEach { t -> FocusChip((if (t == tab) "● " else "") + t.label, onClick = { viewModel.tab.value = t }) }
        }
        when (tab) {
            ManagerTab.DUPLICATES -> DuplicatesTab(viewModel)
            ManagerTab.COMPRESS -> CompressTab(viewModel)
            ManagerTab.MULTI_DISC -> MultiDiscTab(viewModel)
            ManagerTab.SORT -> SortTab(viewModel)
            ManagerTab.HISTORY -> HistoryTab(viewModel, root)
        }
    }
}

@Composable
private fun RunCard(run: ManagerRun?, viewModel: RomManagerViewModel) {
    when (run) {
        is ManagerRun.Running -> FocusCard(modifier = Modifier.fillMaxWidth()) {
            CardTitle(run.title)
            CardBody(run.detail)
            val fraction = run.fraction
            if (fraction != null) LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
            else LinearProgressIndicator(Modifier.fillMaxWidth())
            CardBody(stringResource(R.string.rom_manager_keep_retroforge_open_until_this_finishes))
            FocusChip(stringResource(R.string.library_stop), onClick = viewModel::stop)
        }
        is ManagerRun.Done -> FocusCard(modifier = Modifier.fillMaxWidth()) {
            CardTitle(run.title)
            CardBody(run.summary)
            if (run.problems.isNotEmpty()) {
                StatusPill(plural(run.problems.size, "problem"), StatusTone.CAUTION)
                run.problems.take(8).forEach { CardBody("• $it") }
            }
            FocusChip(stringResource(R.string.rom_manager_ok), onClick = viewModel::dismissRun)
        }
        is ManagerRun.Failed -> FocusCard(modifier = Modifier.fillMaxWidth()) {
            StatusPill(stringResource(R.string.rom_manager_didn_t_run), StatusTone.BAD)
            CardBody(run.reason)
            FocusChip(stringResource(R.string.rom_manager_ok), onClick = viewModel::dismissRun)
        }
        null -> Unit
    }
}

@Composable
private fun ConfirmCard(pending: Pending, root: RootEntity, viewModel: RomManagerViewModel) {
    val refused by viewModel.writeAccessRefused.collectAsStateWithLifecycle()
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let(viewModel::grantWriteAccess) }
    viewModel.accessChecks.collectAsStateWithLifecycle().value
    val canWrite = viewModel.hasWriteAccess(root)
    FocusCard(modifier = Modifier.fillMaxWidth()) {
        when (pending) {
            is Pending.MoveDuplicates -> {
                CardTitle("Move ${if (pending.count == 1) "1 copy" else "${pending.count} copies"}?")
                CardBody(
                    stringResource(R.string.rom_manager_they_go_to_the_folder_at, DUPLICATES_FOLDER, root.name, DUPLICATES_FOLDER),
                )
            }
            is Pending.Compress -> {
                CardTitle("Compress ${plural(pending.count, "game")} (${formatBytes(pending.bytes)})?")
                CardBody(
                    stringResource(R.string.rom_manager_each_game_is_compressed_then_read, ORIGINALS_FOLDER),
                )
            }
            is Pending.DeleteGame -> {
                CardTitle(stringResource(R.string.rom_manager_delete, pending.game.title))
                CardBody(
                    "${if (pending.parts.size == 1) "Its file moves" else "Its ${pending.parts.size} files move"} to the $DELETED_FOLDER folder and its gamelist.xml entry is removed. " +
                        "You can undo this from History until you empty $DELETED_FOLDER with Free up space.",
                )
                pending.parts.take(6).forEach { CardBody("• $it") }
                if (pending.parts.size > 6) CardBody(stringResource(R.string.rom_manager_and_more, pending.parts.size - 6))
            }
            is Pending.Purge -> {
                CardTitle(stringResource(R.string.rom_manager_delete_permanently, pending.summary.folder)) { StatusPill(stringResource(R.string.rom_manager_this_can_t_be_undone), StatusTone.BAD) }
                CardBody(
                    "${plural(pending.summary.files, "file")} (${formatBytes(pending.summary.bytes)}) will be deleted for good. " +
                        "History entries that would need them can no longer be undone.",
                )
            }
            is Pending.SortLoose -> {
                CardTitle("Move ${plural(pending.count, "game")} into system folders?")
                CardBody(
                    stringResource(R.string.rom_manager_each_game_moves_into_the_folder),
                )
            }
            is Pending.MakePlaylists -> {
                CardTitle("Make ${plural(pending.count, "playlist")}?")
                CardBody(
                    stringResource(R.string.rom_manager_each_multi_disc_game_gets_an),
                )
            }
            is Pending.Undo -> {
                CardTitle(stringResource(R.string.rom_manager_undo_this))
                CardBody(pending.log.summary)
                CardBody(stringResource(R.string.rom_manager_files_move_back_where_they_were, DUPLICATES_FOLDER))
            }
        }
        CardBody(stringResource(R.string.rom_manager_if_another_frontend_uses_this_folder))
        if (!canWrite) {
            StatusPill(stringResource(R.string.rom_manager_needs_permission_to_change_files), StatusTone.CAUTION)
            CardBody(
                stringResource(R.string.rom_manager_so_far_retroforge_can_only_read, root.name),
            )
            if (refused) CardBody(stringResource(R.string.rom_manager_that_was_a_different_folder_please, root.name))
        }
    }
    ChipRow {
        if (canWrite) FocusChip(if (pending is Pending.Purge) "Delete permanently" else "Yes, go ahead", onClick = viewModel::confirm)
        else FocusChip(stringResource(R.string.rom_manager_allow_changes_to, root.name), onClick = {
            val rootUri = root.treeUri.toUri()
            pickFolder.launch(DocumentsContract.buildDocumentUriUsingTree(rootUri, DocumentsContract.getTreeDocumentId(rootUri)))
        })
        FocusChip(stringResource(R.string.network_drives_cancel), onClick = viewModel::cancelPending)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DuplicatesTab(viewModel: RomManagerViewModel) {
    val state by viewModel.duplicates.collectAsStateWithLifecycle()
    val rule by viewModel.keepRule.collectAsStateWithLifecycle()
    val selected by viewModel.selectedSets.collectAsStateWithLifecycle()
    viewModel.keepChoice.collectAsStateWithLifecycle().value // keepFor() reads it

    when (val s = state) {
        Loadable.Idle -> FocusCard(modifier = Modifier.fillMaxWidth(), onClick = viewModel::findDuplicates) {
            CardTitle(stringResource(R.string.rom_manager_find_duplicates))
            CardBody(stringResource(R.string.rom_manager_looks_for_the_same_game_in))
        }
        is Loadable.Loading -> {
            CardBody(if (s.total == 0) "Looking for duplicates …" else "Checking set ${s.done + 1} of ${s.total} …")
            com.retroforge.app.ui.DownloadBar(s.share)
        }
        is Loadable.Failed -> { StatusPill(stringResource(R.string.rom_manager_couldn_t_check), StatusTone.BAD); CardBody(s.reason); FocusChip(stringResource(R.string.rom_manager_try_again), onClick = viewModel::findDuplicates) }
        is Loadable.Ready -> {
            val report = s.value
            if (report.sets.isEmpty()) CardBody(stringResource(R.string.rom_manager_no_duplicates_to_tidy))
            else {
                CardBody("${plural(report.sets.size, "set")} of duplicates. Ticked sets hold identical data. Select a file to keep it instead.")
                ChipRow {
                    FocusChip((if (rule == KeepRule.MOST_COMPATIBLE) "● " else "") + "Keep most compatible", onClick = { viewModel.keepRule.value = KeepRule.MOST_COMPATIBLE })
                    FocusChip((if (rule == KeepRule.SMALLEST) "● " else "") + "Keep smallest", onClick = { viewModel.keepRule.value = KeepRule.SMALLEST })
                }
            }
            report.sets.forEachIndexed { i, set ->
                val keep = viewModel.keepFor(i, set)
                val on = i in selected
                FocusCard(modifier = Modifier.fillMaxWidth(), onClick = { viewModel.toggleSet(i) }, selected = on) {
                    Text((if (on) "☑ " else "☐ ") + set.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    when (set.sameness) {
                        Sameness.SAME_DATA -> StatusPill(stringResource(R.string.rom_manager_identical_data), StatusTone.GOOD)
                        Sameness.DIFFERENT_DATA -> StatusPill(stringResource(R.string.rom_manager_different_data_check_before_tidying), StatusTone.CAUTION)
                        Sameness.NOT_CHECKED -> StatusPill(stringResource(R.string.rom_manager_couldn_t_compare_too_large_or), StatusTone.NEUTRAL)
                    }
                }
                set.files.forEach { f ->
                    val keeping = f == keep
                    FocusChip(
                        (if (keeping) "Keep: " else "Move: ") + "${f.fileName} · ${formatBytes(f.size)}",
                        onClick = { viewModel.chooseKeep(i, f.relativePath) },
                    )
                }
            }
            if (report.skipped.isNotEmpty()) {
                FocusCard(modifier = Modifier.fillMaxWidth()) {
                    CardTitle("Left alone: ${plural(report.skipped.size, "set")}")
                    report.skipped.groupBy { it.reason }.forEach { (reason, sets) ->
                        CardBody("${sets.size} × $reason")
                        CardBody("  e.g. ${sets.take(3).joinToString { it.title }}")
                    }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val count = selected.sumOf { report.sets[it].files.size - 1 }
                FocusChip("Move ${if (count == 1) "1 copy" else "$count copies"} to $DUPLICATES_FOLDER", onClick = viewModel::askMoveDuplicates, enabled = count > 0)
                FocusChip(stringResource(R.string.rom_manager_check_again), onClick = viewModel::findDuplicates)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CompressTab(viewModel: RomManagerViewModel) {
    val state by viewModel.compressible.collectAsStateWithLifecycle()
    val selected by viewModel.selectedSystems.collectAsStateWithLifecycle()
    when (val s = state) {
        Loadable.Idle -> FocusCard(modifier = Modifier.fillMaxWidth(), onClick = viewModel::findCompressible) {
            CardTitle(stringResource(R.string.rom_manager_find_games_to_compress))
            CardBody(
                stringResource(R.string.rom_manager_disc_games_cue_bin_and_ps2),
            )
        }
        is Loadable.Loading -> { CardBody(stringResource(R.string.rom_manager_looking_for_uncompressed_games)); com.retroforge.app.ui.DownloadBar(s.share) }
        is Loadable.Failed -> { StatusPill(stringResource(R.string.rom_manager_couldn_t_check), StatusTone.BAD); CardBody(s.reason); FocusChip(stringResource(R.string.rom_manager_try_again), onClick = viewModel::findCompressible) }
        is Loadable.Ready -> {
            val report = s.value
            if (report.jobs.isEmpty()) CardBody(stringResource(R.string.rom_manager_everything_that_can_be_compressed_already))
            else CardBody(stringResource(R.string.rom_manager_select_the_systems_to_compress))
            report.jobs.groupBy { it.system }.forEach { (system, jobs) ->
                val on = system in selected
                FocusCard(modifier = Modifier.fillMaxWidth(), onClick = { viewModel.toggleSystem(system) }, selected = on) {
                    Text(
                        (if (on) "☑ " else "☐ ") + "${viewModel.systemName(system)}: ${plural(jobs.size, "game")} · ${formatBytes(jobs.sumOf { it.inputBytes })}",
                        style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface,
                    )
                    CardBody("To " + jobs.map { it.format }.distinct().joinToString { it.label } + ". " + jobs.take(4).joinToString { it.title } + if (jobs.size > 4) " and ${jobs.size - 4} more" else "")
                    if (jobs.any { it.format == CompressFormat.ZIP }) CardBody(stringResource(R.string.rom_manager_a_zip_is_usually_30_60))
                    if (jobs.any { it.format != CompressFormat.ZIP }) CardBody(stringResource(R.string.rom_manager_a_chd_is_usually_30_60))
                }
            }
            if (report.skipped.isNotEmpty()) {
                FocusCard(modifier = Modifier.fillMaxWidth()) {
                    CardTitle("Left alone: ${plural(report.skipped.size, "game")}")
                    report.skipped.groupBy { it.system to it.reason }.forEach { (key, games) ->
                        CardBody("${viewModel.systemName(key.first)}: ${games.size} × ${key.second}")
                    }
                }
            }
            val jobs = viewModel.selectedJobs()
            ChipRow {
                FocusChip("Compress ${plural(jobs.size, "game")}", onClick = viewModel::askCompress, enabled = jobs.isNotEmpty())
                FocusChip(stringResource(R.string.rom_manager_check_again), onClick = viewModel::findCompressible)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SortTab(viewModel: RomManagerViewModel) {
    val state by viewModel.loose.collectAsStateWithLifecycle()
    when (val s = state) {
        Loadable.Idle -> FocusCard(modifier = Modifier.fillMaxWidth(), onClick = viewModel::findLoose) {
            CardTitle(stringResource(R.string.rom_manager_sort_loose_games_into_system_folders))
            CardBody(
                stringResource(R.string.rom_manager_finds_games_outside_a_folder_for),
            )
        }
        is Loadable.Loading -> { CardBody(stringResource(R.string.rom_manager_looking_for_loose_games)); LinearProgressIndicator(Modifier.fillMaxWidth()) }
        is Loadable.Failed -> { StatusPill(stringResource(R.string.rom_manager_couldn_t_check), StatusTone.BAD); CardBody(s.reason); FocusChip(stringResource(R.string.rom_manager_try_again), onClick = viewModel::findLoose) }
        is Loadable.Ready -> {
            val jobs = s.value
            if (jobs.isEmpty()) CardBody(stringResource(R.string.rom_manager_every_game_is_already_in_its))
            jobs.groupBy { it.folder }.forEach { (folder, list) ->
                FocusCard(modifier = Modifier.fillMaxWidth()) {
                    Text("Into $folder: ${plural(list.size, "game")}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    list.take(5).forEach { CardBody("${it.title}  (from ${it.path.substringBeforeLast('/', "the top folder")})") }
                    if (list.size > 5) CardBody(stringResource(R.string.rom_manager_and_more, list.size - 5))
                }
            }
            ChipRow {
                if (jobs.isNotEmpty()) FocusChip("Move ${plural(jobs.size, "game")}", onClick = viewModel::askSortLoose)
                FocusChip(stringResource(R.string.rom_manager_check_again), onClick = viewModel::findLoose)
            }
        }
    }
}

@Composable
private fun MultiDiscTab(viewModel: RomManagerViewModel) {
    val state by viewModel.multiDisc.collectAsStateWithLifecycle()
    when (val s = state) {
        Loadable.Idle -> FocusCard(modifier = Modifier.fillMaxWidth(), onClick = viewModel::findMultiDisc) {
            CardTitle(stringResource(R.string.rom_manager_find_multi_disc_games))
            CardBody(
                stringResource(R.string.rom_manager_finds_games_split_over_several_discs),
            )
        }
        is Loadable.Loading -> { CardBody(stringResource(R.string.rom_manager_looking_for_multi_disc_games)); com.retroforge.app.ui.DownloadBar(s.share) }
        is Loadable.Failed -> { StatusPill(stringResource(R.string.rom_manager_couldn_t_check), StatusTone.BAD); CardBody(s.reason); FocusChip(stringResource(R.string.rom_manager_try_again), onClick = viewModel::findMultiDisc) }
        is Loadable.Ready -> {
            val report = s.value
            if (report.jobs.isEmpty()) CardBody(stringResource(R.string.rom_manager_every_multi_disc_game_already_has))
            report.jobs.groupBy { it.system }.forEach { (system, jobs) ->
                FocusCard(modifier = Modifier.fillMaxWidth()) {
                    Text("${viewModel.systemName(system)}: ${plural(jobs.size, "game")}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    jobs.take(5).forEach { CardBody("${it.title}: ${plural(it.discs.size, "disc")}") }
                    if (jobs.size > 5) CardBody(stringResource(R.string.rom_manager_and_more, jobs.size - 5))
                }
            }
            if (report.skipped.isNotEmpty()) {
                FocusCard(modifier = Modifier.fillMaxWidth()) {
                    CardTitle("Left alone: ${plural(report.skipped.size, "game")}")
                    report.skipped.take(10).forEach { CardBody("${it.title}: ${it.reason}") }
                }
            }
            ChipRow {
                if (report.jobs.isNotEmpty()) FocusChip("Make ${plural(report.jobs.size, "playlist")}", onClick = viewModel::askMakePlaylists)
                FocusChip(stringResource(R.string.rom_manager_check_again), onClick = viewModel::findMultiDisc)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HistoryTab(viewModel: RomManagerViewModel, root: RootEntity) {
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val holding by viewModel.holding.collectAsStateWithLifecycle()
    LaunchedEffect(root.id) { viewModel.refreshHolding() }
    if (holding.isNotEmpty()) {
        FocusCard(modifier = Modifier.fillMaxWidth()) {
            CardTitle(stringResource(R.string.rom_manager_free_up_space))
            CardBody(stringResource(R.string.rom_manager_files_retroforge_set_aside_delete_a))
            holding.forEach { CardBody("${it.folder}: ${plural(it.files, "file")} · ${formatBytes(it.bytes)}") }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            holding.forEach { h -> FocusChip(stringResource(R.string.rom_manager_delete_permanently_2, h.folder), onClick = { viewModel.askPurge(h) }) }
        }
    }
    val mine = logs.filter { it.rootId == root.id }
    if (mine.isEmpty()) CardBody(stringResource(R.string.rom_manager_nothing_has_been_changed_in_yet, root.name))
    val format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    mine.forEach { log ->
        FocusCard(modifier = Modifier.fillMaxWidth()) {
            CardTitle(when (log.kind) { "compress" -> "Compressed"; "delete" -> "Deleted"; "playlists" -> "Playlists made"; else -> "Duplicates tidied" })
            CardBody(log.summary)
            CardBody(format.format(Date(log.startedAt)) + " · ${plural(log.steps.size, "change")}" + if (!log.finished) " · interrupted" else "")
            log.undoneAt?.let { StatusPill(stringResource(R.string.rom_manager_undone, format.format(Date(it))), StatusTone.NEUTRAL) }
            if (log.undoneAt == null && log.purgedAt != null) StatusPill(stringResource(R.string.rom_manager_can_t_be_undone_its_files), StatusTone.NEUTRAL)
        }
        if (log.undoneAt == null && log.purgedAt == null) FocusChip(stringResource(R.string.rom_manager_undo), onClick = { viewModel.askUndo(log) })
    }
}
