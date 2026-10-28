package com.retroforge.player

import org.junit.Assert.assertEquals
import org.junit.Test

class ViewportTest {
    private val gba = Video(fps = 59.73f, width = 240, height = 160, aspect = 1.5f)

    @Test
    fun originalKeepsTheConsolesShapeCentred() {
        assertEquals(Rect(0, 520, 1080, 720), Viewport.fit(ScreenShape.ORIGINAL, 1080, 1760, gba))
        assertEquals(Rect(255, 0, 1620, 1080), Viewport.fit(ScreenShape.ORIGINAL, 2130, 1080, gba))
    }

    @Test
    fun pixelPerfectUsesWholeNumberScales() {
        // 1080 / 240 = 4.5 -> 4x: 960 x 640.
        assertEquals(Rect(60, 560, 960, 640), Viewport.fit(ScreenShape.PIXEL_PERFECT, 1080, 1760, gba))
    }

    @Test
    fun stretchAndFixedShapes() {
        assertEquals(Rect(0, 0, 2130, 1080), Viewport.fit(ScreenShape.STRETCH, 2130, 1080, gba))
        assertEquals(Rect(345, 0, 1440, 1080), Viewport.fit(ScreenShape.FOUR_THREE, 2130, 1080, gba))
        assertEquals(ScreenShape.ORIGINAL, ScreenShape.PIXEL_PERFECT.next())
    }
}
