package com.retroforge.app.ui.pc

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.SubcomposeAsyncImage
import com.retroforge.app.ui.CardBody
import com.retroforge.app.ui.ChipRow
import com.retroforge.core.ui.ControllerTextField
import com.retroforge.core.ui.FocusCard
import com.retroforge.core.ui.FocusChip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Your PC games, in RetroForge's library: Steam, Epic, GOG and Amazon games started in GameNative, and Windows games
 * started from Winlator Cmod or WinNative shortcuts. Added by Steam search or from a folder of exported files.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PcGamesSection(library: com.retroforge.core.library.LibraryRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pc = remember { PcLibrary(context) }
    val roots by library.roots.collectAsStateWithLifecycle(initialValue = null)
    var changes by remember { mutableIntStateOf(0) }
    val games = remember(changes) { pc.all() }
    var editing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var searching by remember { mutableStateOf(false) }
    var term by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<SteamHit>?>(null) }
    var busy by remember { mutableStateOf(false) }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            status = withContext(Dispatchers.IO) {
                runCatching {
                    val (found, unusable) = pc.scan(uri)
                    val added = pc.add(found)
                    buildString {
                        append(if (found.isEmpty()) "No PC game files in that folder." else "Added $added of ${found.size} games found.")
                        if (unusable > 0) append(" $unusable Winlator shortcuts were skipped: they need to be on the phone's storage or SD card.")
                    }
                }.getOrElse { "Couldn't read that folder: ${it.message}" }
            }
            busy = false
            changes++
        }
    }

    // PC games in the game folders ("windows", "steam" … beside the other systems) are picked up by themselves.
    fun scanGameFolders(say: Boolean) {
        val trees = roots.orEmpty().mapNotNull { runCatching { Uri.parse(it.treeUri) }.getOrNull() }
        if (trees.isEmpty()) { if (say) status = "Add your game folders first (Retro tab → Game folders)."; return }
        busy = true
        scope.launch {
            val note = withContext(Dispatchers.IO) {
                runCatching {
                    val (found, unusable) = pc.scanGameFolders(trees)
                    val added = pc.add(found)
                    when {
                        found.isEmpty() -> "No PC games in your game folders yet: put .steam, .epic, .gog, .pcgame or Winlator .desktop files in a \"windows\" or \"steam\" folder beside your other systems."
                        added == 0 -> "Your game folders' PC games are all here."
                        else -> "Found ${added} new PC games in your game folders."
                    } + if (unusable > 0) " $unusable Winlator shortcuts on a network drive were skipped: Winlator needs them on this device." else ""
                }.getOrElse { "Couldn't look in your game folders: ${it.message}" }
            }
            if (say || note.startsWith("Found")) status = note
            busy = false
            changes++
        }
    }
    androidx.compose.runtime.LaunchedEffect(roots) { if (roots != null) scanGameFolders(say = false) }

    FocusCard(modifier = Modifier.fillMaxWidth()) {
        CardBody(
            "Install games in GameNative (Steam, Epic, GOG, Amazon) or export their shortcuts from Winlator Cmod or WinNative. " +
                "Shortcut files (.steam, .epic, .gog, .amazon, .pcgame, .desktop) in a \"windows\" or \"steam\" folder " +
                "beside snes, psx and the rest are found by themselves.",
        )
        ChipRow {
            FocusChip("Add a Steam game", onClick = { searching = !searching; hits = null })
            FocusChip("Look in my game folders", enabled = !busy, onClick = { scanGameFolders(say = true) })
            FocusChip("Add from another folder", enabled = !busy, onClick = { pickFolder.launch(null) })
            if (games.isNotEmpty()) FocusChip(if (editing) "Done" else "Remove games", onClick = { editing = !editing })
        }
        if (searching) {
            ControllerTextField(
                value = term, onValueChange = { term = it }, singleLine = true,
                label = { Text("Game name on Steam") }, modifier = Modifier.fillMaxWidth(),
            )
            FocusChip("Search Steam", enabled = term.isNotBlank() && !busy, onClick = {
                busy = true
                scope.launch {
                    val result = withContext(Dispatchers.IO) { runCatching { PcLibrary.searchSteam(term.trim()) } }
                    hits = result.getOrNull()
                    status = result.exceptionOrNull()?.let { "Couldn't search Steam: ${it.message}" }
                        ?: if (hits.isNullOrEmpty()) "Steam has nothing called that." else null
                    busy = false
                }
            })
            hits?.takeIf { it.isNotEmpty() }?.let { list ->
                CardBody("Choose your game:")
                ChipRow {
                    list.take(8).forEach { hit ->
                        FocusChip(hit.name, onClick = {
                            pc.add(listOf(PcGame(hit.name, PcSource.STEAM, appId = hit.appId)))
                            status = "Added ${hit.name}. Install it in GameNative to play it from here."
                            searching = false
                            hits = null
                            term = ""
                            changes++
                        })
                    }
                }
            }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        status?.let { CardBody(it) }
    }
    if (games.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            games.forEach { game ->
                PcGameTile(game, pc, editing, onClick = {
                    if (editing) { pc.remove(game); changes++ } else status = pc.launch(game)
                })
            }
        }
    }
}

@Composable
private fun PcGameTile(game: PcGame, pc: PcLibrary, editing: Boolean, onClick: () -> Unit) {
    val cover by androidx.compose.runtime.produceState<java.io.File?>(null, game.key) { value = withContext(Dispatchers.IO) { pc.cover(game) } }
    FocusCard(modifier = Modifier.width(150.dp), onClick = onClick) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(MaterialTheme.shapes.small)) {
            val placeholder: @Composable () -> Unit = {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp), contentAlignment = Alignment.Center) {
                    Text(game.title, textAlign = TextAlign.Center, maxLines = 4, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (cover == null) placeholder()
            else SubcomposeAsyncImage(
                model = cover, contentDescription = null, contentScale = ContentScale.Crop,
                loading = { placeholder() }, error = { placeholder() }, modifier = Modifier.fillMaxSize(),
            )
        }
        Text(game.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 6.dp))
        Text(if (editing) "Select to remove" else game.source.label, style = MaterialTheme.typography.bodySmall,
            color = if (editing) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary)
    }
}
