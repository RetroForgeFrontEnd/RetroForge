package com.retroforge.library

import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.InputStream
import javax.xml.parsers.SAXParserFactory

/** Metadata an EmulationStation gamelist.xml (RetroBat, ES-DE, Batocera) holds for one game. */
data class GameMetadata(
    val name: String? = null,
    val description: String? = null,
    val releaseDate: String? = null,
    val developer: String? = null,
    val publisher: String? = null,
    val genre: String? = null,
    val players: String? = null,
    val rating: Float? = null,
    val image: String? = null,
    val thumbnail: String? = null,
    val marquee: String? = null,
)

/**
 * Reads gamelist.xml without modifying it. Entries are keyed by [normalizePath] of their `<path>`.
 * DOCTYPEs are refused so external entities can never be resolved.
 */
object GamelistParser {

    const val MAX_BYTES = 32L * 1024 * 1024
    private const val MAX_FIELD = 8000
    private val fields = setOf("path", "name", "desc", "releasedate", "developer", "publisher", "genre", "players", "rating", "image", "thumbnail", "marquee")

    fun parse(input: InputStream): Map<String, GameMetadata> {
        val result = mutableMapOf<String, GameMetadata>()
        val factory = SAXParserFactory.newInstance()
        runCatching { factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        val handler = object : DefaultHandler() {
            var depth = 0
            var inGame = false
            var current: MutableMap<String, String>? = null
            val text = StringBuilder()

            override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes?) {
                depth++
                if (qName == "game" && depth == 2) { inGame = true; current = mutableMapOf() }
                text.setLength(0)
            }

            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (inGame && text.length < MAX_FIELD) text.append(ch, start, minOf(length, MAX_FIELD - text.length))
            }

            override fun endElement(uri: String?, localName: String?, qName: String) {
                val game = current
                if (inGame && game != null) {
                    if (depth == 3 && qName in fields) game[qName] = text.toString().trim()
                    if (qName == "game" && depth == 2) {
                        game["path"]?.takeIf { it.isNotEmpty() }?.let { result[normalizePath(it)] = toMetadata(game) }
                        inGame = false
                        current = null
                    }
                }
                text.setLength(0)
                depth--
            }
        }
        factory.newSAXParser().parse(InputSource(input), handler)
        return result
    }

    /** "./SMW HACKS\\Foo.smc" → "smw hacks/foo.smc". Paths are compared case-insensitively. */
    fun normalizePath(path: String): String =
        path.trim().replace('\\', '/').removePrefix("./").trimStart('/').lowercase()

    private fun toMetadata(f: Map<String, String>) = GameMetadata(
        name = f["name"]?.ifEmpty { null },
        description = f["desc"]?.ifEmpty { null },
        releaseDate = f["releasedate"]?.let(::formatDate),
        developer = f["developer"]?.ifEmpty { null },
        publisher = f["publisher"]?.ifEmpty { null },
        genre = f["genre"]?.ifEmpty { null },
        players = f["players"]?.ifEmpty { null },
        rating = f["rating"]?.toFloatOrNull()?.takeIf { it in 0f..1f },
        image = f["image"]?.ifEmpty { null },
        thumbnail = f["thumbnail"]?.ifEmpty { null },
        marquee = f["marquee"]?.ifEmpty { null },
    )

    /** ES dates look like "19930301T000000"; keep YYYY-MM-DD (or just the year when that's all there is). */
    private fun formatDate(raw: String): String? {
        val digits = raw.takeWhile { it.isDigit() }
        return when {
            digits.length >= 8 -> "${digits.substring(0, 4)}-${digits.substring(4, 6)}-${digits.substring(6, 8)}"
            digits.length >= 4 -> digits.substring(0, 4)
            else -> null
        }
    }
}
