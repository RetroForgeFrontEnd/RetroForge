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

    data class Move(val fighter: String, val name: String, val inputs: String)

    /**
     * One game's move list. [buttons] are the game's own buttons in order, each with the RetroPad button it's on unless
     * the user changes it; [checked] is false until someone has tried the moves in the game.
     */
    data class Game(
        val title: String,
        val match: List<String>,
        val not: List<String>,
        val buttons: List<Pair<String, Int>>,
        val checked: Boolean,
        val moves: List<Move>,
    )

    /**
     * Reads a move list file:
     * ```
     * title: Street Fighter II
     * match: street fighter ii | street fighter 2
     * not: (titles that look alike but are other games)
     * buttons: LP=Y MP=X HP=L LK=B MK=A HK=R
     * checked: no
     * Ryu / Ken | Hadouken | 2 3 6+HP
     * ```
     * Lines starting with # are notes. Null without a title or any moves.
     */
    fun parse(text: String): Game? {
        var title = ""
        var match = emptyList<String>()
        var not = emptyList<String>()
        var buttons = emptyList<Pair<String, Int>>()
        var checked = false
        val moves = ArrayList<Move>()
        fun phrases(v: String) = v.split('|').map(::plain).filter { it.isNotBlank() }
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            val key = line.substringBefore(':').trim().lowercase()
            val value = line.substringAfter(':', "").trim()
            when {
                '|' !in line && key == "title" -> title = value
                key == "match" -> match = phrases(value)
                key == "not" -> not = phrases(value)
                '|' !in line && key == "checked" -> checked = value.lowercase() in setOf("yes", "true")
                '|' !in line && key == "buttons" -> buttons = value.split(Regex("""\s+""")).mapNotNull { pair ->
                    val (name, pad) = pair.split('=').takeIf { it.size == 2 } ?: return@mapNotNull null
                    padButtons.firstOrNull { it.second.equals(pad, ignoreCase = true) }?.let { name.uppercase() to it.first }
                }
                else -> line.split('|').map { it.trim() }.takeIf { it.size == 3 && it.all(String::isNotEmpty) }
                    ?.let { (fighter, name, inputs) -> moves += Move(fighter, name, inputs) }
            }
        }
        if (title.isBlank() || moves.isEmpty()) return null
        return Game(title, match.ifEmpty { listOf(plain(title)) }, not, buttons, checked, moves)
    }

    /** The move list among [games] for a game, from its title (any version: Turbo, Champion Edition …). */
    fun gameFor(title: String, games: List<Game>): Game? {
        val t = " ${plain(title)} "
        return games.firstOrNull { g -> g.match.any { " $it " in t } && g.not.none { " $it " in t } }
    }

    /** Every move list: RetroForge's own (assets/moves) and the user's (the player's "moves" folder), theirs first. */
    fun load(context: android.content.Context): List<Game> {
        val own = runCatching { context.assets.list("moves").orEmpty().filter { it.endsWith(".txt") } }.getOrDefault(emptyList())
            .mapNotNull { name -> runCatching { context.assets.open("moves/$name").bufferedReader().use { it.readText() } }.getOrNull() }
        val theirs = context.getExternalFilesDir("moves")?.listFiles { f -> f.name.endsWith(".txt") }.orEmpty()
            .mapNotNull { runCatching { it.readText() }.getOrNull() }
        return (theirs + own).mapNotNull(::parse)
    }

    private fun plain(s: String) = s.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim()

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
