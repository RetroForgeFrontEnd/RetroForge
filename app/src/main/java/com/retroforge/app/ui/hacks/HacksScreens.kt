package com.retroforge.app.ui.hacks

import com.retroforge.app.R

import androidx.compose.ui.res.stringResource

import com.retroforge.app.ui.ChipRow
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.retroforge.app.ui.CardBody
import com.retroforge.app.ui.CardTitle
import com.retroforge.app.ui.ScreenFrame
import com.retroforge.app.ui.library.GameTile
import com.retroforge.app.ui.library.LibraryViewModel
import com.retroforge.core.ui.ControllerTextField
import com.retroforge.core.ui.FocusCard
import com.retroforge.core.ui.FocusChip
import com.retroforge.core.ui.StatusPill
import com.retroforge.core.ui.StatusTone
import com.retroforge.hacks.ListedHack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

@Composable
fun HacksScreen(
    viewModel: HacksViewModel,
    library: LibraryViewModel,
    onOpenGame: (Long) -> Unit,
    onOpenRetro: () -> Unit,
    onOpenHack: (String) -> Unit,
    onOpenSources: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val ownHacks by library.hacks.collectAsStateWithLifecycle()
    val roots by library.roots.collectAsStateWithLifecycle()
    val rootsById = remember(roots) { roots.orEmpty().associateBy { it.id } }
    val chosen by library.chosenCovers.collectAsStateWithLifecycle()
    ScreenFrame(title = stringResource(R.string.hacks_rom_hacks), subtitle = stringResource(R.string.hacks_rom_hacks_you_already_have_and)) {
        if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())

        Text(stringResource(R.string.hacks_your_hack_roms), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
        when {
            roots.isNullOrEmpty() -> FocusCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenRetro) {
                CardBody(stringResource(R.string.hacks_add_your_game_folder_in_retro))
            }
            ownHacks.isEmpty() -> CardBody(stringResource(R.string.hacks_no_hacks_found_in_your_folders))
            else -> ownHacks.groupBy { it.system }.forEach { (system, games) ->
                CardTitle("${library.systemName(system)} · ${games.size}")
                LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(games, key = { it.id }) { game ->
                        GameTile(
                            game,
                            library.art(rootsById, chosen, game),
                            onClick = { onOpenGame(game.id) },
                            modifier = Modifier.width(190.dp),
                        )
                    }
                }
            }
        }

        Text(stringResource(R.string.hacks_discover), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.padding(top = 12.dp))

        if (state.systems.size > 1) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FilterChip("All", selected = state.systemFilter == null) { viewModel.setFilter(null) } }
                items(state.systems) { system ->
                    FilterChip(systemName(system), selected = state.systemFilter == system) { viewModel.setFilter(system) }
                }
            }
        }

        state.visible.forEach { (system, hacks) ->
            CardTitle(systemName(system))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(hacks, key = { it.key }) { hack -> HackCard(hack) { onOpenHack(hack.key) } }
            }
        }
        if (!state.loading && state.hacks.isEmpty()) {
            CardBody(stringResource(R.string.hacks_no_hacks_yet_add_a_catalogue))
        }

        state.problems.forEach { problem ->
            FocusCard(modifier = Modifier.fillMaxWidth()) {
                StatusPill(stringResource(R.string.hacks_catalogue_problem), StatusTone.BAD)
                CardBody(problem)
            }
        }

        FocusCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenSources) {
            CardTitle(stringResource(R.string.hacks_catalogue_sources))
            CardBody("${state.catalogues.size} catalogue${if (state.catalogues.size == 1) "" else "s"} · ${state.hacks.size} hacks. Select to add or remove catalogues.")
        }
    }
}

@Composable
private fun FilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    FocusChip(if (selected) "• $label" else label, onClick = onClick)
}

@Composable
private fun HackCard(hack: ListedHack, onClick: () -> Unit) {
    val entry = hack.entry
    FocusCard(modifier = Modifier.width(300.dp).height(230.dp), onClick = onClick) {
        Text(entry.title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(entry.baseGame, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (entry.authors.isNotEmpty()) {
            Text("by ${entry.authors.joinToString()}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(
            entry.summary,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
fun HackDetailScreen(viewModel: HacksViewModel, key: String, onOpenPatcher: () -> Unit, onOpenHack: (String) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val hack = state.find(key)
    if (hack == null) {
        ScreenFrame(title = stringResource(R.string.hacks_hack)) { CardBody(if (state.loading) "Loading…" else "This hack is no longer in your catalogues.") }
        return
    }
    val entry = hack.entry
    val context = LocalContext.current
    var openError by remember { mutableStateOf<String?>(null) }

    ScreenFrame(title = entry.title, subtitle = "${systemName(entry.system)} · ${entry.baseGame}") {
        FocusCard(modifier = Modifier.fillMaxWidth()) {
            CardBody(entry.summary)
            val facts = listOfNotNull(
                entry.authors.takeIf { it.isNotEmpty() }?.let { "Authors: ${it.joinToString()}" },
                entry.version?.let { "Version: $it" },
                entry.released?.let { "Released: $it" },
                entry.tags.takeIf { it.isNotEmpty() }?.let { "Tags: ${it.joinToString()}" },
                "Listed in: ${hack.sourceName}",
            )
            facts.forEach { CardBody(it) }
        }

        entry.patchRepo?.let { repo ->
            var status by remember { mutableStateOf<String?>(null) }
            var progress by remember { mutableStateOf<Float?>(null) }
            var downloading by remember { mutableStateOf(false) }
            val scope = rememberCoroutineScope()
            FocusCard(modifier = Modifier.fillMaxWidth(), onClick = {
                status = "Getting the latest patch…"
                downloading = true
                scope.launch {
                    val result = withContext(Dispatchers.IO) { runCatching { HackDownloads.latestPatch(repo) { progress = it } } }
                    downloading = false
                    progress = null
                    result.onSuccess { file ->
                        com.retroforge.app.ui.patcher.PendingPatch.file = file
                        status = null
                        onOpenPatcher()
                    }.onFailure { status = "Couldn't download it: ${it.message}" }
                }
            }) {
                CardTitle(stringResource(R.string.hacks_download_and_patch))
                CardBody("Gets the latest patch from the hack's GitHub releases ($repo) and opens the patcher with it. Then pick your ${entry.baseGame} ROM.")
                status?.let { CardBody(it) }
                if (downloading) com.retroforge.app.ui.DownloadBar(progress)
            }
        }

        FocusCard(modifier = Modifier.fillMaxWidth(), onClick = { openError = openPage(context, entry.pageUrl) }) {
            CardTitle(stringResource(R.string.hacks_open_official_page))
            CardBody("Read the full description, check which ROM version it needs, and download the patch there. ${entry.pageUrl}")
            openError?.let { CardBody(it) }
        }

        if (entry.requires.isNotEmpty()) {
            FocusCard(modifier = Modifier.fillMaxWidth()) {
                CardTitle(stringResource(R.string.hacks_needs_these_patches_first_in_this))
            }
            entry.requires.forEach { requiredId ->
                val required = state.findRequirement(hack.sourceId, requiredId)
                FocusChip(required?.entry?.title ?: requiredId, onClick = { required?.let { onOpenHack(it.key) } }, enabled = required != null)
            }
        }

        entry.baseRom?.let { rom ->
            FocusCard(modifier = Modifier.fillMaxWidth()) {
                CardTitle(stringResource(R.string.hacks_base_rom))
                listOfNotNull(
                    rom.name, rom.crc32?.let { "CRC32 ${it.uppercase()}" }, rom.sha1?.let { "SHA-1 ${it.lowercase()}" },
                    rom.size?.let { "$it bytes" }, rom.note,
                ).forEach { CardBody(it) }
            }
        }

        FocusCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenPatcher) {
            CardTitle(stringResource(R.string.hacks_patch_it))
            CardBody(
                "Once you've downloaded the patch from the official page, open the ROM Patcher, pick your ROM and add the patch" +
                    (if (entry.requires.isNotEmpty()) " after the ones listed above." else ".") +
                    " Your original ROM is never changed.",
            )
        }
    }
}

private fun openPage(context: Context, url: String): String? = try {
    context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    null
} catch (_: ActivityNotFoundException) {
    "No browser is installed. Open $url on another device."
}

@Composable
fun CatalogueSourcesScreen(viewModel: HacksViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let(viewModel::addFromFile) }
    var url by remember { mutableStateOf("") }
    var confirming by remember { mutableStateOf(false) }

    ScreenFrame(
        title = stringResource(R.string.hacks_catalogue_sources),
        subtitle = stringResource(R.string.hacks_catalogues_list_hacks_and_link_to),
    ) {
        if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())

        state.catalogues.forEach { loaded ->
            FocusCard(modifier = Modifier.fillMaxWidth()) {
                CardTitle(loaded.catalogue.name)
                if (loaded.builtIn) StatusPill(stringResource(R.string.hacks_built_in), StatusTone.NEUTRAL)
                CardBody("${loaded.catalogue.hacks.size} hacks" + (loaded.catalogue.maintainer?.let { " · maintained by $it" } ?: ""))
                loaded.catalogue.description?.let { CardBody(it.take(300)) }
                loaded.source?.let { source ->
                    CardBody(source.url ?: "Added from a file on this device")
                    CardBody("Added ${formatDate(source.addedAt)}" + (source.refreshedAt?.let { ", updated ${formatDate(it)}" } ?: ""))
                }
                if (loaded.warnings.isNotEmpty()) {
                    CardBody(if (loaded.warnings.size == 1) "1 entry skipped as invalid." else "${loaded.warnings.size} entries skipped as invalid.")
                }
            }
            loaded.source?.let { source ->
                ChipRow {
                    if (source.url != null) FocusChip(stringResource(R.string.hacks_update), onClick = { viewModel.refresh(source.id) }, enabled = !state.busy)
                    FocusChip(stringResource(R.string.hacks_remove), onClick = { viewModel.remove(source.id) }, enabled = !state.busy)
                }
            }
        }

        FocusCard(modifier = Modifier.fillMaxWidth(), onClick = { pickFile.launch(arrayOf("application/json", "text/plain", "*/*")) }) {
            CardTitle(stringResource(R.string.hacks_add_from_a_file))
            CardBody(stringResource(R.string.hacks_choose_a_catalogue_json_file_on))
        }

        FocusCard(modifier = Modifier.fillMaxWidth()) {
            CardTitle(stringResource(R.string.hacks_add_from_a_link))
            CardBody(stringResource(R.string.hacks_paste_the_https_address_of_a))
            ControllerTextField(
                value = url,
                onValueChange = { url = it; confirming = false },
                singleLine = true,
                label = { Text(stringResource(R.string.hacks_https)) },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (!confirming) {
                FocusChip(stringResource(R.string.hacks_download_catalogue), onClick = { confirming = true }, enabled = url.isNotBlank() && !state.busy)
            } else {
                CardBody("Download the catalogue from ${url.trim()}? (up to 2 MB)")
                ChipRow {
                    FocusChip(stringResource(R.string.hacks_yes_download), onClick = { viewModel.addFromUrl(url); confirming = false; url = "" })
                    FocusChip(stringResource(R.string.network_drives_cancel), onClick = { confirming = false })
                }
            }
        }

        state.message?.let { message ->
            FocusCard(modifier = Modifier.fillMaxWidth()) {
                StatusPill(if (state.messageIsError) "Problem" else "Done", if (state.messageIsError) StatusTone.BAD else StatusTone.GOOD)
                CardBody(message)
            }
        }
    }
}

private fun formatDate(millis: Long): String = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(millis))
