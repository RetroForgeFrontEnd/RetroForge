package com.retroforge.app.ui

import com.retroforge.app.R
import com.retroforge.core.ui.pluralStr

/** "1 game", "3 games": [count] of [noun], in RetroForge's language. Nouns without a translation stay English. */
fun countOf(count: Int, noun: String): String {
    val id = when (noun) {
        "game" -> R.plurals.count_game
        "hack" -> R.plurals.count_hack
        "cover" -> R.plurals.count_cover
        "folder" -> R.plurals.count_folder
        "system" -> R.plurals.count_system
        "more system" -> R.plurals.count_more_system
        "problem" -> R.plurals.count_problem
        "set" -> R.plurals.count_set
        "playlist" -> R.plurals.count_playlist
        "file" -> R.plurals.count_file
        "change" -> R.plurals.count_change
        "time" -> R.plurals.count_time
        "disc" -> R.plurals.count_disc
        "3D box" -> R.plurals.count_3d_box
        "film" -> R.plurals.count_film
        "save" -> R.plurals.count_save
        "block" -> R.plurals.count_block
        "catalogue" -> R.plurals.count_catalogue
        "minute" -> R.plurals.count_minute
        "copy" -> R.plurals.count_copy
        else -> return if (count == 1) "1 $noun" else "$count ${noun}s"
    }
    return pluralStr(id, count, count)
}
