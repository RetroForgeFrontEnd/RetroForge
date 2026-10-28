package com.retroforge.app.player

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper

/**
 * Restarts the player after "Switch emulator". Runs in the main process for a moment, so the old player
 * process can end (each game gets a clean emulator) before the new one starts.
 */
class PlayerRelaunchActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        val next = intent.getParcelableExtra<Intent>(EXTRA_NEXT) ?: return finish()
        Handler(Looper.getMainLooper()).postDelayed({
            startActivity(next.setClass(this, PlayerActivity::class.java))
            finish()
            @Suppress("DEPRECATION")
            overridePendingTransition(0, 0)
        }, 600)
    }

    companion object {
        const val EXTRA_NEXT = "next"
    }
}
