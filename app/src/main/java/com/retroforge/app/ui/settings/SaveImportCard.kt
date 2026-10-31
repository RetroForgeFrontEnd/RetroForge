package com.retroforge.app.ui.settings

import com.retroforge.app.R
import com.retroforge.core.ui.str
import androidx.compose.ui.res.stringResource
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.retroforge.app.player.SaveImport
import com.retroforge.app.ui.CardBody
import com.retroforge.app.ui.CardTitle
import com.retroforge.core.library.GameEntity
import com.retroforge.core.library.LibraryRepository
import com.retroforge.core.ui.ControllerTextField
import com.retroforge.core.ui.FocusCard
import com.retroforge.core.ui.FocusChip
import com.retroforge.core.ui.StatusPill
import com.retroforge.core.ui.StatusTone
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** A save file being brought in: its name and contents, what it is, and the library's games it could belong to. */
private class Incoming(val name: String, val bytes: ByteArray, val detected: SaveImport.Detected, val games: List<GameEntity>)

/**
 * Settings → Playing: saves from other emulators and devices, matched to a game in the library by name (the save
 * titles on a PlayStation card), converted and put where the built-in player reads them. The game's old save is kept
 * beside it as `<game>.srm.before-import`.
 */
@Composable
fun SaveImportCard(library: LibraryRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var incoming by remember { mutableStateOf<Incoming?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    var swap by remember { mutableStateOf(false) }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        scope.launch {
            incoming = null
            query = ""
            swap = false
            status = withContext(Dispatchers.IO) {
                runCatching {
                    val name = context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { if (it.moveToFirst()) it.getString(0) else null } ?: uri.lastPathSegment.orEmpty().substringAfterLast('/')
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error(str(R.string.built_in_player_couldn_t_open_the_file))
                    val detected = SaveImport.detect(name, bytes) ?: return@runCatching str(R.string.save_import_unknown)
                    val have = library.systemCounts.first().map { it.system }.filter { it in detected.systems }
                    val names = detected.titles + name
                    val games = have.flatMap { library.gamesFor(it).first() }
                        .map { it to maxOf(SaveImport.score(it.title, names), SaveImport.score(it.fileName, names)) }
                        .sortedByDescending { it.second }
                        .map { it.first }
                    incoming = Incoming(name, bytes, detected, games)
                    null
                }.getOrElse { str(R.string.built_in_player_that_didn_t_work, it.message) }
            }
        }
    }

    fun import(game: GameEntity) {
        val now = incoming ?: return
        scope.launch {
            status = withContext(Dispatchers.IO) {
                runCatching {
                    val folder = File(context.getExternalFilesDir(null) ?: context.filesDir, "saves/${game.system}").apply { mkdirs() }
                    val target = File(folder, game.fileName.substringBeforeLast('.') + ".srm")
                    val existing = target.takeIf { it.isFile }?.readBytes()
                    val converted = runCatching { SaveImport.convert(now.detected, now.bytes, existing, swap) }.getOrElse { e ->
                        val full = e.message.orEmpty().split(':')
                        if (full.firstOrNull() == "full") return@runCatching str(R.string.save_import_full, game.title, full[1], full[2])
                        throw e
                    }
                    val kept = existing?.let { File(folder, target.name + ".before-import").also { it.writeBytes(existing) } }
                    target.writeBytes(converted)
                    incoming = null
                    if (kept != null) str(R.string.save_import_done_kept, game.title, kept.name) else str(R.string.save_import_done, game.title)
                }.getOrElse { str(R.string.built_in_player_that_didn_t_work, it.message) }
            }
        }
    }

    FocusCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle(stringResource(R.string.save_import_title))
        CardBody(stringResource(R.string.save_import_body))
    }
    FocusChip(stringResource(R.string.save_import_choose), onClick = { pick.launch(arrayOf("*/*")) })
    status?.let { CardBody(it) }
    val now = incoming ?: return
    val d = now.detected
    FocusCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle(now.name)
        CardBody(
            when (d.kind) {
                SaveImport.Kind.PS1_CARD -> str(R.string.save_import_ps1_card, d.titles.joinToString().ifEmpty { str(R.string.save_import_empty_card) })
                SaveImport.Kind.PS1_SINGLE -> str(R.string.save_import_ps1_single, d.titles.joinToString())
                SaveImport.Kind.N64_EEPROM -> str(R.string.save_import_n64_eeprom)
                SaveImport.Kind.N64_PAK -> str(R.string.save_import_n64_pak)
                SaveImport.Kind.N64_SRAM -> str(R.string.save_import_n64_sram)
                SaveImport.Kind.N64_FLASH -> str(R.string.save_import_n64_flash)
                SaveImport.Kind.N64_SRM -> str(R.string.save_import_n64_all)
                SaveImport.Kind.BATTERY -> str(R.string.save_import_battery, android.text.format.Formatter.formatShortFileSize(context, now.bytes.size.toLong()))
            },
        )
    }
    if (d.kind == SaveImport.Kind.N64_SRAM || d.kind == SaveImport.Kind.N64_FLASH) {
        FocusChip(stringResource(R.string.save_import_swap, if (swap) stringResource(R.string.save_import_swapped) else stringResource(R.string.save_import_as_is)), onClick = { swap = !swap })
    }
    if (now.games.isEmpty()) {
        CardBody(stringResource(R.string.save_import_no_games))
        return
    }
    CardBody(stringResource(R.string.save_import_pick_game))
    ControllerTextField(value = query, onValueChange = { query = it }, label = { Text(stringResource(R.string.save_import_find_game)) }, modifier = Modifier.fillMaxWidth())
    val wanted = query.trim()
    val shown = if (wanted.isEmpty()) now.games.take(5) else now.games.filter { it.title.contains(wanted, true) || it.fileName.contains(wanted, true) }.take(10)
    shown.forEachIndexed { i, game ->
        FocusCard(modifier = Modifier.fillMaxWidth(), onClick = { import(game) }) {
            Text(game.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            CardBody(listOf(library.systemName(game.system), game.fileName).joinToString(" · "))
            if (i == 0 && wanted.isEmpty()) StatusPill(stringResource(R.string.save_import_best), StatusTone.GOOD)
        }
    }
}
