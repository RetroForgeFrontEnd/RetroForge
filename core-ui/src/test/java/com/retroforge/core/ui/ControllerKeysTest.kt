package com.retroforge.core.ui

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ControllerKeysTest {

    @Test
    fun faceAndShoulderButtonsMapToCommands() {
        assertEquals(NavCommand.CONFIRM, ControllerKeys.commandFor(KeyEvent.KEYCODE_BUTTON_A))
        assertEquals(NavCommand.BACK, ControllerKeys.commandFor(KeyEvent.KEYCODE_BUTTON_B))
        assertEquals(NavCommand.PREVIOUS_SECTION, ControllerKeys.commandFor(KeyEvent.KEYCODE_BUTTON_L1))
        assertEquals(NavCommand.NEXT_SECTION, ControllerKeys.commandFor(KeyEvent.KEYCODE_BUTTON_R1))
    }

    @Test
    fun directionalKeysAreLeftToComposeFocus() {
        assertNull(ControllerKeys.commandFor(KeyEvent.KEYCODE_DPAD_UP))
        assertNull(ControllerKeys.commandFor(KeyEvent.KEYCODE_DPAD_CENTER))
        assertNull(ControllerKeys.commandFor(KeyEvent.KEYCODE_ENTER))
    }
}
