package com.retroforge.player

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Data files some emulators need beside their BIOS folder, from libretro's own assets (the same files RetroArch
 * downloads): PPSSPP's fonts and system dialogs (used by in-game saves). Fetched once, the first time the emulator runs.
 */
object CoreAssets {

    private const val BASE = "https://buildbot.libretro.com/assets/system"

    /** core id -> (zip on libretro's server, a file that shows it's already there). */
    private val assets = mapOf("ppsspp" to ("PPSSPP.zip" to "PPSSPP/ppge_atlas.zim"))

    fun needed(coreId: String, systemDir: File): Boolean = assets[coreId]?.let { !File(systemDir, it.second).isFile } ?: false

    /** Downloads and unpacks [coreId]'s data files into [systemDir]. */
    fun fetch(coreId: String, systemDir: File, userAgent: String) {
        val (zip, _) = assets[coreId] ?: return
        val connection = (URL("$BASE/$zip").openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            setRequestProperty("User-Agent", userAgent)
        }
        try {
            if (connection.responseCode != 200) throw IOException("libretro answered ${connection.responseCode}")
            val root = systemDir.canonicalFile
            ZipInputStream(connection.inputStream.buffered()).use { input ->
                while (true) {
                    val entry = input.nextEntry ?: break
                    val target = File(root, entry.name)
                    // Only inside the BIOS folder.
                    if (!target.canonicalPath.startsWith(root.path + File.separator)) continue
                    if (entry.isDirectory) {
                        target.mkdirs()
                        continue
                    }
                    target.parentFile?.mkdirs()
                    val part = File(target.path + ".part")
                    part.outputStream().use { input.copyTo(it) }
                    target.delete()
                    if (!part.renameTo(target)) throw IOException("couldn't write ${target.name}")
                }
            }
        } finally {
            connection.disconnect()
        }
    }
}
