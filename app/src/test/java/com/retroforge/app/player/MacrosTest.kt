package com.retroforge.app.player

import com.retroforge.player.Pad
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MacrosTest {
    private val buttons = mapOf("LP" to Pad.Y, "HP" to Pad.L, "BL" to Pad.R)
    private val down = Pad.bit(Pad.DOWN)
    private val right = Pad.bit(Pad.RIGHT)
    private val left = Pad.bit(Pad.LEFT)

    @Test
    fun quarterCircleFacingRight() {
        val f = Macros.frames("2 3 6+LP", facingRight = true, buttons = buttons)
        val d = down
        val df = down or right
        val p = right or Pad.bit(Pad.Y)
        assertArrayEquals(intArrayOf(d, d, d, df, df, df, p, p, p, p), f)
    }

    @Test
    fun facingLeftMirrorsForwardAndBack() {
        val f = Macros.frames("6 4", facingRight = false, buttons = buttons)
        assertEquals(left, f.first())
        assertEquals(right, f.last())
    }

    @Test
    fun holdsAndGaps() {
        val f = Macros.frames("4*60 _ 6+HP", facingRight = true, buttons = buttons)
        assertEquals(60 + 2 + 3 + 1, f.size)
        assertEquals(0, f[60])
        assertEquals(right or Pad.bit(Pad.L), f.last())
    }

    @Test
    fun unknownWordsAreSkipped() {
        assertArrayEquals(intArrayOf(0, 0, 0, 0), Macros.frames("XYZ", facingRight = true, buttons = buttons))
    }

    @Test
    fun gamesByTitle() {
        assertNotNull(Macros.gameFor("Street Fighter II' Turbo - Hyper Fighting (USA)"))
        assertNotNull(Macros.gameFor("Super Street Fighter II (USA)"))
        assertNotNull(Macros.gameFor("Mortal Kombat (USA)"))
        assertNull(Macros.gameFor("Mortal Kombat II (USA)"))
        assertNull(Macros.gameFor("Super Mario World"))
    }
}
