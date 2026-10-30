package com.retroforge.pcplayer

import android.content.Context
import android.text.InputType
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection

/**
 * An invisible view the phone's keyboard types into, handing everything on to [onKey] as key presses: the game
 * (and Steam's sign-in) only understands keys, while keyboards such as Samsung's send whole words of text.
 */
class KeyInputView(context: Context, private val onKey: (KeyEvent) -> Unit) : View(context) {

    private val keys = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD)

    init {
        isFocusable = true
        isFocusableInTouchMode = true
    }

    override fun onCheckIsTextEditor() = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        // TYPE_NULL asks for plain key presses where the keyboard allows it; text it sends anyway is turned into keys.
        outAttrs.inputType = InputType.TYPE_NULL
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        return object : BaseInputConnection(this, false) {
            override fun commitText(text: CharSequence, newCursorPosition: Int): Boolean {
                type(text)
                return true
            }

            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                repeat(beforeLength) { press(KeyEvent.KEYCODE_DEL) }
                repeat(afterLength) { press(KeyEvent.KEYCODE_FORWARD_DEL) }
                return true
            }

            override fun sendKeyEvent(event: KeyEvent): Boolean {
                onKey(event)
                return true
            }
        }
    }

    private fun type(text: CharSequence) {
        keys.getEvents(text.toString().toCharArray())?.forEach(onKey)
    }

    private fun press(keyCode: Int) {
        onKey(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        onKey(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }
}
