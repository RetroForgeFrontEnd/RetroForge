package com.retroforge.app.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retroforge.player.Pad
import kotlin.math.abs

/** On-screen buttons per system, laid out like the original controller. */
data class PadLayout(
    val face: List<Pair<String, Int>>,
    val shoulders: List<Pair<String, Int>> = emptyList(),
    val hasSelect: Boolean = true,
) {
    companion object {
        private val twoButton = PadLayout(listOf("B" to Pad.B, "A" to Pad.A))

        fun forSystem(system: String): PadLayout = when (system) {
            "gba" -> PadLayout(listOf("B" to Pad.B, "A" to Pad.A), listOf("L" to Pad.L, "R" to Pad.R))
            "snes", "sfc", "nds" -> PadLayout(
                listOf("Y" to Pad.Y, "X" to Pad.X, "B" to Pad.B, "A" to Pad.A),
                listOf("L" to Pad.L, "R" to Pad.R),
            )
            // Genesis Plus GX: libretro Y, B, A are the Mega Drive's A, B, C.
            "megadrive", "genesis", "sega32x" -> PadLayout(listOf("A" to Pad.Y, "B" to Pad.B, "C" to Pad.A), hasSelect = false)
            "mastersystem", "sg1000" -> PadLayout(listOf("1" to Pad.B, "2" to Pad.A), hasSelect = false)
            "gamegear" -> PadLayout(listOf("1" to Pad.B, "2" to Pad.A), hasSelect = false)
            "pcengine" -> PadLayout(listOf("II" to Pad.B, "I" to Pad.A))
            "atari2600" -> PadLayout(listOf("Fire" to Pad.B))
            else -> twoButton
        }
    }
}

/**
 * Transparent touch controls: d-pad left, face buttons right, shoulders on top, Start/Select in the middle.
 * Each control keeps its own pressed state, so several can be held at once.
 */
@Composable
fun TouchControls(layout: PadLayout, onMask: (Int) -> Unit, onMenu: () -> Unit, modifier: Modifier = Modifier) {
    val parts = remember { IntArray(16) }
    val mask = remember { mutableIntStateOf(0) }
    fun set(slot: Int, value: Int) {
        if (parts[slot] == value) return
        parts[slot] = value
        mask.intValue = parts.fold(0) { acc, part -> acc or part }
        onMask(mask.intValue)
    }

    Column(modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (layout.shoulders.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                layout.shoulders.forEachIndexed { i, (label, button) ->
                    PadButton(label, RoundedCornerShape(12.dp), 88.dp, 44.dp) { down -> set(10 + i, if (down) Pad.bit(button) else 0) }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            DPad { set(0, it) }
            FaceButtons(layout.face) { i, down -> set(1 + i, if (down) Pad.bit(layout.face[i].second) else 0) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            if (layout.hasSelect) {
                PadButton("Select", RoundedCornerShape(50), 76.dp, 32.dp, small = true) { set(8, if (it) Pad.bit(Pad.SELECT) else 0) }
                Spacer(Modifier.width(16.dp))
            }
            PadButton("Start", RoundedCornerShape(50), 76.dp, 32.dp, small = true) { set(9, if (it) Pad.bit(Pad.START) else 0) }
            Spacer(Modifier.width(16.dp))
            PadButton("☰", CircleShape, 40.dp, 40.dp, small = true) { if (it) onMenu() }
        }
    }
}

@Composable
private fun FaceButtons(face: List<Pair<String, Int>>, onPress: (Int, Boolean) -> Unit) {
    val size = 64.dp
    when (face.size) {
        4 -> Box(Modifier.size(size * 3)) {
            // SNES diamond: X top, Y left, A right, B bottom.
            val at = mapOf("X" to Alignment.TopCenter, "Y" to Alignment.CenterStart, "A" to Alignment.CenterEnd, "B" to Alignment.BottomCenter)
            face.forEachIndexed { i, (label, _) ->
                Box(Modifier.align(at[label] ?: Alignment.Center)) { PadButton(label, CircleShape, size, size) { onPress(i, it) } }
            }
        }
        else -> Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Bottom) {
            face.forEachIndexed { i, (label, _) ->
                // A slight upward step to the right, like a Game Boy's B/A.
                Box(Modifier.padding(bottom = (i * 18).dp)) { PadButton(label, CircleShape, size, size) { onPress(i, it) } }
            }
        }
    }
}

@Composable
private fun PadButton(label: String, shape: Shape, width: Dp, height: Dp, small: Boolean = false, onPress: (Boolean) -> Unit) {
    val pressed = remember { mutableIntStateOf(0) }
    Box(
        Modifier
            .size(width, height)
            .alpha(if (pressed.intValue == 1) 0.85f else 0.5f)
            .background(Color(0x55FFFFFF), shape)
            .border(1.5.dp, Color(0xAAFFFFFF), shape)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    pressed.intValue = 1
                    onPress(true)
                    do {
                        val event = awaitPointerEvent()
                    } while (event.changes.any { it.pressed })
                    pressed.intValue = 0
                    onPress(false)
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontSize = if (small) 12.sp else 20.sp, fontWeight = FontWeight.Bold)
    }
}

/** Eight-way d-pad: the direction follows the finger while it's down, with a small dead zone in the middle. */
@Composable
private fun DPad(onMask: (Int) -> Unit) {
    val size = 168.dp
    Box(
        Modifier
            .size(size)
            .alpha(0.5f)
            .pointerInput(Unit) {
                fun directions(at: Offset): Int {
                    val x = (at.x - this.size.width / 2f) / (this.size.width / 2f)
                    val y = (at.y - this.size.height / 2f) / (this.size.height / 2f)
                    if (abs(x) < 0.2f && abs(y) < 0.2f) return 0
                    // A direction counts once it's at least ~40% of the stronger axis, giving diagonals a fair share.
                    val strong = maxOf(abs(x), abs(y))
                    var mask = 0
                    if (abs(x) >= strong * 0.4f) mask = mask or Pad.bit(if (x < 0) Pad.LEFT else Pad.RIGHT)
                    if (abs(y) >= strong * 0.4f) mask = mask or Pad.bit(if (y < 0) Pad.UP else Pad.DOWN)
                    return mask
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    onMask(directions(down.position))
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        onMask(directions(change.position))
                    }
                    onMask(0)
                }
            },
    ) {
        val arm = Modifier.background(Color(0x55FFFFFF), RoundedCornerShape(10.dp)).border(1.5.dp, Color(0xAAFFFFFF), RoundedCornerShape(10.dp))
        Box(arm.size(size / 3, size).align(Alignment.Center))
        Box(arm.size(size, size / 3).align(Alignment.Center))
    }
}
