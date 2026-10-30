package com.retroforge.app.ui.hacks

import com.retroforge.app.R
import com.retroforge.core.ui.str
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
            return Result.Failed(str(R.string.catalogue_fetcher_that_isn_t_a_valid_link))
        }
        return try {
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 10_000
            connection.readTimeout = 15_000
            connection.setRequestProperty("User-Agent", userAgent)
            connection.setRequestProperty("Accept", "application/json")
            when (val code = connection.responseCode) {
                HttpURLConnection.HTTP_OK -> readCapped(connection)
                in 300..399 -> Result.Failed(str(R.string.catalogue_fetcher_the_link_redirects_elsewhere_http_use, code))
                403 -> Result.Failed(str(R.string.catalogue_fetcher_the_site_refused_the_request_http))
                else -> Result.Failed(str(R.string.catalogue_fetcher_the_site_answered_http, code))
            }
        } catch (e: java.io.IOException) {
            Result.Failed(str(R.string.catalogue_fetcher_couldn_t_download_the_catalogue, e.message ?: e.javaClass.simpleName))
        } finally {
            connection.disconnect()
        }
    }

    private fun readCapped(connection: HttpsURLConnection): Result {
        val declared = connection.contentLengthLong
        if (declared > CatalogueParser.MAX_BYTES) return Result.Failed(str(R.string.catalogue_fetcher_the_file_is_larger_than_2))
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        connection.inputStream.use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                out.write(buffer, 0, read)
                if (out.size() > CatalogueParser.MAX_BYTES) return Result.Failed(str(R.string.catalogue_fetcher_the_file_is_larger_than_2))
            }
        }
        return Result.Ok(out.toString(Charsets.UTF_8.name()))
    }
}
