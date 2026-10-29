package com.retroforge.app.ui.settings

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
        CardTitle("Built-in player")
        CardBody(
            "RetroForge plays most systems itself, from Game Boy to PlayStation, N64 and PSP; their emulators download in the " +
                "background. Swipe in from an edge or pause for the game's menu: save states, screen shape, filters, remap buttons, " +
                "cheats, widescreen, Cardboard VR and switching emulator.",
        )
    }
    FocusCard(modifier = Modifier.fillMaxWidth(), onClick = { onResume(!resume) }) {
        CardTitle("Carry on where you left off")
        StatusPill(if (resume) "On" else "Off", if (resume) StatusTone.GOOD else StatusTone.NEUTRAL)
        CardBody("Games save their place when you quit and start from there next time, in the built-in player and in RetroArch.")
    }
    SaveBackupCard(library)
    val switchable = systems.filter { (id, _) -> PlayerCores.choices(id).size > 1 }
    if (switchable.isEmpty()) return
    CardBody("Emulator per system (select to switch):")
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
        status = "Working…"
        status = withContext(Dispatchers.IO) { runCatching(work).getOrElse { "That didn't work: ${it.message}" } }
    }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        run {
            val lists = listsDir(context).apply { deleteRecursively(); mkdirs() }
            library?.let { File(lists, "lists.json").writeText(kotlinx.coroutines.runBlocking { it.exportLists() }) }
            val count = context.contentResolver.openOutputStream(uri)?.use { saveBackup(context).export(it) } ?: error("couldn't open the file")
            lists.deleteRecursively()
            "Backed up $count files."
        }
    }
    val import = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        run {
            val lists = listsDir(context).apply { deleteRecursively() }
            val count = context.contentResolver.openInputStream(uri)?.use { saveBackup(context).restore(it) } ?: error("couldn't open the file")
            val listsFile = File(lists, "lists.json")
            val listsNote = if (library != null && listsFile.exists()) {
                val (matched, missed) = kotlinx.coroutines.runBlocking { library.importLists(listsFile.readText()) }
                " Favourites and playlists: $matched games found" + (if (missed > 0) ", $missed not in your library (add their folder, then restore again)." else ".")
            } else ""
            lists.deleteRecursively()
            if (count == 0) "No RetroForge saves found in that file." else {
                restartNeeded = true
                "Restored $count files.$listsNote RetroForge restarts to load the settings."
            }
        }
    }
    FocusCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle("Back up saves and settings")
        CardBody(
            "One zip with the built-in player's in-game saves, save states and cheats, and RetroForge's settings (screen shapes, " +
                "filters, remapped buttons, emulator choices), favourites and playlists. Keep it somewhere safe, or restore it on a " +
                "new phone (add your game folders first, so favourites and playlists find their games). Restoring replaces files " +
                "with the same name and keeps the rest.",
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FocusChip("Back up to a file", onClick = { export.launch("RetroForge-backup-${LocalDate.now()}.zip") })
            FocusChip("Restore from a backup", onClick = { import.launch(arrayOf("application/zip", "application/octet-stream")) })
        }
        status?.let { CardBody(it) }
        if (restartNeeded) FocusChip("Restart RetroForge now", onClick = { restart(context) })
    }
    StorageCard()
}

private fun File.size(): Long = walkTopDown().filter { it.isFile }.sumOf { it.length() }

private fun Long.mb(): String = if (this < 1_000_000) "under 1 MB" else "%.0f MB".format(this / 1_000_000.0)

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
        CardTitle("Storage")
        CardBody(
            sizes?.let { (cores, games) -> "Emulators: ${cores.mb()}. Copied games: ${games.mb()} (CD and arcade games, the last two played)." }
                ?: "Working out sizes…",
        )
        FocusChip("Clear copied games", onClick = {
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
