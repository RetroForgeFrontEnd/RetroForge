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
import androidx.compose.runtime.collectAsState
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
    // Coming back to the tab (from a game, say) reloads the games and their history.
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { changes++ }
    // How each Windows game has gone on this device (the PC player keeps it), for the tiles' labels.
    val history = remember(changes) { com.retroforge.pcplayer.PcHistory.all(context) }
    // The signed-in account's Steam library (shown on request; the last list shows at once, then Steam is asked again).
    val steamAccount = remember { com.retroforge.pcplayer.steam.SteamAccount(context) }
    val steamLibrary = remember {
        com.retroforge.pcplayer.steam.SteamLibrary(steamAccount, java.io.File(com.retroforge.pcplayer.PcRuntime(context).root, "steam_library.json"))
    }
    var showLibrary by remember { mutableStateOf(false) }
    var library by remember { mutableStateOf<List<com.retroforge.pcplayer.steam.SteamOwnedGame>?>(null) }
    var libraryBusy by remember { mutableStateOf(false) }
    // Downloading a Steam game into RetroForge's PC games: the game asked about, its size, and the running download.
    var downloadFor by remember { mutableStateOf<com.retroforge.pcplayer.steam.SteamOwnedGame?>(null) }
    // The download runs in its own process; its status file is read every second while it runs (less often otherwise).
    var download by remember { mutableStateOf<com.retroforge.pcplayer.steam.SteamDownloadService.State?>(null) }
    val askNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        while (true) {
            val now = withContext(Dispatchers.IO) {
                com.retroforge.pcplayer.steam.SteamDownloadService.read(context)?.also { state ->
                    // A finished download joins the PC games, started by the .exe RetroForge picks (changeable as usual).
                    val known = pc.all().any { it.source == PcSource.WINDOWS && it.appId == state.appId.toString() }
                    if (state.finished && !known) {
                        com.retroforge.pcplayer.GameExe.best(java.io.File(state.folder))?.let { exe ->
                            pc.add(listOf(PcGame(state.name, PcSource.WINDOWS, appId = state.appId.toString(), shortcutPath = exe.path)))
                        }
                    }
                }
            }
            if (now?.finished == true && download?.finished != true) changes++
            download = now
            kotlinx.coroutines.delay(if (now?.running == true) 1_000 else 5_000)
        }
    }
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
        CoverGrid(list) { owned, tileModifier ->
                val installed = onDevice[owned.appId]
                val active = download?.takeIf { it.appId == owned.appId }
                val downloading = active?.running == true
                val hint = when {
                    active?.error != null -> "Download stopped"
                    downloading -> "Downloading"
                    else -> PcCompatibility.tested(owned.name, owned.appId.toString())?.rating?.label
                        ?: if (installed != null) "On this device" else "Get"
                }
                val badge = when {
                    installed != null -> "✓"
                    downloading -> "${(active!!.progress * 100).toInt()}%"
                    active?.error != null -> "!"
                    else -> "⬇"
                }
                PcGameTile(
                    PcGame(owned.name, PcSource.STEAM, appId = owned.appId.toString()), pc, hint, tileModifier, badge,
                    onClick = { if (installed != null) start(installed) else downloadFor = owned },
                )
        }
    }
    if (games.isNotEmpty()) {
        CoverGrid(games) { game, tileModifier ->
                val hint = when {
                    editing -> "Select to remove"
                    choosingExe && game.source == PcSource.WINDOWS -> "Select to choose its .exe"
                    game.source == PcSource.WINDOWS -> PcCompatibility.label(game.title, game.appId, history[game.shortcutPath])
                    else -> null
                }
                PcGameTile(game, pc, hint, tileModifier, onClick = {
                    when {
                        editing -> { pc.remove(game); changes++ }
                        choosingExe && game.source == PcSource.WINDOWS -> exeFor = game
                        else -> start(game)
                    }
                })
        }
    }

    downloadFor?.let { owned ->
        // On the phone or the SD card, whichever has the most room (a part-downloaded game carries on where it is).
        val folder = remember(owned.appId) {
            pc.windowsGamesDirs().map { java.io.File(it, owned.name.replace(Regex("""[\\/:*?"<>|™®]"""), "").trim()) }.firstOrNull { it.isDirectory }
                ?: java.io.File(pc.roomiestGamesDir(), owned.name.replace(Regex("""[\\/:*?"<>|™®]"""), "").trim()) }
        val size by androidx.compose.runtime.produceState<Long?>(null, owned.appId) {
            value = withContext(Dispatchers.IO) {
                runCatching { com.retroforge.pcplayer.steam.SteamDownloads(steamAccount).size(owned.appId) }.getOrNull() ?: -1L
            }
        }
        val free = remember(owned.appId) { folder.parentFile!!.apply { mkdirs() }.usableSpace }
        val where = if (pc.onSdCard(folder)) "the SD card" else "this device"
        val active = download?.takeIf { it.appId == owned.appId }
        // Notifications show a download's progress. Android is asked once, explained here first; a no is left alone.
        val tabPrefs = remember { context.getSharedPreferences("pc_tab", android.content.Context.MODE_PRIVATE) }
        val askNotificationsFirst = remember(owned.appId) {
            android.os.Build.VERSION.SDK_INT >= 33 && !tabPrefs.getBoolean("asked_notifications", false) &&
                context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        }
        fun gb(bytes: Long) = "%.1f GB".format(bytes / 1_000_000_000.0)
        // Room for the game plus a little to spare (saves, shader caches).
        val fits = size?.let { it in 1..(free - 1_000_000_000L) } == true
        // Downloads use Wi-Fi only, unless the player lets them use mobile data (remembered for next time).
        var useMobileData by remember { mutableStateOf(tabPrefs.getBoolean("download_mobile_data", false)) }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { downloadFor = null },
            title = { Text(owned.name) },
            text = {
              androidx.compose.foundation.layout.Column {
                Text(
                    when {
                        active?.running == true ->
                            "Downloading from Steam: ${(active.progress * 100).toInt()}% (${active.step}). It carries on in the background; the notification shows how it's going."
                        size == null -> "Asking Steam how big it is…"
                        size == -1L -> "Steam didn't say how big this game is. Try again in a moment."
                        !fits -> "It needs ${gb(size!!)} and $where has ${gb(free)} free. Free up some space first."
                        else -> "Download it from Steam to play in RetroForge's PC player: ${gb(size!!)}, to $where (${gb(free)} free). " +
                            "It carries on in the background, and a game you've part-downloaded carries on from where it stopped." +
                            (active?.error?.let { "\n\nLast try stopped: $it" } ?: "") +
                            if (askNotificationsFirst) "\n\nRetroForge will ask once to show the download's progress in your notifications (you can say no)." else ""
                    },
                )
                if (fits && active?.running != true) {
                    androidx.compose.foundation.layout.Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 12.dp),
                    ) {
                        androidx.compose.material3.Switch(checked = useMobileData, onCheckedChange = {
                            useMobileData = it
                            tabPrefs.edit().putBoolean("download_mobile_data", it).apply()
                        })
                        Text(
                            if (useMobileData) "Uses mobile data when there's no Wi-Fi (this download is ${gb(size!!)})"
                            else "Wi-Fi only: waits for Wi-Fi if there's none",
                            modifier = Modifier.padding(start = 12.dp),
                        )
                    }
                }
              }
            },
            confirmButton = {
                if (fits && active?.running != true) androidx.compose.material3.TextButton(onClick = {
                    if (askNotificationsFirst) {
                        tabPrefs.edit().putBoolean("asked_notifications", true).apply()
                        askNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                    com.retroforge.pcplayer.steam.SteamDownloadService.start(context, owned.appId, owned.name, folder, allowMobile = useMobileData)
                    downloadFor = null
                }) { Text("Download ${gb(size!!)}") }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { downloadFor = null }) { Text("Close") } },
        )
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

/**
 * Game covers in a grid, like a store's library: as many columns as fit (tiles about 120 dp wide, never fewer than
 * three), each the same width.
 */
@Composable
private fun <T> CoverGrid(items: List<T>, tile: @Composable (T, Modifier) -> Unit) {
    androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth()) {
        val gap = 10.dp
        val columns = maxOf(3, ((maxWidth + gap) / (120.dp + gap)).toInt())
        androidx.compose.foundation.layout.Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            items.chunked(columns).forEach { row ->
                androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(gap)) {
                    row.forEach { item -> tile(item, Modifier.weight(1f)) }
                    // Keep the last row's tiles the same width as the rest.
                    repeat(columns - row.size) { androidx.compose.foundation.layout.Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun PcGameTile(
    game: PcGame,
    pc: PcLibrary,
    hint: String?,
    modifier: Modifier = Modifier.width(150.dp),
    badge: String? = null,
    onClick: () -> Unit,
) {
    val cover by androidx.compose.runtime.produceState<java.io.File?>(null, game.key) { value = withContext(Dispatchers.IO) { pc.cover(game) } }
    // Older games only have Steam's wide banner: shown whole, centred, rather than cropped to a sliver.
    val wide = remember(cover) {
        cover?.let { f -> android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }.also { android.graphics.BitmapFactory.decodeFile(f.path, it) } }
            ?.let { it.outWidth > it.outHeight } == true
    }
    // A cover tile: the box art fills it, the name and a short status underneath, a small badge on the art.
    FocusCard(modifier = modifier, onClick = onClick, contentPadding = 6.dp) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(MaterialTheme.shapes.small)) {
            val placeholder: @Composable () -> Unit = {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant).padding(6.dp), contentAlignment = Alignment.Center) {
                    Text(game.title, textAlign = TextAlign.Center, maxLines = 4, overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (cover == null) placeholder()
            else SubcomposeAsyncImage(
                model = cover, contentDescription = null, contentScale = if (wide) ContentScale.Fit else ContentScale.Crop,
                loading = { placeholder() }, error = { placeholder() },
                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
            )
            badge?.let {
                Text(
                    it, style = MaterialTheme.typography.labelSmall, color = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                        .background(androidx.compose.ui.graphics.Color(0xCC000000), MaterialTheme.shapes.small)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Text(game.title, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 5.dp))
        Text(hint ?: game.source.label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
            color = if (hint != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary)
    }
}
