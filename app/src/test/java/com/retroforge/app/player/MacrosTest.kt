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
    fun readsAMoveListFile() {
        val game = Macros.parse(
            """
            # a note
            title: Mortal Kombat
            match: mortal kombat
            not: mortal kombat ii | ultimate mortal kombat 3
            buttons: HP=X LP=Y BL=R
            checked: no
            Sub-Zero | Freeze | 2 3 6+LP
            """.trimIndent(),
        )
        assertNotNull(game)
        game!!
        assertEquals(listOf("HP" to Pad.X, "LP" to Pad.Y, "BL" to Pad.R), game.buttons)
        assertEquals(false, game.checked)
        assertEquals(Macros.Move("Sub-Zero", "Freeze", "2 3 6+LP"), game.moves.single())
        val games = listOf(game)
        assertNotNull(Macros.gameFor("Mortal Kombat (USA)", games))
        assertNull(Macros.gameFor("Mortal Kombat II (USA)", games))
        assertNull(Macros.gameFor("Ultimate Mortal Kombat 3", games))
        assertNull(Macros.gameFor("Super Mario World", games))
    }

    @Test
    fun aFileWithoutMovesIsSkipped() {
        assertNull(Macros.parse("title: Nothing"))
    }
}
