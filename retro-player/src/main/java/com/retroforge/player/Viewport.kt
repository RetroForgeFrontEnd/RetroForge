package com.retroforge.player

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** How the game picture fills the screen; chosen per system in the in-game menu. */
enum class ScreenShape(val label: String) {
    ORIGINAL("Original"),
    FOUR_THREE("4:3"),
    SIXTEEN_NINE("16:9"),
    STRETCH("Stretch to fill"),
    PIXEL_PERFECT("Pixel perfect"),
    ;

    fun next(): ScreenShape = entries[(ordinal + 1) % entries.size]

    companion object {
        fun of(name: String?) = entries.firstOrNull { it.name == name } ?: ORIGINAL
    }
}

/** How the console's pixels are drawn (player.cpp's fragment shader); chosen per system in the pause menu. */
enum class ScreenFilter(val label: String) {
    SHARP("Sharp pixels"),
    SMOOTH("Smooth"),
    CRT("CRT scanlines"),
    LCD("LCD grid"),
    CRT_CURVED("CRT, curved screen"),
    LCD_GHOSTING("LCD, with ghosting"),
    SCALE2X("Smooth pixel art (Scale2x)"),
    ;

    fun next(): ScreenFilter = entries[(ordinal + 1) % entries.size]

    companion object {
        fun of(name: String?) = entries.firstOrNull { it.name == name } ?: SHARP
    }
}

data class Rect(val x: Int, val y: Int, val w: Int, val h: Int)

object Viewport {
    /** The picture's rectangle on a [width] x [height] surface, centred. */
    fun fit(shape: ScreenShape, width: Int, height: Int, video: Video): Rect {
        if (width <= 0 || height <= 0) return Rect(0, 0, 0, 0)
        val (w, h) = when (shape) {
            ScreenShape.STRETCH -> width to height
            ScreenShape.PIXEL_PERFECT -> {
                // Whole-number scaling of the console's own pixels (square), so every pixel is the same size.
                val bw = max(1, video.width)
                val bh = max(1, video.height)
                val scale = max(1, min(width / bw, height / bh))
                min(width, bw * scale) to min(height, bh * scale)
            }
            else -> {
                val aspect = when (shape) {
                    ScreenShape.FOUR_THREE -> 4f / 3f
                    ScreenShape.SIXTEEN_NINE -> 16f / 9f
                    else -> if (video.aspect > 0) video.aspect else 4f / 3f
                }
                val byWidth = (width / aspect).roundToInt()
                if (byWidth <= height) width to byWidth else floor(height * aspect).toInt() to height
            }
        }
        return Rect((width - w) / 2, (height - h) / 2, w, h)
    }
}
