package com.retroforge.retro

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build

data class RetroArchInstall(val packageName: String, val versionName: String?)

sealed interface LaunchPlan {
    data class Ready(val install: RetroArchInstall, val romPath: String, val cores: List<Core>) : LaunchPlan
    data class NotPossible(val reason: LaunchProblem) : LaunchPlan
}

sealed interface LaunchProblem {
    data object RetroArchNotInstalled : LaunchProblem
    data class SystemNotSupported(val system: String) : LaunchProblem
    /** The file is somewhere RetroArch can't be given a path to (cloud or network providers). */
    data object LocationNotSupported : LaunchProblem
    data class StartFailed(val detail: String) : LaunchProblem
}

/**
 * Starts games in a separately installed RetroArch (GPL-3.0; RetroForge ships none of its code) using its
 * long-standing external launch activity. Verified on RetroArch 1.22.2: docs/EMULATOR_COMPATIBILITY.md.
 */
class RetroArchLauncher(private val context: Context) {

    fun findInstall(): RetroArchInstall? = PACKAGES.firstNotNullOfOrNull { pkg ->
        runCatching {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(pkg, 0)
            }
            RetroArchInstall(pkg, info.versionName)
        }.getOrNull()
    }

    fun plan(system: String, treeUri: String, documentId: String): LaunchPlan {
        val install = findInstall() ?: return LaunchPlan.NotPossible(LaunchProblem.RetroArchNotInstalled)
        val cores = RetroArchCores.coresFor(system).ifEmpty { return LaunchPlan.NotPossible(LaunchProblem.SystemNotSupported(system)) }
        val path = StoragePaths.filePath(treeUri, documentId) ?: return LaunchPlan.NotPossible(LaunchProblem.LocationNotSupported)
        return LaunchPlan.Ready(install, path, cores)
    }

    /** Opens RetroArch with the game. RetroArch shows its own message if the core isn't downloaded yet. */
    /** [configPath] is RetroForge's hotkey config ([RetroArchHotkeys]); null uses RetroArch's own settings. */
    fun launch(install: RetroArchInstall, romPath: String, core: Core, configPath: String? = null): LaunchProblem? {
        val intent = Intent(Intent.ACTION_MAIN)
            .setComponent(ComponentName(install.packageName, ACTIVITY))
            .putExtra("ROM", romPath)
            .putExtra("LIBRETRO", "/data/data/${install.packageName}/cores/${core.id}_libretro_android.so")
            .putExtra("CONFIGFILE", configPath ?: "/storage/emulated/0/Android/data/${install.packageName}/files/retroarch.cfg")
            // The launcher holds the application context, which can only start another app in a new task.
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            null
        } catch (e: ActivityNotFoundException) {
            LaunchProblem.StartFailed("RetroArch's game activity wasn't found. Update RetroArch.")
        } catch (e: SecurityException) {
            LaunchProblem.StartFailed("Android refused to start RetroArch: ${e.message}")
        } catch (e: RuntimeException) {
            LaunchProblem.StartFailed("Couldn't start RetroArch: ${e.message}")
        }
    }

    fun openRetroArch(install: RetroArchInstall): Boolean = context.packageManager.getLaunchIntentForPackage(install.packageName)
        ?.let { runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess } ?: false

    companion object {
        val PACKAGES = listOf("com.retroarch", "com.retroarch.aarch64", "com.retroarch.ra32")
        const val ACTIVITY = "com.retroarch.browser.retroactivity.RetroActivityFuture"
        const val DOWNLOAD_URL = "https://www.retroarch.com/platforms.html"
    }
}
