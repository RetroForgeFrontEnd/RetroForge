package com.retroforge.app.ui.settings

import com.retroforge.core.ui.str
import com.retroforge.app.R

import androidx.compose.ui.res.stringResource

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.retroforge.app.ui.CardBody
import com.retroforge.app.ui.CardTitle
import com.retroforge.core.ui.FocusCard
import com.retroforge.core.ui.FocusChip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * High-resolution texture packs for the built-in player, one folder per game, where each emulator looks:
 * N64 (Mupen64Plus-Next) in system/Mupen64plus/hires_texture, named after the game's internal name ("SUPER MARIO 64");
 * PSP (PPSSPP) in the memory stick's TEXTURES folder (the PSP saves folder), named after the game's ID ("ULUS10041").
 */
/** All texture packs on one page: N64 downloads first, then adding your own N64 and PSP packs. */
@Composable
fun TexturePacksScreen() {
    val context = LocalContext.current
    com.retroforge.app.ui.ScreenFrame(title = stringResource(R.string.settings_texture_packs), subtitle = stringResource(R.string.texture_packs_high_resolution_textures_for_games_in)) {
        TexturePacksCard(context.getExternalFilesDir(null) ?: context.filesDir)
    }
}

@Composable
fun TexturePacksCard(playerDir: File) {
    DownloadPacks(File(playerDir, "system/Mupen64plus/cache"))
    TexturePacks(
        str(R.string.texture_packs_texture_packs_n64),
        str(R.string.texture_packs_high_resolution_texture_packs_for_n64),
        File(playerDir, "system/Mupen64plus/hires_texture"),
    )
    TexturePacks(
        str(R.string.texture_packs_texture_packs_psp),
        str(R.string.texture_packs_high_resolution_texture_packs_for_psp),
        File(playerDir, "saves/psp/TEXTURES"),
    )
}

/** Where the built-in player's emulator for a system looks for HD texture packs, one folder per game. */
object TextureFolders {
    fun of(playerDir: File, system: String): File? = when (system) {
        "n64" -> File(playerDir, "system/Mupen64plus/hires_texture")
        "psp" -> File(playerDir, "saves/psp/TEXTURES")
        // Flycast: system/dc/textures/<game ID>; Dolphin: its user folder's Load/Textures/<game ID>.
        "dreamcast" -> File(playerDir, "system/dc/textures")
        "gamecube", "gc", "wii" -> File(playerDir, "system/dolphin-emu/User/Load/Textures")
        else -> null
    }
}

/** The game page's HD textures: a download when RetroForge knows a free pack for it (N64), and adding your own pack. */
@Composable
fun GameTexturePacks(system: String, title: String) {
    val context = LocalContext.current
    val playerDir = context.getExternalFilesDir(null) ?: context.filesDir
    val dir = TextureFolders.of(playerDir, system) ?: return
    if (system == "n64") DownloadPacks(File(playerDir, "system/Mupen64plus/cache"), only = title)
    TexturePacks(stringResource(R.string.game_textures_title), stringResource(R.string.game_textures_body), dir)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TexturePacks(title: String, body: String, packs: File) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var changes by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf<String?>(null) }
    val installed = remember(changes) { packs.listFiles()?.filter { it.isDirectory }?.map { it.name }?.sorted().orEmpty() }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        tree ?: return@rememberLauncherForActivityResult
        status = str(R.string.texture_packs_copying_the_texture_pack)
        scope.launch {
            status = withContext(Dispatchers.IO) {
                runCatching {
                    val name = copyTree(context, tree, packs) { n -> status = str(R.string.texture_packs_copying_the_texture_pack_files, n) }
                    str(R.string.texture_packs_added_the_game_uses_it_next, name)
                }.getOrElse { str(R.string.texture_packs_couldn_t_copy_it, it.message) }
            }
            changes++
        }
    }
    FocusCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle(title)
        CardBody(body)
        CardBody(if (installed.isEmpty()) stringResource(R.string.texture_packs_no_packs_installed) else "Installed: ${installed.joinToString()}")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FocusChip(stringResource(R.string.texture_packs_add_a_texture_pack_folder), onClick = { pick.launch(null) })
            installed.forEach { name ->
                FocusChip(stringResource(R.string.network_drives_remove, name), onClick = {
                    scope.launch { withContext(Dispatchers.IO) { File(packs, name).deleteRecursively() }; changes++ }
                })
            }
        }
        status?.let { CardBody(it) }
    }
}

/** One-tap downloads of free N64 packs (GhostlyDark's "Reloaded" packs on GitHub). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DownloadPacks(cacheDir: File, only: String? = null) {
    // On a game's page, only that game's pack (and nothing at all when there's none for it).
    fun key(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
    val packs = if (only == null) TexturePackDownloads.packs else TexturePackDownloads.packs.filter { key(only).contains(key(it.game)) }
    if (packs.isEmpty()) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var changes by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val installed = remember(changes) { cacheDir.listFiles()?.filter { it.name.endsWith(".hts", true) }?.sortedBy { it.name }.orEmpty() }
    FocusCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle(stringResource(R.string.texture_packs_download_n64_texture_packs))
        CardBody(
            stringResource(R.string.texture_packs_free_hd_packs_by_ghostlydark_downloaded),
        )
        CardBody(if (installed.isEmpty()) str(R.string.texture_packs_none_downloaded_yet) else "Downloaded: " + installed.joinToString { str(R.string.texture_packs_mb, it.name.removeSuffix(".hts").removeSuffix("_HIRESTEXTURES"), it.length() / 1_000_000) })
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            packs.forEach { pack ->
                FocusChip(stringResource(R.string.pc_games_download, pack.game), enabled = !busy, onClick = {
                    busy = true
                    status = str(R.string.texture_packs_finding_s_latest_release, pack.game)
                    scope.launch {
                        status = withContext(Dispatchers.IO) {
                            runCatching {
                                val names = TexturePackDownloads.install(context, pack, cacheDir) { status = it }
                                str(R.string.texture_packs_added_it_s_used_next_time, pack.game, names.joinToString())
                            }.getOrElse { str(R.string.free_games_couldn_t_get, pack.game, it.message) }
                        }
                        busy = false
                        changes++
                    }
                })
            }
            installed.forEach { file ->
                FocusChip(stringResource(R.string.network_drives_remove, file.name.removeSuffix(".hts").removeSuffix("_HIRESTEXTURES")), enabled = !busy, onClick = {
                    scope.launch { withContext(Dispatchers.IO) { file.delete() }; changes++ }
                })
            }
        }
        status?.let { CardBody(it) }
    }
}

/** Copies the picked folder (with everything in it) into [into]; returns the folder's name. */
private fun copyTree(context: Context, tree: Uri, into: File, onProgress: (Int) -> Unit): String {
    val resolver = context.contentResolver
    val rootId = DocumentsContract.getTreeDocumentId(tree)
    var copied = 0
    fun nameOf(id: String) = resolver.query(DocumentsContract.buildDocumentUriUsingTree(tree, id),
        arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "pack"
    fun walk(id: String, target: File, depth: Int) {
        if (depth > 6) return
        target.mkdirs()
        resolver.query(DocumentsContract.buildChildDocumentsUriUsingTree(tree, id),
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE),
            null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val child = c.getString(0)
                val name = c.getString(1)?.replace('/', '_') ?: continue
                if (c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) {
                    walk(child, File(target, name), depth + 1)
                } else {
                    resolver.openInputStream(DocumentsContract.buildDocumentUriUsingTree(tree, child))?.use { input ->
                        File(target, name).outputStream().use { input.copyTo(it) }
                    }
                    if (++copied % 50 == 0) onProgress(copied)
                }
            }
        }
    }
    val name = nameOf(rootId).replace('/', '_')
    walk(rootId, File(into, name), 0)
    return name
}
