package com.retroforge.app.ui.manager

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
        title = "ROM manager",
        subtitle = "Tidy duplicates and compress games. Every change is shown first and History can undo it. Files are set aside, never deleted, until you choose Free up space.",
    ) {
        val root = roots?.firstOrNull { it.id == rootId }
        if (roots?.isEmpty() == true) {
            CardBody("Add a game folder in Retro first.")
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
            CardBody("Keep RetroForge open until this finishes. Stopping is safe: the game in progress is left as it was.")
            FocusChip("Stop", onClick = viewModel::stop)
        }
        is ManagerRun.Done -> FocusCard(modifier = Modifier.fillMaxWidth()) {
            CardTitle(run.title)
            CardBody(run.summary)
            if (run.problems.isNotEmpty()) {
                StatusPill(plural(run.problems.size, "problem"), StatusTone.CAUTION)
                run.problems.take(8).forEach { CardBody("• $it") }
            }
            FocusChip("OK", onClick = viewModel::dismissRun)
        }
        is ManagerRun.Failed -> FocusCard(modifier = Modifier.fillMaxWidth()) {
            StatusPill("Didn't run", StatusTone.BAD)
            CardBody(run.reason)
            FocusChip("OK", onClick = viewModel::dismissRun)
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
                    "They go to the $DUPLICATES_FOLDER folder at the top of ${root.name}, keeping their folder structure. " +
                        "gamelist.xml entries are pointed at the copy you keep, after saving the original as gamelist.xml.retroforge-backup. " +
                        "Nothing is deleted; delete $DUPLICATES_FOLDER yourself once you're happy.",
                )
            }
            is Pending.Compress -> {
                CardTitle("Compress ${plural(pending.count, "game")} (${formatBytes(pending.bytes)})?")
                CardBody(
                    "Each game is compressed, then read back and checked. Only then do its original files move to the $ORIGINALS_FOLDER folder " +
                        "and gamelist.xml / .m3u entries point at the new file. This can take a long time for disc games.",
                )
            }
            is Pending.DeleteGame -> {
                CardTitle("Delete ${pending.game.title}?")
                CardBody(
                    "${if (pending.parts.size == 1) "Its file moves" else "Its ${pending.parts.size} files move"} to the $DELETED_FOLDER folder and its gamelist.xml entry is removed. " +
                        "You can undo this from History until you empty $DELETED_FOLDER with Free up space.",
                )
                pending.parts.take(6).forEach { CardBody("• $it") }
                if (pending.parts.size > 6) CardBody("… and ${pending.parts.size - 6} more")
            }
            is Pending.Purge -> {
                CardTitle("Delete ${pending.summary.folder} permanently?")
                StatusPill("This can't be undone", StatusTone.BAD)
                CardBody(
                    "${plural(pending.summary.files, "file")} (${formatBytes(pending.summary.bytes)}) will be deleted for good. " +
                        "History entries that would need them can no longer be undone.",
                )
            }
            is Pending.MakePlaylists -> {
                CardTitle("Make ${plural(pending.count, "playlist")}?")
                CardBody(
                    "Each multi-disc game gets an .m3u playlist next to its discs, listing them in order. Your disc files aren't changed. " +
                        "RetroForge then shows each game once, and the emulator can change discs from its menu. Other frontends will list the playlist too.",
                )
            }
            is Pending.Undo -> {
                CardTitle("Undo this?")
                CardBody(pending.log.summary)
                CardBody("Files move back where they were and gamelist.xml / .m3u changes are reversed. Files RetroForge made go to $DUPLICATES_FOLDER rather than being deleted.")
            }
        }
        CardBody("If another frontend uses this folder on a PC, close it first: some rewrite gamelist.xml when they exit.")
        if (!canWrite) {
            StatusPill("Needs permission to change files", StatusTone.CAUTION)
            CardBody(
                "So far RetroForge can only read ${root.name}. Choose the same folder in the next screen and tap \"Use this folder\", then \"Allow\".",
            )
            if (refused) CardBody("That was a different folder. Please choose ${root.name} itself.")
        }
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        if (canWrite) FocusChip(if (pending is Pending.Purge) "Delete permanently" else "Yes, go ahead", onClick = viewModel::confirm)
        else FocusChip("Allow changes to ${root.name}", onClick = {
            val rootUri = root.treeUri.toUri()
            pickFolder.launch(DocumentsContract.buildDocumentUriUsingTree(rootUri, DocumentsContract.getTreeDocumentId(rootUri)))
        })
        FocusChip("Cancel", onClick = viewModel::cancelPending)
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
            CardTitle("Find duplicates")
            CardBody("Looks for the same game in several files (e.g. a .zip and a .7z) and checks whether they hold the same data. This only reads your files.")
        }
        is Loadable.Loading -> {
            CardBody(if (s.total == 0) "Looking for duplicates …" else "Checking set ${s.done + 1} of ${s.total} …")
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        is Loadable.Failed -> { StatusPill("Couldn't check", StatusTone.BAD); CardBody(s.reason); FocusChip("Try again", onClick = viewModel::findDuplicates) }
        is Loadable.Ready -> {
            val report = s.value
            if (report.sets.isEmpty()) CardBody("No duplicates to tidy.")
            else {
                CardBody("${plural(report.sets.size, "set")} of duplicates. Ticked sets hold identical data. Select a file to keep it instead.")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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
                        Sameness.SAME_DATA -> StatusPill("Identical data", StatusTone.GOOD)
                        Sameness.DIFFERENT_DATA -> StatusPill("Different data: check before tidying", StatusTone.CAUTION)
                        Sameness.NOT_CHECKED -> StatusPill("Couldn't compare (too large or unreadable)", StatusTone.NEUTRAL)
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
                FocusChip("Check again", onClick = viewModel::findDuplicates)
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
            CardTitle("Find games to compress")
            CardBody(
                "Disc games (.cue/.bin, and PS2/PSP .iso) become CHD, which RetroArch, DuckStation, Flycast, NetherSX2 and PPSSPP play directly. " +
                    "Cartridge ROMs become .zip. This only reads your files until you confirm.",
            )
        }
        is Loadable.Loading -> { CardBody("Looking for uncompressed games …"); LinearProgressIndicator(Modifier.fillMaxWidth()) }
        is Loadable.Failed -> { StatusPill("Couldn't check", StatusTone.BAD); CardBody(s.reason); FocusChip("Try again", onClick = viewModel::findCompressible) }
        is Loadable.Ready -> {
            val report = s.value
            if (report.jobs.isEmpty()) CardBody("Everything that can be compressed already is.")
            else CardBody("Select the systems to compress.")
            report.jobs.groupBy { it.system }.forEach { (system, jobs) ->
                val on = system in selected
                FocusCard(modifier = Modifier.fillMaxWidth(), onClick = { viewModel.toggleSystem(system) }, selected = on) {
                    Text(
                        (if (on) "☑ " else "☐ ") + "$system: ${plural(jobs.size, "game")} · ${formatBytes(jobs.sumOf { it.inputBytes })}",
                        style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface,
                    )
                    CardBody("To " + jobs.map { it.format }.distinct().joinToString { it.label } + ". " + jobs.take(4).joinToString { it.title } + if (jobs.size > 4) " and ${jobs.size - 4} more" else "")
                    if (jobs.any { it.format == CompressFormat.ZIP }) CardBody("A .zip is usually 30–60% smaller.")
                    if (jobs.any { it.format != CompressFormat.ZIP }) CardBody("A CHD is usually 30–60% smaller; audio CD tracks shrink less.")
                }
            }
            if (report.skipped.isNotEmpty()) {
                FocusCard(modifier = Modifier.fillMaxWidth()) {
                    CardTitle("Left alone: ${plural(report.skipped.size, "game")}")
                    report.skipped.groupBy { it.system to it.reason }.forEach { (key, games) ->
                        CardBody("${key.first}: ${games.size} × ${key.second}")
                    }
                }
            }
            val jobs = viewModel.selectedJobs()
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FocusChip("Compress ${plural(jobs.size, "game")}", onClick = viewModel::askCompress, enabled = jobs.isNotEmpty())
                FocusChip("Check again", onClick = viewModel::findCompressible)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MultiDiscTab(viewModel: RomManagerViewModel) {
    val state by viewModel.multiDisc.collectAsStateWithLifecycle()
    when (val s = state) {
        Loadable.Idle -> FocusCard(modifier = Modifier.fillMaxWidth(), onClick = viewModel::findMultiDisc) {
            CardTitle("Find multi-disc games")
            CardBody(
                "Finds games split over several discs or floppies (\"Disc 1\", \"Disk 2 of 4\", \"Side B\") that have no .m3u playlist, " +
                    "so each plays as one game with disc swapping. This only reads your files.",
            )
        }
        is Loadable.Loading -> { CardBody("Looking for multi-disc games …"); LinearProgressIndicator(Modifier.fillMaxWidth()) }
        is Loadable.Failed -> { StatusPill("Couldn't check", StatusTone.BAD); CardBody(s.reason); FocusChip("Try again", onClick = viewModel::findMultiDisc) }
        is Loadable.Ready -> {
            val report = s.value
            if (report.jobs.isEmpty()) CardBody("Every multi-disc game already has a playlist.")
            report.jobs.groupBy { it.system }.forEach { (system, jobs) ->
                FocusCard(modifier = Modifier.fillMaxWidth()) {
                    Text("$system: ${plural(jobs.size, "game")}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                    jobs.take(5).forEach { CardBody("${it.title}: ${plural(it.discs.size, "disc")}") }
                    if (jobs.size > 5) CardBody("… and ${jobs.size - 5} more")
                }
            }
            if (report.skipped.isNotEmpty()) {
                FocusCard(modifier = Modifier.fillMaxWidth()) {
                    CardTitle("Left alone: ${plural(report.skipped.size, "game")}")
                    report.skipped.take(10).forEach { CardBody("${it.title}: ${it.reason}") }
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                FocusChip("Make ${plural(report.jobs.size, "playlist")}", onClick = viewModel::askMakePlaylists, enabled = report.jobs.isNotEmpty())
                FocusChip("Check again", onClick = viewModel::findMultiDisc)
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
            CardTitle("Free up space")
            CardBody("Files RetroForge set aside. Delete a folder once you're sure you don't need it: this is the only step that deletes for good.")
            holding.forEach { CardBody("${it.folder}: ${plural(it.files, "file")} · ${formatBytes(it.bytes)}") }
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            holding.forEach { h -> FocusChip("Delete ${h.folder} permanently", onClick = { viewModel.askPurge(h) }) }
        }
    }
    val mine = logs.filter { it.rootId == root.id }
    if (mine.isEmpty()) CardBody("Nothing has been changed in ${root.name} yet.")
    val format = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    mine.forEach { log ->
        FocusCard(modifier = Modifier.fillMaxWidth()) {
            CardTitle(when (log.kind) { "compress" -> "Compressed"; "delete" -> "Deleted"; "playlists" -> "Playlists made"; else -> "Duplicates tidied" })
            CardBody(log.summary)
            CardBody(format.format(Date(log.startedAt)) + " · ${plural(log.steps.size, "change")}" + if (!log.finished) " · interrupted" else "")
            log.undoneAt?.let { StatusPill("Undone ${format.format(Date(it))}", StatusTone.NEUTRAL) }
            if (log.undoneAt == null && log.purgedAt != null) StatusPill("Can't be undone: its files were deleted permanently", StatusTone.NEUTRAL)
        }
        if (log.undoneAt == null && log.purgedAt == null) FocusChip("Undo", onClick = { viewModel.askUndo(log) })
    }
}
