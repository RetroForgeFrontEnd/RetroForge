package com.retroforge.app.ui.settings

import com.retroforge.core.ui.str
import com.retroforge.app.R

import androidx.compose.ui.res.stringResource

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.retroforge.app.player.SaveBackup
import com.retroforge.core.library.LibraryRepository
import com.retroforge.app.ui.CardBody
import com.retroforge.app.ui.CardTitle
import com.retroforge.core.ui.FocusCard
import com.retroforge.core.ui.FocusChip
import com.retroforge.core.ui.StatusPill
import com.retroforge.core.ui.StatusTone
import com.retroforge.player.CoreChoices
import com.retroforge.player.PlayerCores
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate

/**
 * The built-in player's settings: which emulator each of the library's systems uses (also switchable mid-game
 * from the pause menu) and resuming where you left off.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BuiltInPlayerCard(systems: List<Pair<String, String>>, choices: CoreChoices, resume: Boolean, onResume: (Boolean) -> Unit, library: LibraryRepository? = null) {
    var changes by remember { mutableIntStateOf(0) }
    FocusCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle(stringResource(R.string.built_in_player_built_in_player))
        CardBody(
            stringResource(R.string.built_in_player_retroforge_has_its_own_emulators_for),
        )
    }
    FocusCard(modifier = Modifier.fillMaxWidth(), onClick = { onResume(!resume) }) {
        CardTitle(stringResource(R.string.built_in_player_carry_on_where_you_left_off)) { StatusPill(if (resume) stringResource(R.string.settings_on) else stringResource(R.string.player_off), if (resume) StatusTone.GOOD else StatusTone.NEUTRAL) }
        CardBody(stringResource(R.string.built_in_player_when_you_quit_a_game_your))
    }
    CopyFirstCard()
    SaveBackupCard(library)
    MemoryCardsCard()
    VideoChoicesCard(systems, choices)
    val switchable = systems.filter { (id, _) -> PlayerCores.choices(id).size > 1 }
    if (switchable.isEmpty()) return
    CardBody(stringResource(R.string.built_in_player_the_emulator_each_system_uses_tap))
    // Read again after each change, so the chips show the new choice.
    val chosen = remember(changes) { choices.all() }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        switchable.forEach { (id, name) ->
            val list = PlayerCores.choices(id)
            val current = list.firstOrNull { it.id == chosen[id] } ?: list.first()
            FocusChip("$name: ${current.name}", onClick = {
                val next = list[(list.indexOf(current) + 1) % list.size]
                choices.set(id, if (next == list.first()) null else next.id)
                changes++
            })
        }
    }
}

/**
 * Vulkan or OpenGL, and the internal resolution, for each system whose emulator has them (also in the pause menu).
 * Kept in the player's own file, so both always show the same choice.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VideoChoicesCard(systems: List<Pair<String, String>>, choices: CoreChoices) {
    val context = LocalContext.current
    val video = remember { CoreChoices(File(context.filesDir, "player/video.properties")) }
    var changes by remember { mutableIntStateOf(0) }
    val cores = remember { choices.all() }
    // The emulator each system uses: the chosen one, else the first.
    val rows = systems.mapNotNull { (id, name) ->
        val list = PlayerCores.choices(id)
        val core = list.firstOrNull { it.id == cores[id] } ?: list.firstOrNull() ?: return@mapNotNull null
        if (core.id !in PlayerCores.vulkanCores && PlayerCores.resolutionOption(core.id) == null) null else Triple(id, name, core.id)
    }
    if (rows.isEmpty()) return
    val saved = remember(changes) { video.all() }
    FocusCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle(stringResource(R.string.built_in_player_graphics_and_resolution))
        CardBody(
            stringResource(R.string.built_in_player_resolution_makes_3d_games_sharper_than),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            rows.forEach { (id, name, coreId) ->
                if (coreId in PlayerCores.vulkanCores) {
                    val vulkan = PlayerCores.usesVulkan(coreId, saved["graphics.$id"])
                    FocusChip("$name: ${PlayerCores.graphicsLabel(coreId, vulkan)}", onClick = {
                        video.set("graphics.$id", if (vulkan) "opengl" else "vulkan")
                        changes++
                    })
                }
                PlayerCores.resolutionOption(coreId)?.let { (_, max) ->
                    val scale = saved["scale.$id"]?.toIntOrNull() ?: 1
                    FocusChip("$name: ${scale}×", onClick = {
                        val next = if (scale >= max) 1 else scale + 1
                        video.set("scale.$id", if (next == 1) null else next.toString())
                        changes++
                    })
                }
            }
        }
    }
}

/** Streaming big games (discs, arcade sets) from their folder or network drive, or copying them to the phone first. */
@Composable
private fun CopyFirstCard() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("views", Context.MODE_PRIVATE) }
    var copyFirst by remember { mutableStateOf(prefs.getBoolean(com.retroforge.app.player.PlayerActivity.COPY_FIRST_PREF, false)) }
    FocusCard(modifier = Modifier.fillMaxWidth(), onClick = {
        copyFirst = !copyFirst
        prefs.edit().putBoolean(com.retroforge.app.player.PlayerActivity.COPY_FIRST_PREF, copyFirst).apply()
    }) {
        CardTitle(stringResource(R.string.built_in_player_disc_games_play_straight_away_or)) { StatusPill(if (copyFirst) stringResource(R.string.built_in_player_copy_first) else stringResource(R.string.built_in_player_play_straight_away), if (copyFirst) StatusTone.NEUTRAL else StatusTone.GOOD) }
        CardBody(
            stringResource(R.string.built_in_player_big_disc_games_playstation_dreamcast_saturn),
        )
    }
}

/**
 * The player's saves, states and cheats, plus RetroForge's settings: app settings, view and player choices (screen
 * shape, filters, remapped buttons …) and which emulator each system uses, and favourites and playlists ("lists", written
 * by LibraryRepository.exportLists just before). Game folders aren't included (folder access can't move to another device).
 */
private fun saveBackup(context: Context): SaveBackup {
    val base = context.getExternalFilesDir(null) ?: context.filesDir
    return SaveBackup(
        mapOf(
            "saves" to File(base, "saves"),
            "states" to File(base, "states"),
            "cheats" to File(context.filesDir, "cheats"),
            "settings" to File(context.filesDir, "datastore"),
            "choices" to File(context.dataDir, "shared_prefs"),
            "player" to File(context.filesDir, "player"),
            "lists" to listsDir(context),
        ),
    )
}

/** Where favourites and playlists are written for a backup, and read back from one. */
private fun listsDir(context: Context) = File(context.cacheDir, "backup-lists")

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SaveBackupCard(library: LibraryRepository?) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var restartNeeded by remember { mutableStateOf(false) }
    fun run(work: () -> String) = scope.launch {
        status = str(R.string.built_in_player_working)
        status = withContext(Dispatchers.IO) { runCatching(work).getOrElse { str(R.string.built_in_player_that_didn_t_work, it.message) } }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        run {
            val lists = listsDir(context).apply { deleteRecursively(); mkdirs() }
            library?.let { File(lists, "lists.json").writeText(kotlinx.coroutines.runBlocking { it.exportLists() }) }
            val count = context.contentResolver.openOutputStream(uri)?.use { saveBackup(context).export(it) } ?: error(str(R.string.built_in_player_couldn_t_open_the_file))
            lists.deleteRecursively()
            str(R.string.built_in_player_backed_up_files, count)
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        run {
            val lists = listsDir(context).apply { deleteRecursively() }
            val count = context.contentResolver.openInputStream(uri)?.use { saveBackup(context).restore(it) } ?: error(str(R.string.built_in_player_couldn_t_open_the_file))
            val listsFile = File(lists, "lists.json")
            val listsNote = if (library != null && listsFile.exists()) {
                val (matched, missed) = kotlinx.coroutines.runBlocking { library.importLists(listsFile.readText()) }
                str(R.string.built_in_player_favourites_and_playlists_games_found, matched) + (if (missed > 0) str(R.string.built_in_player_not_in_your_library_add_their, missed) else ".")
            } else ""
            lists.deleteRecursively()
            if (count == 0) str(R.string.built_in_player_no_retroforge_saves_found_in_that) else {
                restartNeeded = true
                str(R.string.built_in_player_restored_files_retroforge_restarts_to_load, count, listsNote)
            }
        }
    }
    FocusCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle(stringResource(R.string.built_in_player_back_up_saves_and_settings))
        CardBody(
            stringResource(R.string.built_in_player_puts_your_game_saves_save_states),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FocusChip(stringResource(R.string.built_in_player_back_up_to_a_file), onClick = { export.launch("RetroForge-backup-${LocalDate.now()}.zip") })
            FocusChip(stringResource(R.string.built_in_player_restore_from_a_backup), onClick = { import.launch(arrayOf("application/zip", "application/octet-stream")) })
        }
        status?.let { CardBody(it) }
        if (restartNeeded) FocusChip(stringResource(R.string.built_in_player_restart_retroforge_now), onClick = { restart(context) })
    }
    StorageCard()
}

private fun File.size(): Long = walkTopDown().filter { it.isFile }.sumOf { it.length() }

private fun Long.mb(): String = if (this < 1_000_000) str(R.string.built_in_player_under_1_mb) else "%.0f MB".format(this / 1_000_000.0)

/** Space the player uses: its emulators, and games it copied because their emulator needs a real file (CD systems, arcade). */
@Composable
private fun StorageCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val copies = remember { File(context.externalCacheDir ?: context.cacheDir, "games") }
    var changes by remember { mutableIntStateOf(0) }
    var sizes by remember { mutableStateOf<Pair<Long, Long>?>(null) }
    androidx.compose.runtime.LaunchedEffect(changes) {
        sizes = withContext(Dispatchers.IO) { File(context.filesDir, "cores").size() to copies.size() }
    }
    FocusCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle(stringResource(R.string.built_in_player_storage))
        CardBody(
            sizes?.let { (cores, games) -> str(R.string.built_in_player_emulators_copied_games_the_last_two, cores.mb(), games.mb()) }
                ?: str(R.string.built_in_player_working_out_sizes),
        )
        FocusChip(stringResource(R.string.built_in_player_clear_copied_games), onClick = {
            scope.launch {
                withContext(Dispatchers.IO) { copies.deleteRecursively() }
                changes++
            }
        })
    }
}

/** Starts RetroForge afresh, so restored settings are read from their files. */
private fun restart(context: Context) {
    val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return
    context.startActivity(android.content.Intent.makeRestartActivityTask(launch.component))
    Runtime.getRuntime().exit(0)
}
