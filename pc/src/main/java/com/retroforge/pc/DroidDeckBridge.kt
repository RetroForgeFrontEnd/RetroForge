package com.retroforge.pc

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

data class DroidDeckInstall(
    val packageName: String,
    val versionName: String?,
    val acceptsLaunchIntent: Boolean,
)

/**
 * Talks to a separately installed DroidDeck app. RetroForge ships none of DroidDeck's GPL-3.0 code;
 * see docs/droiddeck-architecture-audit.md for the observed (undocumented) intent contract.
 */
class DroidDeckBridge(private val context: Context) {

    fun findInstall(): DroidDeckInstall? {
        val pm = context.packageManager
        val info = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(PACKAGE, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(PACKAGE, 0)
            }
        }.getOrNull() ?: return null
        val launchIntent = Intent(ACTION_LAUNCH_GAME).setPackage(PACKAGE)
        @Suppress("DEPRECATION")
        val handlers = pm.queryIntentActivities(launchIntent, 0)
        return DroidDeckInstall(PACKAGE, info.versionName, handlers.isNotEmpty())
    }

    fun openAppIntent(): Intent? = context.packageManager.getLaunchIntentForPackage(PACKAGE)

    companion object {
        const val PACKAGE = "com.droiddeck.launcher"
        const val ACTION_LAUNCH_GAME = "com.droiddeck.launcher.LAUNCH_GAME"
        const val RELEASES_URL = "https://github.com/Droid-Deck/DroidDeck/releases"
    }
}
