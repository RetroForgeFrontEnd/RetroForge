package com.retroforge.app.ui.media

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.retroforge.core.ui.StatusPill
import com.retroforge.core.ui.StatusTone
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.retroforge.app.network.MediaEntry
import com.retroforge.app.network.MediaServer
import com.retroforge.app.network.MediaServers
import com.retroforge.app.ui.CardBody
import com.retroforge.app.ui.CardTitle
import com.retroforge.app.ui.ChipRow
import com.retroforge.core.ui.FocusCard
import com.retroforge.core.ui.FocusChip
import kotlinx.coroutines.launch

private const val SERVERS = "media_servers"

/**
 * Films from media servers on the network (Plex, Jellyfin, Emby, a NAS …) through DLNA, as TVs and Bigscreen find
 * them: no account or sign-in. Servers found once are remembered; folders open one level at a time.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MediaServersCard(prefs: SharedPreferences) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var servers by remember { mutableStateOf(savedServers(prefs)) }
    var server by remember { mutableStateOf<MediaServer?>(null) }
    // Folders opened so far: (id, title), the server's top first.
    val path = remember { mutableStateListOf<Pair<String, String>>() }
    var entries by remember { mutableStateOf<List<MediaEntry>?>(null) }
    var status by remember { mutableStateOf<String?>(null) }

    fun open(target: MediaServer, id: String, title: String, back: Boolean = false) {
        server = target
        if (!back) path += id to title
        entries = null
        status = "Opening $title…"
        scope.launch {
            runCatching { MediaServers.browse(target, id) }
                .onSuccess { entries = it; status = if (it.isEmpty()) "Nothing to watch in here." else null }
                .onFailure { status = "${target.name} didn't answer. Is it switched on, on the same network?" }
        }
    }

    fun back() {
        path.removeAt(path.lastIndex)
        if (path.isEmpty()) { server = null; entries = null; status = null }
        else path.last().let { (id, title) -> open(server ?: return, id, title, back = true) }
    }
    // Android's back steps up a folder; the red ✕ still leaves the Media tab (this card is far down the page).
    com.retroforge.app.ui.CloseStep(enabled = server != null, takeOverClose = false) { back() }

    FocusCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle("Plex and other media servers")
        val current = server
        if (current == null) {
            CardBody(
                "Watch films from Plex, Jellyfin, Emby or a NAS on your home network, with no sign-in: RetroForge finds them the " +
                    "way smart TVs and Bigscreen do (DLNA). In Plex: Settings → DLNA → Enable the DLNA server (on by default).",
            )
            ChipRow {
                FocusChip("Find media servers", onClick = {
                    status = "Looking on your network (a few seconds)…"
                    scope.launch {
                        val found = MediaServers.find(context)
                        if (found.isNotEmpty()) {
                            servers = (found + servers).distinctBy { it.controlUrl }
                            saveServers(prefs, servers)
                        }
                        status = when {
                            found.isEmpty() -> "No media servers answered. Check the server is on and sharing by DLNA, and that this phone is on the same network."
                            else -> "Found ${found.size}: ${found.joinToString { it.name }}."
                        }
                    }
                })
                servers.forEach { s -> FocusChip(s.name, onClick = { path.clear(); open(s, "0", s.name) }) }
                if (servers.isNotEmpty()) FocusChip("Forget these", onClick = { servers = emptyList(); saveServers(prefs, emptyList()) })
            }
        } else {
            CardBody(path.joinToString(" › ") { it.second })
            ChipRow {
                FocusChip("‹ Back", onClick = { back() })
                if (path.size > 1) FocusChip("Servers", onClick = { path.clear(); server = null; entries = null; status = null })
            }
            entries?.let { list ->
                val (folders, videos) = list.partition { it.isFolder }
                if (folders.isNotEmpty()) ChipRow {
                    folders.forEach { f -> FocusChip(f.title + (f.count?.let { " ($it)" } ?: ""), onClick = { open(current, f.id, f.title) }) }
                }
                if (videos.isNotEmpty()) {
                    // A folder called "3D Films" (or with SBS or 3D in its name) holds 3D films, whatever their titles say.
                    val folder3d = path.any { (_, title) -> Films.parse("", "$title.mkv", 0).is3d }
                    CardBody("${videos.size} to watch:")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        videos.forEach { v -> ServerFilm(v, folder3d) { play(context, v, it) } }
                    }
                }
            }
        }
        status?.let { CardBody(it) }
    }
}

/** A film on a server: the server's own poster (Plex and Jellyfin send one), title, and a 3D badge. */
@Composable
private fun ServerFilm(video: MediaEntry, folder3d: Boolean, onPlay: (Boolean) -> Unit) {
    val is3d = folder3d || is3d(video)
    val context = LocalContext.current
    val poster by produceState<java.io.File?>(null, video.art) { value = video.art?.let { poster(context, it) } }
    FocusCard(modifier = Modifier.width(136.dp), onClick = { onPlay(is3d) }) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp))) {
            poster?.let { AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f)) }
            if (is3d) Box(Modifier.align(Alignment.TopEnd).padding(6.dp)) { StatusPill("3D", StatusTone.GOOD) }
        }
        Text(video.title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** The server's poster, downloaded once into the cache (so it shows straight away next time). */
private suspend fun poster(context: Context, url: String): java.io.File? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    val file = java.io.File(context.cacheDir, "posters/server-${url.hashCode().toUInt().toString(16)}.jpg")
    if (file.length() > 0) return@withContext file
    runCatching {
        val c = java.net.URL(url).openConnection() as java.net.HttpURLConnection
        c.connectTimeout = 3000
        c.readTimeout = 10000
        try {
            if (c.responseCode !in 200..299) return@runCatching null
            file.parentFile?.mkdirs()
            val part = java.io.File(file.path + ".part")
            c.inputStream.use { input -> part.outputStream().use { input.copyTo(it) } }
            part.renameTo(file)
            file
        } finally {
            c.disconnect()
        }
    }.getOrNull()
}

/** 3D going by the server's file name (when the address has one) or the title. */
private fun is3d(video: MediaEntry): Boolean {
    val url = video.url.orEmpty()
    val fileName = url.substringBefore('?').substringAfterLast('/').takeIf { '.' in it }
    return listOfNotNull(fileName, "${video.title}.mkv").any { Films.parse(url, it, 0).is3d }
}

private fun play(context: Context, video: MediaEntry, is3d: Boolean) {
    val url = video.url ?: return
    context.startActivity(
        Intent(context, VideoActivity::class.java)
            .putExtra(VideoActivity.EXTRA_URI, url)
            .putExtra(VideoActivity.EXTRA_TITLE, video.title)
            .putExtra(VideoActivity.EXTRA_3D, is3d)
            .putExtra(VideoActivity.EXTRA_FILE_NAME, video.title),
    )
}

/** Servers found before, one per line: name, description address and control address, tab-separated. */
private fun savedServers(prefs: SharedPreferences): List<MediaServer> =
    prefs.getString(SERVERS, null).orEmpty().lines().mapNotNull { line ->
        line.split('\t').takeIf { it.size == 3 }?.let { (name, location, control) -> MediaServer(name, location, control) }
    }

private fun saveServers(prefs: SharedPreferences, servers: List<MediaServer>) {
    prefs.edit().putString(SERVERS, servers.joinToString("\n") { "${it.name}\t${it.location}\t${it.controlUrl}" }).apply()
}
