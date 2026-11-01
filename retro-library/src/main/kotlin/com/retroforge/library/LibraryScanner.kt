package com.retroforge.library

import com.retroforge.library.manage.HOLDING_FOLDERS
import java.io.IOException
import java.io.InputStream
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

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
        // Games outside system folders: loose files and folders not named after a system, sorted by file type.
        val (looseBase, loosePrefix) = when {
            layout == RootLayout.SINGLE_SYSTEM -> null to ""
            systemDirs.firstOrNull()?.prefix?.startsWith("roms/", ignoreCase = true) == true ->
                topChildren.firstOrNull { it.isDirectory && it.name.equals("roms", ignoreCase = true) } to systemDirs.first().prefix.substringBefore('/') + "/"
            else -> root to ""
        }
        val loose = looseBase?.let { base -> scanLoose(base, loosePrefix, problems, isCancelled) }.orEmpty()
        games += loose
        val looseFolders = loose.map { it.relativePath.removePrefix(loosePrefix).substringBefore('/', "") }.toSet()
        var filesSeen = 0
        systemDirs.forEachIndexed { index, (prefix, dir, rule) ->
            if (isCancelled()) throw ScanCancelled()
            onProgress(ScanProgress(index, systemDirs.size, rule.name, filesSeen))
            val found = scanSystem(prefix, dir, rule, problems, isCancelled)
            filesSeen += found.size
            games += found
        }
        onProgress(ScanProgress(systemDirs.size, systemDirs.size, null, filesSeen))
        val finalLayout = if (layout == RootLayout.UNRECOGNISED && loose.isNotEmpty()) RootLayout.COLLECTION else layout
        if (finalLayout != layout) problems.removeAll { it.message.startsWith("No system folders found") }
        // Folders that never hold games (a frontend's BIOS or saves) aren't worth mentioning.
        val reported = unrecognised.filter { it !in looseFolders && it.substringAfterLast('/').lowercase() !in NON_GAME_FOLDERS }
        return ScanResult(finalLayout, markDuplicates(games), problems, reported)
    }

    private data class SystemDir(val prefix: String, val dir: ScanNode, val rule: SystemRule)

    /** How many folders are opened looking for system folders one level down (each is a directory listing, slow over a network). */
    private val MAX_NESTED_LOOKS = 12

    private fun detectLayout(root: ScanNode, children: List<ScanNode>, problems: MutableList<ScanProblem>): Triple<RootLayout, List<SystemDir>, List<String>> {
        rules.forFolder(root.name)?.let { return Triple(RootLayout.SINGLE_SYSTEM, listOf(SystemDir("", root, it)), emptyList()) }

        val dirs = children.filter { it.isDirectory }
        // A frontend's own folder (RetroBat, ES-DE): its "roms" folder wins, even when a folder beside it happens to be
        // named like a system (RetroBat's "screenshots" is a gallery "system").
        dirs.firstOrNull { it.name.equals("roms", ignoreCase = true) }?.let { roms ->
            val inner = try { roms.children() } catch (e: IOException) { emptyList() }
            val inRoms = inner.filter { it.isDirectory }.mapNotNull { dir -> rules.forFolder(dir.name)?.let { SystemDir("${roms.name}/${dir.name}/", dir, it) } }
            if (inRoms.isNotEmpty()) {
                val unrecognised = inner.filter { it.isDirectory && rules.forFolder(it.name) == null && it.name.lowercase() !in HOLDING_FOLDERS }.map { it.name }.sorted()
                return Triple(RootLayout.COLLECTION, inRoms.sortedBy { it.dir.name.lowercase() }, unrecognised)
            }
        }
        val matched = dirs.mapNotNull { dir -> rules.forFolder(dir.name)?.let { SystemDir(dir.name + "/", dir, it) } }
        if (matched.isNotEmpty()) {
            val unrecognised = dirs.filter { rules.forFolder(it.name) == null && it.name.lowercase() !in HOLDING_FOLDERS }.map { it.name }.sorted()
            return Triple(RootLayout.COLLECTION, matched.sortedBy { it.dir.name.lowercase() }, unrecognised)
        }
        // The user may have picked the folder above "roms" (e.g. the RetroBat folder itself), or a folder holding a
        // few collections ("Games" with "My roms" inside): system folders one level down count too, "roms" first.
        val nested = mutableListOf<SystemDir>()
        val unrecognised = mutableListOf<String>()
        dirs.filter { it.name.lowercase() !in HOLDING_FOLDERS }.sortedBy { !it.name.equals("roms", ignoreCase = true) }.take(MAX_NESTED_LOOKS).forEach { outer ->
            val inner = try { outer.children() } catch (e: IOException) {
                problems += ScanProblem(outer.name, "Can't open: ${e.message}")
                emptyList()
            }
            val found = inner.filter { it.isDirectory }.mapNotNull { dir -> rules.forFolder(dir.name)?.let { SystemDir("${outer.name}/${dir.name}/", dir, it) } }
            nested += found
            if (found.isNotEmpty()) {
                unrecognised += inner.filter { it.isDirectory && rules.forFolder(it.name) == null && it.name.lowercase() !in HOLDING_FOLDERS }.map { "${outer.name}/${it.name}" }
            }
        }
        if (nested.isNotEmpty()) return Triple(RootLayout.COLLECTION, nested.sortedBy { it.dir.name.lowercase() }, unrecognised.sorted())
        problems += ScanProblem(root.name, "No system folders found. Pick a \"roms\" folder (containing snes, nes, psx …) or a single system folder.")
        return Triple(RootLayout.UNRECOGNISED, emptyList(), emptyList())
    }

    private data class FileEntry(val path: String, val node: ScanNode)

    /**
     * Games in [base] that aren't in a system folder: loose files, and anything in folders not named after a system.
     * Only file types that belong to one system are taken; archives and disc images (zip, iso, chd …) could be
     * anything, so they're counted and reported instead.
     */
    private fun scanLoose(base: ScanNode, prefix: String, problems: MutableList<ScanProblem>, isCancelled: () -> Boolean): List<ScannedGame> {
        val found = mutableListOf<ScannedGame>()
        var unsure = 0
        fun walk(node: ScanNode, path: String, depth: Int) {
            if (isCancelled()) throw ScanCancelled()
            val children = try { node.children() } catch (e: IOException) { return }
            for (child in children) {
                val childPath = if (path.isEmpty()) child.name else "$path/${child.name}"
                val lower = child.name.lowercase()
                when {
                    lower.startsWith(".") -> Unit
                    child.isDirectory -> {
                        // System folders (at the top, or one level down) are scanned on their own; media and holding
                        // folders aren't games.
                        val skip = (depth <= 1 && rules.forFolder(child.name) != null) || lower in mediaFolders || lower in HOLDING_FOLDERS
                        if (!skip && depth < maxDepth) walk(child, childPath, depth + 1)
                    }
                    else -> {
                        val ext = lower.substringAfterLast('.', "")
                        val system = LOOSE_EXTENSIONS[ext]?.let { rules.byId(it) }
                        if (system != null && isSwitchAddOn(system.id, child.name)) {
                            Unit
                        } else if (system != null) {
                            val name = RomNames.parse(child.name)
                            found += ScannedGame(
                                system = system.id,
                                relativePath = prefix + childPath,
                                fileName = child.name,
                                nodeId = child.id,
                                size = child.size,
                                lastModified = child.lastModified,
                                name = name,
                                hackFolder = hackLabel(childPath, name),
                                metadata = null,
                                imageNodeId = null,
                                duplicateKey = null,
                                discs = null,
                            )
                        } else if (ext in UNSURE_EXTENSIONS) {
                            unsure++
                        }
                    }
                }
            }
        }
        walk(base, "", 0)
        if (unsure > 0) {
            problems += ScanProblem(
                base.name,
                "$unsure files outside system folders could be for several systems (zip, 7z, iso, chd, cue, bin …), so they weren't added. " +
                    "Put them in a folder named after their system (psx, snes …) to add them.",
            )
        }
        return found
    }

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
                // An artwork folder that won't open only costs some box art (the gamelist's own paths still work),
                // so it isn't reported: RetroBat has images, videos and manuals in every system folder.
                if (!inMedia) problems += ScanProblem((prefix + path).trimEnd('/'), "Can't open folder: ${e.message}")
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
                    // PS4 and PS5 games are dumped folders, named anything: one with an eboot.bin is a game. Updates, patches
                    // and mods sit beside the game as "<ID>-UPDATE" / "-patch" / "-mods" and are the player's, not games.
                    child.isDirectory && !inMedia && rule.id in dumpedFolderSystems && ps4Extra.containsMatchIn(child.name) -> Unit
                    child.isDirectory && !inMedia && rule.id in dumpedFolderSystems && hasEboot(child) -> romFiles += FileEntry(childPath, child)
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
        return romFiles.filter { it.path.lowercase() !in hidden && !isSwitchAddOn(rule.id, it.node.name) }.map { entry ->
            // Arcade romsets are named by short names ("sf2.zip"): shown by their full titles. PS4 folders take the title
            // inside their param.sfo (PS5: param.json), so a folder named "CUSA12345" still shows the game's name.
            val sfoTitle = when {
                !entry.node.isDirectory -> null
                rule.id == "ps4" -> ps4Title(entry.node)
                rule.id == "ps5" -> ps5Title(entry.node)
                else -> null
            }
            val name = ArcadeNames.named(rule.id, entry.node.name, RomNames.parse(sfoTitle ?: entry.node.name))
            // A playlist RetroForge (or the user) made has no gamelist entry of its own: borrow its first disc's.
            val meta = metadata[entry.path.lowercase()] ?: if (isPlaylist(entry)) firstDisc(entry)?.let { metadata[it] } else null
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
                hackFolder = hackLabel(entry.path, name),
                metadata = meta,
                imageNodeId = image,
                duplicateKey = null,
                discs = if (!entry.node.isDirectory && entry.node.name.endsWith(".m3u", ignoreCase = true)) discCount(entry) else null,
            )
        }
    }


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

    private fun isPlaylist(entry: FileEntry) = !entry.node.isDirectory && entry.node.name.endsWith(".m3u", ignoreCase = true)

    private fun firstDisc(entry: FileEntry): String? = runCatching { readReferences(entry.node).firstOrNull() }.getOrNull()?.let { ref ->
        val dir = entry.path.substringBeforeLast('/', "")
        (if (dir.isEmpty()) ref else "$dir/$ref").lowercase()
    }

    private fun discCount(entry: FileEntry): Int? = runCatching { readReferences(entry.node).size }.getOrNull()?.takeIf { it > 0 }

    private fun markDuplicates(games: List<ScannedGame>): List<ScannedGame> {
        fun key(g: ScannedGame) = listOf(g.system, g.name.sortKey, g.name.tags.joinToString("|").lowercase(), g.hackFolder ?: "").joinToString("\u0000")
        val counts = games.groupingBy(::key).eachCount()
        return games.map { if ((counts[key(it)] ?: 0) > 1) it.copy(duplicateKey = key(it)) else it }
    }
}

/** File types that belong to a single system, for games found outside system folders. */
private val LOOSE_EXTENSIONS = mapOf(
    "gba" to "gba", "gb" to "gb", "gbc" to "gbc", "nes" to "nes", "fds" to "fds", "sfc" to "snes", "smc" to "snes",
    "n64" to "n64", "z64" to "n64", "v64" to "n64", "nds" to "nds", "3ds" to "3ds", "cia" to "3ds",
    "sms" to "mastersystem", "gg" to "gamegear", "md" to "megadrive", "gen" to "megadrive", "smd" to "megadrive",
    "32x" to "sega32x", "sg" to "sg1000", "pce" to "pcengine", "sgx" to "supergrafx", "a26" to "atari2600",
    "a78" to "atari7800", "lnx" to "lynx", "j64" to "jaguar", "ws" to "wswan", "wsc" to "wswanc", "ngp" to "ngp",
    "ngc" to "ngpc", "vb" to "virtualboy", "col" to "colecovision", "int" to "intellivision", "vec" to "vectrex",
    "min" to "pokemini", "cso" to "psp", "xci" to "switch", "nsp" to "switch", "wbfs" to "wii", "gcm" to "gamecube",
)

/** Archives and disc images: could be for many systems. */
private val UNSURE_EXTENSIONS = setOf("zip", "7z", "rar", "iso", "chd", "cue", "bin", "img", "pbp", "rvz", "m3u", "gdi", "cdi")

/** Folders beside the system folders that hold no games: BIOS, saves and the like. */
private val NON_GAME_FOLDERS = setOf("bios", "system", "saves", "states", "screenshots", "media", "images", "videos", "manuals", "cheats", "shaders", "overlays", "themes")

/**
 * What a game is labelled as a hack: the folder holding it ("SMW HACKS", the outermost when nested), else its patch tag,
 * else "" for a [h] flag; null when it isn't one.
 */
fun hackLabel(path: String, name: RomName): String? =
    path.split('/').dropLast(1).firstOrNull { it.contains("hack", ignoreCase = true) }
        ?: name.hackName ?: if (RomFlag.HACK in name.flags) "" else null

/**
 * A Switch update or DLC rather than a game: its title id ([0100C2700C252800], [0100DCA0064A7001]) doesn't end in 000
 * as a game's does. Emulators install these on top of the game; they don't start on their own.
 */
private val ps4Extra = Regex("""-(update|patch|mods)$""", RegexOption.IGNORE_CASE)

private fun hasEboot(folder: ScanNode): Boolean =
    runCatching { folder.children().any { !it.isDirectory && it.name.equals("eboot.bin", ignoreCase = true) } }.getOrDefault(false)

/** The game's title from sce_sys/param.sfo, or null. */
private fun ps4Title(folder: ScanNode): String? = runCatching {
    val sys = folder.children().firstOrNull { it.isDirectory && it.name.equals("sce_sys", ignoreCase = true) } ?: return null
    val sfo = sys.children().firstOrNull { !it.isDirectory && it.name.equals("param.sfo", ignoreCase = true) } ?: return null
    if (sfo.size > ParamSfo.MAX_BYTES) return null
    ParamSfo.parse(sfo.open().use { it.readBytes() })["TITLE"]?.lines()?.first()?.trim()?.takeIf { it.isNotEmpty() }
}.getOrNull()

private val dumpedFolderSystems = setOf("ps4", "ps5")

/**
 * The game's title from sce_sys/param.json, or null: localizedParameters holds a defaultLanguage ("en-US") and an
 * object per language with its titleName.
 */
private fun ps5Title(folder: ScanNode): String? = runCatching {
    val sys = folder.children().firstOrNull { it.isDirectory && it.name.equals("sce_sys", ignoreCase = true) } ?: return null
    val param = sys.children().firstOrNull { !it.isDirectory && it.name.equals("param.json", ignoreCase = true) } ?: return null
    // A title in every language makes these far bigger than a param.sfo.
    if (param.size > 1024 * 1024) return null
    val localized = Json.parseToJsonElement(param.open().use { it.readBytes().decodeToString() })
        .jsonObject["localizedParameters"]?.jsonObject ?: return null
    val language = localized["defaultLanguage"]?.jsonPrimitive?.content ?: "en-US"
    val titles = listOfNotNull(localized[language]) + localized.values
    titles.firstNotNullOfOrNull { (it as? JsonObject)?.get("titleName")?.jsonPrimitive?.content?.trim()?.takeIf(String::isNotEmpty) }
}.getOrNull()

fun isSwitchAddOn(system: String, fileName: String): Boolean =
    system == "switch" && switchTitleId.findAll(fileName).any { !it.groupValues[1].endsWith("000") }

private val switchTitleId = Regex("""\[(0100[0-9a-fA-F]{12})]""")
