package com.retroforge.app.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import com.retroforge.app.R
import com.retroforge.app.ui.media.MusicService
import androidx.compose.ui.res.stringResource

/**
 * The user's own music, from the Music tab, in the pause menu: what's playing, with Previous, Play/Pause and Next, so
 * a song can be skipped without leaving the game. Nothing shows while no music is loaded.
 */
@Composable
internal fun PauseMusic(modifier: Modifier = Modifier) {
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
    @Suppress("UNUSED_EXPRESSION") tick // read so the strip redraws on every change
    val meta = player.currentMediaItem?.mediaMetadata ?: return
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            listOfNotNull(meta.title?.toString(), meta.artist?.toString()).joinToString(" · ").ifEmpty { stringResource(R.string.music_now_playing) },
            color = MenuCyan, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val pad = PaddingValues(horizontal = 6.dp, vertical = 6.dp)
            val previous = stringResource(R.string.music_previous)
            val toggle = stringResource(if (player.isPlaying) R.string.music_pause else R.string.music_play)
            val next = stringResource(R.string.music_next)
            MenuButton(onClick = { player.seekToPrevious() }, Modifier.weight(1f).semantics { contentDescription = previous }, pad) { Text("⏮", fontSize = 16.sp) }
            MenuButton(onClick = { if (player.isPlaying) player.pause() else player.play() }, Modifier.weight(1f).semantics { contentDescription = toggle }, pad) {
                Text(if (player.isPlaying) "⏸" else "▶", fontSize = 16.sp)
            }
            MenuButton(onClick = { player.seekToNext() }, Modifier.weight(1f).semantics { contentDescription = next }, pad) { Text("⏭", fontSize = 16.sp) }
        }
    }
}
