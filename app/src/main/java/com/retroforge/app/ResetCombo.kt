package com.retroforge.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import com.retroforge.core.ui.AppLanguage
import java.io.File

/**
 * The emergency combo: hold Start + Select + L1 + R1 + L2 + R2 for two seconds anywhere in RetroForge's menus. It
 * offers to put RetroForge back in the phone's language (picked by default, so pressing A is safe), or, after
 * asking twice, to reset every setting. Its words are always English, so they can be read whatever language was picked.
 * Games, saves, save states and the library are never touched.
 */
class ResetCombo(private val activity: Activity) {
    private val combo = setOf(
        KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_SELECT,
        KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_BUTTON_R2,
    )
    private val held = mutableSetOf<Int>()
    private val handler = Handler(Looper.getMainLooper())
    private val whenHeld = Runnable { if (held.containsAll(combo)) offer() }

    /** Watches every key; never consumes one, so menus still get them. */
    fun onKey(event: KeyEvent) {
        if (event.keyCode !in combo) return
        when (event.action) {
            KeyEvent.ACTION_DOWN -> if (held.add(event.keyCode) && held.containsAll(combo)) handler.postDelayed(whenHeld, 2000)
            KeyEvent.ACTION_UP -> {
                held.remove(event.keyCode)
                handler.removeCallbacks(whenHeld)
            }
        }
    }

    private fun offer() {
        held.clear()
        AlertDialog.Builder(activity)
            .setTitle("Reset RetroForge")
            .setMessage(
                "Use the phone's language: puts RetroForge back in your phone's language. Nothing else changes.\n\n" +
                    "Factory reset: every RetroForge setting back to how it was when installed. Your games, saves and save states are kept.",
            )
            .setPositiveButton("Use the phone's language") { _, _ -> AppLanguage.set(activity, ""); activity.recreate() }
            .setNegativeButton("Factory reset…") { _, _ -> confirmFactoryReset() }
            .setNeutralButton("Cancel", null)
            .show()
            .getButton(AlertDialog.BUTTON_POSITIVE)?.requestFocus()
    }

    private fun confirmFactoryReset() {
        AlertDialog.Builder(activity)
            .setTitle("Factory reset: are you sure?")
            .setMessage(
                "This resets ALL of RetroForge's settings: language, theme, emulator choices, remapped buttons, hotkeys, " +
                    "covers chosen, save syncing, accounts and network drive sign-ins. It can't be undone.\n\n" +
                    "Kept: your game folders and library, your games, saves and save states.",
            )
            .setPositiveButton("Cancel", null)
            .setNegativeButton("Reset everything") { _, _ -> factoryReset() }
            .show()
            .getButton(AlertDialog.BUTTON_POSITIVE)?.requestFocus()
    }

    private fun factoryReset() {
        AppLanguage.set(activity, "")
        val data = activity.applicationInfo.dataDir
        // Settings live in Android's preference files and RetroForge's DataStore; the library, saves and states don't.
        File(data, "shared_prefs").listFiles()?.forEach { it.delete() }
        File(activity.filesDir, "datastore").deleteRecursively()
        File(activity.filesDir, "network_drives.json").delete()
        val restart = activity.packageManager.getLaunchIntentForPackage(activity.packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        activity.startActivity(restart)
        activity.finishAffinity()
        Runtime.getRuntime().exit(0)
    }
}
