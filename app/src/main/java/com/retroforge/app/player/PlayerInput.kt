package com.retroforge.app.player

import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import com.retroforge.player.Pad
import com.retroforge.retro.HotkeyCombo
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Turns controller and touch input into libretro's joypad mask for player 1, and spots the hotkeys.
 * Face buttons go by position, as in RetroArch: Android's bottom button (A on an Xbox pad) is the
 * libretro/SNES bottom button (B), and so on.
 */
class PlayerInput(
    private val menuCombo: HotkeyCombo,
    private val quitCombo: HotkeyCombo,
    private val fastForwardOnR2: Boolean,
    private val onMask: (Int) -> Unit,
    private val onAnalog: (stick: Int, x: Int, y: Int) -> Unit,
    private val onFastForward: (Boolean) -> Unit,
    private val onMenu: () -> Unit,
    private val onQuit: () -> Unit,
) {
    private var keys = 0
    private var hat = 0
    private var stick = 0
    private var triggers = 0
    private var touch = 0
    private var fastForward = false

    /** Buttons that made a hotkey stay away from the game until they're let go. */
    private var swallowed = 0
    private var holdStartedAt = 0L

    /** True once a physical controller has been used; touch controls hide until the screen is touched again. */
    var usingController = false
        private set

    fun onKey(event: KeyEvent): Boolean {
        val button = buttonFor(event.keyCode) ?: return false
        if (event.repeatCount > 0) return true
        usingController = true
        keys = if (event.action == KeyEvent.ACTION_DOWN) keys or Pad.bit(button) else keys and Pad.bit(button).inv()
        update()
        return true
    }

    fun onMotion(event: MotionEvent): Boolean {
        if (event.source and InputDevice.SOURCE_JOYSTICK != InputDevice.SOURCE_JOYSTICK || event.action != MotionEvent.ACTION_MOVE) return false
        usingController = true
        val hx = event.getAxisValue(MotionEvent.AXIS_HAT_X)
        val hy = event.getAxisValue(MotionEvent.AXIS_HAT_Y)
        hat = directions(hx, hy, 0.5f)
        val lx = event.getAxisValue(MotionEvent.AXIS_X)
        val ly = event.getAxisValue(MotionEvent.AXIS_Y)
        stick = directions(lx, ly, 0.5f)
        onAnalog(0, axis(lx), axis(ly))
        onAnalog(1, axis(event.getAxisValue(MotionEvent.AXIS_Z)), axis(event.getAxisValue(MotionEvent.AXIS_RZ)))
        val l2 = maxOf(event.getAxisValue(MotionEvent.AXIS_LTRIGGER), event.getAxisValue(MotionEvent.AXIS_BRAKE))
        val r2 = maxOf(event.getAxisValue(MotionEvent.AXIS_RTRIGGER), event.getAxisValue(MotionEvent.AXIS_GAS))
        triggers = (if (l2 > 0.5f) Pad.bit(Pad.L2) else 0) or (if (r2 > 0.5f) Pad.bit(Pad.R2) else 0)
        update()
        return true
    }

    fun setTouch(mask: Int) {
        usingController = false
        touch = mask
        update()
    }

    /** Lets go of everything (menu opened, app paused) so no button stays stuck down. */
    fun releaseAll() {
        keys = 0; hat = 0; stick = 0; triggers = 0; touch = 0; swallowed = 0
        update()
    }

    private fun update() {
        var mask = keys or hat or stick or triggers or touch
        swallowed = swallowed and mask
        if (fired(quitCombo, mask)) {
            swallowed = mask
            onQuit()
        } else if (fired(menuCombo, mask)) {
            swallowed = mask
            onMenu()
        }
        mask = mask and swallowed.inv()

        val wantFast = fastForwardOnR2 && mask and Pad.bit(Pad.R2) != 0
        if (fastForwardOnR2) mask = mask and Pad.bit(Pad.R2).inv()
        if (wantFast != fastForward) {
            fastForward = wantFast
            onFastForward(wantFast)
        }
        onMask(mask)
    }

    private fun fired(combo: HotkeyCombo, mask: Int): Boolean {
        val needed = when (combo) {
            HotkeyCombo.NONE -> return false
            HotkeyCombo.L3_R3 -> Pad.bit(Pad.L3) or Pad.bit(Pad.R3)
            HotkeyCombo.START_SELECT -> Pad.bit(Pad.START) or Pad.bit(Pad.SELECT)
            HotkeyCombo.L1_R1 -> Pad.bit(Pad.L) or Pad.bit(Pad.R)
            HotkeyCombo.DOWN_SELECT -> Pad.bit(Pad.DOWN) or Pad.bit(Pad.SELECT)
            HotkeyCombo.L2_R2 -> Pad.bit(Pad.L2) or Pad.bit(Pad.R2)
            HotkeyCombo.HOLD_START, HotkeyCombo.HOLD_SELECT -> return held(if (combo == HotkeyCombo.HOLD_START) Pad.START else Pad.SELECT, mask)
        }
        // Touch Start + Select isn't a hotkey: the on-screen menu button is there instead.
        return mask and needed == needed && swallowed and needed == 0 && touch and needed != needed
    }

    // Checked on each input change; a hold fires on the first change after 1 second.
    private fun held(button: Int, mask: Int): Boolean {
        if (mask and Pad.bit(button) == 0 || touch and Pad.bit(button) != 0) {
            holdStartedAt = 0
            return false
        }
        val now = SystemClock.uptimeMillis()
        if (holdStartedAt == 0L) holdStartedAt = now
        return now - holdStartedAt >= 1000 && swallowed and Pad.bit(button) == 0
    }

    private fun directions(x: Float, y: Float, threshold: Float): Int =
        (if (x < -threshold) Pad.bit(Pad.LEFT) else 0) or (if (x > threshold) Pad.bit(Pad.RIGHT) else 0) or
            (if (y < -threshold) Pad.bit(Pad.UP) else 0) or (if (y > threshold) Pad.bit(Pad.DOWN) else 0)

    private fun axis(v: Float) = if (abs(v) < 0.1f) 0 else (v * 0x7fff).roundToInt().coerceIn(-0x7fff, 0x7fff)

    companion object {
        fun buttonFor(keyCode: Int): Int? = when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> Pad.B
            KeyEvent.KEYCODE_BUTTON_B -> Pad.A
            KeyEvent.KEYCODE_BUTTON_X -> Pad.Y
            KeyEvent.KEYCODE_BUTTON_Y -> Pad.X
            KeyEvent.KEYCODE_BUTTON_L1 -> Pad.L
            KeyEvent.KEYCODE_BUTTON_R1 -> Pad.R
            KeyEvent.KEYCODE_BUTTON_L2 -> Pad.L2
            KeyEvent.KEYCODE_BUTTON_R2 -> Pad.R2
            KeyEvent.KEYCODE_BUTTON_THUMBL -> Pad.L3
            KeyEvent.KEYCODE_BUTTON_THUMBR -> Pad.R3
            KeyEvent.KEYCODE_BUTTON_START -> Pad.START
            KeyEvent.KEYCODE_BUTTON_SELECT -> Pad.SELECT
            KeyEvent.KEYCODE_DPAD_UP -> Pad.UP
            KeyEvent.KEYCODE_DPAD_DOWN -> Pad.DOWN
            KeyEvent.KEYCODE_DPAD_LEFT -> Pad.LEFT
            KeyEvent.KEYCODE_DPAD_RIGHT -> Pad.RIGHT
            else -> null
        }
    }
}
