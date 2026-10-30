package com.retroforge.app.ui.library

import com.retroforge.app.R
import com.retroforge.core.ui.str

/** The game layout's name on screen, in RetroForge's language. */
fun GameLayout.shownLabel(): String = str(
    when (this) {
        GameLayout.THEME -> R.string.library_layout_theme
        GameLayout.GRID -> R.string.library_as_grid
        GameLayout.LIST -> R.string.library_as_list
        GameLayout.SHELF -> R.string.library_as_bookshelf
    },
)

/** The tile style's name on screen, in RetroForge's language. */
fun TileStyle.shownLabel(): String = str(
    when (this) {
        TileStyle.CONSOLES -> R.string.library_tiles_consoles
        TileStyle.GAME_ART -> R.string.library_tiles_game_art
        TileStyle.ART_AND_CONSOLE -> R.string.library_tiles_art_and_console
        TileStyle.PLAIN -> R.string.library_tiles_plain
    },
)
