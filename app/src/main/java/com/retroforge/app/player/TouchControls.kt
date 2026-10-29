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
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.draw.shadow
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.offset
import kotlinx.coroutines.launch
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
    /** Second shoulder pair (L2/R2), beside the first. */
    val triggers: List<Pair<String, Int>> = emptyList(),
    /** Analog thumbsticks; a quick tap on one is L3/R3. */
    val sticks: Boolean = false,
    /** False for one-stick controllers (Dreamcast, PSP). */
    val rightStick: Boolean = true,
    /** Printed on the right stick when it stands for buttons (the N64's C buttons). */
    val rightStickLabel: String? = null,
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
            "megadrive", "genesis", "sega32x", "megacd", "segacd" -> PadLayout(listOf("A" to Pad.Y, "B" to Pad.B, "C" to Pad.A), hasSelect = false)
            // Saturn (Yabause and Beetle Saturn): X Y Z over A B C are libretro Y X L over B A R; the Saturn's L/R are L2/R2.
            "saturn" -> PadLayout(
                listOf("X" to Pad.Y, "Y" to Pad.X, "Z" to Pad.L, "A" to Pad.B, "B" to Pad.A, "C" to Pad.R),
                listOf("L" to Pad.L2, "R" to Pad.R2),
                hasSelect = false,
            )
            // N64 (Mupen64Plus-Next): A/B are libretro B/Y, Z is L2, C buttons are the right stick.
            "n64" -> PadLayout(
                listOf("B" to Pad.Y, "A" to Pad.B),
                listOf("L" to Pad.L, "R" to Pad.R),
                triggers = listOf("Z" to Pad.L2),
                hasSelect = false,
                sticks = true,
                rightStickLabel = "C",
            )
            // PSP (PPSSPP): PlayStation symbols, L/R and the analog stick.
            "psp" -> PadLayout(
                listOf("□" to Pad.Y, "△" to Pad.X, "✕" to Pad.B, "○" to Pad.A),
                listOf("L" to Pad.L, "R" to Pad.R),
                sticks = true,
                rightStick = false,
            )
            // Dreamcast (Flycast): Y top, X left, B right, A bottom; the analog triggers are L2/R2.
            "dreamcast" -> PadLayout(
                listOf("X" to Pad.Y, "Y" to Pad.X, "A" to Pad.B, "B" to Pad.A),
                triggers = listOf("L" to Pad.L2, "R" to Pad.R2),
                hasSelect = false,
                sticks = true,
                rightStick = false,
            )
            "naomi", "naomi2", "atomiswave" -> PadLayout(
                listOf("3" to Pad.Y, "4" to Pad.X, "1" to Pad.B, "2" to Pad.A),
                listOf("5" to Pad.L, "6" to Pad.R),
            )
            // Virtual Boy: A/B and the shoulder buttons (the second d-pad isn't on screen).
            "virtualboy" -> PadLayout(listOf("B" to Pad.B, "A" to Pad.A), listOf("L" to Pad.L, "R" to Pad.R))
            "mastersystem", "sg1000" -> PadLayout(listOf("1" to Pad.B, "2" to Pad.A), hasSelect = false)
            "gamegear" -> PadLayout(listOf("1" to Pad.B, "2" to Pad.A), hasSelect = false)
            "pcengine", "pcenginecd" -> PadLayout(listOf("II" to Pad.B, "I" to Pad.A))
            // Arcade (FinalBurn Neo / MAME): four buttons, Coin (Select) and Start.
            "fbneo", "neogeo", "cps1", "cps2", "cps3", "mame" -> PadLayout(
                listOf("Y" to Pad.Y, "X" to Pad.X, "B" to Pad.B, "A" to Pad.A),
                listOf("L" to Pad.L, "R" to Pad.R),
            )
            "atari2600" -> PadLayout(listOf("Fire" to Pad.B))
            // PlayStation: △ top, ○ right, ✕ bottom, □ left (libretro X, A, B, Y), L1/R1, L2/R2 and both sticks.
            "psx" -> PadLayout(
                listOf("□" to Pad.Y, "△" to Pad.X, "✕" to Pad.B, "○" to Pad.A),
                listOf("L1" to Pad.L, "R1" to Pad.R),
                triggers = listOf("L2" to Pad.L2, "R2" to Pad.R2),
                sticks = true,
            )
            else -> twoButton
        }
    }
}

/** Height the controls need, to decide whether the [compact] layout is needed to give the picture room. */
fun touchControlsHeight(layout: PadLayout, compact: Boolean) = when {
    compact -> 200.dp
    layout.sticks -> 470.dp
    layout.shoulders.isEmpty() && layout.triggers.isEmpty() -> 290.dp
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
    onAnalog: (stick: Int, x: Float, y: Float) -> Unit = { _, _, _ -> },
) {
    val parts = remember { IntArray(20) }
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
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp * k)) {
                        layout.triggers.getOrNull(0)?.let { (label, button) ->
                            PadButton(label, RoundedCornerShape(10.dp), 52.dp * k, 28.dp * k, small = true, faint = true, role = ButtonRole.SHOULDER) { down -> set(12, if (down) Pad.bit(button) else 0) }
                        }
                        layout.shoulders.getOrNull(0)?.let { (label, button) ->
                            PadButton(label, RoundedCornerShape(10.dp), if (layout.triggers.isEmpty()) 60.dp * k else 52.dp * k, 28.dp * k, small = true, faint = true, role = ButtonRole.SHOULDER) { down -> set(10, if (down) Pad.bit(button) else 0) }
                        } ?: Spacer(Modifier.height(32.dp * k))
                    }
                    DPad(if (layout.sticks) 104.dp * k else if (layout.face.size <= 3) 132.dp * k else 116.dp * k, faint = true) { set(0, it) }
                    if (layout.sticks) Stick(84.dp * k, faint = true, onMove = { x, y -> onAnalog(0, x, y) }) { set(14, if (it) Pad.bit(Pad.L3) else 0) }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp * k), verticalAlignment = Alignment.CenterVertically) {
                        if (layout.hasSelect) PadButton("Select", RoundedCornerShape(50), 60.dp * k, 26.dp * k, small = true, faint = true, role = ButtonRole.PILL) { set(8, if (it) Pad.bit(Pad.SELECT) else 0) }
                        PadButton("☰", CircleShape, 32.dp * k, 32.dp * k, small = true, faint = true, role = ButtonRole.PILL) { if (it) onMenu() }
                    }
                }
                Column(Modifier.fillMaxHeight(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.SpaceBetween) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp * k)) {
                        layout.shoulders.getOrNull(1)?.let { (label, button) ->
                            PadButton(label, RoundedCornerShape(10.dp), if (layout.triggers.isEmpty()) 60.dp * k else 52.dp * k, 28.dp * k, small = true, faint = true, role = ButtonRole.SHOULDER) { down -> set(11, if (down) Pad.bit(button) else 0) }
                        } ?: Spacer(Modifier.height(32.dp * k))
                        layout.triggers.getOrNull(1)?.let { (label, button) ->
                            PadButton(label, RoundedCornerShape(10.dp), 52.dp * k, 28.dp * k, small = true, faint = true, role = ButtonRole.SHOULDER) { down -> set(13, if (down) Pad.bit(button) else 0) }
                        }
                    }
                    FaceButtons(
                        layout.face,
                        // Fewer buttons get bigger ones (GBA's two are easy to miss at the four-button size).
                        when {
                            layout.sticks -> 38.dp * k
                            layout.face.size <= 2 -> 58.dp * k
                            layout.face.size == 3 -> 50.dp * k
                            else -> 42.dp * k
                        },
                        faint = true,
                    ) { i, down -> set(1 + i, if (down) Pad.bit(layout.face[i].second) else 0) }
                    if (layout.sticks && layout.rightStick) Stick(84.dp * k, faint = true, label = layout.rightStickLabel, onMove = { x, y -> onAnalog(1, x, y) }) { set(15, if (it) Pad.bit(Pad.R3) else 0) }
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

    Column(modifier.padding(horizontal = 20.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) Column@{
        if (layout.shoulders.isNotEmpty() || layout.triggers.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    layout.triggers.getOrNull(0)?.let { (label, button) ->
                        PadButton(label, RoundedCornerShape(12.dp), 72.dp, 40.dp, role = ButtonRole.SHOULDER) { down -> set(12, if (down) Pad.bit(button) else 0) }
                    }
                    layout.shoulders.getOrNull(0)?.let { (label, button) ->
                        PadButton(label, RoundedCornerShape(12.dp), if (layout.triggers.isEmpty()) 88.dp else 72.dp, if (layout.triggers.isEmpty()) 44.dp else 40.dp, role = ButtonRole.SHOULDER) { down -> set(10, if (down) Pad.bit(button) else 0) }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    layout.shoulders.getOrNull(1)?.let { (label, button) ->
                        PadButton(label, RoundedCornerShape(12.dp), if (layout.triggers.isEmpty()) 88.dp else 72.dp, if (layout.triggers.isEmpty()) 44.dp else 40.dp, role = ButtonRole.SHOULDER) { down -> set(11, if (down) Pad.bit(button) else 0) }
                    }
                    layout.triggers.getOrNull(1)?.let { (label, button) ->
                        PadButton(label, RoundedCornerShape(12.dp), 72.dp, 40.dp, role = ButtonRole.SHOULDER) { down -> set(13, if (down) Pad.bit(button) else 0) }
                    }
                }
            }
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // Narrow phones: shrink the d-pad and buttons together so a row of three (Mega Drive) keeps a gap from the d-pad.
            val n = layout.face.size
            val faceSpan = when (n) { 4 -> 3f; 6 -> 3.3f; else -> n.toFloat() }
            val gaps = when (n) { 4 -> 0.dp; 6 -> 28.dp; else -> 14.dp * (n - 1) }
            val k = ((maxWidth - gaps - 24.dp) / (168.dp + 64.dp * faceSpan)).coerceAtMost(1f)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                DPad(168.dp * k) { set(0, it) }
                FaceButtons(layout.face, 64.dp * k) { i, down -> set(1 + i, if (down) Pad.bit(layout.face[i].second) else 0) }
            }
        }
        if (layout.sticks) {
            // DualShock-style: sticks below the d-pad and the buttons, Select / Start / menu between them.
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Stick(112.dp, onMove = { x, y -> onAnalog(0, x, y) }) { set(14, if (it) Pad.bit(Pad.L3) else 0) }
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (layout.hasSelect) PadButton("Select", RoundedCornerShape(50), 68.dp, 30.dp, small = true, role = ButtonRole.PILL) { set(8, if (it) Pad.bit(Pad.SELECT) else 0) }
                    PadButton("Start", RoundedCornerShape(50), 68.dp, 30.dp, small = true, role = ButtonRole.PILL) { set(9, if (it) Pad.bit(Pad.START) else 0) }
                    PadButton("☰", CircleShape, 36.dp, 36.dp, small = true, role = ButtonRole.PILL) { if (it) onMenu() }
                }
                if (layout.rightStick) Stick(112.dp, label = layout.rightStickLabel, onMove = { x, y -> onAnalog(1, x, y) }) { set(15, if (it) Pad.bit(Pad.R3) else 0) }
                else Spacer(Modifier.size(112.dp))
            }
            return@Column
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
            // Diamond by button position (libretro/SNES): X top, Y left, A right, B bottom. PlayStation: △ ○ ✕ □.
            val at = mapOf(Pad.X to Alignment.TopCenter, Pad.Y to Alignment.CenterStart, Pad.A to Alignment.CenterEnd, Pad.B to Alignment.BottomCenter)
            face.forEachIndexed { i, (label, button) ->
                Box(Modifier.align(at[button] ?: Alignment.Center)) { PadButton(label, CircleShape, size, size, faint = faint) { onPress(i, it) } }
            }
        }
        // Six-button pads (Saturn): a row of three over a row of three, the bottom row nudged right like the real pad.
        6 -> Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(0..2, 3..5).forEach { row ->
                Row(Modifier.padding(start = if (row.first == 3) size * 0.3f else 0.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    row.forEach { i ->
                        val (label, _) = face[i]
                        PadButton(label, CircleShape, size, size, faint = faint) { onPress(i, it) }
                    }
                }
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
        role == ButtonRole.FACE && label in skin.faceText -> skin.faceText.getValue(label)
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
    val down = pressed.intValue == 1
    Box(
        Modifier
            .size(width, height)
            .alpha(alpha)
            // Console look: a soft shadow and a lit-from-above gradient, flatter while pressed.
            .then(if (skin != null) Modifier.shadow(if (down) 1.dp else 5.dp, shape, clip = false) else Modifier)
            .background(if (skin != null) Brush.verticalGradient(if (down) listOf(fill.shade(-0.1f), fill.shade(0.06f)) else listOf(fill.shade(0.16f), fill.shade(-0.12f))) else SolidColor(fill), shape)
            .border(if (skin != null) 1.dp else 1.5.dp, if (skin == null) Color(0xAAFFFFFF) else Color(0x26FFFFFF), shape)
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
        if (label in PLAYSTATION_SYMBOLS) {
            Symbol(label, text, Modifier.fillMaxSize(0.42f))
        } else {
            Text(label, color = text, fontSize = if (small) 12.sp else 20.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private val PLAYSTATION_SYMBOLS = setOf("△", "○", "✕", "□")

/** The PlayStation face symbols drawn as bold shapes (a font's thin glyphs look flimsy at this size). */
@Composable
private fun Symbol(label: String, color: Color, modifier: Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        val stroke = androidx.compose.ui.graphics.drawscope.Stroke(width = size.minDimension * 0.13f, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val w = size.width
        val h = size.height
        when (label) {
            "○" -> drawCircle(color, radius = size.minDimension * 0.42f, style = stroke)
            "□" -> drawRect(color, topLeft = Offset(w * 0.12f, h * 0.12f), size = androidx.compose.ui.geometry.Size(w * 0.76f, h * 0.76f), style = stroke)
            "△" -> drawPath(Path().apply { moveTo(w * 0.5f, h * 0.08f); lineTo(w * 0.94f, h * 0.86f); lineTo(w * 0.06f, h * 0.86f); close() }, color, style = stroke)
            "✕" -> {
                drawLine(color, Offset(w * 0.12f, h * 0.12f), Offset(w * 0.88f, h * 0.88f), stroke.width, StrokeCap.Round)
                drawLine(color, Offset(w * 0.88f, h * 0.12f), Offset(w * 0.12f, h * 0.88f), stroke.width, StrokeCap.Round)
            }
        }
    }
}

/** Lighter (positive) or darker (negative) by [amount] (0..1). */
private fun Color.shade(amount: Float) = androidx.compose.ui.graphics.lerp(this, if (amount > 0) Color.White else Color.Black, abs(amount))

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
        if (skin == null) {
            val arm = Modifier.background(Color(0x55FFFFFF), RoundedCornerShape(10.dp)).border(1.5.dp, Color(0xAAFFFFFF), RoundedCornerShape(10.dp))
            Box(arm.size(size / 3, size).align(Alignment.Center))
            Box(arm.size(size, size / 3).align(Alignment.Center))
        } else {
            // Console look: four arms with depth and arrows; split into separate pads where the console's were (PlayStation).
            androidx.compose.foundation.Canvas(Modifier.fillMaxSize().shadow(0.dp)) {
                val s = this.size.minDimension
                val armW = s / 3f
                val gap = if (skin.splitDpad) s * 0.06f else 0f
                val corner = androidx.compose.ui.geometry.CornerRadius(s * 0.06f)
                val top = skin.dpad.shade(0.18f)
                val bottom = skin.dpad.shade(-0.14f)
                fun arm(x: Float, y: Float, aw: Float, ah: Float) {
                    drawRoundRect(Color.Black.copy(alpha = 0.35f), Offset(x, y + s * 0.02f), androidx.compose.ui.geometry.Size(aw, ah), corner)
                    drawRoundRect(Brush.verticalGradient(listOf(top, bottom), startY = y, endY = y + ah), Offset(x, y), androidx.compose.ui.geometry.Size(aw, ah), corner)
                }
                if (skin.splitDpad) {
                    val len = s / 2f - armW / 2f - gap
                    arm(armW, 0f, armW, len)                       // up
                    arm(armW, s - len, armW, len)                  // down
                    arm(0f, armW, len, armW)                       // left
                    arm(s - len, armW, len, armW)                  // right
                } else {
                    arm(armW, 0f, armW, s)
                    arm(0f, armW, s, armW)
                    drawCircle(bottom, radius = armW * 0.22f, center = Offset(s / 2f, s / 2f))
                }
                val arrow = skin.label.copy(alpha = 0.55f)
                val a = armW * 0.22f
                fun tri(cx: Float, cy: Float, dx: Float, dy: Float) {
                    drawPath(Path().apply {
                        moveTo(cx + dx * a, cy + dy * a)
                        lineTo(cx - dx * a * 0.6f + dy * a, cy - dy * a * 0.6f + dx * a)
                        lineTo(cx - dx * a * 0.6f - dy * a, cy - dy * a * 0.6f - dx * a)
                        close()
                    }, arrow)
                }
                tri(s / 2f, armW * 0.55f, 0f, -1f)
                tri(s / 2f, s - armW * 0.55f, 0f, 1f)
                tri(armW * 0.55f, s / 2f, -1f, 0f)
                tri(s - armW * 0.55f, s / 2f, 1f, 0f)
            }
        }
    }
}

/**
 * A floating analog stick: it centres where the thumb lands, follows it up to the rim and springs back on release.
 * Reports -1..1 on each axis; a quick tap without moving is a stick click (L3/R3).
 */
@Composable
private fun Stick(size: Dp, faint: Boolean = false, label: String? = null, onMove: (Float, Float) -> Unit, onClick: (Boolean) -> Unit) {
    val skin = LocalConsoleSkin.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var knob by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(Offset.Zero) }
    val base = skin?.button ?: Color(0x55FFFFFF)
    Box(
        Modifier
            .size(size)
            .alpha(if (skin != null) (if (faint) 0.8f else 1f) else if (faint) 0.35f else 0.5f)
            .background(
                if (skin != null) Brush.radialGradient(listOf(base.shade(-0.35f), base.shade(-0.15f), base.shade(0.05f))) else SolidColor(base.copy(alpha = 0.35f)),
                CircleShape,
            )
            .border(if (skin != null) 1.dp else 1.5.dp, if (skin == null) Color(0xAAFFFFFF) else Color(0x26FFFFFF), CircleShape)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val started = System.currentTimeMillis()
                    val reach = this.size.width / 2.5f
                    var moved = false
                    fun follow(at: Offset) {
                        var v = (at - down.position) / reach
                        val length = v.getDistance()
                        if (length > 1f) v /= length
                        if (length > 0.2f) moved = true
                        knob = v
                        onMove(v.x, v.y)
                    }
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        follow(change.position)
                    }
                    knob = Offset.Zero
                    onMove(0f, 0f)
                    if (!moved && System.currentTimeMillis() - started < 250) {
                        scope.launch {
                            onClick(true)
                            kotlinx.coroutines.delay(120)
                            onClick(false)
                        }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val travel = size * 0.28f
        if (skin == null) {
            Box(
                Modifier
                    .offset(travel * knob.x, travel * knob.y)
                    .size(size * 0.46f)
                    .background(Color(0x88FFFFFF), CircleShape)
                    .border(1.5.dp, Color(0xCCFFFFFF), CircleShape),
            )
        } else {
            // A rubber cap: domed gradient with a grip ring, casting a shadow on the base.
            androidx.compose.foundation.Canvas(
                Modifier.offset(travel * knob.x, travel * knob.y).size(size * 0.56f).shadow(6.dp, CircleShape, clip = false),
            ) {
                val r = this.size.minDimension / 2f
                val cap = skin.dpad
                drawCircle(Brush.radialGradient(listOf(cap.shade(0.22f), cap, cap.shade(-0.2f)), center = Offset(r * 0.8f, r * 0.7f), radius = r * 1.3f), r)
                drawCircle(cap.shade(-0.28f), r * 0.72f, style = androidx.compose.ui.graphics.drawscope.Stroke(r * 0.07f))
                drawCircle(cap.shade(0.1f), r * 0.52f, style = androidx.compose.ui.graphics.drawscope.Stroke(r * 0.04f))
            }
        }
        if (label != null) {
            androidx.compose.material3.Text(
                label, color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size.value * 0.2f).sp,
                modifier = Modifier.offset(travel * knob.x, travel * knob.y),
            )
        }
    }
}
