package com.retroforge.app.ui.files

import android.content.ActivityNotFoundException
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.retroforge.app.R
import com.retroforge.app.ui.CardBody
import com.retroforge.app.ui.CardTitle
import com.retroforge.app.ui.CloseStep
import com.retroforge.core.library.RomManagerRepository.Companion.formatBytes
import com.retroforge.core.ui.ControllerTextField
import com.retroforge.core.ui.FocusCard
import com.retroforge.core.ui.FocusChip
import com.retroforge.core.ui.StatusPill
import com.retroforge.core.ui.StatusTone
import com.retroforge.core.ui.ThemeBackdrop
import java.text.DateFormat
import java.util.Date

/**
 * A file browser for the folders RetroForge can reach: the game collections and any folder added here. Copy, cut and
 * paste, rename, new folder, delete, share and open with another app. Every action is a chip, so a controller can do
 * all of it (Select turns A into tick/untick).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FileBrowserScreen(viewModel: FileBrowserViewModel) {
    val kioskGuard = com.retroforge.app.ui.rememberKioskGuard()
    val places by viewModel.places.collectAsStateWithLifecycle()
    val place by viewModel.place.collectAsStateWithLifecycle()
    val path by viewModel.path.collectAsStateWithLifecycle()
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val sort by viewModel.sort.collectAsStateWithLifecycle()
    val showHidden by viewModel.showHidden.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val selecting by viewModel.selecting.collectAsStateWithLifecycle()
    val selected by viewModel.selected.collectAsStateWithLifecycle()
    val clipboard by viewModel.clipboard.collectAsStateWithLifecycle()
    val task by viewModel.task.collectAsStateWithLifecycle()
    val ask by viewModel.ask.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val addFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { it?.let(viewModel::addPlace) }
    val allowChanges = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { picked ->
        val p = (viewModel.ask.value as? FileAsk.WriteAccess)?.place ?: return@rememberLauncherForActivityResult
        if (picked != null) viewModel.grantWrite(p, picked)
    }

    // Back leaves select mode, then goes up a folder, then leaves the browser.
    CloseStep(enabled = selecting || path.size > 1) { if (selecting) viewModel.clearSelection() else viewModel.up() }

    val shown = remember(entries, sort, showHidden, filter) { viewModel.sorted(entries.orEmpty(), sort, showHidden, filter) }
    val running = task is FileTask.Running

    fun open(entry: FileEntry) {
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(entry.uri, entry.mime.ifEmpty { "*/*" }).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.files_open_with)))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.files_no_app, Toast.LENGTH_SHORT).show()
        }
    }

    fun share(list: List<FileEntry>) {
        val files = list.filter { !it.isFolder }.ifEmpty { return }
        val intent = if (files.size == 1) {
            Intent(Intent.ACTION_SEND).setType(files[0].mime.ifEmpty { "*/*" }).putExtra(Intent.EXTRA_STREAM, files[0].uri)
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).setType("*/*").putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(files.map { it.uri }))
        }.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.files_share)))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, R.string.files_no_app, Toast.LENGTH_SHORT).show()
        }
    }

    ThemeBackdrop {
        LazyColumn(
            modifier = Modifier.widthIn(max = 960.dp).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.files_title), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(end = 40.dp))
                    Text(stringResource(R.string.files_subtitle), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    places.orEmpty().forEach { p -> FocusChip((if (p.tree == place?.tree) "● " else "") + p.label, onClick = { viewModel.open(p) }) }
                    FocusChip("+ " + stringResource(R.string.files_add_folder), onClick = { addFolder.launch(null) })
                    place?.takeIf { it.added }?.let { p -> FocusChip(stringResource(R.string.files_remove_place), onClick = { viewModel.removePlace(p) }) }
                }
            }
            if (place == null) {
                item { CardBody(stringResource(R.string.files_pick_a_place)) }
                return@LazyColumn
            }

            task?.let { t -> item { TaskCard(t, viewModel) } }
            ask?.let { a -> item { AskCard(a, viewModel, onAllowChanges = { allowChanges.launch(place?.tree) }) } }

            item {
                // Breadcrumb: each folder on the way is a chip back to it.
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    path.forEachIndexed { i, (_, name) ->
                        if (i > 0) Text("›", style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.CenterVertically))
                        FocusChip(name, onClick = { viewModel.goTo(i) })
                    }
                }
            }
            if (!running) item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (selecting) {
                        val chosen = shown.filter { it.id in selected }
                        FocusChip(stringResource(R.string.files_done), onClick = viewModel::clearSelection)
                        FocusChip(stringResource(R.string.files_select_all), onClick = { viewModel.selectAll(shown) })
                        if (chosen.isNotEmpty()) {
                            FocusChip(stringResource(R.string.files_copy), onClick = { viewModel.copy(cut = false) })
                            FocusChip(stringResource(R.string.files_cut), onClick = { kioskGuard { viewModel.copy(cut = true) } })
                            if (chosen.size == 1) FocusChip(stringResource(R.string.files_rename), onClick = viewModel::askRename)
                            FocusChip(stringResource(R.string.files_delete), onClick = { kioskGuard(viewModel::askDelete) })
                            if (chosen.any { !it.isFolder }) FocusChip(stringResource(R.string.files_share), onClick = { share(chosen) })
                            chosen.singleOrNull()?.takeIf { !it.isFolder }?.let { e -> FocusChip(stringResource(R.string.files_open_with), onClick = { open(e) }) }
                        }
                    } else {
                        if (path.size > 1) FocusChip("↑ " + stringResource(R.string.files_up), onClick = { viewModel.up() })
                        FocusChip(stringResource(R.string.files_select), onClick = { viewModel.startSelecting() })
                        FocusChip("+ " + stringResource(R.string.files_new_folder), onClick = viewModel::askNewFolder)
                        FocusChip(stringResource(when (sort) {
                            FileSort.NAME -> R.string.files_sort_name
                            FileSort.SIZE -> R.string.files_sort_size
                            FileSort.DATE -> R.string.files_sort_date
                        }), onClick = { viewModel.setSort(FileSort.entries[(sort.ordinal + 1) % FileSort.entries.size]) })
                        FocusChip(stringResource(if (showHidden) R.string.files_hide_hidden else R.string.files_show_hidden), onClick = { viewModel.setShowHidden(!showHidden) })
                    }
                    clipboard?.let { c ->
                        FocusChip(stringResource(if (c.cut) R.string.files_move_here else R.string.files_paste_here, c.entries.size), onClick = viewModel::paste)
                        FocusChip(stringResource(R.string.files_clear_clipboard), onClick = viewModel::clearClipboard)
                    }
                }
            }
            if (selecting) item { CardBody(stringResource(R.string.files_selected, selected.size)) }
            item {
                ControllerTextField(filter, { viewModel.filter.value = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.files_filter)) })
            }
            error?.let { e -> item { StatusPill(e, StatusTone.BAD) } }
            if (entries != null && shown.isEmpty() && error == null) item { CardBody(stringResource(R.string.files_empty)) }

            items(shown, key = { it.id }) { entry ->
                val isSelected = entry.id in selected
                FocusCard(
                    modifier = Modifier.fillMaxWidth(),
                    selected = isSelected,
                    contentPadding = 12.dp,
                    onClick = {
                        when {
                            selecting -> viewModel.toggle(entry)
                            entry.isFolder -> viewModel.enter(entry)
                            else -> open(entry)
                        }
                    },
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (selecting) Text(if (isSelected) "☑" else "☐", style = MaterialTheme.typography.titleLarge)
                        Text(iconFor(entry), style = MaterialTheme.typography.titleLarge)
                        Column(Modifier.weight(1f)) {
                            Text(entry.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            val details = listOfNotNull(
                                if (entry.isFolder) null else formatBytes(entry.size),
                                entry.modified.takeIf { it > 0 }?.let { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it)) },
                            ).joinToString(" · ")
                            if (details.isNotEmpty()) Text(details, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskCard(task: FileTask, viewModel: FileBrowserViewModel) {
    FocusCard(modifier = Modifier.fillMaxWidth()) {
        when (task) {
            is FileTask.Running -> {
                CardTitle(stringResource(if (task.moving) R.string.files_moving else R.string.files_copying))
                CardBody(task.name)
                val fraction = task.fraction
                if (fraction != null) LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                else LinearProgressIndicator(Modifier.fillMaxWidth())
                FocusChip(stringResource(R.string.files_stop), onClick = viewModel::stop)
            }
            is FileTask.Done -> {
                StatusPill(stringResource(R.string.files_some_not_done), StatusTone.CAUTION)
                task.problems.take(8).forEach { CardBody("• $it") }
                FocusChip(stringResource(R.string.files_ok), onClick = viewModel::dismissTask)
            }
            is FileTask.Failed -> {
                StatusPill(stringResource(R.string.files_failed), StatusTone.BAD)
                CardBody(task.reason)
                FocusChip(stringResource(R.string.files_ok), onClick = viewModel::dismissTask)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AskCard(ask: FileAsk, viewModel: FileBrowserViewModel, onAllowChanges: () -> Unit) {
    FocusCard(modifier = Modifier.fillMaxWidth()) {
        when (ask) {
            is FileAsk.Delete -> {
                CardTitle(ask.entries.singleOrNull()?.let { stringResource(R.string.files_delete_one, it.name) } ?: stringResource(R.string.files_delete_many, ask.entries.size))
                CardBody(stringResource(R.string.files_delete_body))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FocusChip(stringResource(R.string.files_delete), onClick = { viewModel.delete(ask.entries) })
                    FocusChip(stringResource(R.string.files_cancel), onClick = viewModel::dismissAsk)
                }
            }
            is FileAsk.Rename, FileAsk.NewFolder -> {
                val rename = ask as? FileAsk.Rename
                var name by remember(ask) { mutableStateOf(rename?.entry?.name ?: "") }
                CardTitle(stringResource(if (rename != null) R.string.files_rename else R.string.files_new_folder))
                ControllerTextField(name, { name = it.take(255) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.files_name)) })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FocusChip(stringResource(if (rename != null) R.string.files_rename else R.string.files_create), enabled = name.isNotBlank(), onClick = {
                        if (rename != null) viewModel.rename(rename.entry, name) else viewModel.newFolder(name)
                    })
                    FocusChip(stringResource(R.string.files_cancel), onClick = viewModel::dismissAsk)
                }
            }
            is FileAsk.WriteAccess -> {
                CardTitle(stringResource(R.string.files_read_only_title))
                CardBody(stringResource(R.string.files_read_only_body, ask.place.label))
                if (ask.wrongPick) StatusPill(stringResource(R.string.files_wrong_folder, ask.place.label), StatusTone.CAUTION)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FocusChip(stringResource(R.string.files_allow_changes), onClick = onAllowChanges)
                    FocusChip(stringResource(R.string.files_cancel), onClick = viewModel::dismissAsk)
                }
            }
        }
    }
}

private fun iconFor(entry: FileEntry): String {
    if (entry.isFolder) return "📁"
    val ext = entry.name.substringAfterLast('.', "").lowercase()
    return when {
        entry.mime.startsWith("image/") -> "🖼"
        entry.mime.startsWith("video/") -> "🎬"
        entry.mime.startsWith("audio/") -> "🎵"
        ext in setOf("zip", "7z", "rar", "gz", "tar") -> "🗜"
        ext in setOf("apk", "apks", "xapk") -> "📦"
        ext in setOf("txt", "xml", "json", "cfg", "ini", "log", "md", "m3u", "cue", "gdi") -> "📝"
        ext in setOf("chd", "iso", "bin", "cso", "rvz", "gcm", "wbfs", "zcci", "3ds", "cci", "nsp", "xci", "pbp", "cdi", "img") -> "💿"
        ext in setOf("nes", "sfc", "smc", "gb", "gbc", "gba", "nds", "n64", "z64", "v64", "md", "gen", "sms", "gg", "pce", "a26", "lnx", "j64", "ws", "wsc", "ngp", "vb") -> "🎮"
        else -> "📄"
    }
}
