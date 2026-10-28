package com.retroforge.app.player

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.retroforge.player.Rect

/**
 * A console-inspired look for the player: the body colour around the game, a dark screen surround and buttons
 * coloured like the original pad. Original designs (colours and layout only, no logos or names), drawn in code so
 * they're sharp at any size and follow rotation.
 */
data class ConsoleSkin(
    val body: Color,
    val bodyShade: Color,
    val bezel: Color,
    val button: Color,
    val label: Color,
    val dpad: Color,
    val pill: Color,
    val pillLabel: Color,
    val shoulder: Color = pill,
    /** Face buttons in their own colours (by label), e.g. the SNES's four. */
    val face: Map<String, Color> = emptyMap(),
) {
    companion object {
        private val dark = ConsoleSkin(
            body = Color(0xFF26272D), bodyShade = Color(0xFF1B1C21), bezel = Color(0xFF111114),
            button = Color(0xFF3A3B43), label = Color(0xFFE8E8EE), dpad = Color(0xFF3A3B43),
            pill = Color(0xFF3A3B43), pillLabel = Color(0xFFCFCFD8),
        )

        fun forSystem(system: String): ConsoleSkin = when (system) {
            "gba" -> ConsoleSkin(
                body = Color(0xFF473C9E), bodyShade = Color(0xFF372E80), bezel = Color(0xFF22222A),
                button = Color(0xFF2C2C35), label = Color(0xFFE6E6F0), dpad = Color(0xFF2C2C35),
                pill = Color(0xFF2C2C35), pillLabel = Color(0xFFC9C6E8), shoulder = Color(0xFF6157C0),
            )
            "gb" -> ConsoleSkin(
                body = Color(0xFFC9C5BC), bodyShade = Color(0xFFB4B0A7), bezel = Color(0xFF5E6070),
                button = Color(0xFF8C1D4F), label = Color(0xFFF5E8EE), dpad = Color(0xFF26262C),
                pill = Color(0xFF85858E), pillLabel = Color(0xFF2E2E36),
            )
            "gbc" -> ConsoleSkin(
                body = Color(0xFF2B9A9A), bodyShade = Color(0xFF217E7E), bezel = Color(0xFF1E1E26),
                button = Color(0xFF1F1F27), label = Color(0xFFE9F5F5), dpad = Color(0xFF1F1F27),
                pill = Color(0xFF1F1F27), pillLabel = Color(0xFFBFE6E6),
            )
            "nes", "fds" -> ConsoleSkin(
                body = Color(0xFFBDBDC0), bodyShade = Color(0xFF2C2C30), bezel = Color(0xFF26262A),
                button = Color(0xFFC4262E), label = Color(0xFFFFFFFF), dpad = Color(0xFF1C1C1F),
                pill = Color(0xFF1C1C1F), pillLabel = Color(0xFFD8D8DC),
            )
            "snes", "sfc" -> ConsoleSkin(
                body = Color(0xFFCBCCD5), bodyShade = Color(0xFFB2B3BE), bezel = Color(0xFF3A3B45),
                button = Color(0xFF6E6F7A), label = Color(0xFFFFFFFF), dpad = Color(0xFF3A3B45),
                pill = Color(0xFF8A8B96), pillLabel = Color(0xFF26262E), shoulder = Color(0xFF9C9DA8),
                face = mapOf("X" to Color(0xFF2F5BD3), "A" to Color(0xFFD32F2F), "B" to Color(0xFFE2B300), "Y" to Color(0xFF2E9D48)),
            )
            "megadrive", "genesis", "sega32x" -> ConsoleSkin(
                body = Color(0xFF1D1D20), bodyShade = Color(0xFF131315), bezel = Color(0xFF0B0B0D),
                button = Color(0xFF2E2E33), label = Color(0xFFE8E8EC), dpad = Color(0xFF2E2E33),
                pill = Color(0xFF2E2E33), pillLabel = Color(0xFFE8E8EC),
            )
            "mastersystem", "sg1000" -> ConsoleSkin(
                body = Color(0xFF1E1E22), bodyShade = Color(0xFF141417), bezel = Color(0xFF0C0C0E),
                button = Color(0xFF3A3A41), label = Color(0xFFE5484D), dpad = Color(0xFF3A3A41),
                pill = Color(0xFF3A3A41), pillLabel = Color(0xFFE5484D),
            )
            "gamegear" -> ConsoleSkin(
                body = Color(0xFF202027), bodyShade = Color(0xFF16161B), bezel = Color(0xFF0E0E12),
                button = Color(0xFF34406A), label = Color(0xFFEDEFF7), dpad = Color(0xFF2C2C33),
                pill = Color(0xFF34406A), pillLabel = Color(0xFFEDEFF7),
            )
            "pcengine" -> ConsoleSkin(
                body = Color(0xFFECEDEF), bodyShade = Color(0xFFD5D6DA), bezel = Color(0xFF2B2B30),
                button = Color(0xFF202024), label = Color(0xFFFFFFFF), dpad = Color(0xFF202024),
                pill = Color(0xFF9C9CA4), pillLabel = Color(0xFF202024),
            )
            "nds" -> ConsoleSkin(
                body = Color(0xFFD3D6DC), bodyShade = Color(0xFFBCC0C7), bezel = Color(0xFF2A2D34),
                button = Color(0xFFF2F3F5), label = Color(0xFF3A3D44), dpad = Color(0xFF3A3D44),
                pill = Color(0xFF8C9099), pillLabel = Color(0xFF1E2026), shoulder = Color(0xFFB0B4BC),
            )
            else -> dark
        }
    }
}

/** The skin the touch controls draw with; null = the plain see-through controls. */
val LocalConsoleSkin = staticCompositionLocalOf<ConsoleSkin?> { null }

/** Space kept round the picture for the screen surround when the console look is on. */
val BEZEL_MARGIN: Dp = 14.dp

/**
 * Draws the console body over everything except the game picture ([picture], in this canvas's pixels), with a
 * rounded dark surround just outside the picture. Leaves the picture itself untouched (the game shows through).
 */
@Composable
fun ConsoleFrame(skin: ConsoleSkin, picture: Rect, modifier: Modifier) {
    Canvas(modifier) {
        if (picture.w <= 0 || picture.h <= 0) return@Canvas
        val margin = BEZEL_MARGIN.toPx()
        val radius = CornerRadius(margin * 1.2f)
        val hole = androidx.compose.ui.geometry.Rect(Offset(picture.x.toFloat(), picture.y.toFloat()), Size(picture.w.toFloat(), picture.h.toFloat()))
        val bezel = hole.inflate(margin)
        val body = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(androidx.compose.ui.geometry.Rect(Offset.Zero, size))
            addRoundRect(RoundRect(bezel, radius))
        }
        drawPath(body, skin.body)
        val ring = Path().apply {
            fillType = PathFillType.EvenOdd
            addRoundRect(RoundRect(bezel, radius))
            addRect(hole)
        }
        drawPath(ring, skin.bezel)
    }
}
