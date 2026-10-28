package com.retroforge.artwork

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

data class HttpResponse(val code: Int, val body: ByteArray)

class TooLargeException(limit: Long) : IOException("response larger than $limit bytes")

/** Minimal HTTPS GET, behind an interface so sources can be tested without the network. */
fun interface HttpGet {
    fun get(url: String, headers: Map<String, String>, maxBytes: Long): HttpResponse
}

/** HTTPS only. Redirects are followed only within HTTPS (HttpURLConnection never crosses protocols). */
class JvmHttpGet(private val userAgent: String) : HttpGet {
    override fun get(url: String, headers: Map<String, String>, maxBytes: Long): HttpResponse {
        require(url.startsWith("https://")) { "only https:// is allowed" }
        val connection = URL(url).openConnection() as HttpsURLConnection
        try {
            connection.connectTimeout = 10_000
            connection.readTimeout = 20_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("User-Agent", userAgent)
            headers.forEach(connection::setRequestProperty)
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            if (connection.contentLengthLong > maxBytes) throw TooLargeException(maxBytes)
            // The read timeout only fires when nothing arrives; a deadline also stops a connection that trickles forever.
            val deadline = System.nanoTime() + TOTAL_DEADLINE_NS
            val body = stream?.use { input ->
                val out = ByteArrayOutputStream()
                val buffer = ByteArray(32 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    out.write(buffer, 0, read)
                    if (out.size() > maxBytes) throw TooLargeException(maxBytes)
                    if (System.nanoTime() > deadline) throw IOException("download took longer than ${TOTAL_DEADLINE_NS / 1_000_000_000} s")
                }
                out.toByteArray()
            } ?: ByteArray(0)
            return HttpResponse(code, body)
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val MAX_IMAGE_BYTES = 15L * 1024 * 1024
        const val MAX_JSON_BYTES = 4L * 1024 * 1024
        const val HTTP_OK = HttpURLConnection.HTTP_OK
        private const val TOTAL_DEADLINE_NS = 60L * 1_000_000_000
    }
}

/** True for PNG, JPEG, WebP or GIF data, so a server error page is never saved as a cover. */
fun looksLikeImage(bytes: ByteArray): Boolean {
    fun at(i: Int) = bytes.getOrNull(i)?.toInt()?.and(0xFF) ?: -1
    return (at(0) == 0x89 && at(1) == 0x50 && at(2) == 0x4E && at(3) == 0x47) ||
        (at(0) == 0xFF && at(1) == 0xD8) ||
        (at(0) == 0x52 && at(1) == 0x49 && at(2) == 0x46 && at(3) == 0x46 && at(8) == 0x57 && at(9) == 0x45) ||
        (at(0) == 0x47 && at(1) == 0x49 && at(2) == 0x46)
}
