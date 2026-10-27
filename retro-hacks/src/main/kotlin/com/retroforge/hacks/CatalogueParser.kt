package com.retroforge.hacks

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

sealed interface ParseResult {
    /** [warnings] describe entries that were dropped or references that don't resolve. */
    data class Valid(val catalogue: HackCatalogue, val warnings: List<String>) : ParseResult
    data class Invalid(val reason: String) : ParseResult
}

/**
 * Parses untrusted catalogue JSON. Structural problems reject the whole file; problems in a single entry drop
 * that entry with a warning, so one bad hack doesn't hide the rest. Text is length-limited and links must be HTTPS.
 */
object CatalogueParser {

    const val FORMAT = "retroforge-hack-catalogue"
    const val SUPPORTED_VERSION = 1
    const val MAX_BYTES = 2 * 1024 * 1024
    private const val MAX_HACKS = 5000

    private val idPattern = Regex("[a-z0-9][a-z0-9._-]{0,63}")
    private val systemPattern = Regex("[a-z0-9][a-z0-9+_-]{0,31}")
    private val crcPattern = Regex("[0-9A-Fa-f]{8}")
    private val sha1Pattern = Regex("[0-9A-Fa-f]{40}")

    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): ParseResult {
        if (text.length > MAX_BYTES) return ParseResult.Invalid("The catalogue is larger than 2 MB.")
        if (text.trimStart().startsWith("<")) {
            return ParseResult.Invalid("That's a web page, not a catalogue file. Use the link to the catalogue's .json file itself.")
        }
        val catalogue = try {
            json.decodeFromString(HackCatalogue.serializer(), text)
        } catch (e: SerializationException) {
            return ParseResult.Invalid("Not a valid catalogue file: ${e.message?.lineSequence()?.firstOrNull()?.take(200)}")
        } catch (e: IllegalArgumentException) {
            return ParseResult.Invalid("Not a valid catalogue file: ${e.message?.take(200)}")
        }
        if (catalogue.format != FORMAT) return ParseResult.Invalid("This isn't a RetroForge hack catalogue (format \"${catalogue.format.take(60)}\").")
        if (catalogue.version > SUPPORTED_VERSION) {
            return ParseResult.Invalid("This catalogue uses format version ${catalogue.version}; this RetroForge supports up to $SUPPORTED_VERSION. Update RetroForge.")
        }
        if (!idPattern.matches(catalogue.id)) return ParseResult.Invalid("The catalogue id \"${catalogue.id.take(60)}\" isn't valid (lowercase letters, digits, . _ -).")
        if (catalogue.name.isBlank() || catalogue.name.length > 100) return ParseResult.Invalid("The catalogue needs a name of up to 100 characters.")
        if (catalogue.hacks.size > MAX_HACKS) return ParseResult.Invalid("The catalogue lists more than $MAX_HACKS hacks.")

        val warnings = mutableListOf<String>()
        val seen = mutableSetOf<String>()
        val kept = catalogue.hacks.filter { entry ->
            val problem = problemWith(entry) ?: if (!seen.add(entry.id)) "duplicate id" else null
            if (problem != null) warnings += "Skipped \"${entry.title.take(60)}\": $problem."
            problem == null
        }
        val keptIds = kept.map { it.id }.toSet()
        kept.forEach { entry ->
            entry.requires.filterNot { it in keptIds }.forEach { missing ->
                warnings += "\"${entry.title.take(60)}\" requires \"$missing\", which isn't in this catalogue."
            }
        }
        return ParseResult.Valid(catalogue.copy(name = catalogue.name.trim(), hacks = kept), warnings)
    }

    private fun problemWith(entry: HackEntry): String? = when {
        !idPattern.matches(entry.id) -> "invalid id"
        entry.title.isBlank() || entry.title.length > 200 -> "title missing or too long"
        !systemPattern.matches(entry.system) -> "invalid system id"
        entry.baseGame.isBlank() || entry.baseGame.length > 200 -> "base game missing or too long"
        entry.summary.isBlank() || entry.summary.length > 1000 -> "summary missing or longer than 1000 characters"
        !isHttps(entry.pageUrl) -> "page link must be an https:// address"
        entry.authors.size > 20 || entry.authors.any { it.length > 100 } -> "too many or too long author names"
        entry.tags.size > 20 || entry.tags.any { it.length > 40 } -> "too many or too long tags"
        entry.baseRom?.crc32?.let { !crcPattern.matches(it) } == true -> "base ROM CRC32 must be 8 hex digits"
        entry.baseRom?.sha1?.let { !sha1Pattern.matches(it) } == true -> "base ROM SHA-1 must be 40 hex digits"
        else -> null
    }

    fun isHttps(url: String): Boolean =
        url.length <= 2000 && url.startsWith("https://") && url.length > "https://".length && url.none { it.isWhitespace() }
}
