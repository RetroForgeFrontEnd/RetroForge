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
    fun everyBuiltInListReadsAndMatchesOnlyItsGame() {
        val dir = listOf("src/main/assets/moves", "app/src/main/assets/moves").map { java.io.File(it) }.first { it.isDirectory }
        val files: List<java.io.File> = dir.listFiles()!!.filter { it.name.endsWith(".txt") }.sortedBy { it.name }
        val games: List<Macros.Game> = files.map { f ->
            val g = Macros.parse(f.readText())
            assertNotNull(f.name, g)
            g!!
        }
        games.forEach { g ->
            (g.systemButtons.values + listOf(g.buttons)).forEach { layout ->
                assertEquals(g.title, g.buttons.map { it.first }.toSet(), layout.map { it.first }.toSet())
            }
            g.moves.forEach { m -> assert(Macros.finalButtons(m.inputs).all { it in g.buttons.toMap() }) { "${g.title}: ${m.name}" } }
        }
        fun titleFor(name: String) = Macros.gameFor(name, games)?.title
        assertEquals("Street Fighter II", titleFor("Super Street Fighter II Turbo (USA)"))
        assertEquals("Street Fighter Alpha", titleFor("Street Fighter Alpha 2 (USA)"))
        assertEquals("Street Fighter III", titleFor("Street Fighter III 3rd Strike"))
        assertEquals("Mortal Kombat", titleFor("Mortal Kombat (USA)"))
        assertEquals("Mortal Kombat II", titleFor("Mortal Kombat II (USA)"))
        assertEquals("The King of Fighters", titleFor("The King of Fighters '98"))
        assertEquals("Mortal Kombat 3", titleFor("Ultimate Mortal Kombat 3 (USA)"))
        assertEquals("Mortal Kombat 3", titleFor("Mortal Kombat Trilogy"))
        assertEquals("Tekken 3", titleFor("Tekken 3 (Europe)"))
        assertEquals("Marvel vs. Capcom 2", titleFor("Marvel vs. Capcom 2 - New Age of Heroes"))
        assertEquals("Fatal Fury Special", titleFor("Fatal Fury Special (USA)"))
        assertEquals("Fatal Fury", titleFor("Fatal Fury 2 (USA)"))
        assertEquals("Capcom crossovers", titleFor("Marvel vs. Capcom - Clash of Super Heroes"))
        assertEquals("Capcom crossovers", titleFor("X-Men vs. Street Fighter"))
        assertEquals("Contra", titleFor("Contra (USA)"))
        assertEquals("Super C", titleFor("Super C (USA)"))
        assertNull(titleFor("Contra III - The Alien Wars (USA)"))
        assertEquals("Darkstalkers", titleFor("Darkstalkers 3"))
        assertEquals("Night Warriors", titleFor("Night Warriors - Darkstalkers' Revenge"))
        assertEquals("Night Warriors", titleFor("Vampire Hunter - Darkstalkers' Revenge"))
        assertEquals("Gradius", titleFor("Gradius (USA)"))
        assertNull(titleFor("Gradius III (USA)"))
        assertEquals("Life Force", titleFor("Life Force (USA)"))
        assertEquals("Killer Instinct", titleFor("Killer Instinct (USA)"))
        assertNull(titleFor("Killer Instinct Gold (USA)"))
        assertEquals("Mega Man", titleFor("Mega Man 4 (USA)"))
        assertEquals("Mega Man X", titleFor("Mega Man X (USA)"))
        assertEquals("Mega Man X2", titleFor("Mega Man X2 (USA)"))
        assertNull(titleFor("Mega Man 2 (USA)"))
        assertEquals("Mortal Kombat 4", titleFor("Mortal Kombat 4 (USA)"))
        assertEquals("Mortal Kombat 4", titleFor("Mortal Kombat Gold (USA)"))
        assertEquals("Punch-Out!!", titleFor("Mike Tyson's Punch-Out!! (USA)"))
        assertNull(titleFor("Super Punch-Out!! (USA)"))
        assertEquals("Samurai Shodown II", titleFor("Samurai Shodown II"))
        assertEquals("Sonic the Hedgehog", titleFor("Sonic The Hedgehog (W) (REV00) [!]"))
        assertEquals("Sonic the Hedgehog 3", titleFor("Sonic The Hedgehog 3 (USA)"))
        assertEquals("Sonic the Hedgehog 3", titleFor("Sonic 3 & Knuckles"))
        assertNull(titleFor("Sonic The Hedgehog 2 (World)"))
        assertEquals("The King of Fighters '94", titleFor("The King of Fighters '94 (Japan)"))
        assertEquals("The King of Fighters", titleFor("The King of Fighters '95"))
        assertEquals("Art of Fighting", titleFor("Art of Fighting (USA)"))
        assertEquals("Art of Fighting", titleFor("Ryuuko no Ken"))
        assertEquals("Art of Fighting", titleFor("Art of Fighting (Japan)"))
        assertEquals("Clay Fighter", titleFor("Clay Fighter (USA)"))
        assertEquals("Clay Fighter", titleFor("ClayFighter - Tournament Edition (USA)"))
        assertNull(titleFor("Clay Fighter 2 - Judgment Clay (USA)"))
        assertEquals("Eternal Champions", titleFor("Eternal Champions (USA)"))
        assertNull(titleFor("Eternal Champions - Challenge from the Dark Side (USA)"))
        assertEquals("Samurai Shodown II", titleFor("Samurai Shodown II / Shin Samurai Spirits"))
        assertEquals("Samurai Shodown", titleFor("Samurai Shodown III"))
        assertEquals("X-Men: Children of the Atom", titleFor("X-Men - Children of the Atom (USA)"))
        assertEquals("Capcom crossovers", titleFor("X-Men vs. Street Fighter (USA)"))
        assertEquals("Fighter's History Dynamite", titleFor("Fighter's History Dynamite"))
        assertEquals("Fighter's History Dynamite", titleFor("Karnov's Revenge"))
        assertNull(titleFor("Fighter's History (USA)"))
        assertEquals("Primal Rage", titleFor("Primal Rage (USA)"))
        assertNull(titleFor("Tekken 2"))
        assertEquals("Art of Fighting 2", titleFor("Art of Fighting 2 (USA)"))
        assertEquals("Art of Fighting 2", titleFor("Ryuuko no Ken 2"))
        assertNull(titleFor("Art of Fighting 3 - The Path of the Warrior"))
        assertEquals("Dragon Ball Z: Super Butouden 2", titleFor("Dragon Ball Z - Super Butouden 2 (Japan)"))
        assertNull(titleFor("Dragon Ball Z - Super Butouden 3 (Japan)"))
        assertEquals("Double Dragon V", titleFor("Double Dragon V - The Shadow Falls (USA)"))
        assertNull(titleFor("Double Dragon (USA)"))
        assertEquals("Martial Champion", titleFor("Martial Champion (ver EAB)"))
        assertEquals("Real Bout Fatal Fury 2", titleFor("Real Bout Fatal Fury 2 - The Newcomers"))
        assertNull(titleFor("Real Bout Fatal Fury Special"))
        assertEquals("The Last Blade 2", titleFor("Last Blade 2, The"))
        assertEquals("The Last Blade 2", titleFor("Bakumatsu Roman Dai Ni Maku Gekka no Kenshi"))
        assertNull(titleFor("The Last Blade"))
        assertEquals("The King of Fighters 2002", titleFor("The King of Fighters 2002 (NGM-2650)"))
        assertEquals("The King of Fighters", titleFor("The King of Fighters 2001"))
        assertEquals("Street Fighter Alpha: Warriors' Dreams", titleFor("Street Fighter Alpha - Warriors' Dreams (USA)"))
        assertEquals("Street Fighter Alpha: Warriors' Dreams", titleFor("Street Fighter Zero (Japan)"))
        assertEquals("Street Fighter Alpha", titleFor("Street Fighter Zero 3 (Japan)"))
        assertEquals("Fatal Fury 3", titleFor("Fatal Fury 3 - Road to the Final Victory"))
        assertEquals("Breakers", titleFor("Breakers"))
        assertNull(titleFor("Breakers Revenge"))
        assertEquals("Real Bout Fatal Fury", titleFor("Real Bout Fatal Fury (NGM-095)"))
        assertEquals("Real Bout Fatal Fury 2", titleFor("Real Bout Fatal Fury 2"))
        assertEquals("Fatal Fury", titleFor("Fatal Fury 2 (USA)"))
    }

    @Test
    fun aGameCanHoldEachStepLonger() {
        val game = Macros.parse("title: Slow\nstep: 5\nbuttons: LP=B\nX | Y | 4 6+LP")!!
        assertEquals(5, game.step)
        assertEquals(5 + 5 + 1, Macros.frames(game.moves.single().inputs, true, game.buttons.toMap(), game.step).size)
        assertEquals(3, Macros.parse("title: Fast\nX | Y | 4")!!.step)
    }

    @Test
    fun aSystemCanHaveItsOwnLayout() {
        val game = Macros.parse("title: T\nbuttons: LP=Y\nbuttons.megadrive genesis: LP=L\nX | Y | LP")!!
        assertEquals(listOf("LP" to Pad.Y), game.buttonsFor("snes"))
        assertEquals(listOf("LP" to Pad.L), game.buttonsFor("megadrive"))
        assertEquals(listOf("LP" to Pad.L), game.buttonsFor("genesis"))
    }

    @Test
    fun aFileWithoutMovesIsSkipped() {
        assertNull(Macros.parse("title: Nothing"))
    }

    @Test
    fun finalButtons() {
        assertEquals(setOf("HP"), Macros.finalButtons("2 3 6+HP"))
        assertEquals(setOf("LP", "BL", "LK"), Macros.finalButtons("4+LP+BL+LK"))
        assertEquals(setOf("BL"), Macros.finalButtons("BL*4 8+BL 5+BL 8+BL*3"))
        assertEquals(emptySet<String>(), Macros.finalButtons("2 8"))
        assertEquals(emptySet<String>(), Macros.finalButtons("8 _ 8 START"))
    }

    @Test
    fun movesReadAsArrows() {
        assertEquals("↓↘→ HP", Macros.readable("2 3 6+HP"))
        assertEquals("↓↙← HP", Macros.readable("2 3 6+HP", facingRight = false))
        assertEquals("←(hold) → HP", Macros.readable("4*60 6+HP"))
        assertEquals("→, → C", Macros.readable("6 _ 6+C"))
        assertEquals("C, C, C", Macros.readable("C _ C _ C"))
        assertEquals("A+B+C", Macros.readable("A+B+C"))
        assertEquals("↑, ↑, START", Macros.readable("8 _ 8 _ START"))
    }

    @Test
    fun aCheatCodeListNeverMirrors() {
        assertEquals(false, Macros.parse("title: Codes\nfacing: no\nX | Y | 6 6 4")!!.facing)
        assertEquals(true, Macros.parse("title: Fight\nX | Y | 6 6 4")!!.facing)
    }

    @Test
    fun startAndSelectPressThosePadButtons() {
        assertEquals(Pad.bit(Pad.START), Macros.frames("START", facingRight = true, buttons = buttons).first())
        assertEquals(Pad.bit(Pad.SELECT), Macros.frames("SELECT", facingRight = true, buttons = buttons).first())
    }
}
