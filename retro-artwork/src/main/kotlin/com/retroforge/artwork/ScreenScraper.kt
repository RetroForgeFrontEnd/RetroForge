package com.retroforge.artwork

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayInputStream
import java.net.URLEncoder
import javax.xml.parsers.SAXParserFactory

/** RetroForge's own ScreenScraper developer credentials, issued by ScreenScraper on request. Not in source control. */
data class ScreenScraperApp(val devId: String, val devPassword: String, val softName: String)

/** The user's own ScreenScraper account (optional: raises their quota and thread limits). */
data class ScreenScraperUser(val name: String, val password: String)

data class SsMedia(val type: String, val region: String?, val format: String?, val url: String)

data class SsGame(val id: String?, val name: String?, val medias: List<SsMedia>)

data class SsQuota(val requestsToday: Int?, val maxRequestsPerDay: Int?)

sealed interface SsResult {
    data class Found(val game: SsGame, val quota: SsQuota) : SsResult
    data object NotFound : SsResult
    /** This build has no developer credentials, so ScreenScraper can't be used. */
    data object NotAvailable : SsResult
    data object BadLogin : SsResult
    data class Failed(val reason: String) : SsResult
}

/**
 * ScreenScraper API v2 (https://www.screenscraper.fr/webapi2.php), modelled on ES-DE's scraper (MIT). One lookup per
 * game, only when the user asks. ScreenScraper's terms: the API may only be built into entirely free apps unless
 * ScreenScraper agrees otherwise, and every app needs its own developer id. Credentials travel as URL parameters,
 * as the API requires; they're never logged, and media URLs are stripped of them before being stored.
 */
class ScreenScraper(private val http: HttpGet, private val app: () -> ScreenScraperApp?, private val user: () -> ScreenScraperUser?) {

    val available: Boolean get() = app() != null

    /** Looks a ROM up by name and size, plus its MD5 when known (more reliable). */
    fun gameInfo(systemId: Int, fileName: String, size: Long, md5: String?): SsResult {
        val credentials = app() ?: return SsResult.NotAvailable
        val url = buildString {
            append("$BASE/jeuInfos.php?devid=").append(enc(credentials.devId))
            append("&devpassword=").append(enc(credentials.devPassword))
            append("&softname=").append(enc(credentials.softName))
            user()?.let { append("&ssid=").append(enc(it.name)).append("&sspassword=").append(enc(it.password)) }
            append("&output=xml&romtype=rom&systemeid=").append(systemId)
            append("&romnom=").append(enc(fileName.substringAfterLast('/')))
            append("&romtaille=").append(size)
            md5?.let { append("&md5=").append(it) }
        }
        return try {
            val response = http.get(url, emptyMap(), JvmHttpGet.MAX_JSON_BYTES)
            val body = response.body.toString(Charsets.UTF_8)
            when (response.code) {
                200 -> if (body.contains("<jeu")) parse(response.body) else SsResult.NotFound
                404 -> SsResult.NotFound
                401, 403 -> if (body.contains("dev", ignoreCase = true)) SsResult.NotAvailable else SsResult.BadLogin
                423 -> SsResult.Failed("ScreenScraper's API is closed for now (server busy or maintenance).")
                426 -> SsResult.Failed("ScreenScraper has blocked this version of RetroForge; an update is needed.")
                429 -> SsResult.Failed("ScreenScraper's per-minute limit was reached. Wait a minute.")
                430 -> SsResult.Failed("Your ScreenScraper quota for today is used up.")
                431 -> SsResult.Failed("ScreenScraper says too many unrecognised files were looked up today.")
                else -> SsResult.Failed("ScreenScraper answered HTTP ${response.code}.")
            }
        } catch (e: Exception) {
            SsResult.Failed("Couldn't reach ScreenScraper: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    companion object {
        const val BASE = "https://api.screenscraper.fr/api2"
        const val SIGN_UP = "https://www.screenscraper.fr/"

        /** Cover types, best first: front box, 3D box, then the "mix" images many frontends use. */
        val COVER_TYPES = listOf("box-2D", "box-3D", "mixrbv1", "mixrbv2")
        private val FALLBACK_REGIONS = listOf("wor", "us", "eu", "uk", "jp", "ss", "cus")
        private val secretParams = setOf("devid", "devpassword", "softname", "ssid", "sspassword")

        private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

        /** Covers in [game], ordered by type then by region preference (the user's first, then world/USA/Europe/Japan). */
        fun covers(game: SsGame, preferredRegion: String? = null): List<SsMedia> {
            val regions = listOfNotNull(preferredRegion) + FALLBACK_REGIONS
            return game.medias.filter { it.type in COVER_TYPES }.sortedWith(
                compareBy({ COVER_TYPES.indexOf(it.type) }, { regions.indexOf(it.region).let { i -> if (i < 0) regions.size else i } }),
            )
        }

        /** A media URL with the credentials removed, safe to store or show. */
        fun withoutCredentials(url: String): String {
            val base = url.substringBefore('?')
            val query = url.substringAfter('?', "").split('&').filter { it.isNotEmpty() && it.substringBefore('=') !in secretParams }
            return if (query.isEmpty()) base else base + "?" + query.joinToString("&")
        }

        /** Reads a jeuInfos XML response. DOCTYPEs are refused, so no external entities can be resolved. */
        fun parse(bytes: ByteArray): SsResult {
            val factory = SAXParserFactory.newInstance()
            runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            var gameId: String? = null
            var name: String? = null
            val medias = mutableListOf<SsMedia>()
            var requestsToday: Int? = null
            var maxPerDay: Int? = null
            val handler = object : DefaultHandler() {
                val path = ArrayDeque<String>()
                var attrs: Map<String, String> = emptyMap()
                val text = StringBuilder()
                override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes) {
                    path.addLast(qName)
                    if (qName == "jeu" && gameId == null) gameId = attributes.getValue("id")
                    attrs = (0 until attributes.length).associate { attributes.getQName(it) to attributes.getValue(it) }
                    text.setLength(0)
                }
                override fun characters(ch: CharArray, start: Int, length: Int) { if (text.length < 4096) text.append(ch, start, length) }
                override fun endElement(uri: String?, localName: String?, qName: String) {
                    val parent = path.elementAtOrNull(path.size - 2)
                    val value = text.toString().trim()
                    when {
                        qName == "media" && parent == "medias" && value.startsWith("https://") ->
                            medias += SsMedia(attrs["type"].orEmpty(), attrs["region"], attrs["format"], value)
                        qName == "nom" && parent == "noms" && name == null && value.isNotEmpty() -> name = value
                        qName == "requeststoday" && parent == "ssuser" -> requestsToday = value.toIntOrNull()
                        qName == "maxrequestsperday" && parent == "ssuser" -> maxPerDay = value.toIntOrNull()
                    }
                    path.removeLastOrNull()
                    text.setLength(0)
                }
            }
            return try {
                factory.newSAXParser().parse(InputSource(ByteArrayInputStream(bytes)), handler)
                SsResult.Found(SsGame(gameId, name, medias), SsQuota(requestsToday, maxPerDay))
            } catch (e: Exception) {
                SsResult.Failed("ScreenScraper sent something RetroForge couldn't read.")
            }
        }

        private val json = Json { ignoreUnknownKeys = true }

        @Serializable
        private data class SystemsFile(val systems: Map<String, Int>)

        /** RetroBat-style system folder name -> ScreenScraper system id (screenscraper_systems.json). */
        val systemIds: Map<String, Int> by lazy {
            val text = checkNotNull(ScreenScraper::class.java.getResourceAsStream("/screenscraper_systems.json")) { "screenscraper_systems.json missing" }
                .bufferedReader().use { it.readText() }
            json.decodeFromString(SystemsFile.serializer(), text).systems
        }
    }
}
