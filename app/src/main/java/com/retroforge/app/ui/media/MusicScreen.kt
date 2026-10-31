package com.retroforge.app.ui.media

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import coil3.compose.AsyncImage
import com.retroforge.app.R
import com.retroforge.app.ui.CardBody
import com.retroforge.app.ui.CardTitle
import com.retroforge.app.ui.ChipRow
import com.retroforge.core.ui.FocusCard
import com.retroforge.core.ui.FocusChip
import com.retroforge.core.ui.pluralStr
import com.retroforge.core.ui.str
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val MUSIC_FOLDER = "music_folder"
private const val MUSIC_ON_PHONE = "music_on_phone"
private const val SHOW_RADIO = "music_show_radio"

private val AUDIO_PERMISSION = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

/**
 * The Media tab's music: every song on the phone (Android's music library, with its tags) and/or one music folder
 * (on the phone, a network drive or a cloud drive), as albums. Plays in the background through [MusicService].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MusicSection(prefs: SharedPreferences) {
    val context = LocalContext.current
    var folder by remember { mutableStateOf(prefs.getString(MUSIC_FOLDER, null)) }
    var onPhone by remember { mutableStateOf(prefs.getBoolean(MUSIC_ON_PHONE, false) && allowed(context)) }
    var refresh by remember { mutableIntStateOf(0) }
    var denied by remember { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        denied = !granted
        onPhone = granted
        prefs.edit().putBoolean(MUSIC_ON_PHONE, granted).apply()
    }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        prefs.edit().putString(MUSIC_FOLDER, uri.toString()).apply()
        folder = uri.toString()
    }
    val albums by produceState<List<Album>?>(null, folder, onPhone, refresh) {
        value = null
        value = withContext(Dispatchers.IO) {
            val songs = (if (onPhone) runCatching { phoneSongs(context) }.onFailure { android.util.Log.w("Music", "Couldn't read the phone's music", it) }.getOrDefault(emptyList()) else emptyList()) +
                (folder?.let { runCatching { folderSongs(context, it.toUri()) }.getOrDefault(emptyList()) } ?: emptyList())
            Music.albums(songs.distinctBy { it.uri })
        }
    }

    NowPlayingCard()
    // Your own music, or radio.
    var radio by remember { mutableStateOf(prefs.getBoolean(SHOW_RADIO, false)) }
    ChipRow {
        FocusChip((if (!radio) "● " else "") + stringResource(R.string.radio_my_music), onClick = { radio = false; prefs.edit().putBoolean(SHOW_RADIO, false).apply() })
        FocusChip((if (radio) "● " else "") + stringResource(R.string.radio_title), onClick = { radio = true; prefs.edit().putBoolean(SHOW_RADIO, true).apply() })
    }
    if (radio) {
        RadioSection(prefs)
        return
    }
    ChipRow {
        FocusChip((if (onPhone) "● " else "") + stringResource(R.string.music_on_phone), onClick = {
            when {
                onPhone -> { onPhone = false; prefs.edit().putBoolean(MUSIC_ON_PHONE, false).apply() }
                allowed(context) -> { onPhone = true; prefs.edit().putBoolean(MUSIC_ON_PHONE, true).apply() }
                else -> ask.launch(AUDIO_PERMISSION)
            }
        })
        FocusChip(if (folder == null) stringResource(R.string.music_choose_folder) else stringResource(R.string.music_change_folder), onClick = { pick.launch(null) })
        if (folder != null) FocusChip(stringResource(R.string.music_forget_folder), onClick = { prefs.edit().remove(MUSIC_FOLDER).apply(); folder = null })
        if (folder != null || onPhone) FocusChip(stringResource(R.string.player_look_again), onClick = { refresh++ })
    }
    if (denied) CardBody(stringResource(R.string.music_permission_denied))
    val list = albums
    when {
        folder == null && !onPhone -> FocusCard(modifier = Modifier.fillMaxWidth()) {
            CardTitle(stringResource(R.string.music_intro_title))
            CardBody(stringResource(R.string.music_intro_body))
        }
        list == null -> CardBody(stringResource(R.string.music_looking))
        list.isEmpty() -> CardBody(stringResource(R.string.music_none))
        else -> {
            val all = list.flatMap { it.tracks }
            ChipRow {
                FocusChip(stringResource(R.string.music_play_all), onClick = { MusicService.play(context, all) })
                FocusChip(stringResource(R.string.music_shuffle), onClick = { MusicService.play(context, all, shuffle = true) })
            }
            Text(stringResource(R.string.music_albums, list.size), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
            // A dozen albums at a time, so the page isn't one long wall of covers.
            var showing by remember(list) { mutableIntStateOf(12) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                list.take(showing).forEach { AlbumCard(it) }
            }
            if (list.size > showing) FocusChip(stringResource(R.string.media_see_more, list.size - showing), onClick = { showing += 12 })
        }
    }
    com.retroforge.app.ui.NetworkDrivesCard(str(R.string.media_music), kind = "music", onUse = { uri, progress ->
        progress(str(R.string.music_looking), null)
        prefs.edit().putString(MUSIC_FOLDER, uri.toString()).apply()
        folder = uri.toString()
        val count = withContext(Dispatchers.IO) { runCatching { folderSongs(context, uri).size }.getOrDefault(0) }
        if (count == 0) str(R.string.music_added_none) else pluralStr(R.plurals.music_added_songs, count, count)
    })
}

private fun allowed(context: Context) = ContextCompat.checkSelfPermission(context, AUDIO_PERMISSION) == PackageManager.PERMISSION_GRANTED

@Composable
private fun AlbumCard(album: Album) {
    var open by remember { mutableStateOf(false) }
    if (open) AlbumDetails(album, onClose = { open = false })
    FocusCard(modifier = Modifier.width(150.dp), onClick = { open = true }) {
        Cover(rememberAlbumArt(album), Modifier.fillMaxWidth())
        Text(album.title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text(album.artist ?: pluralStringResource(R.plurals.music_songs, album.tracks.size, album.tracks.size),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** An album's art, or a plain square with a note when it has none. */
@Composable
private fun Cover(art: Any?, modifier: Modifier) {
    Box(modifier.aspectRatio(1f).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        Text("♪", style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary)
        art?.let { AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    }
}

/** An album's songs: tap one to play the album from there. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AlbumDetails(album: Album, onClose: () -> Unit) {
    val context = LocalContext.current
    androidx.compose.ui.window.Dialog(onDismissRequest = onClose) {
        FocusCard(modifier = Modifier.fillMaxWidth()) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Cover(rememberAlbumArt(album), Modifier.width(96.dp))
                Column {
                    CardTitle(album.title)
                    album.artist?.let { CardBody(it) }
                    CardBody(pluralStringResource(R.plurals.music_songs, album.tracks.size, album.tracks.size))
                }
            }
            ChipRow {
                FocusChip(stringResource(R.string.music_play_all), onClick = { MusicService.play(context, album.tracks); onClose() })
                FocusChip(stringResource(R.string.music_shuffle), onClick = { MusicService.play(context, album.tracks, shuffle = true); onClose() })
                FocusChip(stringResource(R.string.retro_forge_root_close), onClick = onClose)
            }
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                album.tracks.forEachIndexed { i, track ->
                    FocusCard(modifier = Modifier.fillMaxWidth(), onClick = { MusicService.play(context, album.tracks, i); onClose() }) {
                        Text(listOfNotNull(track.number?.toString(), track.title).joinToString(". "), style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (album.artist == null) track.artist?.let { CardBody(it) }
                    }
                }
            }
        }
    }
}

/** What's playing, with its controls; nothing while no music is loaded. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun NowPlayingCard() {
    val context = LocalContext.current
    var controller by remember { mutableStateOf<MediaController?>(null) }
    var tick by remember { mutableIntStateOf(0) }
    DisposableEffect(Unit) {
        val future = MediaController.Builder(context, MusicService.token(context)).buildAsync()
        val listener = object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) { tick++ }
        }
        future.addListener({
            controller = runCatching { future.get() }.getOrNull()?.also { it.addListener(listener) }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            controller?.removeListener(listener)
            MediaController.releaseFuture(future)
        }
    }
    val player = controller ?: return
    @Suppress("UNUSED_EXPRESSION") tick // read so the card redraws on every change
    val item = player.currentMediaItem ?: return
    var open by remember { mutableStateOf(false) }
    if (open) NowPlayingPage(player, onClose = { open = false })
    FocusCard(modifier = Modifier.fillMaxWidth(), onClick = { open = true }) {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            // Art from the song's own tags once it's playing, else its folder's cover.
            Cover(player.mediaMetadata.artworkData ?: item.mediaMetadata.artworkUri?.toString(), Modifier.width(72.dp))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.music_now_playing), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                CardTitle(item.mediaMetadata.title?.toString().orEmpty())
                listOfNotNull(item.mediaMetadata.artist, item.mediaMetadata.albumTitle).joinToString(" · ").takeIf { it.isNotBlank() }?.let { CardBody(it) }
                // A station or file that won't play: say so rather than sit silent.
                if (player.playerError != null) CardBody(stringResource(R.string.music_wont_play))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            RoundButton(RoundIcon.Previous, stringResource(R.string.music_previous), onClick = { player.seekToPrevious() }, diameter = 48.dp)
            BigPlayButton(player.isPlaying, onClick = { if (player.isPlaying) player.pause() else player.play() }, diameter = 60.dp)
            RoundButton(RoundIcon.Next, stringResource(R.string.music_next), onClick = { player.seekToNext() }, diameter = 48.dp)
        }
        ChipRow {
            FocusChip(stringResource(R.string.music_visuals_and_eq), onClick = { open = true })
            FocusChip(stringResource(R.string.music_stop), onClick = { player.stop(); player.clearMediaItems() })
        }
    }
}

/**
 * Every song in Android's music library (the phone and its memory card), with its tags. Songs not yet read by Android
 * (no tags, not yet marked as music) count too, named from their file and folders; ringtones and alarms don't.
 */
private fun phoneSongs(context: Context): List<Track> {
    val songs = mutableListOf<Track>()
    val folderColumn = if (Build.VERSION.SDK_INT >= 29) MediaStore.Audio.Media.RELATIVE_PATH else @Suppress("DEPRECATION") MediaStore.Audio.Media.DATA
    val columns = arrayOf(
        MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM,
        MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.TRACK, MediaStore.Audio.Media.DISPLAY_NAME, folderColumn,
    )
    val notMusic = listOf(MediaStore.Audio.Media.IS_RINGTONE, MediaStore.Audio.Media.IS_NOTIFICATION, MediaStore.Audio.Media.IS_ALARM)
        .joinToString(" AND ") { "($it IS NULL OR $it = 0)" } // unread files have none of these set yet
    context.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, columns, notMusic, null, null)?.use { c ->
        while (c.moveToNext()) {
            val id = c.getLong(0)
            val uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id).toString()
            val file = c.getString(6) ?: continue
            if (file.substringAfterLast('.', "").lowercase() !in Music.AUDIO_EXTENSIONS) continue
            val artist = c.getString(2)?.takeIf { it.isNotBlank() && it != MediaStore.UNKNOWN_STRING }
            val album = c.getString(3)?.takeIf { it.isNotBlank() && it != MediaStore.UNKNOWN_STRING }
            if (artist == null && album == null) {
                // Untagged: the folder is the album and the one above it the artist, as for a music folder.
                val folders = c.getString(7).orEmpty().trimEnd('/').let { if (Build.VERSION.SDK_INT >= 29) it else it.substringBeforeLast('/') }.split('/')
                songs += Music.parse(uri, file, folders.lastOrNull(), folders.getOrNull(folders.size - 2)?.takeIf { folders.size > 2 }, "folder:" + folders.joinToString("/"))
                continue
            }
            var title = c.getString(1)?.takeIf { it.isNotBlank() } ?: file.substringBeforeLast('.')
            var by = artist
            // No artist tag: Android's title is then the file's name, often "Artist - Title".
            if (by == null && " - " in title) {
                by = title.substringBefore(" - ").trim()
                title = title.substringAfter(" - ").trim()
            }
            val albumId = c.getLong(4)
            songs += Track(uri, title, by, album ?: title, Music.trackNumber(c.getInt(5)),
                null, "phone:$albumId")
        }
    }
    return songs
}

/** Every song under [tree] (a few folders deep): each folder an album, the folder above it the artist. */
private fun folderSongs(context: Context, tree: Uri): List<Track> {
    val songs = mutableListOf<Track>()
    fun walk(parentId: String, name: String?, parentName: String?, depth: Int) {
        if (depth > 5) return
        context.contentResolver.query(
            DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId),
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE),
            null, null, null,
        )?.use { c ->
            val files = mutableListOf<Pair<String, String>>()
            while (c.moveToNext()) {
                val id = c.getString(0)
                val child = c.getString(1) ?: continue
                if (c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) walk(id, child, name, depth + 1) else files += id to child
            }
            val cover = files.firstOrNull { (_, n) ->
                n.substringBeforeLast('.').lowercase() in Music.COVER_NAMES && n.substringAfterLast('.', "").lowercase() in Films.IMAGE_EXTENSIONS
            }?.let { DocumentsContract.buildDocumentUriUsingTree(tree, it.first).toString() }
            files.filter { it.second.substringAfterLast('.', "").lowercase() in Music.AUDIO_EXTENSIONS }.forEach { (id, file) ->
                // The top folder chosen is the collection, not an artist.
                songs += Music.parse(DocumentsContract.buildDocumentUriUsingTree(tree, id).toString(), file, name, parentName.takeIf { depth >= 2 }, parentId, cover)
            }
        }
    }
    walk(DocumentsContract.getTreeDocumentId(tree), null, null, 0)
    return songs
}
