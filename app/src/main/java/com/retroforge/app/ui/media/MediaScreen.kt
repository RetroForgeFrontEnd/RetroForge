package com.retroforge.app.ui.media

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import coil3.compose.AsyncImage
import com.retroforge.app.BuildConfig
import com.retroforge.app.ui.CardBody
import com.retroforge.app.ui.CardTitle
import com.retroforge.app.ui.ScreenFrame
import com.retroforge.core.ui.ControllerTextField
import com.retroforge.core.ui.FocusCard
import com.retroforge.core.ui.FocusChip
import com.retroforge.core.ui.StatusPill
import com.retroforge.core.ui.StatusTone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

private const val FOLDER = "media_folder"
private const val TMDB_KEY = "tmdb_key"

/**
 * Films on the device, Plex-style: posters in a grid, newest first. Artwork comes from TMDB when the user adds their
 * own free API key, otherwise a frame from the film. Plays in RetroForge's video player or another app.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MediaScreen(prefs: SharedPreferences) {
    val context = LocalContext.current
    var folder by remember { mutableStateOf(prefs.getString(FOLDER, null)) }
    // The user's own key wins; otherwise RetroForge's built-in one (when this build has it).
    var key by remember { mutableStateOf(prefs.getString(TMDB_KEY, null) ?: BuildConfig.TMDB_KEY.ifBlank { null }) }
    var refresh by remember { mutableIntStateOf(0) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        prefs.edit().putString(FOLDER, uri.toString()).apply()
        folder = uri.toString()
    }
    val films by produceState<List<Film>?>(null, folder, refresh) {
        value = folder?.let { tree -> withContext(Dispatchers.IO) { runCatching { scan(context, tree.toUri()) }.getOrDefault(emptyList()) } }
    }

    ScreenFrame(title = "Media", subtitle = "Your films, with artwork, played right here.") {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FocusChip("Use RetroForge's film folders", onClick = { pick.launch(makeFilmFolders()) })
            FocusChip(if (folder == null) "Choose another folder" else "Change folder", onClick = { pick.launch(null) })
            if (folder != null) FocusChip("Look again", onClick = { refresh++ })
        }
        if (folder == null) {
            FocusCard(modifier = Modifier.fillMaxWidth()) {
                CardTitle("Watch your films in RetroForge")
                CardBody(
                    "\"Use RetroForge's film folders\" makes Movies/RetroForge on your phone, with a Films folder and a 3D Films " +
                        "folder inside. Choose \"Use this folder\" when Android asks, then copy films into them (USB cable or a file " +
                        "manager) and press Look again. Or choose any folder you already keep films in, on the phone, an SD card or a " +
                        "USB drive. RetroForge only reads it.",
                )
                CardBody("3D films (side by side or top and bottom) get a 3D badge and play in Cardboard modes. How to watch: Settings → How to use RetroForge → Media.")
            }
        }
        val list = films
        when {
            folder == null -> Unit
            list == null -> CardBody("Looking for films…")
            list.isEmpty() -> CardBody("No films found in that folder (mp4, mkv, avi, mov and others).")
            else -> {
                // 3D films first, in their own row, then the rest.
                list.partition { it.is3d }.toList().zip(listOf("3D films", "Films")).forEach { (group, title) ->
                    if (group.isEmpty()) return@forEach
                    Text("$title (${group.size})", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        group.forEach { FilmCard(it, key) }
                    }
                }
            }
        }
        if (BuildConfig.TMDB_KEY.isBlank()) TmdbCard(prefs.getString(TMDB_KEY, null)) { new ->
            prefs.edit().putString(TMDB_KEY, new).apply()
            key = new
            refresh++
        }
        com.retroforge.app.ui.NetworkDrivesCard("Films", onUse = { uri, progress ->
            progress("Looking for films…", null)
            prefs.edit().putString(FOLDER, uri.toString()).apply()
            folder = uri.toString()
            val count = withContext(Dispatchers.IO) { runCatching { scan(context, uri).size }.getOrDefault(0) }
            if (count == 0) "Added, but no films were found there." else "Added: $count film${if (count == 1) "" else "s"} found. They're above."
        })
        MediaServersCard(prefs)
    }
}

/** What TMDB knows about a film (saved next to its poster). */
private data class FilmInfo(val overview: String, val rating: Double, val released: String, val runtime: Int?, val imdb: String?)

private fun infoFile(context: Context, film: Film) = File(context.cacheDir, "posters/${film.uri.hashCode().toUInt().toString(16)}.json")

private fun loadInfo(context: Context, film: Film): FilmInfo? = runCatching {
    val o = JSONObject(infoFile(context, film).readText())
    FilmInfo(
        o.optString("overview"), o.optDouble("vote_average", 0.0), o.optString("release_date"), o.optInt("runtime").takeIf { it > 0 },
        o.optString("imdb_id").takeIf { it.startsWith("tt") },
    )
}.getOrNull()

@Composable
private fun FilmCard(film: Film, tmdbKey: String?) {
    val context = LocalContext.current
    val art by produceState<File?>(null, film.uri, tmdbKey) {
        value = withContext(Dispatchers.IO) { artwork(context, film, tmdbKey) }
    }
    var open by remember { mutableStateOf(false) }
    if (open) FilmDetails(film, art, onPlay = { open = false; play(context, film) }, onClose = { open = false })
    FocusCard(modifier = Modifier.width(150.dp), onClick = { open = true }) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp))) {
            art?.let { AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f)) }
            if (film.is3d) Box(Modifier.align(Alignment.TopEnd).padding(6.dp)) { StatusPill("3D", StatusTone.GOOD) }
        }
        Text(film.title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
        film.year?.let { Text(it.toString(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

/** Poster, story, rating and Play: a film's page, over the grid. */
@Composable
private fun FilmDetails(film: Film, art: File?, onPlay: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val info by produceState<FilmInfo?>(null, film.uri) { value = withContext(Dispatchers.IO) { loadInfo(context, film) } }
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose) {
        FocusCard(modifier = Modifier.fillMaxWidth()) {
            androidx.compose.foundation.layout.Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Box(Modifier.width(120.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp))) {
                    art?.let { AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f)) }
                }
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    CardTitle(film.title)
                    val facts = listOfNotNull(
                        (info?.released?.take(4)?.takeIf { it.isNotBlank() } ?: film.year?.toString()),
                        info?.runtime?.let { "${it / 60} h ${it % 60} min" },
                        info?.rating?.takeIf { it > 0 }?.let { "★ %.1f".format(it) },
                    )
                    if (facts.isNotEmpty()) Text(facts.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (film.is3d) StatusPill("3D side by side", StatusTone.GOOD)
                }
            }
            CardBody(info?.overview?.takeIf { it.isNotBlank() } ?: "No description. Add a TMDB key below the films for posters, stories and ratings.")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FocusChip("Play", onClick = onPlay)
                // A link to the film's IMDb page (from TMDB's record); nothing is read from IMDb itself.
                info?.imdb?.let { id ->
                    FocusChip("Open on IMDb", onClick = {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, "https://www.imdb.com/title/$id/".toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    })
                }
                FocusChip("Close", onClick = onClose)
            }
        }
    }
}

@Composable
private fun TmdbCard(saved: String?, onSave: (String?) -> Unit) {
    var draft by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    val context = LocalContext.current
    FocusCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle("Film posters (TMDB)")
        StatusPill(if (saved.isNullOrBlank()) "Using frames from the films" else "TMDB key saved", if (saved.isNullOrBlank()) StatusTone.NEUTRAL else StatusTone.GOOD)
        CardBody(
            "For proper posters, create a free account at themoviedb.org, copy your API key (v3) and paste it here. It stays on this " +
                "device and is sent only to TMDB. This product uses the TMDB API but is not endorsed or certified by TMDB.",
        )
        ControllerTextField(
            value = draft, onValueChange = { draft = it.trim() }, singleLine = true, label = { Text("TMDB API key") },
            visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FocusChip(if (show) "Hide key" else "Show key", onClick = { show = !show })
            FocusChip("Save key", onClick = { onSave(draft.ifBlank { null }); draft = "" }, enabled = draft.length >= 16)
            FocusChip("Get a free key", onClick = {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, "https://www.themoviedb.org/settings/api".toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            })
            if (!saved.isNullOrBlank()) FocusChip("Remove key", onClick = { onSave(null) })
        }
    }
}

private fun play(context: Context, film: Film) {
    context.startActivity(
        Intent(context, VideoActivity::class.java)
            .putExtra(VideoActivity.EXTRA_URI, film.uri)
            .putExtra(VideoActivity.EXTRA_TITLE, film.title)
            .putExtra(VideoActivity.EXTRA_3D, film.is3d)
            .putExtra(VideoActivity.EXTRA_FILE_NAME, film.fileName),
    )
}

/** Every video under [tree] (a few folders deep), newest first. */
/**
 * Makes Movies/RetroForge with Films and 3D Films inside (apps may make folders in Movies themselves), and returns
 * it as the folder Android's picker opens at, so one tap on "Use this folder" lets RetroForge read it.
 */
private fun makeFilmFolders(): Uri {
    val root = java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_MOVIES), "RetroForge")
    runCatching { java.io.File(root, "Films").mkdirs(); java.io.File(root, "3D Films").mkdirs() }
    return DocumentsContract.buildDocumentUri("com.android.externalstorage.documents", "primary:Movies/RetroForge")
}

private fun scan(context: Context, tree: Uri): List<Film> {
    val films = mutableListOf<Film>()
    fun walk(parentId: String, depth: Int) {
        if (depth > 4) return
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        context.contentResolver.query(
            children,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_LAST_MODIFIED),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getString(0)
                val name = c.getString(1) ?: continue
                if (c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) {
                    walk(id, depth + 1)
                } else if (name.substringAfterLast('.', "").lowercase() in Films.VIDEO_EXTENSIONS) {
                    films += Films.parse(DocumentsContract.buildDocumentUriUsingTree(tree, id).toString(), name, c.getLong(3))
                }
            }
        }
    }
    walk(DocumentsContract.getTreeDocumentId(tree), 0)
    return films.sortedByDescending { it.modified }
}

/** A poster from TMDB (with a key), else a frame from about a tenth of the way in. Cached. */
private fun artwork(context: Context, film: Film, tmdbKey: String?): File? {
    val name = film.uri.hashCode().toUInt().toString(16)
    val poster = File(context.cacheDir, "posters/$name.jpg")
    if (poster.isFile) return poster
    if (!tmdbKey.isNullOrBlank()) {
        runCatching { tmdbPoster(film, tmdbKey, infoFile(context, film)) }.getOrNull()?.let { bytes ->
            poster.parentFile?.mkdirs()
            poster.writeBytes(bytes)
            return poster
        }
    }
    val frame = File(context.cacheDir, "frames/$name.jpg")
    if (frame.isFile) return frame
    return runCatching {
        MediaMetadataRetriever().use { r ->
            r.setDataSource(context, film.uri.toUri())
            val length = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            val bitmap = r.getFrameAtTime(length * 100, MediaMetadataRetriever.OPTION_CLOSEST_SYNC) ?: return null
            frame.parentFile?.mkdirs()
            frame.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 82, it) }
            frame
        }
    }.getOrNull()
}

/** The poster for the best TMDB match; its details (story, rating, date, runtime) are saved to [info] on the way. */
private fun tmdbPoster(film: Film, key: String, info: File): ByteArray? {
    val k = URLEncoder.encode(key, "UTF-8")
    val query = "https://api.themoviedb.org/3/search/movie?api_key=$k" +
        "&query=${URLEncoder.encode(film.title, "UTF-8")}" + (film.year?.let { "&year=$it" } ?: "")
    val match = JSONObject(get(query) ?: return null).optJSONArray("results")?.optJSONObject(0) ?: return null
    // The film's own record adds the runtime.
    val details = match.optInt("id").takeIf { it > 0 }?.let { id -> get("https://api.themoviedb.org/3/movie/$id?api_key=$k")?.let(::JSONObject) }
    info.parentFile?.mkdirs()
    info.writeText(
        JSONObject()
            .put("overview", match.optString("overview"))
            .put("vote_average", match.optDouble("vote_average", 0.0))
            .put("release_date", match.optString("release_date"))
            .put("runtime", details?.optInt("runtime") ?: 0)
            .put("imdb_id", details?.optString("imdb_id").orEmpty())
            .toString(),
    )
    val path = match.optString("poster_path").takeIf { it.isNotBlank() && it != "null" } ?: return null
    return getBytes("https://image.tmdb.org/t/p/w342$path")
}

private fun get(url: String): String? = getBytes(url)?.decodeToString()

private fun getBytes(url: String): ByteArray? {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 30_000
        setRequestProperty("User-Agent", "RetroForge/${BuildConfig.VERSION_NAME}")
    }
    return try {
        if (connection.responseCode == 200) connection.inputStream.use { it.readBytes() } else null
    } finally {
        connection.disconnect()
    }
}
