package com.retroforge.library

import com.retroforge.library.manage.HOLDING_FOLDERS
import java.io.IOException
import java.io.InputStream

/**
 * A read-only view of a folder tree. Implemented over Android's Storage Access Framework in the app and over
 * java.io.File in tests. Implementations must never write.
 */
interface ScanNode {
    /** Opaque, stable identifier (a SAF document id or an absolute path). */
    val id: String
    val name: String
    val isDirectory: Boolean
    val size: Long
    val lastModified: Long

    /** @throws IOException when the folder can't be listed (permission revoked, drive removed …). */
    fun children(): List<ScanNode>

    fun open(): InputStream
}

enum class RootLayout {
    /** A folder of system folders, like RetroBat's or ES-DE's `roms`. */
    COLLECTION,
    /** A single system folder, e.g. `snes`. */
    SINGLE_SYSTEM,
    UNRECOGNISED,
}

data class ScannedGame(
    val system: String,
    /** Path from the picked folder, "/"-separated, e.g. "snes/SMW HACKS/Foo.smc". */
    val relativePath: String,
    val fileName: String,
    val nodeId: String,
    val size: Long,
    val lastModified: Long,
    val name: RomName,
    /** The hack folder this game sits in ("SMW HACKS"), or "" when the file name alone marks it as a hack. */
    val hackFolder: String?,
    val metadata: GameMetadata?,
    val imageNodeId: String?,
    /** Same game, same tags, different file (e.g. a .zip and a .7z copy). Equal keys mean duplicates. */
    val duplicateKey: String?,
    /** Disc count for an .m3u playlist. */
    val discs: Int?,
) {
    val isHack: Boolean get() = hackFolder != null
    val displayTitle: String get() = metadata?.name ?: name.title
}

data class ScanProblem(val path: String, val message: String)

data class ScanProgress(val systemsDone: Int, val systemsTotal: Int, val currentSystem: String?, val filesSeen: Int)

data class ScanResult(
    val layout: RootLayout,
    val games: List<ScannedGame>,
    val problems: List<ScanProblem>,
    /** Folders in a collection root that didn't match any known system. */
    val unrecognisedFolders: List<String>,
)

class ScanCancelled : Exception()

/**
 * Finds games under a folder the user picked. Read-only: it lists folders and reads only gamelist.xml and
 * playlist files (.m3u/.cue/.gdi, to hide the disc and track files they list). It never writes.
 */
class LibraryScanner(private val rules: SystemRules) {

    private val mediaFolders = setOf(
        "images", "image", "media", "downloaded_media", "videos", "video", "snap", "snaps", "mixart", "wheel", "wheels",
        "marquee", "marquees", "manuals", "manual", "boxart", "box", "covers", "screenshots", "thumbnails", "titles",
        "fanart", "maps", "bezels", "cartridges", "fronts",
    )
    private val ignoredFiles = setOf("gamelist.xml", "_info.txt", "systeminfo.txt", "metadata.txt", "desktop.ini", "thumbs.db", ".ds_store")
    private val imageExtensions = setOf("png", "jpg", "jpeg", "webp", "gif")
    private val playlistExtensions = setOf("m3u", "cue", "gdi")
    private val maxDepth = 6

    fun scan(root: ScanNode, onProgress: (ScanProgress) -> Unit = {}, isCancelled: () -> Boolean = { false }): ScanResult {
        val problems = mutableListOf<ScanProblem>()
        val topChildren = try {
            root.children()
        } catch (e: IOException) {
            return ScanResult(RootLayout.UNRECOGNISED, emptyList(), listOf(ScanProblem(root.name, "Can't open this folder: ${e.message}")), emptyList())
        }

        val (layout, systemDirs, unrecognised) = detectLayout(root, topChildren, problems)
        val games = mutableListOf<ScannedGame>()
        var filesSeen = 0
        systemDirs.forEachIndexed { index, (prefix, dir, rule) ->
            if (isCancelled()) throw ScanCancelled()
            onProgress(ScanProgress(index, systemDirs.size, rule.name, filesSeen))
            val found = scanSystem(prefix, dir, rule, problems, isCancelled)
            filesSeen += found.size
            games += found
        }
        onProgress(ScanProgress(systemDirs.size, systemDirs.size, null, filesSeen))
        return ScanResult(layout, markDuplicates(games), problems, unrecognised)
    }

    private data class SystemDir(val prefix: String, val dir: ScanNode, val rule: SystemRule)

    private fun detectLayout(root: ScanNode, children: List<ScanNode>, problems: MutableList<ScanProblem>): Triple<RootLayout, List<SystemDir>, List<String>> {
        rules.forFolder(root.name)?.let { return Triple(RootLayout.SINGLE_SYSTEM, listOf(SystemDir("", root, it)), emptyList()) }

        val dirs = children.filter { it.isDirectory }
        val matched = dirs.mapNotNull { dir -> rules.forFolder(dir.name)?.let { SystemDir(dir.name + "/", dir, it) } }
        if (matched.isNotEmpty()) {
            val unrecognised = dirs.filter { rules.forFolder(it.name) == null && it.name.lowercase() !in HOLDING_FOLDERS }.map { it.name }.sorted()
            return Triple(RootLayout.COLLECTION, matched.sortedBy { it.dir.name.lowercase() }, unrecognised)
        }
        // The user may have picked the folder above "roms" (e.g. the RetroBat folder itself).
        dirs.firstOrNull { it.name.equals("roms", ignoreCase = true) }?.let { roms ->
            val inner = try { roms.children() } catch (e: IOException) {
                problems += ScanProblem(roms.name, "Can't open: ${e.message}")
                emptyList()
            }
            val nested = inner.filter { it.isDirectory }.mapNotNull { dir -> rules.forFolder(dir.name)?.let { SystemDir("${roms.name}/${dir.name}/", dir, it) } }
            if (nested.isNotEmpty()) {
                val unrecognised = inner.filter { it.isDirectory && rules.forFolder(it.name) == null && it.name.lowercase() !in HOLDING_FOLDERS }.map { it.name }.sorted()
                return Triple(RootLayout.COLLECTION, nested.sortedBy { it.dir.name.lowercase() }, unrecognised)
            }
        }
        problems += ScanProblem(root.name, "No system folders found. Pick a \"roms\" folder (containing snes, nes, psx …) or a single system folder.")
        return Triple(RootLayout.UNRECOGNISED, emptyList(), emptyList())
    }

    private data class FileEntry(val path: String, val node: ScanNode)

    private fun scanSystem(prefix: String, dir: ScanNode, rule: SystemRule, problems: MutableList<ScanProblem>, isCancelled: () -> Boolean): List<ScannedGame> {
        val romFiles = mutableListOf<FileEntry>()
        val imageIndex = mutableMapOf<String, String>()
        var gamelistNode: ScanNode? = null
        var tooDeep = 0

        fun walk(node: ScanNode, path: String, depth: Int, inMedia: Boolean) {
            if (isCancelled()) throw ScanCancelled()
            val children = try {
                node.children()
            } catch (e: IOException) {
                problems += ScanProblem((prefix + path).trimEnd('/'), "Can't open folder: ${e.message}")
                return
            }
            for (child in children) {
                val childPath = if (path.isEmpty()) child.name else "$path/${child.name}"
                val ext = child.name.substringAfterLast('.', "").lowercase()
                when {
                    child.name.startsWith(".") -> Unit
                    child.isDirectory && child.name.lowercase() in HOLDING_FOLDERS -> Unit
                    // EmulationStation treats a folder named like a game ("Skate (EUR).ps3", "XCOM.pc") as one game.
                    child.isDirectory && !inMedia && ext.isNotEmpty() && ext in rule.extensions -> romFiles += FileEntry(childPath, child)
                    child.isDirectory -> {
                        val media = inMedia || child.name.lowercase() in mediaFolders
                        if (depth < maxDepth) walk(child, childPath, depth + 1, media) else tooDeep++
                    }
                    inMedia -> if (ext in imageExtensions) imageIndex[childPath.lowercase()] = child.id
                    depth == 0 && child.name.equals("gamelist.xml", ignoreCase = true) -> gamelistNode = child
                    child.name.lowercase() in ignoredFiles -> Unit
                    ext in rule.extensions -> romFiles += FileEntry(childPath, child)
                }
            }
        }
        walk(dir, "", 0, inMedia = false)
        if (tooDeep > 0) problems += ScanProblem(prefix.trimEnd('/').ifEmpty { dir.name }, "$tooDeep folders nested more than $maxDepth levels deep were not scanned.")

        val metadata = gamelistNode?.let { node ->
            try {
                if (node.size > GamelistParser.MAX_BYTES) {
                    problems += ScanProblem(prefix + node.name, "gamelist.xml is larger than 32 MB; ignored.")
                    null
                } else {
                    node.open().use(GamelistParser::parse)
                }
            } catch (e: Exception) {
                problems += ScanProblem(prefix + node.name, "Couldn't read gamelist.xml (${e.message?.take(120)}); titles come from file names.")
                null
            }
        }.orEmpty()

        val hidden = referencedByPlaylists(romFiles, prefix, problems)
        return romFiles.filter { it.path.lowercase() !in hidden }.map { entry ->
            val name = RomNames.parse(entry.node.name)
            val meta = metadata[entry.path.lowercase()]
            val image = listOfNotNull(meta?.image, meta?.thumbnail, meta?.marquee)
                .firstNotNullOfOrNull { imageIndex[GamelistParser.normalizePath(it)] }
            ScannedGame(
                system = rule.id,
                relativePath = prefix + entry.path,
                fileName = entry.node.name,
                nodeId = entry.node.id,
                size = if (entry.node.isDirectory) 0 else entry.node.size,
                lastModified = entry.node.lastModified,
                name = name,
                hackFolder = hackFolderOf(entry.path) ?: if (RomFlag.HACK in name.flags) "" else null,
                metadata = meta,
                imageNodeId = image,
                duplicateKey = null,
                discs = if (!entry.node.isDirectory && entry.node.name.endsWith(".m3u", ignoreCase = true)) discCount(entry) else null,
            )
        }
    }

    /** Folder segment containing "hack" (e.g. "SMW HACKS"); nested ones report the outermost. */
    private fun hackFolderOf(path: String): String? =
        path.split('/').dropLast(1).firstOrNull { it.contains("hack", ignoreCase = true) }

    /** Files listed inside .m3u/.cue/.gdi playlists in the same system, so multi-file games appear once. */
    private fun referencedByPlaylists(files: List<FileEntry>, prefix: String, problems: MutableList<ScanProblem>): Set<String> {
        val hidden = mutableSetOf<String>()
        val playlists = files.filter { !it.node.isDirectory && it.node.name.substringAfterLast('.', "").lowercase() in playlistExtensions }
        // .m3u first: a .cue listed by an .m3u is hidden, and so are the tracks that .cue lists.
        for (playlist in playlists.sortedBy { if (it.node.name.endsWith(".m3u", true)) 0 else 1 }) {
            val dirPath = playlist.path.substringBeforeLast('/', "")
            val references = try {
                readReferences(playlist.node)
            } catch (e: IOException) {
                problems += ScanProblem(prefix + playlist.path, "Couldn't read playlist: ${e.message}")
                continue
            }
            references.forEach { ref ->
                val resolved = (if (dirPath.isEmpty()) ref else "$dirPath/$ref").lowercase()
                if (resolved != playlist.path.lowercase()) hidden += resolved
            }
        }
        return hidden
    }

    private fun readReferences(node: ScanNode): List<String> {
        if (node.size > 256 * 1024) return emptyList()
        val text = node.open().use { it.readBytes() }.toString(Charsets.UTF_8)
        val ext = node.name.substringAfterLast('.').lowercase()
        return text.lineSequence().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.mapNotNull { line ->
            when (ext) {
                "m3u" -> line
                "cue" -> Regex("""^FILE\s+"([^"]+)"""", RegexOption.IGNORE_CASE).find(line)?.groupValues?.get(1)
                    ?: Regex("""^FILE\s+(\S+)""", RegexOption.IGNORE_CASE).find(line)?.groupValues?.get(1)
                "gdi" -> line.split(Regex("\\s+")).getOrNull(4)?.trim('"')
                else -> null
            }
        }.map { it.replace('\\', '/').removePrefix("./") }.toList()
    }

    private fun discCount(entry: FileEntry): Int? = runCatching { readReferences(entry.node).size }.getOrNull()?.takeIf { it > 0 }

    private fun markDuplicates(games: List<ScannedGame>): List<ScannedGame> {
        fun key(g: ScannedGame) = listOf(g.system, g.name.sortKey, g.name.tags.joinToString("|").lowercase(), g.hackFolder ?: "").joinToString("\u0000")
        val counts = games.groupingBy(::key).eachCount()
        return games.map { if ((counts[key(it)] ?: 0) > 1) it.copy(duplicateKey = key(it)) else it }
    }
}
