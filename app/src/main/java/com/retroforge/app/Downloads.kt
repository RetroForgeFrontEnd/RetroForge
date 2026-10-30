package com.retroforge.app

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Downloads with how far they've got, so every download in RetroForge can show a percentage. */
object Downloads {
    /**
     * The file at [url]. [onProgress] gets the share done, 0 to 1, as it arrives (or null throughout when the server
     * doesn't say how big it is). Fails past [maxBytes].
     */
    fun bytes(url: String, maxBytes: Long = 512L * 1024 * 1024, onProgress: (Float?) -> Unit = {}): ByteArray {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            setRequestProperty("User-Agent", "RetroForge/${BuildConfig.VERSION_NAME}")
        }
        try {
            if (connection.responseCode != 200) throw IOException("the server answered ${connection.responseCode}")
            val total = connection.contentLengthLong.takeIf { it > 0 }
            if (total != null && total > maxBytes) throw IOException("the file is over ${maxBytes / (1024 * 1024)} MB")
            val out = ByteArrayOutputStream(total?.toInt() ?: 64 * 1024)
            val buffer = ByteArray(64 * 1024)
            var lastShown = -1
            connection.inputStream.use { input ->
                while (true) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    if (out.size() > maxBytes) throw IOException("the file is over ${maxBytes / (1024 * 1024)} MB")
                    if (total == null) onProgress(null)
                    else (out.size() * 100L / total).toInt().let { pct -> if (pct != lastShown) { lastShown = pct; onProgress(pct / 100f) } }
                }
            }
            return out.toByteArray()
        } finally {
            connection.disconnect()
        }
    }
}
