package com.retroforge.app.ui.settings

import android.content.Context
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
import com.retroforge.app.BuildConfig
import com.retroforge.app.player.Bezels
import com.retroforge.app.ui.CardBody
import com.retroforge.app.ui.CardTitle
import com.retroforge.core.ui.FocusCard
import com.retroforge.core.ui.FocusChip
import com.retroforge.core.ui.StatusPill
import com.retroforge.core.ui.StatusTone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Bezels from The Bezel Project round the game in landscape: on or off for every system (each system can be switched
 * in the game's menu too), and a download per system in the library. Nothing is included in RetroForge.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BezelsCard(systems: List<Pair<String, String>>, title: String = "Bezels (The Bezel Project)") {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("views", Context.MODE_PRIVATE) }
    val bezels = remember { Bezels(File(context.filesDir, "bezels"), "RetroForge/${BuildConfig.VERSION_NAME}") }
    var on by remember { mutableStateOf(prefs.getBoolean(Bezels.PREF, true)) }
    var changes by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    // How far a download has got, 0 to 1 (null when none is running).
    var progress by remember { mutableStateOf<Float?>(null) }
    // Library systems with a pack (Mega Drive and Genesis share one, so it's offered once).
    val offered = remember(systems) { systems.filter { Bezels.repoFor(it.first) != null }.distinctBy { Bezels.repoFor(it.first) } }
    val downloaded = remember(changes, offered) { offered.filter { bezels.downloaded(it.first) } }
    FocusCard(modifier = Modifier.fillMaxWidth(), onClick = {
        on = !on
        prefs.edit().putBoolean(Bezels.PREF, on).apply()
    }) {
        CardTitle(title) { StatusPill(if (on) "On" else "Off", if (on) StatusTone.GOOD else StatusTone.NEUTRAL) }
        CardBody(
            "Artwork round the game in landscape, like RetroArch and RetroBat bezels: a frame for each system, and for most games " +
                "their own. Made by The Bezel Project (github.com/thebezelproject) and downloaded from their GitHub, not included " +
                "in RetroForge. Download a system below; each game's own bezel (about 150 KB) is fetched the first time it starts. " +
                "Bezels match No-Intro file names. Select this card to switch them on or off; Picture in the game's menu switches a system.",
        )
        CardBody(if (downloaded.isEmpty()) "No systems downloaded yet." else "Downloaded: " + downloaded.joinToString { it.second })
        // Above the buttons, so it stays in view however many systems there are.
        status?.let { CardBody(it) }
        progress?.let { p ->
            CardBody("${(p * 100).toInt()}%")
            if (p > 0f) androidx.compose.material3.LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
            else androidx.compose.material3.LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Every system in the library at once, so every scanned game gets its bezel.
            val missing = offered.filter { it.first !in downloaded.map { d -> d.first } }
            if (missing.size > 1) FocusChip("Download all (${missing.size} systems)", enabled = !busy, onClick = {
                busy = true
                progress = 0f
                scope.launch {
                    val failed = mutableListOf<String>()
                    missing.forEachIndexed { i, (id, name) ->
                        status = "Downloading bezels… $name (${i + 1} of ${missing.size})"
                        withContext(Dispatchers.IO) { runCatching { bezels.install(id) } }.onFailure { failed += name }
                        progress = (i + 1f) / missing.size
                        changes++
                    }
                    progress = null
                    status = if (failed.isEmpty()) "Added bezels for all ${missing.size} systems. Their games show bezels in landscape."
                    else "Added ${missing.size - failed.size} systems. Couldn't get: ${failed.joinToString()}. Try those again below."
                    busy = false
                }
            })
            offered.forEach { (id, name) ->
                val have = id in downloaded.map { it.first }
                FocusChip(if (have) "Remove $name" else "Download $name", enabled = !busy, onClick = {
                    busy = true
                    status = if (have) null else "Downloading $name's bezels…"
                    if (!have) progress = 0f
                    scope.launch {
                        status = withContext(Dispatchers.IO) {
                            if (have) { bezels.remove(id); null }
                            else runCatching { bezels.install(id); "Added $name. Its games show bezels in landscape." }
                                .getOrElse { "Couldn't get $name's bezels: ${it.message}" }
                        }
                        busy = false
                        progress = null
                        changes++
                    }
                })
            }
        }
        if (offered.isEmpty()) CardBody("None of the systems in your library have Bezel Project bezels.")
    }
}
