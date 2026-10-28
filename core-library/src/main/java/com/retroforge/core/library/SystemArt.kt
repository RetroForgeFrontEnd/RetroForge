package com.retroforge.core.library

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** Artwork for one system, read from a theme folder the user chose (never shipped with RetroForge). */
@Serializable
data class SystemArtwork(
    /** Square artwork tile (canvas-es carousel art, Alekfull-NX square logos …). */
    val tile: String? = null,
    /** The system's logo or name artwork (often SVG). */
    val logo: String? = null,
    /** A picture of the console itself (canvas-es carousel icons). */
    val console: String? = null,
    val year: String? = null,
    val manufacturer: String? = null,
    /** "RRGGBB" from the theme's metadata. */
    val color: String? = null,
)

@Serializable
data class SystemArtIndex(
    val treeUri: String,
    val themeName: String,
    val systems: Map<String, SystemArtwork>,
    /** A full-screen background from the theme, if it has one. */
    val wallpaper: String? = null,
    /** Index format; older indexes are rebuilt so new kinds of artwork are found. */
    val version: Int = 1,
)

/**
 * Finds per-system artwork in a frontend theme folder by file name (`<system>.webp|png|jpg|svg`, `<system>.xml`),
 * using the folder layouts of canvas-es / ES-DE themes and Alekfull-NX. The theme's files are only read, never
 * copied or changed; the index (document URIs) is kept in [cacheFile] so the folder isn't walked every start.
 */
class SystemArtStore(private val resolver: ContentResolver, private val cacheFile: File) {

    private val json = Json { ignoreUnknownKeys = true }

    fun cached(): SystemArtIndex? = runCatching { json.decodeFromString<SystemArtIndex>(cacheFile.readText()) }.getOrNull()

    fun clear() {
        cacheFile.delete()
    }

    /** Walks the theme folder and saves the index. Runs on a background thread. */
    fun index(treeUri: Uri, themeName: String): SystemArtIndex {
        val tiles = mutableMapOf<String, String>()
        val logos = mutableMapOf<String, String>()
        val consoles = mutableMapOf<String, String>()
        val metadata = mutableMapOf<String, String>()
        val wallpapers = mutableMapOf<String, String>()
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)

        fun walk(docId: String, path: List<String>) {
            if (path.size > 6) return
            for ((childId, name, mime) in children(treeUri, docId)) {
                val lower = name.lowercase()
                if (mime == Document.MIME_TYPE_DIR) {
                    if (lower in SKIP_DIRS) continue
                    walk(childId, path + lower)
                    continue
                }
                val ext = lower.substringAfterLast('.', "")
                val base = lower.substringBeforeLast('.')
                val parent = path.lastOrNull().orEmpty()
                val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, childId).toString()
                when {
                    parent == "metadata" && ext == "xml" -> metadata.putIfAbsent(base, uri)
                    "wallpaper" in parent && ext in RASTER -> wallpapers.putIfAbsent(base, uri)
                    ext == "svg" && "logo" in parent -> logos.putIfAbsent(base, uri)
                    ext in RASTER && ("icons-art" in parent || parent == "art") -> tiles.putIfAbsent(base, uri)
                    ext in RASTER && ("icons-icons" in parent || parent == "consoles" || parent == "devices") -> consoles.putIfAbsent(base, uri)
                    // Alekfull-NX: square raster tiles in assets/logos, name artwork in assets/logos2.
                    ext in RASTER && parent == "logos" -> tiles.putIfAbsent(base, uri)
                    ext in RASTER && parent == "logos2" -> logos.putIfAbsent(base, uri)
                }
            }
        }
        walk(rootId, emptyList())

        val ids = (tiles.keys + logos.keys + consoles.keys + metadata.keys).filter { !it.startsWith("_") }.toSet()
        val systems = ids.associateWith { id ->
            val meta = metadata[id]?.let { readMetadata(Uri.parse(it)) }.orEmpty()
            SystemArtwork(
                tile = tiles[id],
                logo = logos[id],
                console = consoles[id],
                year = meta["systemReleaseYear"],
                manufacturer = meta["systemManufacturer"],
                color = meta["systemColor"]?.takeIf { it.matches(Regex("[0-9A-Fa-f]{6}")) },
            )
        }
        val wallpaper = wallpapers["dark"] ?: wallpapers.values.firstOrNull()
        val result = SystemArtIndex(treeUri.toString(), themeName, systems, wallpaper, VERSION)
        cacheFile.parentFile?.mkdirs()
        cacheFile.writeText(json.encodeToString(SystemArtIndex.serializer(), result))
        return result
    }

    private data class Child(val id: String, val name: String, val mime: String)

    private fun children(tree: Uri, parentId: String): List<Child> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        return resolver.query(uri, arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE), null, null, null)
            ?.use { c -> generateSequence { if (c.moveToNext()) Child(c.getString(0), c.getString(1), c.getString(2)) else null }.toList() }
            .orEmpty()
    }

    /** `<systemReleaseYear>1989</systemReleaseYear>` and friends from an ES-DE theme metadata file. */
    private fun readMetadata(uri: Uri): Map<String, String> = runCatching {
        val text = resolver.openInputStream(uri)?.use { it.readBytes().take(64 * 1024).toByteArray().decodeToString() }.orEmpty()
        Regex("<(system[A-Za-z]+)>([^<]{1,200})</\\1>").findAll(text).associate { it.groupValues[1] to it.groupValues[2].trim() }
    }.getOrDefault(emptyMap())

    companion object {
        const val VERSION = 2
        private val RASTER = setOf("webp", "png", "jpg", "jpeg")
        private val SKIP_DIRS = setOf("fonts", "sounds", "uisounds", "lang", "colorsets", "iconsets")
    }
}
