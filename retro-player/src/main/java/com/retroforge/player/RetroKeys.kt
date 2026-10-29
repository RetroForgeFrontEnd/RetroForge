package com.retroforge.player

import android.view.KeyEvent

/**
 * Keyboard keys for computer systems (C64, Amiga, DOS, MSX …): Android key codes and typed characters as libretro
 * key codes (RETROK_*), which are mostly ASCII for the main keys.
 */
object RetroKeys {
    const val SHIFT_MOD = 1
    const val CTRL_MOD = 2
    const val ALT_MOD = 4
    const val LSHIFT = 304
    const val RETURN = 13
    const val BACKSPACE = 8

    /** The libretro key for an Android key code, or 0 when it has none. */
    fun fromAndroid(keyCode: Int): Int = when (keyCode) {
        in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z -> 'a'.code + (keyCode - KeyEvent.KEYCODE_A)
        in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> '0'.code + (keyCode - KeyEvent.KEYCODE_0)
        in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F12 -> 282 + (keyCode - KeyEvent.KEYCODE_F1)
        KeyEvent.KEYCODE_SPACE -> 32
        KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> RETURN
        KeyEvent.KEYCODE_DEL -> BACKSPACE
        KeyEvent.KEYCODE_FORWARD_DEL -> 127
        KeyEvent.KEYCODE_TAB -> 9
        KeyEvent.KEYCODE_ESCAPE -> 27
        KeyEvent.KEYCODE_MINUS -> '-'.code
        KeyEvent.KEYCODE_EQUALS -> '='.code
        KeyEvent.KEYCODE_LEFT_BRACKET -> '['.code
        KeyEvent.KEYCODE_RIGHT_BRACKET -> ']'.code
        KeyEvent.KEYCODE_BACKSLASH -> '\\'.code
        KeyEvent.KEYCODE_SEMICOLON -> ';'.code
        KeyEvent.KEYCODE_APOSTROPHE -> '\''.code
        KeyEvent.KEYCODE_COMMA -> ','.code
        KeyEvent.KEYCODE_PERIOD -> '.'.code
        KeyEvent.KEYCODE_SLASH -> '/'.code
        KeyEvent.KEYCODE_GRAVE -> '`'.code
        KeyEvent.KEYCODE_DPAD_UP -> 273
        KeyEvent.KEYCODE_DPAD_DOWN -> 274
        KeyEvent.KEYCODE_DPAD_RIGHT -> 275
        KeyEvent.KEYCODE_DPAD_LEFT -> 276
        KeyEvent.KEYCODE_INSERT -> 277
        KeyEvent.KEYCODE_MOVE_HOME -> 278
        KeyEvent.KEYCODE_MOVE_END -> 279
        KeyEvent.KEYCODE_PAGE_UP -> 280
        KeyEvent.KEYCODE_PAGE_DOWN -> 281
        KeyEvent.KEYCODE_CAPS_LOCK -> 301
        KeyEvent.KEYCODE_SHIFT_RIGHT -> 303
        KeyEvent.KEYCODE_SHIFT_LEFT -> LSHIFT
        KeyEvent.KEYCODE_CTRL_RIGHT -> 305
        KeyEvent.KEYCODE_CTRL_LEFT -> 306
        KeyEvent.KEYCODE_ALT_RIGHT -> 307
        KeyEvent.KEYCODE_ALT_LEFT -> 308
        else -> 0
    }

    /** Libretro modifier bits for an Android meta state. */
    fun modifiers(metaState: Int): Int =
        (if (metaState and KeyEvent.META_SHIFT_ON != 0) SHIFT_MOD else 0) or
            (if (metaState and KeyEvent.META_CTRL_ON != 0) CTRL_MOD else 0) or
            (if (metaState and KeyEvent.META_ALT_ON != 0) ALT_MOD else 0)

    private const val SHIFTED_DIGITS = ")!@#$%^&*("
    private val shiftedSymbols = mapOf(
        '_' to '-', '+' to '=', '{' to '[', '}' to ']', '|' to '\\', ':' to ';', '"' to '\'', '<' to ',', '>' to '.', '?' to '/', '~' to '`',
    )

    /** A typed character as (key, needs shift) on a US layout, for the on-screen keyboard; null when it has no key. */
    fun fromChar(c: Char): Pair<Int, Boolean>? = when {
        c in 'a'..'z' -> c.code to false
        c in 'A'..'Z' -> c.lowercaseChar().code to true
        c in '0'..'9' -> c.code to false
        c in SHIFTED_DIGITS -> ('0'.code + SHIFTED_DIGITS.indexOf(c)) to true
        c in shiftedSymbols -> shiftedSymbols.getValue(c).code to true
        c == '\n' -> RETURN to false
        c.code in 32..126 -> c.code to false
        else -> null
    }
}
