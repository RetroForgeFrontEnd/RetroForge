package com.retroforge.app.ui.hacks

import com.retroforge.hacks.CatalogueParser
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection

/**
 * Downloads a catalogue file the user explicitly asked for. HTTPS only, no redirects followed (so a link can't
 * bounce somewhere else), a 2 MB cap and short timeouts. Only catalogue JSON is fetched here — never ROMs or patches.
 */
class CatalogueFetcher(private val userAgent: String) {

    sealed interface Result {
        data class Ok(val text: String) : Result
        data class Failed(val reason: String) : Result
    }

    fun fetch(url: String): Result {
        if (!CatalogueParser.isHttps(url)) return Result.Failed("Only https:// links are allowed.")
        val connection = try {
            URL(url).openConnection() as? HttpsURLConnection ?: return Result.Failed("Only https:// links are allowed.")
        } catch (e: Exception) {
            return Result.Failed("That isn't a valid link.")
        }
        return try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("User-Agent", userAgent)
            connection.setRequestProperty("Accept", "application/json")
            when (val code = connection.responseCode) {
                HttpURLConnection.HTTP_OK -> readCapped(connection)
                in 300..399 -> Result.Failed("The link redirects elsewhere (HTTP $code). Use the final address of the catalogue file.")
                403 -> Result.Failed("The site refused the request (HTTP 403). It may not allow apps to download from it.")
                else -> Result.Failed("The site answered HTTP $code.")
            }
        } catch (e: java.io.IOException) {
            Result.Failed("Couldn't download the catalogue: ${e.message ?: e.javaClass.simpleName}")
        } finally {
            connection.disconnect()
        }
    }

    private fun readCapped(connection: HttpsURLConnection): Result {
        val declared = connection.contentLengthLong
        if (declared > CatalogueParser.MAX_BYTES) return Result.Failed("The file is larger than 2 MB, so it isn't a catalogue.")
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        connection.inputStream.use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                out.write(buffer, 0, read)
                if (out.size() > CatalogueParser.MAX_BYTES) return Result.Failed("The file is larger than 2 MB, so it isn't a catalogue.")
            }
        }
        return Result.Ok(out.toString(Charsets.UTF_8.name()))
    }
}
