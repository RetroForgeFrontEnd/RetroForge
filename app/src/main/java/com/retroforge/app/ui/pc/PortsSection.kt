package com.retroforge.app.ui.pc

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
        FocusChip((if (!windowsTab) "● " else "") + "Android ports", onClick = { windowsTab = false; showAll = false })
        FocusChip((if (windowsTab) "● " else "") + "Windows ports", onClick = { windowsTab = true; showAll = false })
    }
    CardBody(
        if (!windowsTab) "Games rebuilt to run natively on Android, from decompiled code or PC source ports. They run better than emulation " +
            "and add widescreen and higher frame rates. Each needs your own copy of the game; RetroForge never supplies game files."
        else "Ports with only a Windows version. They run through GameNative or Winlator, so they need a strong Snapdragon phone " +
            "and may not run well; all are experimental. Each needs your own copy of the game.",
    )
    val tabPorts = ports.filter { it.isWindows == windowsTab }
    val mine = tabPorts.filter { it.id in owned }
    if (mine.isEmpty()) {
        CardBody("None of your games has a port in this list yet.")
    } else {
        Text("For games you own", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        mine.forEach { PortCard(it, owned[it.id], library) }
    }
    val rest = tabPorts.filter { it.id !in owned }
    if (showAll) {
        Text("All ports", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        rest.forEach { PortCard(it, null, library) }
    } else {
        if (rest.isNotEmpty()) FocusChip("Show all ${rest.size} other ports", onClick = { showAll = true })
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
            if (port.status == "experimental") StatusPill("Experimental", StatusTone.CAUTION)
            if (owned != null) StatusPill("You have ${owned.title}", StatusTone.GOOD)
        }
        CardBody("Needs: ${port.needs}")
        if (port.isWindows) {
            CardBody(
                "How: download the Windows version from its releases page and unzip it into Download/RetroForge/Ports. " +
                    "Then in GameNative (or Winlator) add it as a custom game and pick its .exe.",
            )
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (port.isWindows) {
                FocusChip("Download Windows version", onClick = {
                    prepared = "Looking for the Windows version…"
                    scope.launch {
                        prepared = withContext(Dispatchers.IO) {
                            runCatching {
                                val file = PortDownloads.pickWindows(PortDownloads.latest(port)) ?: return@runCatching "No Windows download in the latest release. Use Project page."
                                val where = PortDownloads.unpackWindows(context, port, file) { p -> prepared = "Downloading ${file.name}… $p%" }
                                "Unpacked into $where. In GameNative, add it as a custom game and pick its .exe."
                            }.getOrElse { "Didn't download: ${it.message}" }
                        }
                    }
                })
                val runner = WINDOWS_RUNNERS.firstNotNullOfOrNull { context.packageManager.getLaunchIntentForPackage(it) }
                FocusChip(if (runner != null) "Open GameNative" else "Get GameNative", onClick = {
                    if (runner != null) runCatching { context.startActivity(runner) } else open(context, GAMENATIVE_PAGE)
                })
            } else {
                if (launch != null) FocusChip("Open", onClick = { runCatching { context.startActivity(launch) } })
                FocusChip(if (launch != null) "Update" else "Download and install", onClick = {
                    prepared = "Looking for the latest version…"
                    scope.launch {
                        prepared = withContext(Dispatchers.IO) {
                            runCatching {
                                val file = PortDownloads.pickApk(PortDownloads.latest(port)) ?: return@runCatching "No Android download in the latest release. Use Project page."
                                PortDownloads.installApk(context, file) { p -> prepared = "Downloading ${file.name}… $p%" }
                                "Downloaded ${file.name}. Confirm the install in Android's window (the first time, allow RetroForge to install apps)."
                            }.getOrElse { "Didn't download: ${it.message}" }
                        }
                    }
                })
                FocusChip("Get with Obtainium", onClick = { get(context, port) })
            }
            if (owned != null) FocusChip("Prepare files", onClick = {
                prepared = "Copying…"
                scope.launch {
                    prepared = withContext(Dispatchers.IO) {
                        runCatching { prepare(context, library, port, owned) }.getOrElse { "Couldn't copy the game: ${it.message}" }
                    }
                }
            })
            FocusChip("Project page", onClick = { open(context, port.repo) })
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
    val root = library.roots.first().firstOrNull { it.id == game.rootId } ?: error("its game folder isn't available")
    val source = library.documentUri(root, game.documentId)
    val resolver = context.contentResolver
    val folder = "${android.os.Environment.DIRECTORY_DOWNLOADS}/RetroForge/Ports/${port.name.replace(":", " -").replace(Regex("[\\/*?\"<>|]"), "_")}"
    fun save(name: String, write: (java.io.OutputStream) -> Unit) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) error("needs Android 10 or newer")
        val values = android.content.ContentValues().apply {
            put(android.provider.MediaStore.Downloads.DISPLAY_NAME, name)
            put(android.provider.MediaStore.Downloads.RELATIVE_PATH, folder)
        }
        val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("Android refused to create the file")
        resolver.openOutputStream(uri)?.use(write) ?: error("couldn't write the file")
    }
    var name = game.fileName
    if (game.fileName.endsWith(".zip", ignoreCase = true)) {
        // Ports want the bare ROM: the first file in the zip.
        resolver.openInputStream(source)?.use { input ->
            java.util.zip.ZipInputStream(input.buffered()).use { zip ->
                val entry = generateSequence { zip.nextEntry }.firstOrNull { !it.isDirectory } ?: error("the zip is empty")
                name = entry.name.substringAfterLast('/')
                save(name) { zip.copyTo(it) }
            }
        } ?: error("couldn't open the game")
    } else {
        resolver.openInputStream(source)?.use { input -> save(name) { input.copyTo(it) } } ?: error("couldn't open the game")
    }
    return "Copied $name to Download/RetroForge/Ports. Your original stays in your game folder. In the port, choose this file when it asks for the game."
}
