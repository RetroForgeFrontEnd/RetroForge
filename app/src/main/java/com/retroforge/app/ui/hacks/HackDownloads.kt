package com.retroforge.app.ui.hacks

import com.retroforge.app.R
import com.retroforge.core.ui.str
import com.retroforge.app.BuildConfig
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Patches from hacks that publish GitHub releases (owner/repo in the catalogue). Nothing else is downloaded. */
object HackDownloads {
    private val patchFiles = Regex("""\.(zip|ips|bps|ups|xdelta)$""", RegexOption.IGNORE_CASE)

    /** The latest release's patch file: (file name, bytes). */
    fun latestPatch(repo: String, onProgress: (Float?) -> Unit = {}): Pair<String, ByteArray> {
        val release = JSONObject(get("https://api.github.com/repos/$repo/releases/latest").decodeToString())
        val assets = release.optJSONArray("assets") ?: throw IOException(str(R.string.hack_downloads_no_files_in_the_latest_release))
        val asset = (0 until assets.length()).map { assets.getJSONObject(it) }.firstOrNull { patchFiles.containsMatchIn(it.optString("name")) }
            ?: throw IOException(str(R.string.hack_downloads_no_patch_file_in_the_latest))
        if (asset.optLong("size") > 64L * 1024 * 1024) throw IOException(str(R.string.hack_downloads_the_patch_is_over_64_mb))
        return asset.getString("name") to com.retroforge.app.Downloads.bytes(asset.getString("browser_download_url"), 64L * 1024 * 1024, onProgress)
    }

    private fun get(url: String): ByteArray {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            setRequestProperty("User-Agent", "RetroForge/${BuildConfig.VERSION_NAME}")
        }
        try {
            if (connection.responseCode != 200) throw IOException(str(R.string.downloads_the_server_answered, connection.responseCode))
            return connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }
}
