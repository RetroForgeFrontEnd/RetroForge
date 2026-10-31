package com.retroforge.app.ui.pc

import com.retroforge.core.ui.str
import com.retroforge.app.R

import androidx.compose.ui.res.stringResource

import com.retroforge.app.ui.ChipRow
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.retroforge.app.ui.CardBody
import com.retroforge.app.ui.CardTitle
import com.retroforge.core.library.GameEntity
import com.retroforge.core.library.LibraryRepository
import com.retroforge.core.ui.FocusCard
import com.retroforge.core.ui.FocusChip
import com.retroforge.core.ui.StatusPill
import com.retroforge.core.ui.StatusTone
import com.retroforge.library.GamePort
import com.retroforge.library.GamePorts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val OBTAINIUM = listOf("dev.imranr.obtainium", "dev.imranr.obtainium.fdroid")

/** Apps that run Windows games, the first installed one is opened. */
private val WINDOWS_RUNNERS = listOf("app.gamenative", "com.winlator")
private const val GAMENATIVE_PAGE = "https://github.com/utkarshdalal/GameNative/releases/latest"

/**
 * Native Android ports (decompilations, recompilations, PC source ports), with the ones for games in the user's
 * library first. Each needs the user's own copy of the game; "Get" opens Obtainium (installs and updates from the
 * project's releases) or the releases page.
 */
@Composable
fun PortsSection(library: LibraryRepository) {
    val ports = remember { GamePorts.bundled() }
    // port id -> a game the user owns that it can use
    val owned by produceState(emptyMap<String, GameEntity>(), library) {
        value = withContext(Dispatchers.IO) {
            val bySystem = ports.flatMap { it.systems }.distinct().associateWith { library.gamesFor(it).first() }
            ports.mapNotNull { port ->
                port.systems.firstNotNullOfOrNull { system ->
                    bySystem[system].orEmpty().firstOrNull { !it.isHack && port.matches(system, it.title) } // a hack isn't the original game
                }?.let { port.id to it }
            }.toMap()
        }
    }
    var showAll by remember { mutableStateOf(false) }
    var windowsTab by remember { mutableStateOf(false) }

    ChipRow {
        FocusChip((if (!windowsTab) "● " else "") + stringResource(R.string.ports_android_ports), onClick = { windowsTab = false; showAll = false })
        FocusChip((if (windowsTab) "● " else "") + stringResource(R.string.ports_windows_ports), onClick = { windowsTab = true; showAll = false })
    }
    CardBody(
        if (!windowsTab) str(R.string.ports_games_rebuilt_to_run_natively_on)
        else str(R.string.ports_ports_with_only_a_windows_version),
    )
    val tabPorts = ports.filter { it.isWindows == windowsTab }
    val mine = tabPorts.filter { it.id in owned }
    if (mine.isEmpty()) {
        CardBody(stringResource(R.string.ports_none_of_your_games_has_a))
    } else {
        Text(stringResource(R.string.ports_for_games_you_own), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        mine.forEach { PortCard(it, owned[it.id], library) }
    }
    val rest = tabPorts.filter { it.id !in owned }
    if (showAll) {
        Text(stringResource(R.string.ports_all_ports), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        rest.forEach { PortCard(it, null, library) }
    } else {
        if (rest.isNotEmpty()) FocusChip(stringResource(R.string.ports_show_all_other_ports, rest.size), onClick = { showAll = true })
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PortCard(port: GamePort, owned: GameEntity?, library: LibraryRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var prepared by remember { mutableStateOf<String?>(null) }
    val launch = port.`package`?.let { context.packageManager.getLaunchIntentForPackage(it) }
    FocusCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle(port.name)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatusPill(port.kindLabel, StatusTone.NEUTRAL)
            if (port.status == "experimental") StatusPill(stringResource(R.string.ports_experimental), StatusTone.CAUTION)
            if (owned != null) StatusPill(stringResource(R.string.ports_you_have, owned.title), StatusTone.GOOD)
        }
        CardBody(stringResource(R.string.ports_needs, port.needs))
        if (port.isWindows) {
            CardBody(
                stringResource(R.string.ports_how_download_the_windows_version_from),
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (port.isWindows) {
                FocusChip(stringResource(R.string.ports_download_windows_version), onClick = {
                    prepared = str(R.string.ports_looking_for_the_windows_version)
                    scope.launch {
                        prepared = withContext(Dispatchers.IO) {
                            runCatching {
                                val file = PortDownloads.pickWindows(PortDownloads.latest(port)) ?: return@runCatching str(R.string.ports_no_windows_download_in_the_latest)
                                val where = PortDownloads.unpackWindows(context, port, file) { p -> prepared = str(R.string.ports_downloading, file.name, p) }
                                str(R.string.ports_unpacked_into_in_gamenative_add_it, where)
                            }.getOrElse { str(R.string.ports_didn_t_download, it.message) }
                        }
                    }
                })
                val runner = WINDOWS_RUNNERS.firstNotNullOfOrNull { context.packageManager.getLaunchIntentForPackage(it) }
                FocusChip(if (runner != null) stringResource(R.string.ports_open_gamenative) else stringResource(R.string.ports_get_gamenative), onClick = {
                    if (runner != null) runCatching { context.startActivity(runner) } else open(context, GAMENATIVE_PAGE)
                })
            } else {
                if (launch != null) FocusChip(stringResource(R.string.home_layouts_open), onClick = { runCatching { context.startActivity(launch) } })
                // The Play build can't install apps itself; Get (Obtainium or the releases page) still works.
                if (!com.retroforge.app.Store.play) FocusChip(if (launch != null) stringResource(R.string.hacks_update) else stringResource(R.string.ports_download_and_install), onClick = {
                    prepared = str(R.string.ports_looking_for_the_latest_version)
                    scope.launch {
                        prepared = withContext(Dispatchers.IO) {
                            runCatching {
                                val file = PortDownloads.pickApk(PortDownloads.latest(port)) ?: return@runCatching str(R.string.ports_no_android_download_in_the_latest)
                                PortDownloads.installApk(context, file) { p -> prepared = str(R.string.ports_downloading, file.name, p) }
                                str(R.string.ports_downloaded_confirm_the_install_in_android, file.name)
                            }.getOrElse { str(R.string.ports_didn_t_download, it.message) }
                        }
                    }
                })
                FocusChip(stringResource(R.string.ports_get_with_obtainium), onClick = { get(context, port) })
            }
            if (owned != null) FocusChip(stringResource(R.string.ports_prepare_files), onClick = {
                prepared = str(R.string.ports_copying)
                scope.launch {
                    prepared = withContext(Dispatchers.IO) {
                        runCatching { prepare(context, library, port, owned) }.getOrElse { str(R.string.ports_couldn_t_copy_the_game, it.message) }
                    }
                }
            })
            FocusChip(stringResource(R.string.ports_project_page), onClick = { open(context, port.repo) })
        }
        prepared?.let { CardBody(it) }
    }
}

/** Obtainium when installed (it installs from the project's releases and keeps it updated), otherwise the releases page. */
private fun get(context: Context, port: GamePort) {
    val hasObtainium = OBTAINIUM.any { runCatching { context.packageManager.getPackageInfo(it, 0) }.isSuccess }
    if (hasObtainium && runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, "obtainium://add/${port.repo}".toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
    ) return
    open(context, port.repo + "/releases/latest")
}

private fun open(context: Context, url: String) {
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}

/**
 * Copies the user's game into Download/RetroForge/Ports/<port>, unzipped, so the port's own file picker finds it
 * easily. The original in the game folder is untouched (both stay playable). Returns what happened, for the card.
 */
private suspend fun prepare(context: Context, library: LibraryRepository, port: GamePort, game: GameEntity): String {
    val root = library.roots.first().firstOrNull { it.id == game.rootId } ?: error(str(R.string.ports_its_game_folder_isn_t_available))
    val source = library.documentUri(root, game.documentId)
    val resolver = context.contentResolver
    val folder = "${android.os.Environment.DIRECTORY_DOWNLOADS}/RetroForge/Ports/${port.name.replace(":", " -").replace(Regex("[\\/*?\"<>|]"), "_")}"
    fun save(name: String, write: (java.io.OutputStream) -> Unit) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) error(str(R.string.port_downloads_needs_android_10_or_newer))
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Downloads.DISPLAY_NAME, name)
            put(android.provider.MediaStore.Downloads.RELATIVE_PATH, folder)
        }
        val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error(str(R.string.ports_android_refused_to_create_the_file))
        resolver.openOutputStream(uri)?.use(write) ?: error(str(R.string.ports_couldn_t_write_the_file))
    }
    var name = game.fileName
    if (game.fileName.endsWith(".zip", ignoreCase = true)) {
        // Ports want the bare ROM: the first file in the zip.
        resolver.openInputStream(source)?.use { input ->
            java.util.zip.ZipInputStream(input.buffered()).use { zip ->
                val entry = generateSequence { zip.nextEntry }.firstOrNull { !it.isDirectory } ?: error(str(R.string.ports_the_zip_is_empty))
                name = entry.name.substringAfterLast('/')
                save(name) { zip.copyTo(it) }
            }
        } ?: error(str(R.string.game_files_couldn_t_open_the_game))
    } else {
        resolver.openInputStream(source)?.use { input -> save(name) { input.copyTo(it) } } ?: error(str(R.string.game_files_couldn_t_open_the_game))
    }
    return str(R.string.ports_copied_to_download_retroforge_ports_your, name)
}
