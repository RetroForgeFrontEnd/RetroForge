package com.retroforge.app.ui.settings

import com.retroforge.app.R

import androidx.compose.ui.res.stringResource

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.retroforge.app.BuildConfig
import com.retroforge.app.ui.CardBody
import com.retroforge.app.ui.CardTitle
import com.retroforge.app.ui.ScreenFrame
import com.retroforge.core.ui.FocusCard
import com.retroforge.core.ui.FocusChip
import com.retroforge.core.ui.StatusPill
import com.retroforge.core.ui.StatusTone
import com.retroforge.pc.DriverAdvice
import com.retroforge.pc.DriverFile
import com.retroforge.pc.GpuDrivers
import com.retroforge.pc.GpuProbe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val FOLDER = "RetroForge/GPU drivers"

/** GPU check, and Turnip driver downloads for emulators with a driver manager (and, later, the built-in player). */
@Composable
fun GpuDriversScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val agent = "RetroForge/${BuildConfig.VERSION_NAME}"
    var advice by remember { mutableStateOf<DriverAdvice?>(null) }
    var files by remember { mutableStateOf<List<DriverFile>?>(null) }
    var listStatus by remember { mutableStateOf<String?>(null) }
    var showAnyway by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val progress = remember { mutableStateMapOf<String, String>() }
    // The driver RetroForge's own players use (built-in player's Vulkan cores, PC player), or null for the phone's.
    var inPlayers by remember { mutableStateOf(com.retroforge.player.GpuDriver.current(context)?.second) }
    LaunchedEffect(Unit) { advice = withContext(Dispatchers.IO) { GpuDrivers.advice(GpuProbe.probe()) } }

    ScreenFrame(title = stringResource(R.string.gpu_drivers_gpu_and_drivers), subtitle = stringResource(R.string.gpu_drivers_what_graphics_chip_this_device_has)) {
        FocusCard(modifier = Modifier.fillMaxWidth()) {
            CardTitle(stringResource(R.string.gpu_drivers_your_gpu))
            val a = advice
            if (a == null) {
                CardBody(stringResource(R.string.pc_stream_checking))
            } else {
                CardBody(a.gpuName)
                StatusPill(if (a.canUseCustomDrivers) "Custom drivers can be used" else "Custom drivers can't be used", if (a.canUseCustomDrivers) StatusTone.GOOD else StatusTone.NEUTRAL)
                CardBody(a.advice)
            }
        }

        FocusCard(modifier = Modifier.fillMaxWidth()) {
            CardTitle(stringResource(R.string.gpu_drivers_using_a_driver))
            CardBody(
                "Download a driver below; it's saved to Download/$FOLDER. Then, in an emulator with a GPU driver manager " +
                    "(Eden, Dolphin, NetherSX2 and others), open its graphics settings, choose to install a driver and pick the zip. " +
                    "Winlator and GameNative come with Turnip and choose it in each container's settings.",
            )
            CardBody(stringResource(R.string.gpu_drivers_try_one_driver_at_a_time))
        }

        if (advice?.canUseCustomDrivers == true) {
            FocusCard(modifier = Modifier.fillMaxWidth()) {
                CardTitle(stringResource(R.string.gpu_drivers_retroforge_s_own_players))
                CardBody(
                    stringResource(R.string.gpu_drivers_the_built_in_player_s_vulkan),
                )
                StatusPill(inPlayers?.let { "Using $it" } ?: "Using the phone's own driver", if (inPlayers != null) StatusTone.GOOD else StatusTone.NEUTRAL)
                CardBody(if (inPlayers == null) "Choose Use in RetroForge on a driver below." else "If games get worse, go back to the phone's driver.")
                if (inPlayers != null) FocusChip(stringResource(R.string.gpu_drivers_use_the_phone_s_own_driver), onClick = {
                    stopUsingInPlayers(context)
                    inPlayers = null
                })
            }
        }

        val canUse = advice?.canUseCustomDrivers == true
        if (advice != null && !canUse && !showAnyway) {
            FocusChip(stringResource(R.string.gpu_drivers_show_drivers_anyway_for_another_device), onClick = { showAnyway = true })
        }
        if (canUse || showAnyway) {
            FocusCard(modifier = Modifier.fillMaxWidth(), onClick = {
                listStatus = "Looking for the latest Turnip builds…"
                scope.launch {
                    val found = withContext(Dispatchers.IO) { GpuDrivers.fetch(agent) }
                    files = found
                    listStatus = if (found.isEmpty()) "Couldn't get the list. Check the internet connection and try again." else null
                }
            }) {
                CardTitle(if (files == null) "Find Turnip drivers" else "Look again")
                CardBody(
                    "Turnip is the open-source Vulkan driver for Adreno GPUs from the Mesa project (MIT licence), built by " +
                        GpuDrivers.SOURCES.joinToString(" and ") { it.name } + ". RetroForge lists their GitHub releases and downloads only what you pick.",
                )
                listStatus?.let { CardBody(it) }
            }
            if (files.orEmpty().isNotEmpty()) {
                com.retroforge.core.ui.ControllerTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    label = { androidx.compose.material3.Text(stringResource(R.string.gpu_drivers_search_drivers_e_g_26_0)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            val preferA8xx = advice?.preferA8xx == true
            val words = query.trim().split(' ').filter { it.isNotBlank() }
            files.orEmpty()
                .filter { file -> words.all { w -> listOf(file.fileName, file.release, file.source).any { it.contains(w, ignoreCase = true) } } }
                .sortedByDescending { preferA8xx && it.fileName.contains("8xx", ignoreCase = true) }
                .take(if (words.isEmpty()) 12 else 60)
                .forEach { file ->
                    FocusCard(modifier = Modifier.fillMaxWidth(), onClick = {
                        if (progress[file.url]?.endsWith("%") == true) return@FocusCard
                        progress[file.url] = "0%"
                        scope.launch {
                            progress[file.url] = withContext(Dispatchers.IO) {
                                runCatching { save(context, file, agent) { p -> progress[file.url] = "$p%" } }
                                    .fold({ "Saved to $it" }, { "Didn't download: ${it.message}" })
                            }
                        }
                    }) {
                        CardTitle(file.fileName)
                        CardBody("${file.release} · ${file.source} · ${file.published} · ${"%.1f".format(file.size / 1_000_000.0)} MB")
                        CardBody(progress[file.url]?.let { if (it.endsWith("%")) "Downloading… $it" else it } ?: "Select to download.")
                    }
                    if (canUse) FocusChip("Use ${file.release} in RetroForge", onClick = {
                        if (progress[file.url]?.endsWith("%") == true) return@FocusChip
                        progress[file.url] = "0%"
                        scope.launch {
                            progress[file.url] = withContext(Dispatchers.IO) {
                                runCatching { useInPlayers(context, file, agent) { p -> progress[file.url] = "$p%" } }
                                    .fold({ "RetroForge's players now use it" }, { "Couldn't use it: ${it.message}" })
                            }
                            inPlayers = com.retroforge.player.GpuDriver.current(context)?.second
                        }
                    })
                }
        }
    }
}

/**
 * Downloads [file] and unpacks it for RetroForge's own players (files/gpu-driver: its meta.json and library, as
 * adrenotools packages it), replacing the one they used.
 */
private fun useInPlayers(context: Context, file: DriverFile, agent: String, onProgress: (Int) -> Unit) {
    val zip = File(context.cacheDir, "drivers/${file.fileName}")
    GpuDrivers.download(file, zip, agent, onProgress)
    val target = com.retroforge.player.GpuDriver.dir(context)
    val fresh = File(target.path + ".new")
    fresh.deleteRecursively()
    fresh.mkdirs()
    try {
        java.util.zip.ZipFile(zip).use { archive ->
            for (entry in archive.entries()) {
                if (entry.isDirectory) continue
                val out = File(fresh, entry.name.substringAfterLast('/'))
                archive.getInputStream(entry).use { input -> out.outputStream().use { input.copyTo(it) } }
            }
        }
        val meta = File(fresh, "meta.json").takeIf { it.isFile }?.readText() ?: error("it has no meta.json (not an adrenotools driver)")
        val library = Regex("\"libraryName\"\\s*:\\s*\"([^\"]+)\"").find(meta)?.groupValues?.get(1) ?: error("its meta.json names no library")
        if (!File(fresh, library).isFile) error("$library is missing from it")
        target.deleteRecursively()
        if (!fresh.renameTo(target)) error("couldn't put it in place")
    } finally {
        fresh.deleteRecursively()
        zip.delete()
    }
}

/** Back to the phone's own driver in RetroForge's players. */
private fun stopUsingInPlayers(context: Context) {
    com.retroforge.player.GpuDriver.dir(context).deleteRecursively()
}

/** Downloads [file] and puts it in Download/RetroForge/GPU drivers (app storage on Android 9 and older). Returns where. */
private fun save(context: Context, file: DriverFile, agent: String, onProgress: (Int) -> Unit): String {
    val temp = File(context.cacheDir, "drivers/${file.fileName}")
    GpuDrivers.download(file, temp, agent, onProgress)
    try {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val dir = File(context.getExternalFilesDir(null), "GPU drivers").apply { mkdirs() }
            temp.copyTo(File(dir, file.fileName), overwrite = true)
            return dir.path
        }
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, file.fileName)
            put(MediaStore.Downloads.MIME_TYPE, "application/zip")
            put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/$FOLDER")
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: error("Android refused to create the file")
        resolver.openOutputStream(uri)?.use { out -> temp.inputStream().use { it.copyTo(out) } } ?: error("couldn't write the file")
        return "Download/$FOLDER"
    } finally {
        temp.delete()
    }
}
