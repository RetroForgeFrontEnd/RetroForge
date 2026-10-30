package com.retroforge.app.ui.library

import com.retroforge.app.R

import androidx.compose.ui.res.stringResource

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.DocumentsContract
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.retroforge.app.ui.CardBody
import com.retroforge.core.library.GameEntity
import com.retroforge.core.library.RootEntity
import com.retroforge.core.ui.FocusChip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** A game's manual and gameplay video, where RetroBat and ES-DE keep them beside the games. */
data class GameMedia(val manual: Uri?, val video: Uri?)

/**
 * Looks for the game's manual (manuals/<game>-manual.pdf) and video (videos/<game>-video.mp4) in its system folder, as
 * RetroBat's scraper saves them, so they show without a rescan. Folders whose document ids aren't paths have none.
 */
suspend fun findGameMedia(resolver: ContentResolver, root: RootEntity?, game: GameEntity): GameMedia = withContext(Dispatchers.IO) {
    val tree = root?.treeUri?.let(Uri::parse) ?: return@withContext GameMedia(null, null)
    val id = game.documentId
    val parent = when {
        '/' in id -> id.substringBeforeLast('/')
        ':' in id -> id.substringBefore(':') + ":"
        else -> return@withContext GameMedia(null, null)
    }
    val base = game.fileName.substringBeforeLast('.')
    fun child(path: String) = if (parent.endsWith(":")) parent + path else "$parent/$path"
    fun existing(paths: List<String>): Uri? = paths.asSequence().map { DocumentsContract.buildDocumentUriUsingTree(tree, child(it)) }
        .firstOrNull { uri -> runCatching { resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { it.moveToFirst() } }.getOrNull() == true }
    GameMedia(
        manual = existing(listOf("manuals/$base-manual.pdf", "manuals/$base.pdf")),
        video = existing(listOf("videos/$base-video.mp4", "videos/$base.mp4")),
    )
}

/** The game's video, silent and looping, like a frontend's game page. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class) // PlayerView.resizeMode
@Composable
fun GameVideo(uri: Uri, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val player = remember(uri) {
        ExoPlayer.Builder(context).build().apply {
            volume = 0f
            repeatMode = Player.REPEAT_MODE_ONE
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    AndroidView(
        factory = { PlayerView(it).apply { useController = false; resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT; this.player = player } },
        modifier = modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(androidx.compose.material3.MaterialTheme.shapes.large).background(Color.Black),
    )
}

/** The manual's pages, one under the other, drawn by Android's own PDF renderer (no PDF app needed). */
@Composable
fun ManualViewer(uri: Uri, title: String, onClose: () -> Unit) {
    val context = LocalContext.current
    val opened by produceState<Pair<PdfRenderer, android.os.ParcelFileDescriptor>?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val fd = context.contentResolver.openFileDescriptor(uri, "r") ?: return@runCatching null
                PdfRenderer(fd) to fd
            }.getOrNull()
        }
    }
    // Closes the document this effect saw (not whatever `opened` holds when it ends).
    DisposableEffect(opened) {
        val document = opened
        onDispose { document?.let { (r, fd) -> runCatching { r.close(); fd.close() } } }
    }
    // PdfRenderer draws one page at a time.
    val lock = remember { Mutex() }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color(0xFF111114))) {
            val renderer = opened?.first
            if (renderer == null) {
                CardBody(stringResource(R.string.game_extras_opening_the_manual_of, title))
            } else {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val widthPx = with(LocalDensity.current) { maxWidth.toPx().toInt() }
                    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(renderer.pageCount) { index ->
                            val page by produceState<Bitmap?>(null, index) {
                                value = withContext(Dispatchers.IO) {
                                    lock.withLock {
                                        runCatching {
                                            renderer.openPage(index).use { p ->
                                                val h = (widthPx.toFloat() * p.height / p.width).toInt().coerceAtLeast(1)
                                                Bitmap.createBitmap(widthPx, h, Bitmap.Config.ARGB_8888).also { b ->
                                                    b.eraseColor(AndroidColor.WHITE)
                                                    p.render(b, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                                }
                                            }
                                        }.getOrNull()
                                    }
                                }
                            }
                            page?.let { Image(it.asImageBitmap(), contentDescription = "Page ${index + 1}", contentScale = ContentScale.FillWidth, modifier = Modifier.fillMaxWidth()) }
                                ?: Box(Modifier.fillMaxWidth().aspectRatio(0.7f).background(Color(0xFF222228)))
                        }
                    }
                }
            }
            Box(Modifier.align(Alignment.TopEnd).padding(12.dp)) { FocusChip(stringResource(R.string.game_extras_close_manual), onClick = onClose) }
        }
    }
}
