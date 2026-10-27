package com.retroforge.core.ui

import android.view.KeyEvent

/** Frontend-level commands a gamepad, TV remote or keyboard can issue. */
enum class NavCommand { CONFIRM, BACK, PREVIOUS_SECTION, NEXT_SECTION }

/**
 * Default mapping from Android key codes to frontend commands. D-pad / arrow keys are left to
 * Compose's own focus traversal; this only covers buttons Compose does not interpret.
 * Mapping is for RetroForge's menus only — emulators keep their own controller configuration.
 */
object ControllerKeys {

    fun commandFor(keyCode: Int): NavCommand? = when (keyCode) {
        KeyEvent.KEYCODE_BUTTON_A -> NavCommand.CONFIRM
        KeyEvent.KEYCODE_BUTTON_B -> NavCommand.BACK
        KeyEvent.KEYCODE_BUTTON_L1 -> NavCommand.PREVIOUS_SECTION
        KeyEvent.KEYCODE_BUTTON_R1 -> NavCommand.NEXT_SECTION
        else -> null
    }
}
