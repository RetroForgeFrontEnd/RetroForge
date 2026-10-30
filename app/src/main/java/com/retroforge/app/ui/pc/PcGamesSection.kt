package com.retroforge.app.ui.pc

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
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
 * Your PC games, in RetroForge's library: Steam, Epic, GOG and Amazon games started in GameNative, Windows games
 * started from Winlator Cmod or WinNative shortcuts, and Windows game folders played in RetroForge's own PC player.
 * Added by Steam search, from a folder of exported files, or by copying a game's folder in.
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
    // The signed-in account's Steam library (shown on request; the last list shows at once, then Steam is asked again).
    val steamAccount = remember { com.retroforge.pcplayer.steam.SteamAccount(context) }
    val steamLibrary = remember {
        com.retroforge.pcplayer.steam.SteamLibrary(steamAccount, java.io.File(com.retroforge.pcplayer.PcRuntime(context).root, "steam_library.json"))
    }
    var showLibrary by remember { mutableStateOf(false) }
    var library by remember { mutableStateOf<List<com.retroforge.pcplayer.steam.SteamOwnedGame>?>(null) }
    var libraryBusy by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    // Choosing which .exe starts a Windows game: the mode, then the game whose .exe files are listed.
    var choosingExe by remember { mutableStateOf(false) }
    var exeFor by remember { mutableStateOf<PcGame?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var searching by remember { mutableStateOf(false) }
    var term by remember { mutableStateOf("") }
    var hits by remember { mutableStateOf<List<SteamHit>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var copying by remember { mutableStateOf<Int?>(null) }
    // A RetroForge-player game waiting for the user to agree to the runtime download.
    var needsRuntime by remember { mutableStateOf<PcGame?>(null) }

    fun start(game: PcGame) {
        if (game.source == PcSource.WINDOWS && com.retroforge.pcplayer.PcRuntime.deviceSupported &&
            com.retroforge.pcplayer.PcRuntime(context).needsBigDownload()) needsRuntime = game
        else if (game.source == PcSource.WINDOWS && game.appId.isNullOrBlank()) scope.launch {
            status = pc.launch(withContext(Dispatchers.IO) { pc.withSteamAppId(game) })
        }
        else status = pc.launch(game)
    }

    val pickWindowsGame = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        busy = true
        copying = 0
        scope.launch {
            status = withContext(Dispatchers.IO) {
                runCatching {
                    pc.addWindowsGame(uri) { copying = it }?.let { "Added ${it.title}. Select it to play it in RetroForge's PC player." }
                        ?: "No Windows game in that folder: pick the folder that holds the game's .exe."
                }.getOrElse { "Couldn't copy that folder: ${it.message}" }
            }
            copying = null
            busy = false
            changes++
        }
    }

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
                "beside snes, psx and the rest are found by themselves. Or add a Windows game's folder to play it in " +
                "RetroForge's own PC player (early test: the first game downloads the PC runtime, about 280 MB).",
        )
        ChipRow {
            FocusChip(if (showLibrary) "Hide my Steam library" else "My Steam library", onClick = {
                showLibrary = !showLibrary
                if (showLibrary && !libraryBusy) {
                    if (!steamAccount.signedIn) {
                        status = "Sign in to Steam first (Steam sign-in on this tab)."
                        showLibrary = false
                        return@FocusChip
                    }
                    library = steamLibrary.cached().ifEmpty { null }
                    libraryBusy = true
                    scope.launch {
                        val result = withContext(Dispatchers.IO) { runCatching { steamLibrary.refresh() } }
                        result.getOrNull()?.let { library = it }
                        result.exceptionOrNull()?.let { status = "Couldn't get your Steam library: ${it.message}" }
                        libraryBusy = false
                    }
                }
            })
            FocusChip("Add a Steam game", onClick = { searching = !searching; hits = null })
            FocusChip("Look in my game folders", enabled = !busy, onClick = { scanGameFolders(say = true) })
            FocusChip("Add from another folder", enabled = !busy, onClick = { pickFolder.launch(null) })
            FocusChip("Add a Windows game folder", enabled = !busy, onClick = { pickWindowsGame.launch(null) })
            if (games.isNotEmpty() && !choosingExe) FocusChip(if (editing) "Done" else "Remove games", onClick = { editing = !editing })
            if (games.any { it.source == PcSource.WINDOWS } && !editing) {
                FocusChip(if (choosingExe) "Done" else "Change a game's .exe", onClick = { choosingExe = !choosingExe })
            }
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
        copying?.let { CardBody("Copying the game… $it%") }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        status?.let { CardBody(it) }
    }
    if (showLibrary) {
        val onDevice = games.filter { it.source == PcSource.WINDOWS }.mapNotNull { g -> g.appId?.toIntOrNull()?.let { it to g } }.toMap()
        val list = library.orEmpty().filter { it.windows }
        Text(
            when {
                library == null -> "Getting your Steam library…"
                libraryBusy -> "Your Steam library (${list.size} games, checking for changes…)"
                else -> "Your Steam library (${list.size} games)"
            },
            style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface,
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            list.forEach { owned ->
                val installed = onDevice[owned.appId]
                PcGameTile(
                    PcGame(owned.name, PcSource.STEAM, appId = owned.appId.toString()), pc,
                    if (installed != null) "On this device" else "Not downloaded",
                    onClick = {
                        if (installed != null) start(installed)
                        else status = "Downloading ${owned.name} from Steam is coming next. For now, add its folder from a PC."
                    },
                )
            }
        }
    }
    if (games.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            games.forEach { game ->
                val hint = when {
                    editing -> "Select to remove"
                    choosingExe && game.source == PcSource.WINDOWS -> "Select to choose its .exe"
                    else -> null
                }
                PcGameTile(game, pc, hint, onClick = {
                    when {
                        editing -> { pc.remove(game); changes++ }
                        choosingExe && game.source == PcSource.WINDOWS -> exeFor = game
                        else -> start(game)
                    }
                })
            }
        }
    }

    exeFor?.let { game ->
        val choices by androidx.compose.runtime.produceState<List<java.io.File>?>(null, game.key) {
            value = withContext(Dispatchers.IO) { pc.exeChoices(game) }
        }
        val folder = remember(game.key) { pc.windowsGameFolder(game) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { exeFor = null },
            title = { Text("Which .exe starts ${game.title}?") },
            text = {
                androidx.compose.foundation.layout.Column(
                    Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState()),
                ) {
                    when {
                        choices == null -> Text("Looking in the game's folder…")
                        choices!!.isEmpty() -> Text("There's no .exe in the game's folder.")
                        else -> choices!!.forEachIndexed { i, exe ->
                            val name = folder?.let { exe.relativeTo(it).path } ?: exe.name
                            val current = exe.path == game.shortcutPath
                            androidx.compose.material3.TextButton(onClick = {
                                pc.setExe(game, exe)
                                status = "${game.title} now starts $name."
                                exeFor = null
                                choosingExe = false
                                changes++
                            }) {
                                Text(
                                    (if (current) "✓ " else "") + name + "  ·  ${exe.length() / (1024 * 1024)} MB" +
                                        if (i == 0) "  (best guess)" else "",
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { exeFor = null }) { Text("Cancel") } },
        )
    }

    needsRuntime?.let { game ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { needsRuntime = null },
            title = { Text("Download the PC player?") },
            text = {
                Text(
                    "RetroForge's PC player needs its Windows runtime first: Wine (Proton 11), FEX and a Linux image built " +
                        "for Android. That's about 280 MB to download and about 1.8 GB on this device. It downloads once, " +
                        "with a percentage, the first time ${game.title} starts.",
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    needsRuntime = null
                    scope.launch { status = pc.launch(withContext(Dispatchers.IO) { pc.withSteamAppId(game) }) }
                }) { Text("Download and play") }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { needsRuntime = null }) { Text("Not now") } },
        )
    }
}

@Composable
private fun PcGameTile(game: PcGame, pc: PcLibrary, hint: String?, onClick: () -> Unit) {
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
        Text(hint ?: game.source.label, style = MaterialTheme.typography.bodySmall,
            color = if (hint != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary)
    }
}
