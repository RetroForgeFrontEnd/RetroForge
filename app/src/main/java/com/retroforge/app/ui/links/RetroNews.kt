package com.retroforge.app.ui.links

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.json.JSONArray
import org.json.JSONObject
import org.w3c.dom.Element

/**
 * Retro gaming news, emulator releases and modding, from the sites' and projects' own public RSS and Atom feeds.
 * Headlines and a line of each story are shown as the site wrote them; opening one goes to the site.
 */
object RetroNews {
    enum class Kind { NEWS, EMULATORS, MODDING }

    data class Source(val name: String, val url: String, val kind: Kind)

    data class Story(val source: String, val kind: Kind, val title: String, val link: String, val summary: String, val time: Long)

    /** Each feed checked on 2026-10-31 (it answered with RSS or Atom). */
    val sources = listOf(
        Source("Time Extension", "https://www.timeextension.com/feeds/latest", Kind.NEWS),
        Source("Retro Dodo", "https://retrododo.com/feed/", Kind.NEWS),
        Source("Libretro", "https://www.libretro.com/index.php/feed/", Kind.EMULATORS),
        Source("Dolphin", "https://dolphin-emu.org/blog/feeds/", Kind.EMULATORS),
        Source("RetroArch releases", "https://github.com/libretro/RetroArch/releases.atom", Kind.EMULATORS),
        Source("PPSSPP releases", "https://github.com/hrydgard/ppsspp/releases.atom", Kind.EMULATORS),
        Source("Azahar releases", "https://github.com/azahar-emu/azahar/releases.atom", Kind.EMULATORS),
        Source("melonDS releases", "https://github.com/melonDS-emu/melonDS/releases.atom", Kind.EMULATORS),
        Source("RetroRGB", "https://www.retrorgb.com/feed", Kind.MODDING),
        Source("Hackaday retrocomputing", "https://hackaday.com/category/retrocomputing/feed/", Kind.MODDING),
    )

    /** How old the saved stories can be before opening the news fetches them again. */
    const val FRESH_MS = 3 * 60 * 60 * 1000L
    private const val PER_SOURCE = 15
    private const val SUMMARY = 220

    /** The stories in an RSS or Atom feed, newest first. Throws if it isn't one. */
    fun parse(source: Source, bytes: ByteArray): List<Story> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = false
            isExpandEntityReferences = false
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        }
        val root = factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes)).documentElement
        val atom = root.tagName == "feed"
        val items = root.getElementsByTagName(if (atom) "entry" else "item")
        return (0 until items.length).mapNotNull { i ->
            val item = items.item(i) as Element
            val title = plain(text(item, "title")).takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val link = if (atom) {
                children(item, "link").firstOrNull { it.getAttribute("rel").let { r -> r.isEmpty() || r == "alternate" } }?.getAttribute("href").orEmpty()
            } else text(item, "link")
            if (!link.startsWith("https://") && !link.startsWith("http://")) return@mapNotNull null
            val body = listOf("summary", "description", "content", "content:encoded").firstNotNullOfOrNull { tag -> text(item, tag).takeIf { it.isNotBlank() } }.orEmpty()
            val date = listOf("published", "updated", "pubDate", "dc:date").firstNotNullOfOrNull { tag -> time(text(item, tag)) } ?: 0L
            Story(source.name, source.kind, title, link.trim(), short(plain(body)), date)
        }.sortedByDescending { it.time }.take(PER_SOURCE)
    }

    private fun children(item: Element, tag: String) =
        (0 until item.childNodes.length).map { item.childNodes.item(it) }.filterIsInstance<Element>().filter { it.tagName == tag }

    private fun text(item: Element, tag: String) = children(item, tag).firstOrNull()?.textContent?.trim().orEmpty()

    /** RSS dates (RFC 822) and Atom dates (ISO 8601), as epoch milliseconds. */
    fun time(text: String): Long? {
        val t = text.trim().takeIf { it.isNotEmpty() } ?: return null
        return runCatching { OffsetDateTime.parse(t).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { ZonedDateTime.parse(t, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() }.getOrNull()
            ?: runCatching { java.text.SimpleDateFormat("EEE, d MMM yyyy HH:mm:ss zzz", java.util.Locale.US).parse(t)?.time }.getOrNull()
    }

    private val entities = mapOf("amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ", "hellip" to "…", "rsquo" to "’", "lsquo" to "‘", "rdquo" to "”", "ldquo" to "“", "ndash" to "–", "mdash" to "—")

    /** HTML as plain text: tags gone, entities turned back into characters, spaces tidied. */
    fun plain(html: String): String = html
        .replace(Regex("(?is)<(script|style)[^>]*>.*?</\\1>"), " ")
        .replace(Regex("<[^>]+>"), " ")
        .replace(Regex("&(#x[0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);")) { m ->
            val e = m.groupValues[1]
            when {
                e.startsWith("#x") -> e.drop(2).toIntOrNull(16)?.let { String(Character.toChars(it)) }
                e.startsWith("#") -> e.drop(1).toIntOrNull()?.let { String(Character.toChars(it)) }
                else -> entities[e]
            } ?: m.value
        }
        .replace(Regex("\\s+"), " ").trim()

    private fun short(text: String) = if (text.length <= SUMMARY) text else text.take(SUMMARY).substringBeforeLast(' ') + "…"

    /** What a fetch found: every story, newest first, and the sources that couldn't be read. */
    data class Fetched(val stories: List<Story>, val failed: List<String>, val time: Long)

    /** Every source at once; [get] fetches one address. */
    suspend fun fetch(get: (String) -> ByteArray): Fetched = coroutineScope {
        val results = sources.map { source -> async(Dispatchers.IO) { source to runCatching { parse(source, get(source.url)) } } }.awaitAll()
        Fetched(
            results.flatMap { it.second.getOrDefault(emptyList()) }.sortedByDescending { it.time },
            results.filter { it.second.isFailure }.map { it.first.name },
            System.currentTimeMillis(),
        )
    }

    fun download(url: String, userAgent: String): ByteArray {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", userAgent)
        }
        try {
            if (connection.responseCode != 200) throw IOException("HTTP ${connection.responseCode}")
            return connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    fun save(file: File, fetched: Fetched) {
        val stories = JSONArray()
        fetched.stories.forEach { s ->
            stories.put(JSONObject().put("source", s.source).put("kind", s.kind.name).put("title", s.title).put("link", s.link).put("summary", s.summary).put("time", s.time))
        }
        file.parentFile?.mkdirs()
        file.writeText(JSONObject().put("time", fetched.time).put("stories", stories).put("failed", JSONArray(fetched.failed)).toString())
    }

    fun load(file: File): Fetched? = runCatching {
        val json = JSONObject(file.readText())
        val stories = json.getJSONArray("stories")
        val failed = json.optJSONArray("failed")
        Fetched(
            (0 until stories.length()).map { i ->
                val s = stories.getJSONObject(i)
                Story(s.getString("source"), Kind.valueOf(s.getString("kind")), s.getString("title"), s.getString("link"), s.getString("summary"), s.getLong("time"))
            },
            (0 until (failed?.length() ?: 0)).map { failed!!.getString(it) },
            json.getLong("time"),
        )
    }.getOrNull()
}
