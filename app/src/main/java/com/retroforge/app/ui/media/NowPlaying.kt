package com.retroforge.app.ui.media

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.C
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import com.retroforge.app.R
import com.retroforge.app.player.MenuButton
import com.retroforge.app.player.MenuCyan
import com.retroforge.app.player.MenuGrey
import com.retroforge.app.player.MenuOrange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The music player's own page, in the game player's look: the album art, moving bars drawn from the music itself,
 * where the song is, the controls and the EQ.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NowPlayingPage(player: Player, onClose: () -> Unit) {
    var tick by remember { mutableStateOf(0) }
    var position by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf<Float?>(null) }
    var showEq by remember { mutableStateOf(false) }
    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition
            tick++
            delay(250)
        }
    }
    @Suppress("UNUSED_EXPRESSION") tick // read so the page redraws as the song plays
    val meta = player.mediaMetadata
    val length = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        MaterialTheme(colorScheme = darkColorScheme(primary = MenuCyan)) {
            Box(Modifier.fillMaxSize().background(Color(0xFF0B1020)), contentAlignment = Alignment.TopCenter) {
                Column(
                    Modifier.safeDrawingPadding().widthIn(max = 640.dp).fillMaxWidth().verticalScroll(rememberScrollState()).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.music_now_playing), color = MenuOrange, fontWeight = FontWeight.Bold, fontSize = 17.sp, modifier = Modifier.weight(1f))
                        TextButton(onClick = onClose) { Text("✕", color = MenuCyan, fontSize = 20.sp) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        val art = meta.artworkData
                        Box(Modifier.width(140.dp).aspectRatio(1f).clip(RoundedCornerShape(12.dp)).border(1.dp, MenuOrange.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                            .background(Color(0xFF1C1C22)), contentAlignment = Alignment.Center) {
                            Text("♪", color = MenuOrange, fontSize = 48.sp)
                            val bitmap = remember(art) { art?.let { runCatching { BitmapFactory.decodeByteArray(it, 0, it.size) }.getOrNull() } }
                            if (bitmap != null) Image(bitmap.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                            else meta.artworkUri?.let { AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                        }
                        Column(Modifier.weight(1f)) {
                            Text(meta.title?.toString().orEmpty(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            meta.artist?.let { Text(it.toString(), color = MenuCyan, fontSize = 15.sp, maxLines = 2) }
                            meta.albumTitle?.let { Text(it.toString(), color = MenuGrey, fontSize = 13.sp, maxLines = 2) }
                        }
                    }
                    Visuals(player.isPlaying, Modifier.fillMaxWidth().height(160.dp))
                    val at = dragging ?: if (length > 0) position.toFloat() / length else 0f
                    Slider(
                        value = at.coerceIn(0f, 1f),
                        onValueChange = { dragging = it },
                        onValueChangeFinished = { dragging?.let { player.seekTo((it * length).toLong()) }; dragging = null },
                        colors = SliderDefaults.colors(thumbColor = MenuOrange, activeTrackColor = MenuOrange, inactiveTrackColor = MenuGrey.copy(alpha = 0.35f)),
                    )
                    Row(Modifier.fillMaxWidth()) {
                        Text(clock((at * length).toLong()), color = MenuCyan, fontSize = 13.sp)
                        Box(Modifier.weight(1f))
                        Text(clock(length), color = MenuGrey, fontSize = 13.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        RoundButton(RoundIcon.Previous, stringResource(R.string.music_previous), onClick = { player.seekToPrevious() })
                        BigPlayButton(player.isPlaying, onClick = { if (player.isPlaying) player.pause() else player.play() })
                        RoundButton(RoundIcon.Next, stringResource(R.string.music_next), onClick = { player.seekToNext() })
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        MenuButton(onClick = { player.shuffleModeEnabled = !player.shuffleModeEnabled }) {
                            Text((if (player.shuffleModeEnabled) "● " else "") + stringResource(R.string.music_shuffle))
                        }
                        MenuButton(onClick = { player.repeatMode = if (player.repeatMode == Player.REPEAT_MODE_OFF) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF }) {
                            Text((if (player.repeatMode != Player.REPEAT_MODE_OFF) "● " else "") + stringResource(R.string.music_repeat))
                        }
                        MenuButton(onClick = { showEq = !showEq }) { Text((if (showEq) "● " else "") + stringResource(R.string.eq_title)) }
                    }
                    if (showEq) Box(
                        Modifier.fillMaxWidth().background(Color(0xFF1C1C22), RoundedCornerShape(16.dp)).border(1.dp, MenuOrange.copy(alpha = 0.5f), RoundedCornerShape(16.dp)).padding(12.dp),
                    ) { EqPanel() }
                }
            }
        }
    }
}

/** The players' big round Play / Pause: orange, drawn (not an emoji, which phones colour in their own way), white ring when focused. */
@Composable
fun BigPlayButton(playing: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, diameter: androidx.compose.ui.unit.Dp = 84.dp) =
    RoundButton(if (playing) RoundIcon.Pause else RoundIcon.Play, stringResource(if (playing) R.string.music_pause else R.string.play_play),
        onClick, modifier, diameter, filled = true)

enum class RoundIcon { Play, Pause, Previous, Next }

/**
 * The players' round buttons, with drawn icons (not emoji, which phones colour in their own way). [filled] is the big
 * orange one (Play / Pause); the others are dark with an orange ring and icon, so Play still stands out. White ring when focused.
 */
@Composable
fun RoundButton(icon: RoundIcon, label: String, onClick: () -> Unit, modifier: Modifier = Modifier, diameter: androidx.compose.ui.unit.Dp = 60.dp, filled: Boolean = false) {
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val circle = androidx.compose.foundation.shape.CircleShape
    Box(
        modifier.size(diameter).clip(circle).background(if (filled) MenuOrange else Color(0xFF1C1C22))
            .border(if (focused) 4.dp else 2.dp, if (focused) Color.White else if (filled) MenuOrange else MenuOrange.copy(alpha = 0.7f), circle)
            .clickable(interactionSource = interaction, indication = androidx.compose.material3.ripple(), onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(diameter * 0.4f)) {
            val ink = if (filled) Color(0xFF1C1C22) else MenuOrange
            val w = size.width
            val h = size.height
            fun triangle(fromX: Float, toX: Float) = drawPath(androidx.compose.ui.graphics.Path().apply {
                moveTo(fromX, 0f)
                lineTo(toX, h / 2)
                lineTo(fromX, h)
                close()
            }, ink)
            when (icon) {
                RoundIcon.Play -> triangle(w * 0.18f, w)
                RoundIcon.Pause -> {
                    drawRoundRect(ink, Offset(w * 0.12f, 0f), Size(w * 0.3f, h), CornerRadius(3.dp.toPx()))
                    drawRoundRect(ink, Offset(w * 0.58f, 0f), Size(w * 0.3f, h), CornerRadius(3.dp.toPx()))
                }
                // A bar, then a triangle pointing at it (|◀) or away from it (▶|).
                RoundIcon.Previous -> {
                    drawRoundRect(ink, Offset(0f, 0f), Size(w * 0.18f, h), CornerRadius(2.dp.toPx()))
                    triangle(w, w * 0.18f)
                }
                RoundIcon.Next -> {
                    triangle(0f, w * 0.82f)
                    drawRoundRect(ink, Offset(w * 0.82f, 0f), Size(w * 0.18f, h), CornerRadius(2.dp.toPx()))
                }
            }
        }
    }
}

/** Bars that move with the music, orange at the bottom turning cyan at the top, with peaks that fall slowly. */
@Composable
fun Visuals(playing: Boolean, modifier: Modifier) {
    val count = 32
    val shown = remember { FloatArray(count) }
    val peaks = remember { FloatArray(count) }
    val samples = remember { FloatArray(MusicTap.SIZE) }
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(playing) {
        while (true) {
            withFrameNanos { now ->
                if (playing) MusicTap.snapshot(samples) else samples.fill(0f)
                val target = Spectrum.bars(samples, count)
                for (i in 0 until count) {
                    // Up at once, down gently, so the bars dance rather than flicker.
                    shown[i] = if (target[i] > shown[i]) target[i] else shown[i] * 0.88f + target[i] * 0.12f
                    peaks[i] = maxOf(shown[i], peaks[i] - 0.012f)
                }
                frame = now
            }
        }
    }
    val brush = remember { Brush.verticalGradient(listOf(MenuCyan, MenuOrange)) }
    Canvas(modifier.clip(RoundedCornerShape(12.dp)).background(Color(0xFF1C1C22))) {
        @Suppress("UNUSED_EXPRESSION") frame // read so it draws every frame
        val gap = 3.dp.toPx()
        val w = (size.width - gap * (count + 1)) / count
        for (i in 0 until count) {
            val x = gap + i * (w + gap)
            val h = (shown[i] * size.height * 0.92f).coerceAtLeast(2.dp.toPx())
            drawRoundRect(brush, Offset(x, size.height - h), Size(w, h), CornerRadius(3.dp.toPx()))
            val py = size.height - peaks[i] * size.height * 0.92f - 3.dp.toPx()
            drawRect(MenuCyan, Offset(x, py), Size(w, 2.dp.toPx()))
        }
    }
}

private fun clock(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

/** Album art saved inside a song's tags (a JPG or PNG), read once and kept; null when it has none. */
object MusicArt {
    suspend fun embedded(context: Context, uri: String): File? = withContext(Dispatchers.IO) {
        val file = File(context.cacheDir, "music-art/${uri.hashCode().toUInt().toString(16)}")
        if (file.exists()) return@withContext file.takeIf { it.length() > 0 }
        val bytes = runCatching {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(context, android.net.Uri.parse(uri))
                r.embeddedPicture
            } finally {
                r.release()
            }
        }.getOrNull()
        file.parentFile?.mkdirs()
        file.writeBytes(bytes ?: ByteArray(0)) // an empty file: looked, none
        if (bytes != null) file else null
    }
}

/** An album's art: its folder's cover image, else the art in its first song's tags. */
@Composable
fun rememberAlbumArt(album: Album): Any? {
    val context = LocalContext.current
    val embedded by produceState<File?>(null, album.key) {
        if (album.art == null) value = album.tracks.firstOrNull()?.let { MusicArt.embedded(context, it.uri) }
    }
    return album.art ?: embedded
}
