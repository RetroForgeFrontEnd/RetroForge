package com.retroforge.app

import android.content.Context
import com.retroforge.app.ui.pc.PortDownloads
import com.retroforge.app.ui.pc.ReleaseFile
import org.json.JSONArray

/**
 * RetroForge's own updates, from its GitHub releases (betas included). Checked every few hours when the app starts, unless
 * switched off; Settings → Help and about checks on request. Installing goes through Android's installer, which asks
 * the user to confirm.
 */
object AppUpdate {
    const val REPO = "RetroForgeFrontEnd/RetroForge"
    private const val PREFS = "app_update"
    /** How often the Home check looks for a new release: a few times a day, so one shows up soon after it's out. */
    private const val CHECK_EVERY_MS = 3L * 60 * 60 * 1000

    data class Available(val version: String, val notes: String, val apk: ReleaseFile)

    fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    fun autoCheck(context: Context) = prefs(context).getBoolean("auto", true)

    /** The newest release, if it's newer than this version and has an APK; null otherwise. Not on the main thread. */
    fun check(): Available? {
        val releases = JSONArray(Downloads.bytes("https://api.github.com/repos/$REPO/releases?per_page=5", 2L * 1024 * 1024).decodeToString())
        val newest = (0 until releases.length()).map { releases.getJSONObject(it) }.firstOrNull { !it.optBoolean("draft") } ?: return null
        val version = newest.optString("tag_name").removePrefix("v")
        if (!isNewer(version, BuildConfig.VERSION_NAME)) return null
        val assets = newest.optJSONArray("assets") ?: return null
        val files = (0 until assets.length()).map { assets.getJSONObject(it) }
            .map { ReleaseFile(it.getString("name"), it.getString("browser_download_url"), it.optLong("size")) }
        val apk = PortDownloads.pickApk(files) ?: return null
        return Available(version, newest.optString("body").lineSequence().take(12).joinToString("\n").trim(), apk)
    }

    /** Checks when a few hours have passed since the last check that reached GitHub (unless switched off). */
    fun checkIfDue(context: Context): Available? {
        val prefs = prefs(context)
        if (!autoCheck(context) || System.currentTimeMillis() - prefs.getLong("last", 0) < CHECK_EVERY_MS) return null
        // Only a check that reached GitHub counts: offline, the next start tries again.
        val found = runCatching { check() }.onSuccess { prefs.edit().putLong("last", System.currentTimeMillis()).apply() }.getOrNull()
        return found?.takeIf { it.version != prefs.getString("skipped", null) }
    }

    /** Downloads the APK and opens Android's installer; [onProgress] gets 0 to 100. */
    fun install(context: Context, update: Available, onProgress: (Int) -> Unit) = PortDownloads.installApk(context, update.apk, onProgress)

    /** "0.7.0-beta" is newer than "0.6.0-beta"; a release is newer than its own beta ("0.7.0" > "0.7.0-beta"). */
    fun isNewer(candidate: String, current: String): Boolean {
        fun parts(v: String) = v.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val a = parts(candidate)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return '-' !in candidate && '-' in current
    }
}
