package com.retroforge.app.player

import com.retroforge.player.Pad

/**
 * Special moves, combos and finishers played with one tap, for fighting games in the built-in player.
 *
 * A move is written in the numpad notation fighting-game players use, for a fighter facing right: 1–9 are the
 * directions as on a phone's keypad turned upside down (6 forward, 4 back, 2 down, 3 down-forward, 8 up, 5 neutral),
 * joined with `+` to the game's buttons by their names (LP, HP, BL …). Each step lasts three frames, or `*n` frames
 * (`4*60` holds back for a second, for charge moves), and `_` lets go of everything for two frames, so `6 _ 6` is
 * two taps. Facing left mirrors forward and back.
 */
object Macros {

    /** A fighting game's own buttons, and which RetroPad button each one is on unless the user changes it. */
    data class Layout(val buttons: List<Pair<String, Int>>)

    data class Move(val fighter: String, val name: String, val inputs: String)

    data class Game(val title: String, val layout: Layout, val moves: List<Move>)

    /** Street Fighter II on the SNES and in the arcade (FinalBurn Neo): punches on top, kicks below. */
    private val sixButtons = Layout(listOf("LP" to Pad.Y, "MP" to Pad.X, "HP" to Pad.L, "LK" to Pad.B, "MK" to Pad.A, "HK" to Pad.R))

    /** Mortal Kombat's high and low punches and kicks, and block. */
    private val mortalKombat = Layout(listOf("HP" to Pad.X, "LP" to Pad.Y, "BL" to Pad.R, "HK" to Pad.A, "LK" to Pad.B))

    private val games = listOf(
        Game(
            "Street Fighter II", sixButtons,
            listOf(
                Move("Ryu / Ken", "Hadouken", "2 3 6+HP"),
                Move("Ryu / Ken", "Shoryuken", "6 2 3+HP"),
                Move("Ryu / Ken", "Hurricane Kick", "2 1 4+HK"),
                Move("Guile", "Sonic Boom", "4*60 6+HP"),
                Move("Guile", "Flash Kick", "2*60 8+HK"),
                Move("Chun-Li", "Spinning Bird Kick", "2*60 8+HK"),
                Move("Blanka", "Rolling Attack", "4*60 6+HP"),
                Move("E. Honda", "Sumo Headbutt", "4*60 6+HP"),
                Move("Zangief", "Spinning Piledriver", "6 3 2 1 4 7 8+HP"),
                Move("Dhalsim", "Yoga Fire", "2 3 6+HP"),
                Move("Dhalsim", "Yoga Flame", "6 3 2 1 4+HP"),
                Move("Sagat", "Tiger Shot", "2 3 6+HP"),
                Move("Sagat", "Tiger Uppercut", "6 2 3+HP"),
                Move("M. Bison", "Psycho Crusher", "4*60 6+HP"),
                Move("Balrog", "Dash Straight", "4*60 6+HP"),
                Move("Vega", "Rolling Crystal Flash", "4*60 6+HP"),
            ),
        ),
        Game(
            "Mortal Kombat", mortalKombat,
            listOf(
                Move("Liu Kang", "Fireball", "6 _ 6+HP"),
                Move("Liu Kang", "Flying Kick", "6 _ 6+HK"),
                Move("Sub-Zero", "Freeze", "2 3 6+LP"),
                Move("Sub-Zero", "Slide", "4+LP+BL+LK"),
                Move("Sub-Zero", "Fatality: Spine Rip (close)", "6 2 6+HP"),
                Move("Scorpion", "Spear", "4 _ 4+LP"),
                Move("Scorpion", "Teleport Punch", "2 4+HP"),
                Move("Scorpion", "Fatality: Toasty (two steps away)", "BL*4 8+BL 5+BL 8+BL"),
                Move("Johnny Cage", "Green Bolt", "4 6+LP"),
                Move("Johnny Cage", "Shadow Kick", "4 6+LK"),
                Move("Johnny Cage", "Fatality: Uppercut (close)", "6 _ 6 _ 6+HP"),
                Move("Raiden", "Lightning", "2 6+LP"),
                Move("Raiden", "Fatality: Electrocution (close)", "6 4 _ 4 _ 4+HP"),
                Move("Kano", "Fatality: Heart Rip (close)", "4 2 6+LP"),
                Move("Sonya", "Fatality: Kiss of Death", "6 _ 6 4 _ 4+BL"),
            ),
        ),
    )

    /** The move list for a game, from its title (any version: Turbo, Champion Edition, Super …). */
    fun gameFor(title: String): Game? {
        val t = title.lowercase().replace(Regex("[^a-z0-9]+"), " ")
        return when {
            "street fighter ii" in t || "street fighter 2" in t || "sf2" in t -> games[0]
            "mortal kombat" in t && Regex("""mortal kombat (ii|2|3|4|trilogy|mythologies)""").find(t) == null -> games[1]
            else -> null
        }
    }

    /**
     * The per-frame joypad masks for [inputs]: facing right or left, with the game's buttons on the RetroPad buttons
     * in [buttons] (name → RetroPad button). Unknown words are skipped.
     */
    fun frames(inputs: String, facingRight: Boolean, buttons: Map<String, Int>): IntArray {
        val out = ArrayList<Int>()
        for (step in inputs.trim().split(Regex("""\s+""")).filter { it.isNotEmpty() }) {
            if (step == "_") { repeat(2) { out += 0 }; continue }
            val (body, count) = step.split('*').let { it[0] to (it.getOrNull(1)?.toIntOrNull() ?: 3) }
            var mask = 0
            for (part in body.split('+')) {
                mask = mask or (part.singleOrNull()?.digitToIntOrNull()?.let { direction(it, facingRight) }
                    ?: buttons[part.uppercase()]?.let(Pad::bit) ?: 0)
            }
            repeat(count.coerceIn(1, 600)) { out += mask }
        }
        // One more frame of the last step, so the final press isn't cut short.
        out.lastOrNull()?.let { out += it }
        return out.toIntArray()
    }

    private fun direction(digit: Int, facingRight: Boolean): Int {
        val forward = Pad.bit(if (facingRight) Pad.RIGHT else Pad.LEFT)
        val back = Pad.bit(if (facingRight) Pad.LEFT else Pad.RIGHT)
        val up = Pad.bit(Pad.UP)
        val down = Pad.bit(Pad.DOWN)
        return when (digit) {
            1 -> down or back
            2 -> down
            3 -> down or forward
            4 -> back
            6 -> forward
            7 -> up or back
            8 -> up
            9 -> up or forward
            else -> 0
        }
    }

    /** Short names for the RetroPad buttons a fighting-game button can sit on. */
    val padButtons = listOf(Pad.B to "B", Pad.A to "A", Pad.Y to "Y", Pad.X to "X", Pad.L to "L", Pad.R to "R", Pad.L2 to "L2", Pad.R2 to "R2")
}
