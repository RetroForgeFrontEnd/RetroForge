package com.retroforge.app

import com.retroforge.core.ui.str
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
            .setTitle(str(R.string.reset_combo_reset_retroforge))
            .setMessage(
                str(R.string.reset_combo_use_the_phone_s_language_puts),
            )
            .setPositiveButton(str(R.string.reset_combo_use_the_phone_s_language)) { _, _ -> AppLanguage.set(activity, ""); activity.recreate() }
            .setNegativeButton(str(R.string.reset_combo_factory_reset)) { _, _ -> confirmFactoryReset() }
            .setNeutralButton(str(R.string.network_drives_cancel), null)
            .show()
            .getButton(AlertDialog.BUTTON_POSITIVE)?.requestFocus()
    }

    private fun confirmFactoryReset() {
        AlertDialog.Builder(activity)
            .setTitle(str(R.string.reset_combo_factory_reset_are_you_sure))
            .setMessage(
                str(R.string.reset_combo_this_resets_all_of_retroforge_s),
            )
            .setPositiveButton(str(R.string.network_drives_cancel), null)
            .setNegativeButton(str(R.string.reset_combo_reset_everything)) { _, _ -> factoryReset() }
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
