package com.retroforge.app.ui.settings

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
    val progress = remember { mutableStateMapOf<String, String>() }
    LaunchedEffect(Unit) { advice = withContext(Dispatchers.IO) { GpuDrivers.advice(GpuProbe.probe()) } }

    ScreenFrame(title = "GPU and drivers", subtitle = "What graphics chip this device has, and custom drivers for emulators that can use them.") {
        FocusCard(modifier = Modifier.fillMaxWidth()) {
            CardTitle("Your GPU")
            val a = advice
            if (a == null) {
                CardBody("Checking…")
            } else {
                CardBody(a.gpuName)
                StatusPill(if (a.canUseCustomDrivers) "Custom drivers can be used" else "Custom drivers can't be used", if (a.canUseCustomDrivers) StatusTone.GOOD else StatusTone.NEUTRAL)
                CardBody(a.advice)
            }
        }

        FocusCard(modifier = Modifier.fillMaxWidth()) {
            CardTitle("Using a driver")
            CardBody(
                "Download a driver below; it's saved to Download/$FOLDER. Then, in an emulator with a GPU driver manager " +
                    "(Eden, Dolphin, NetherSX2 and others), open its graphics settings, choose to install a driver and pick the zip. " +
                    "Winlator and GameNative come with Turnip and choose it in each container's settings.",
            )
            CardBody("Try one driver at a time. If a game gets worse, switch that emulator back to the system driver.")
            CardBody("RetroForge's built-in player will use these too, once it supports GPU systems (N64, PSP, GameCube …).")
        }

        val canUse = advice?.canUseCustomDrivers == true
        if (advice != null && !canUse && !showAnyway) {
            FocusChip("Show drivers anyway (for another device)", onClick = { showAnyway = true })
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
            val preferA8xx = advice?.preferA8xx == true
            files.orEmpty()
                .sortedByDescending { preferA8xx && it.fileName.contains("8xx", ignoreCase = true) }
                .take(12)
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
                }
        }
    }
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
