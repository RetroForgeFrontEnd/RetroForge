package com.retroforge.hacks

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class CatalogueSource(
    val id: String,
    val name: String,
    /** The https address it was added from, or null for a catalogue file picked on the device. */
    val url: String? = null,
    val addedAt: Long,
    val refreshedAt: Long? = null,
)

data class LoadedCatalogue(val source: CatalogueSource?, val catalogue: HackCatalogue, val warnings: List<String>) {
    val builtIn: Boolean get() = source == null
}

sealed interface AddResult {
    data class Added(val source: CatalogueSource, val warnings: List<String>) : AddResult
    data class Rejected(val reason: String) : AddResult
}

/**
 * Keeps user-added catalogues as validated JSON files in RetroForge's private storage. The built-in catalogue is
 * passed in by the app and can't be removed; null means there is none. Nothing here touches the network.
 */
class CatalogueStore(private val dir: File, private val builtIn: (() -> String)?, private val clock: () -> Long = System::currentTimeMillis) {

    private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
    private val index = File(dir, "sources.json")

    fun sources(): List<CatalogueSource> = runCatching {
        json.decodeFromString(ListSerializer(CatalogueSource.serializer()), index.readText())
    }.getOrDefault(emptyList())

    /** Built-in first, then user sources in the order added. A source whose file no longer parses is reported, not hidden. */
    fun load(): Pair<List<LoadedCatalogue>, List<String>> {
        val loaded = mutableListOf<LoadedCatalogue>()
        val problems = mutableListOf<String>()
        when (val parsed = builtIn?.let { CatalogueParser.parse(it()) }) {
            is ParseResult.Valid -> loaded += LoadedCatalogue(null, parsed.catalogue, parsed.warnings)
            is ParseResult.Invalid -> problems += "Built-in catalogue: ${parsed.reason}"
            null -> {}
        }
        for (source in sources()) {
            val text = runCatching { fileFor(source.id).readText() }.getOrNull()
            when (val parsed = text?.let(CatalogueParser::parse)) {
                is ParseResult.Valid -> loaded += LoadedCatalogue(source, parsed.catalogue, parsed.warnings)
                is ParseResult.Invalid -> problems += "${source.name}: ${parsed.reason}"
                null -> problems += "${source.name}: its saved copy is missing."
            }
        }
        return loaded to problems
    }

    /** Adds a new catalogue, or replaces one with the same id (a refresh). */
    fun add(text: String, url: String?): AddResult {
        val parsed = CatalogueParser.parse(text)
        if (parsed is ParseResult.Invalid) return AddResult.Rejected(parsed.reason)
        val catalogue = (parsed as ParseResult.Valid).catalogue
        if (builtIn?.let { CatalogueParser.parse(it()) }.let { it is ParseResult.Valid && it.catalogue.id == catalogue.id }) {
            return AddResult.Rejected("This catalogue has the same id as the built-in one.")
        }
        val existing = sources()
        val previous = existing.firstOrNull { it.id == catalogue.id }
        val now = clock()
        val source = CatalogueSource(
            id = catalogue.id,
            name = catalogue.name,
            url = url,
            addedAt = previous?.addedAt ?: now,
            refreshedAt = if (previous != null) now else null,
        )
        dir.mkdirs()
        fileFor(source.id).writeText(text)
        writeIndex(existing.filterNot { it.id == source.id } + source)
        return AddResult.Added(source, parsed.warnings)
    }

    fun remove(id: String) {
        writeIndex(sources().filterNot { it.id == id })
        fileFor(id).delete()
    }

    private fun writeIndex(sources: List<CatalogueSource>) {
        dir.mkdirs()
        val temp = File(dir, "sources.json.tmp")
        temp.writeText(json.encodeToString(ListSerializer(CatalogueSource.serializer()), sources))
        if (!temp.renameTo(index)) {
            index.delete()
            temp.renameTo(index)
        }
    }

    // Ids are validated by CatalogueParser (lowercase letters, digits, . _ -), so they are safe as file names.
    private fun fileFor(id: String) = File(dir, "$id.catalogue.json")

    companion object {
        /** All hacks from all catalogues; the same official page listed twice is shown once (first catalogue wins). */
        fun merge(catalogues: List<LoadedCatalogue>): List<ListedHack> {
            val seenPages = mutableSetOf<String>()
            return catalogues.flatMap { loaded ->
                loaded.catalogue.hacks.map { ListedHack(loaded.catalogue.id, loaded.catalogue.name, it) }
            }.filter { seenPages.add(pageKey(it.entry.pageUrl)) }
        }

        /** Normalises a page link so trivially different spellings of the same page compare equal. */
        fun pageKey(url: String): String = url.trimEnd('/').lowercase()
    }
}
