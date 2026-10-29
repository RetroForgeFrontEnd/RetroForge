package com.retroforge.app.player

import android.content.Context
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import com.retroforge.player.NativePlayer
import com.retroforge.player.RetroKeys

/**
 * An invisible view that brings up Android's keyboard for computer systems (C64, Amiga, DOS …) and passes what is
 * typed to the emulator as key presses: letters, numbers, symbols (with Shift where the key needs it), Enter and
 * Backspace.
 */
class KeyboardCatcher(context: Context) : View(context) {
    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun onCheckIsTextEditor() = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        // No suggestions or autocorrect: every key goes straight through.
        outAttrs.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN or EditorInfo.IME_ACTION_NONE
        return object : BaseInputConnection(this, false) {
            override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
                text.forEach { type(it) }
                return true
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                repeat(maxOf(1, beforeLength)) { tap(RetroKeys.BACKSPACE, 8, 0) }
                return true
            }

            override fun sendKeyEvent(event: KeyEvent): Boolean {
                val key = RetroKeys.fromAndroid(event.keyCode)
                if (key == 0) return super.sendKeyEvent(event)
                NativePlayer.setKey(key, event.action == KeyEvent.ACTION_DOWN, event.unicodeChar, RetroKeys.modifiers(event.metaState))
                return true
            }

            override fun performEditorAction(actionCode: Int): Boolean {
                tap(RetroKeys.RETURN, 13, 0)
                return true
            }
        }
    }

    fun show() {
        requestFocus()
        context.getSystemService(InputMethodManager::class.java).showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
    }

    fun hide() {
        context.getSystemService(InputMethodManager::class.java).hideSoftInputFromWindow(windowToken, 0)
        clearFocus()
    }

    // Typed text arrives in bursts ("RUN" in one go); emulators read keys once a frame, so each key is pressed and
    // released in turn, about 90 ms apart, Shift held around the ones that need it.
    private var nextAt = 0L
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private fun type(c: Char) {
        val (key, shift) = RetroKeys.fromChar(c) ?: return
        later(if (shift) RetroKeys.SHIFT_MOD else 0) { mods ->
            if (shift) NativePlayer.setKey(RetroKeys.LSHIFT, true, 0, mods)
            NativePlayer.setKey(key, true, c.code, mods)
        }.also { at -> mainHandler.postAtTime({ NativePlayer.setKey(key, false, 0, 0); if (shift) NativePlayer.setKey(RetroKeys.LSHIFT, false, 0, 0) }, at + 60) }
    }

    private fun tap(key: Int, character: Int, modifiers: Int) {
        later(modifiers) { mods -> NativePlayer.setKey(key, true, character, mods) }
            .also { at -> mainHandler.postAtTime({ NativePlayer.setKey(key, false, 0, modifiers) }, at + 60) }
    }

    /** Runs [press] at the next free moment in the key queue; returns when (uptime ms). */
    private fun later(modifiers: Int, press: (Int) -> Unit): Long {
        val at = maxOf(android.os.SystemClock.uptimeMillis(), nextAt)
        nextAt = at + 90
        mainHandler.postAtTime({ press(modifiers) }, at)
        return at
    }
}
