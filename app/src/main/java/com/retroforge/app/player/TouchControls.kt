package com.retroforge.app.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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

/** Height the controls need, to decide whether the [compact] layout is needed to give the picture room. */
fun touchControlsHeight(layout: PadLayout, compact: Boolean) = when {
    compact -> 200.dp
    layout.shoulders.isEmpty() -> 290.dp
    else -> 350.dp
}

/**
 * Transparent touch controls: d-pad left, face buttons right, shoulders on top, Start/Select in the middle.
 * [compact] puts everything in one band (shoulders above the d-pad and buttons, Start/Select stacked in the
 * middle) for tall pictures like the DS's, so the picture can stay full width.
 * [landscape] keeps everything in the two side columns (shoulders top, Select/menu and Start bottom), so nothing
 * covers the middle of the picture, such as the DS's touchscreen.
 * Each control keeps its own pressed state, so several can be held at once.
 */
@Composable
fun TouchControls(
    layout: PadLayout,
    onMask: (Int) -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    landscape: Boolean = false,
) {
    val parts = remember { IntArray(16) }
    val mask = remember { mutableIntStateOf(0) }
    fun set(slot: Int, value: Int) {
        if (parts[slot] == value) return
        parts[slot] = value
        mask.intValue = parts.fold(0) { acc, part -> acc or part }
        onMask(mask.intValue)
    }

    if (landscape) {
        // Sized from the screen height (about a phone's 410 dp in landscape = 1x), smaller than in portrait and
        // fainter, since they sit over the picture.
        BoxWithConstraints(modifier) {
            val k = (maxHeight / 410.dp).coerceIn(0.7f, 1.3f)
            Row(Modifier.fillMaxSize().padding(horizontal = 8.dp * k, vertical = 8.dp * k), horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.fillMaxHeight(), verticalArrangement = Arrangement.SpaceBetween) {
                    layout.shoulders.getOrNull(0)?.let { (label, button) ->
                        PadButton(label, RoundedCornerShape(10.dp), 60.dp * k, 28.dp * k, small = true, faint = true, role = ButtonRole.SHOULDER) { down -> set(10, if (down) Pad.bit(button) else 0) }
                    } ?: Spacer(Modifier.height(32.dp * k))
                    DPad(116.dp * k, faint = true) { set(0, it) }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp * k), verticalAlignment = Alignment.CenterVertically) {
                        if (layout.hasSelect) PadButton("Select", RoundedCornerShape(50), 60.dp * k, 26.dp * k, small = true, faint = true, role = ButtonRole.PILL) { set(8, if (it) Pad.bit(Pad.SELECT) else 0) }
                        PadButton("☰", CircleShape, 32.dp * k, 32.dp * k, small = true, faint = true, role = ButtonRole.PILL) { if (it) onMenu() }
                    }
                }
                Column(Modifier.fillMaxHeight(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.SpaceBetween) {
                    layout.shoulders.getOrNull(1)?.let { (label, button) ->
                        PadButton(label, RoundedCornerShape(10.dp), 60.dp * k, 28.dp * k, small = true, faint = true, role = ButtonRole.SHOULDER) { down -> set(11, if (down) Pad.bit(button) else 0) }
                    } ?: Spacer(Modifier.height(32.dp * k))
                    FaceButtons(layout.face, 42.dp * k, faint = true) { i, down -> set(1 + i, if (down) Pad.bit(layout.face[i].second) else 0) }
                    PadButton("Start", RoundedCornerShape(50), 60.dp * k, 26.dp * k, small = true, faint = true, role = ButtonRole.PILL) { set(9, if (it) Pad.bit(Pad.START) else 0) }
                }
            }
        }
        return
    }

    if (compact) {
        Row(modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                layout.shoulders.getOrNull(0)?.let { (label, button) ->
                    PadButton(label, RoundedCornerShape(10.dp), 64.dp, 32.dp, small = true, role = ButtonRole.SHOULDER) { down -> set(10, if (down) Pad.bit(button) else 0) }
                }
                DPad(132.dp) { set(0, it) }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (layout.hasSelect) PadButton("Select", RoundedCornerShape(50), 64.dp, 30.dp, small = true, role = ButtonRole.PILL) { set(8, if (it) Pad.bit(Pad.SELECT) else 0) }
                PadButton("Start", RoundedCornerShape(50), 64.dp, 30.dp, small = true, role = ButtonRole.PILL) { set(9, if (it) Pad.bit(Pad.START) else 0) }
                PadButton("☰", CircleShape, 36.dp, 36.dp, small = true, role = ButtonRole.PILL) { if (it) onMenu() }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                layout.shoulders.getOrNull(1)?.let { (label, button) ->
                    PadButton(label, RoundedCornerShape(10.dp), 64.dp, 32.dp, small = true, role = ButtonRole.SHOULDER) { down -> set(11, if (down) Pad.bit(button) else 0) }
                }
                FaceButtons(layout.face, 46.dp) { i, down -> set(1 + i, if (down) Pad.bit(layout.face[i].second) else 0) }
            }
        }
        return
    }

    Column(modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        if (layout.shoulders.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                layout.shoulders.forEachIndexed { i, (label, button) ->
                    PadButton(label, RoundedCornerShape(12.dp), 88.dp, 44.dp, role = ButtonRole.SHOULDER) { down -> set(10 + i, if (down) Pad.bit(button) else 0) }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            DPad(168.dp) { set(0, it) }
            FaceButtons(layout.face, 64.dp) { i, down -> set(1 + i, if (down) Pad.bit(layout.face[i].second) else 0) }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            if (layout.hasSelect) {
                PadButton("Select", RoundedCornerShape(50), 76.dp, 32.dp, small = true, role = ButtonRole.PILL) { set(8, if (it) Pad.bit(Pad.SELECT) else 0) }
                Spacer(Modifier.width(16.dp))
            }
            PadButton("Start", RoundedCornerShape(50), 76.dp, 32.dp, small = true, role = ButtonRole.PILL) { set(9, if (it) Pad.bit(Pad.START) else 0) }
            Spacer(Modifier.width(16.dp))
            PadButton("☰", CircleShape, 40.dp, 40.dp, small = true, role = ButtonRole.PILL) { if (it) onMenu() }
        }
    }
}

@Composable
private fun FaceButtons(face: List<Pair<String, Int>>, size: Dp, faint: Boolean = false, onPress: (Int, Boolean) -> Unit) {
    when (face.size) {
        4 -> Box(Modifier.size(size * 3)) {
            // SNES diamond: X top, Y left, A right, B bottom.
            val at = mapOf("X" to Alignment.TopCenter, "Y" to Alignment.CenterStart, "A" to Alignment.CenterEnd, "B" to Alignment.BottomCenter)
            face.forEachIndexed { i, (label, _) ->
                Box(Modifier.align(at[label] ?: Alignment.Center)) { PadButton(label, CircleShape, size, size, faint = faint) { onPress(i, it) } }
            }
        }
        else -> Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Bottom) {
            face.forEachIndexed { i, (label, _) ->
                // A slight upward step to the right, like a Game Boy's B/A.
                Box(Modifier.padding(bottom = size * (i * 0.28f))) { PadButton(label, CircleShape, size, size, faint = faint) { onPress(i, it) } }
            }
        }
    }
}

private enum class ButtonRole { FACE, SHOULDER, PILL }

@Composable
private fun PadButton(
    label: String,
    shape: Shape,
    width: Dp,
    height: Dp,
    small: Boolean = false,
    faint: Boolean = false,
    role: ButtonRole = ButtonRole.FACE,
    onPress: (Boolean) -> Unit,
) {
    val pressed = remember { mutableIntStateOf(0) }
    val skin = LocalConsoleSkin.current
    // With a console look: solid buttons in the pad's colours (a little see-through where they sit over the picture).
    val fill = when {
        skin == null -> Color(0x55FFFFFF)
        role == ButtonRole.PILL -> skin.pill
        role == ButtonRole.SHOULDER -> skin.shoulder
        else -> skin.face[label] ?: skin.button
    }
    val text = when {
        skin == null -> Color.White
        role == ButtonRole.PILL -> skin.pillLabel
        role == ButtonRole.FACE && label in skin.face -> Color.White
        else -> skin.label
    }
    val alpha = when {
        skin != null && pressed.intValue == 1 -> 0.7f
        skin != null -> if (faint) 0.8f else 1f
        pressed.intValue == 1 -> 0.85f
        faint -> 0.35f
        else -> 0.5f
    }
    Box(
        Modifier
            .size(width, height)
            .alpha(alpha)
            .background(fill, shape)
            .border(1.5.dp, if (skin == null) Color(0xAAFFFFFF) else Color(0x33000000), shape)
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
        Text(label, color = text, fontSize = if (small) 12.sp else 20.sp, fontWeight = FontWeight.Bold)
    }
}

/** Eight-way d-pad: the direction follows the finger while it's down, with a small dead zone in the middle. */
@Composable
private fun DPad(size: Dp, faint: Boolean = false, onMask: (Int) -> Unit) {
    val skin = LocalConsoleSkin.current
    Box(
        Modifier
            .size(size)
            .alpha(if (skin != null) (if (faint) 0.8f else 1f) else if (faint) 0.35f else 0.5f)
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
        val arm = Modifier.background(skin?.dpad ?: Color(0x55FFFFFF), RoundedCornerShape(10.dp))
            .border(1.5.dp, if (skin == null) Color(0xAAFFFFFF) else Color(0x33000000), RoundedCornerShape(10.dp))
        Box(arm.size(size / 3, size).align(Alignment.Center))
        Box(arm.size(size, size / 3).align(Alignment.Center))
    }
}
