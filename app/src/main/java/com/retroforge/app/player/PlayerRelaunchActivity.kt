package com.retroforge.app.player

import android.app.Activity
import android.app.ActivityManager
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper

/**
 * Restarts the player after "Switch emulator" or a new resolution. Runs in the main process until the old player
 * process has ended, so the new player gets a fresh process and a clean emulator core.
 */
class PlayerRelaunchActivity : Activity() {
    private val handler = Handler(Looper.getMainLooper())
    private var waited = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        val next = intent.getParcelableExtra<Intent>(EXTRA_NEXT) ?: return finish()
        handler.postDelayed({ startWhenGone(next) }, STEP_MS)
    }

    private fun startWhenGone(next: Intent) {
        if (playerRunning() && waited < MAX_WAIT_MS) {
            waited += STEP_MS.toInt()
            handler.postDelayed({ startWhenGone(next) }, STEP_MS)
            return
        }
        startActivity(next.setClass(this, PlayerActivity::class.java))
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    private fun playerRunning(): Boolean {
        val name = "$packageName:player"
        return getSystemService(ActivityManager::class.java).runningAppProcesses.orEmpty().any { it.processName == name }
    }

    companion object {
        const val EXTRA_NEXT = "next"
        private const val STEP_MS = 100L
        private const val MAX_WAIT_MS = 5000
    }
}
