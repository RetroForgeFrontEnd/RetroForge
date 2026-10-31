package com.retroforge.app.ui.media

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.retroforge.app.R
import com.retroforge.app.player.MenuButton
import com.retroforge.app.player.MenuCyan
import com.retroforge.app.player.MenuGrey
import com.retroforge.app.player.MenuOrange
import kotlinx.coroutines.delay

private val Panel = Color(0xE61C1C22)
private val PanelShape = RoundedCornerShape(16.dp)

/**
 * The film player's controls, in the game player's look: dark panels with an orange edge, cyan buttons, Play filled
 * orange. Tap the film (or press a controller button) to show them; they hide again after a few seconds of playing.
 */
@Composable
fun VideoControls(player: ExoPlayer, title: String, mode: ViewMode, shown: Boolean, onShown: (Boolean) -> Unit, onMode: () -> Unit, onClose: () -> Unit) {
    var position by remember { mutableLongStateOf(0L) }
    var length by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(player.isPlaying) }
    var hasSubtitles by remember { mutableStateOf(false) }
    var subtitlesOn by remember { mutableStateOf(true) }
    var dragging by remember { mutableStateOf<Float?>(null) }
    var showEq by remember { mutableStateOf(false) }
    // Bumped by every press, so the controls stay while they're being used.
    var used by remember { mutableIntStateOf(0) }
    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition
            length = player.duration.takeIf { it != C.TIME_UNSET } ?: 0L
            playing = player.isPlaying
            hasSubtitles = player.currentTracks.groups.any { it.type == C.TRACK_TYPE_TEXT }
            delay(250)
        }
    }
    LaunchedEffect(shown, used, playing) {
        if (shown && playing && !showEq) {
            delay(4000)
            onShown(false)
        }
    }
    val play = remember { FocusRequester() }
    LaunchedEffect(shown) { if (shown) runCatching { play.requestFocus() } }
    fun use(action: () -> Unit) { used++; action() }

    Box(
        Modifier.fillMaxSize().clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onShown(!shown) },
    ) {
        if (!shown) return@Box
        Row(
            Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(12.dp).widthIn(max = 720.dp).fillMaxWidth()
                .background(Panel, PanelShape).border(1.dp, MenuOrange.copy(alpha = 0.5f), PanelShape).padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(title, color = MenuOrange, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            MenuButton(onClick = { use(onMode) }) { Text(stringResource(R.string.video_view_mode, mode.label)) }
            TextButton(onClick = onClose) { Text("✕", color = MenuCyan, fontSize = 18.sp) }
        }
        Column(
            Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(12.dp).widthIn(max = 720.dp).fillMaxWidth()
                .background(Panel, PanelShape).border(1.dp, MenuOrange.copy(alpha = 0.5f), PanelShape).padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val shownAt = dragging ?: if (length > 0) position.toFloat() / length else 0f
            Slider(
                value = shownAt.coerceIn(0f, 1f),
                onValueChange = { used++; dragging = it },
                onValueChangeFinished = { dragging?.let { player.seekTo((it * length).toLong()) }; dragging = null },
                colors = SliderDefaults.colors(thumbColor = MenuOrange, activeTrackColor = MenuOrange, inactiveTrackColor = MenuGrey.copy(alpha = 0.35f)),
            )
            Row(Modifier.fillMaxWidth()) {
                Text(time((shownAt * length).toLong()), color = MenuCyan, fontSize = 13.sp)
                Box(Modifier.weight(1f))
                Text(time(length), color = MenuGrey, fontSize = 13.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                MenuButton(onClick = { use { player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0)) } }) { Text("−10s") }
                BigPlayButton(playing, onClick = { use { if (player.isPlaying) player.pause() else player.play() } }, modifier = Modifier.focusRequester(play), diameter = 64.dp)
                MenuButton(onClick = { use { player.seekTo(player.currentPosition + 30_000) } }) { Text("+30s") }
                if (hasSubtitles) MenuButton(onClick = {
                    use {
                        subtitlesOn = !subtitlesOn
                        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !subtitlesOn).build()
                    }
                }) { Text(stringResource(if (subtitlesOn) R.string.video_subtitles_on else R.string.video_subtitles_off)) }
                MenuButton(onClick = { use { showEq = !showEq } }) { Text((if (showEq) "● " else "") + stringResource(R.string.eq_title)) }
            }
            if (showEq) EqPanel()
        }
    }
}

/** 83 minutes → "1:23:00"; under an hour → "4:05". */
private fun time(ms: Long): String {
    val s = ms / 1000
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}
