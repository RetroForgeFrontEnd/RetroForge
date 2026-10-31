package com.retroforge.app

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.retroforge.app.ui.RetroForgeRoot
import com.retroforge.core.ui.ControllerKeys
import com.retroforge.core.ui.NavCommand
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow

class MainActivity : ComponentActivity() {

    // The language picked in Settings, on Android 12 and older (newer versions apply it themselves).
    override fun attachBaseContext(newBase: android.content.Context) = super.attachBaseContext(com.retroforge.core.ui.AppLanguage.wrap(newBase))


    /** Start + Select + L1 + R1 + L2 + R2 held: the language and factory-reset escape hatch. */
    private val resetCombo = ResetCombo(this)

    // ComponentActivity marks its dispatchKeyEvent as library-internal, but overriding Activity's is how every app
    // reads controller keys.
    @android.annotation.SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        resetCombo.onKey(event)
        if (com.retroforge.app.ui.settings.ControllerTap.offer(event)) return true
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchGenericMotionEvent(event: android.view.MotionEvent): Boolean =
        com.retroforge.app.ui.settings.ControllerTap.offer(event) || super.dispatchGenericMotionEvent(event)

    private val sectionCommands = MutableSharedFlow<NavCommand>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** A section to open, asked for by an intent (a download notification opens the PC tab, say). */
    private val openSection = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent?.getStringExtra(EXTRA_OPEN_SECTION)?.let { openSection.tryEmit(it) }
        enableEdgeToEdge()
        val container = (application as RetroForgeApplication).container
        com.retroforge.core.ui.UiSounds.init(this)
        com.retroforge.core.ui.UiSounds.enabled = container.viewPrefs.getBoolean(UI_SOUNDS, true)
        setContent { RetroForgeRoot(container, sectionCommands, openSection) }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_OPEN_SECTION)?.let { openSection.tryEmit(it) }
    }

    override fun onResume() {
        super.onResume()
        awaitingResult = false
        // Dual-screen devices: the second display shows RetroForge's art, or the open game's box (Settings → Dual screen).
        val prefs = (application as RetroForgeApplication).container.viewPrefs
        com.retroforge.app.ui.SecondScreen.show(this, prefs.getBoolean(com.retroforge.app.ui.settings.DUAL_SCREEN_BROWSE, false))
    }

    override fun onPause() {
        super.onPause()
        com.retroforge.app.ui.SecondScreen.hide()
    }

    /** A picker (a file, a folder) is open for RetroForge: its answer comes back here, so that isn't leaving. */
    private var awaitingResult = false

    @Deprecated("Deprecated in ComponentActivity; still how ActivityResultLaunchers start their activity.")
    override fun startActivityForResult(intent: android.content.Intent, requestCode: Int, options: Bundle?) {
        awaitingResult = true
        @Suppress("DEPRECATION")
        super.startActivityForResult(intent, requestCode, options)
    }

    override fun onStart() {
        super.onStart()
        // Saves swapped with the user's other devices on the Wi-Fi: served while RetroForge is on screen, and
        // swapped with any that are open now (on opening, and on coming back from a game).
        if (com.retroforge.app.player.PeerSync.isOn(this)) {
            com.retroforge.app.player.PeerSync.start(this)
            val app = applicationContext
            kotlin.concurrent.thread(name = "peer-sync", isDaemon = true) {
                val changed = com.retroforge.app.player.PeerSync.syncAll(app).mapNotNull { r ->
                    r.report?.takeIf { it.pulled + it.pushed > 0 }?.let { getString(R.string.peer_sync_toast, r.name, it.pulled, it.pushed) }
                }
                if (changed.isNotEmpty()) runOnUiThread { android.widget.Toast.makeText(app, changed.joinToString("\n"), android.widget.Toast.LENGTH_LONG).show() }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (!awaitingResult && !isChangingConfigurations) com.retroforge.app.player.PeerSync.stop()
        // Kiosk mode: going Home, to another app or into a game locks it again; a picker opened from Settings
        // doesn't, or the page would lock under it and the chosen file be lost.
        if (!awaitingResult && !isChangingConfigurations) Kiosk.lock()
    }

    // Gamepad buttons the UI did not consume are translated here rather than relying on each
    // device's key-character-map fallbacks, which differ between vendors.
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean = when (val command = ControllerKeys.commandFor(keyCode)) {
        NavCommand.CONFIRM -> window.superDispatchKeyEvent(event.asDpadCenter()) || super.onKeyDown(keyCode, event)
        NavCommand.BACK -> {
            event.startTracking()
            true
        }
        NavCommand.PREVIOUS_SECTION, NavCommand.NEXT_SECTION -> {
            if (event.repeatCount == 0) sectionCommands.tryEmit(command)
            true
        }
        null -> super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean = when (ControllerKeys.commandFor(keyCode)) {
        NavCommand.CONFIRM -> window.superDispatchKeyEvent(event.asDpadCenter()) || super.onKeyUp(keyCode, event)
        NavCommand.BACK -> {
            if (event.isTracking && !event.isCanceled) {
                com.retroforge.core.ui.UiSounds.back()
                onBackPressedDispatcher.onBackPressed()
            }
            true
        }
        NavCommand.PREVIOUS_SECTION, NavCommand.NEXT_SECTION -> true
        null -> super.onKeyUp(keyCode, event)
    }

    private fun KeyEvent.asDpadCenter() = KeyEvent(
        downTime, eventTime, action, KeyEvent.KEYCODE_DPAD_CENTER,
        repeatCount, metaState, deviceId, scanCode, flags, source,
    )

    companion object {
        /** An intent extra: the route of a section to open ("pc"). */
        const val EXTRA_OPEN_SECTION = "open_section"
    }
}

/** View preference: interface sounds on or off. */
const val UI_SOUNDS = "ui_sounds"
