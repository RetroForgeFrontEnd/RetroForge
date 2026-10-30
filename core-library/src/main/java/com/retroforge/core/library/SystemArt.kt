package com.retroforge.core.library

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/** `<systemReleaseYear>1989</systemReleaseYear>` and friends from an ES-DE theme metadata file. */
internal fun parseMetadata(text: String): Map<String, String> =
    Regex("<(system[A-Za-z]+)>([^<]{1,200})</\\1>").findAll(text).associate { it.groupValues[1] to it.groupValues[2].trim() }

/**
 * RetroForge's system ids (RetroBat's) that themes file under another name: canvas-es and ES-DE use "gc" for GameCube,
 * "atarilynx" for Lynx and so on. A theme's own file for the RetroForge id still wins.
 */
val themeNames = mapOf(
    "gamecube" to "gc", "jaguar" to "atarijaguar", "jaguarcd" to "atarijaguarcd", "lynx" to "atarilynx",
    "sg1000" to "sg-1000", "wswan" to "wonderswan", "wswanc" to "wonderswancolor", "megadrive-msu" to "msu-md",
    "oricatmos" to "oric", "doom3" to "boom3", "gw" to "gameandwatch", "zeldaclassic" to "zeldac", "Windows" to "windows",
)

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
            .toMutableMap()
        themeNames.forEach { (ours, theirs) -> if (ours !in systems) systems[theirs]?.let { systems[ours] = it } }
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
        parseMetadata(resolver.openInputStream(uri)?.use { it.readBytes().take(64 * 1024).toByteArray().decodeToString() }.orEmpty())
    }.getOrDefault(emptyMap())

    companion object {
        const val VERSION = 2
        private val RASTER = setOf("webp", "png", "jpg", "jpeg")
        private val SKIP_DIRS = setOf("fonts", "sounds", "uisounds", "lang", "colorsets", "iconsets")
    }
}

/**
 * The Retro tab's default artwork: canvas-es by Siddy212 (CC0-1.0, github.com/Siddy212/canvas-es), fetched from its
 * official repository for the systems in the library only, into RetroForge's private storage. Nothing is bundled
 * with the app. Console pictures, logos and details are small and fetched in the background; the larger game-art
 * tiles only when [withGameArt].
 */
class CanvasArtDownloader(private val dir: java.io.File, private val userAgent: String) {

    private val json = Json { ignoreUnknownKeys = true }
    private val cache = java.io.File(dir, "index.json")
    private val _index = kotlinx.coroutines.flow.MutableStateFlow(load())
    val index: kotlinx.coroutines.flow.StateFlow<SystemArtIndex?> = _index
    private val lock = kotlinx.coroutines.sync.Mutex()

    private fun load(): SystemArtIndex? = runCatching { json.decodeFromString<SystemArtIndex>(cache.readText()) }.getOrNull()

    /** Fetches what's missing for [systems]; files already here (or missing upstream) aren't asked for again. */
    suspend fun ensure(systems: Collection<String>, withGameArt: Boolean) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        lock.withLock {
            val known = _index.value?.systems.orEmpty().toMutableMap()
            for (id in systems.distinct()) {
                val other = themeNames[id]
                fun get(kind: String, ext: String) = fetch(kind, id, ext) ?: other?.let { fetch(kind, it, ext) }
                val console = get("carousel-icons-icons", "webp")
                val logo = get("logos", "svg")
                val meta = get("metadata", "xml")?.let { runCatching { parseMetadata(it.readText()) }.getOrNull() }.orEmpty()
                val art = if (withGameArt) get("carousel-icons-art", "webp")
                else local("carousel-icons-art", id, "webp") ?: other?.let { local("carousel-icons-art", it, "webp") }
                if (console == null && logo == null && art == null && meta.isEmpty()) continue
                known[id] = SystemArtwork(
                    tile = art?.uri(), logo = logo?.uri(), console = console?.uri(),
                    year = meta["systemReleaseYear"], manufacturer = meta["systemManufacturer"],
                    color = meta["systemColor"]?.takeIf { it.matches(Regex("[0-9A-Fa-f]{6}")) },
                )
            }
            val index = SystemArtIndex(treeUri = "", themeName = "canvas-es", systems = known, wallpaper = null, version = SystemArtStore.VERSION)
            dir.mkdirs()
            cache.writeText(json.encodeToString(SystemArtIndex.serializer(), index))
            _index.value = index
        }
    }

    private fun local(kind: String, id: String, ext: String) = java.io.File(dir, "$kind/$id.$ext").takeIf { it.isFile }

    private fun java.io.File.uri() = android.net.Uri.fromFile(this).toString()

    /** The file, downloading it first if needed; null when canvas-es has none for [id] or the download fails. */
    private fun fetch(kind: String, id: String, ext: String): java.io.File? {
        local(kind, id, ext)?.let { return it }
        val missing = java.io.File(dir, "$kind/$id.$ext.none")
        if (missing.isFile) return null
        val connection = (java.net.URL("$BASE/$kind/$id.$ext").openConnection() as java.net.HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", userAgent)
        }
        return try {
            when (connection.responseCode) {
                200 -> {
                    val target = java.io.File(dir, "$kind/$id.$ext").apply { parentFile?.mkdirs() }
                    val part = java.io.File(target.path + ".part")
                    connection.inputStream.use { input -> part.outputStream().use { input.copyTo(it) } }
                    if (part.renameTo(target)) target else null
                }
                404 -> {
                    missing.parentFile?.mkdirs()
                    missing.writeText("")
                    null
                }
                else -> null
            }
        } catch (_: java.io.IOException) {
            null
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        const val BASE = "https://raw.githubusercontent.com/Siddy212/canvas-es/main/_inc/systems"
        const val CREDIT = "System artwork: canvas-es by Siddy212 (CC0-1.0), github.com/Siddy212/canvas-es"
    }
}
