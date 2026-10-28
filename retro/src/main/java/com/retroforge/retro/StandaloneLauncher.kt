package com.retroforge.retro

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import com.retroforge.library.IntentExtra
import com.retroforge.library.LaunchSpec
import com.retroforge.library.StandaloneEmulator

/**
 * Starts games in free standalone emulator apps the user installed themselves (DuckStation, Dolphin, PPSSPP …),
 * using the launch intents ES-DE documents for each. RetroForge ships none of their code and never installs them.
 */
class StandaloneLauncher(private val context: Context) {

    /** The installed (package, activity) of [emulator], or null. */
    fun installed(emulator: StandaloneEmulator): Pair<String, String>? = emulator.components.firstOrNull { (pkg, _) -> isInstalled(pkg) }

    private fun isInstalled(pkg: String): Boolean = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(pkg, 0)
        }
    }.isSuccess

    fun launch(component: Pair<String, String>, spec: LaunchSpec): LaunchProblem? {
        val (pkg, activity) = component
        val intent = Intent(spec.action ?: Intent.ACTION_MAIN).setComponent(ComponentName(pkg, activity))
        spec.category?.let(intent::addCategory)
        spec.data?.let { intent.data = Uri.parse(it) }
        for (extra in spec.extras) {
            when (extra) {
                is IntentExtra.Text -> intent.putExtra(extra.name, extra.value)
                is IntentExtra.Bool -> intent.putExtra(extra.name, extra.value)
            }
        }
        // Started from the application context, so always in a new task.
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (spec.clearTask) intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        if (spec.clearTop) intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return try {
            // The game is only readable through RetroForge's folder permission; pass read access on for this file.
            spec.grants.forEach { context.grantUriPermission(pkg, Uri.parse(it), Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            if (spec.data != null && spec.data in spec.grants) intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(intent)
            null
        } catch (e: ActivityNotFoundException) {
            LaunchProblem.StartFailed("The emulator's game screen wasn't found. Update the emulator.")
        } catch (e: SecurityException) {
            LaunchProblem.StartFailed("Android refused to start the emulator: ${e.message}")
        } catch (e: RuntimeException) {
            LaunchProblem.StartFailed("Couldn't start the emulator: ${e.message}")
        }
    }

    /**
     * Opens where to get [emulator]: Obtainium's "add app" screen when Obtainium is installed and the emulator
     * publishes releases it can track (a full app setup from the Obtainium Emulation Pack, or a GitHub repository),
     * otherwise the official download page.
     */
    fun openDownload(emulator: StandaloneEmulator, preferObtainium: Boolean = true): Boolean {
        val obtainium = emulator.obtainiumApp?.let { "obtainium://app/" + Uri.encode(it) }
            ?: emulator.obtainium?.let { "obtainium://add/$it" }
        if (preferObtainium && obtainium != null) {
            val add = Intent(Intent.ACTION_VIEW, Uri.parse(obtainium)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (runCatching { context.startActivity(add) }.isSuccess) return true
        }
        return runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(emulator.page)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess
    }

    fun hasObtainium(): Boolean = OBTAINIUM_PACKAGES.any(::isInstalled)

    companion object {
        val OBTAINIUM_PACKAGES = listOf("dev.imranr.obtainium", "dev.imranr.obtainium.fdroid")
        const val OBTAINIUM_PAGE = "https://github.com/ImranR98/Obtainium"
    }
}
