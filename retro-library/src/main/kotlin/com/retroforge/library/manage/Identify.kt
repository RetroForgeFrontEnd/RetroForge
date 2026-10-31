package com.retroforge.library.manage

import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.IOException
import java.io.InputStream
import java.util.zip.CRC32

/** One dump in a No-Intro or Redump game list: the file name the list gives it, and its checksum. */
data class DatRom(val game: String, val fileName: String, val size: Long, val crc: Long)

/** A system's game list (a clrmamepro or Logiqx XML .dat), looked up by checksum and size. */
class GameList(val name: String, val version: String?, val roms: List<DatRom>) {
    private val byCrc = roms.groupBy { it.crc to it.size }
    fun find(crc: Long, size: Long): DatRom? = byCrc[crc to size]?.firstOrNull()
}

/** Reads the two .dat formats in use: clrmamepro (libretro-database) and Logiqx XML (No-Intro's DAT-o-MATIC, Redump). */
object DatParser {
    private val headerName = Regex("""^\s*name\s+"([^"]*)"""")
    private val headerVersion = Regex("""^\s*version\s+"?([^"\s]+)"?""")
    private val gameName = Regex("""^\s*name\s+"([^"]*)"""")
    private val romLine = Regex("""rom\s*\(\s*name\s+"([^"]+)"\s+size\s+(\d+)\s+crc\s+([0-9A-Fa-f]{8})""")
    private val xmlGame = Regex("""<(?:game|machine)\s[^>]*name="([^"]*)"""")
    private val xmlRom = Regex("""<rom\s[^>]*>""")
    private val attr = { tag: String, key: String -> Regex("""\s$key="([^"]*)"""").find(tag)?.groupValues?.get(1) }

    fun parse(text: String): GameList = if (text.trimStart().startsWith("<")) parseXml(text) else parseClrMamePro(text)

    private fun parseClrMamePro(text: String): GameList {
        var name = ""
        var version: String? = null
        var block = ""
        var game = ""
        val roms = mutableListOf<DatRom>()
        for (line in text.lineSequence()) {
            val t = line.trim()
            when {
                t.startsWith("clrmamepro") -> block = "header"
                t.startsWith("game (") || t.startsWith("machine (") -> { block = "game"; game = "" }
                t == ")" -> block = ""
                block == "header" -> {
                    headerName.find(t)?.let { name = it.groupValues[1] }
                    headerVersion.find(t)?.let { version = it.groupValues[1] }
                }
                block == "game" -> {
                    if (game.isEmpty()) gameName.find(t)?.let { game = it.groupValues[1] }
                    romLine.find(t)?.let { m ->
                        roms += DatRom(game, m.groupValues[1], m.groupValues[2].toLong(), m.groupValues[3].toLong(16))
                    }
                }
            }
        }
        return GameList(name, version, roms)
    }

    private fun parseXml(text: String): GameList {
        val name = Regex("""<header>.*?<name>([^<]*)</name>""", RegexOption.DOT_MATCHES_ALL).find(text)?.groupValues?.get(1).orEmpty()
        val version = Regex("""<header>.*?<version>([^<]*)</version>""", RegexOption.DOT_MATCHES_ALL).find(text)?.groupValues?.get(1)
        val roms = mutableListOf<DatRom>()
        var game = ""
        // Games and their roms in document order: each <rom> belongs to the <game> before it.
        val tokens = Regex("""<(?:game|machine)\s[^>]*>|<rom\s[^>]*>""").findAll(text)
        for (m in tokens) {
            val tag = m.value
            if (!tag.startsWith("<rom")) { game = GamelistEditor.unescape(xmlGame.find(tag)?.groupValues?.get(1).orEmpty()); continue }
            val file = attr(tag, "name") ?: continue
            val size = attr(tag, "size")?.toLongOrNull() ?: continue
            val crc = attr(tag, "crc")?.takeIf { it.length == 8 }?.toLongOrNull(16) ?: continue
            roms += DatRom(game, GamelistEditor.unescape(file), size, crc)
        }
        return GameList(GamelistEditor.unescape(name), version, roms)
    }
}

/** A checksum of a game's data as a game list could record it; [inner] names the file inside an archive. */
data class RomSum(val crc: Long, val size: Long, val inner: String? = null)

/**
 * The checksums a game file could be listed under. Besides the file as it is, copier and emulator headers are left out
 * (iNES, FDS, Atari 7800, Lynx, SNES copier headers), since lists differ on whether they count them, and an N64 game
 * in byte-swapped (.v64) or little-endian (.n64) order is also summed in the lists' big-endian order.
 */
object RomChecksum {
    private const val BUF = 256 * 1024
    /** Archived games bigger than this aren't unpacked to try without a header (cartridges are far smaller). */
    private const val MAX_UNPACK = 64L * 1024 * 1024

    private val headerSystems = setOf("nes", "fds", "snes", "sfc", "snes-msu1", "satellaview", "sufami", "atari7800", "lynx", "n64")

    fun of(files: CollectionFiles, path: String, system: String, cancelled: () -> Boolean = { false }): List<RomSum> =
        when (path.substringAfterLast('.').lowercase()) {
            "zip" -> zipSums(files, path, system)
            "7z" -> sevenZipSums(files, path, system)
            else -> {
                val size = files.size(path) ?: throw IOException("$path is missing")
                files.open(path).use { sums(it, size, system, null, cancelled) }
            }
        }

    private fun zipSums(files: CollectionFiles, path: String, system: String): List<RomSum> =
        ZipFile.builder().setSeekableByteChannel(files.openRead(path)).get().use { zip ->
            zip.entries.toList().filter { !it.isDirectory && it.size >= 0 }.flatMap { e ->
                val quick = RomSum(e.crc, e.size, e.name)
                if (system !in headerSystems || e.size > MAX_UNPACK) listOf(quick)
                else zip.getInputStream(e).use { sums(it, e.size, system, e.name) { false } }
            }
        }

    private fun sevenZipSums(files: CollectionFiles, path: String, system: String): List<RomSum> =
        SevenZFile.builder().setSeekableByteChannel(files.openRead(path)).get().use { sz ->
            val out = mutableListOf<RomSum>()
            for (e in sz.entries) {
                if (e.isDirectory || !e.hasStream()) continue
                if (e.hasCrc) out += RomSum(e.crcValue, e.size, e.name)
                if ((system in headerSystems || !e.hasCrc) && e.size <= MAX_UNPACK) {
                    out += sz.getInputStream(e).use { sums(it, e.size, system, e.name) { false } }
                }
            }
            out.distinct()
        }

    /** Reads the data once, summing every variant at the same time. */
    internal fun sums(input: InputStream, size: Long, system: String, inner: String?, cancelled: () -> Boolean): List<RomSum> {
        val first = ByteArray(minOf(BUF.toLong(), size).toInt())
        var got = 0
        while (got < first.size) { val n = input.read(first, got, first.size - got); if (n < 0) break; got += n }
        val skip = if (system in headerSystems) headerLength(first, got, size, system) else 0
        val swap = if (system == "n64") n64Order(first, got) else 0
        val whole = CRC32()
        val headerless = if (skip > 0) CRC32() else null
        val swapped = if (swap != 0) CRC32() else null
        var offset = 0L
        fun feed(buf: ByteArray, n: Int) {
            whole.update(buf, 0, n)
            if (headerless != null) {
                val from = (skip - offset).coerceIn(0, n.toLong()).toInt()
                if (from < n) headerless.update(buf, from, n - from)
            }
            if (swapped != null) { reorder(buf, n, swap); swapped.update(buf, 0, n) }
            offset += n
        }
        feed(first, got)
        val buf = ByteArray(BUF)
        while (true) {
            if (cancelled()) throw CancelledException()
            var n = 0
            // Whole groups of 4 bytes keep the N64 reordering lined up across reads.
            while (n < buf.size) { val r = input.read(buf, n, buf.size - n); if (r < 0) break; n += r }
            if (n == 0) break
            feed(buf, n)
            if (n < buf.size) break
        }
        return listOfNotNull(
            RomSum(whole.value, offset, inner),
            headerless?.let { RomSum(it.value, offset - skip, inner) },
            swapped?.let { RomSum(it.value, offset, inner) },
        )
    }

    /** Bytes of header in front of the game data, or 0. */
    internal fun headerLength(b: ByteArray, n: Int, size: Long, system: String): Int {
        fun magic(at: Int, s: String) = n >= at + s.length && s.indices.all { b[at + it] == s[it].code.toByte() }
        return when {
            (system == "nes") && magic(0, "NES\u001A") -> 16
            system == "fds" && magic(0, "FDS\u001A") -> 16
            system == "atari7800" && magic(1, "ATARI7800") -> 128
            system == "lynx" && magic(0, "LYNX") -> 64
            system in setOf("snes", "sfc", "snes-msu1", "satellaview", "sufami") && size % 1024 == 512L -> 512
            else -> 0
        }
    }

    /** 2 for byte-swapped (.v64), 4 for little-endian (.n64), 0 for the lists' own big-endian order (.z64). */
    internal fun n64Order(b: ByteArray, n: Int): Int {
        if (n < 4) return 0
        val w = ((b[0].toInt() and 0xFF) shl 24) or ((b[1].toInt() and 0xFF) shl 16) or ((b[2].toInt() and 0xFF) shl 8) or (b[3].toInt() and 0xFF)
        return when (w) { 0x37804012 -> 2; 0x40123780 -> 4; else -> 0 }
    }

    private fun reorder(b: ByteArray, n: Int, mode: Int) {
        var i = 0
        if (mode == 2) while (i + 1 < n) { val t = b[i]; b[i] = b[i + 1]; b[i + 1] = t; i += 2 }
        else while (i + 3 < n) {
            var t = b[i]; b[i] = b[i + 3]; b[i + 3] = t
            t = b[i + 1]; b[i + 1] = b[i + 2]; b[i + 2] = t
            i += 4
        }
    }
}

/** A game in the library to identify. */
data class IdentifyCandidate(val system: String, val relativePath: String, val systemFolder: String, val title: String)

enum class IdentifyOutcome { NAMED_RIGHT, RENAME, UNKNOWN, SKIPPED }

/**
 * What checking one game found. For [IdentifyOutcome.RENAME], [renames] are the file renames in order (a cue sheet's
 * tracks first, the game's own file last) and [newPath] is where the game will be.
 */
data class Identified(
    val candidate: IdentifyCandidate,
    val outcome: IdentifyOutcome,
    val listedAs: String? = null,
    val newPath: String? = null,
    val renames: List<Pair<String, String>> = emptyList(),
    val reason: String? = null,
)

/** Works out whether a game is in its system's list and what its files should be called. Reads only. */
object Identifier {
    /** Kinds of game that can't be identified by a checksum of their file (yet), with the reason shown. */
    private val unsupported = mapOf(
        "chd" to "CHD discs can't be checked against Redump yet",
        "rvz" to "RVZ discs can't be checked against Redump yet",
        "gdi" to "GD-ROM sets can't be checked yet",
        "cdi" to "DiscJuggler images aren't in Redump",
        "m3u" to "playlists are left alone (check their discs before making the playlist)",
        "pbp" to "PBP files aren't in Redump",
        "cso" to "CSO files aren't in Redump",
        "zcci" to "compressed 3DS games aren't in the lists",
        "zcxi" to "compressed 3DS games aren't in the lists",
        "z3dsx" to "compressed 3DS games aren't in the lists",
        "rar" to "RAR archives can't be read",
    )

    /** Arcade sets must keep the names MAME and FinalBurn Neo look for. */
    val arcade = setOf("mame", "fbneo", "neogeo", "naomi", "naomi2", "atomiswave", "cps1", "cps2", "cps3", "arcade")

    fun skipReason(path: String): String? = unsupported[path.substringAfterLast('.').lowercase()]

    /**
     * Checks one game. [sums] gives the checksums of a file in the collection (see [RomChecksum]); [cueText] the text
     * of a cue sheet.
     */
    fun identify(
        c: IdentifyCandidate,
        list: GameList,
        sums: (String) -> List<RomSum>,
        cueText: (String) -> String,
    ): Identified {
        skipReason(c.relativePath)?.let { return Identified(c, IdentifyOutcome.SKIPPED, reason = it) }
        val folder = parentOf(c.relativePath)
        val name = nameOf(c.relativePath)
        val ext = name.substringAfterLast('.', "")
        if (ext.equals("cue", true)) {
            val bins = CueSheet.files(cueText(c.relativePath))
            if (bins.isEmpty()) return Identified(c, IdentifyOutcome.SKIPPED, reason = "the cue sheet lists no tracks")
            if (bins.any { '/' in it }) return Identified(c, IdentifyOutcome.SKIPPED, reason = "its tracks are in another folder")
            val rom = sums(join(folder, bins.first())).firstNotNullOfOrNull { list.find(it.crc, it.size) }
                ?: return Identified(c, IdentifyOutcome.UNKNOWN)
            val game = safe(rom.game)
            val wanted = trackNames(game, rom.fileName, bins.size)
            val renames = bins.zip(wanted).filter { (from, to) -> from != to }.map { (from, to) -> join(folder, from) to join(folder, to) } +
                listOfNotNull((join(folder, "$game.cue")).takeIf { nameOf(it) != name }?.let { c.relativePath to it })
            return if (renames.isEmpty()) Identified(c, IdentifyOutcome.NAMED_RIGHT, rom.game)
            else Identified(c, IdentifyOutcome.RENAME, rom.game, join(folder, "$game.cue"), renames)
        }
        val found = sums(c.relativePath)
        val match = found.firstNotNullOfOrNull { s -> list.find(s.crc, s.size)?.let { s to it } }
            ?: return Identified(c, IdentifyOutcome.UNKNOWN)
        val rom = match.second
        // Keep the file's own extension: a zipped game stays a .zip, a .v64 stays a .v64.
        val base = safe(rom.fileName.substringBeforeLast('.', rom.fileName))
        val target = if (ext.isEmpty()) base else "$base.$ext"
        if (target == name) return Identified(c, IdentifyOutcome.NAMED_RIGHT, rom.game)
        val inside = if (match.first.inner != null && found.map { it.inner }.distinct().size > 1) " (one of its files)" else ""
        return Identified(c, IdentifyOutcome.RENAME, rom.game + inside, join(folder, target), listOf(c.relativePath to join(folder, target)))
    }

    /**
     * Track file names for a cue sheet of [count] files, following the list's name for track 1: "Game (Track 01).bin"
     * keeps two digits, "Game (Track 1).bin" one, and a one-file game is just "Game.bin".
     */
    internal fun trackNames(game: String, firstTrack: String, count: Int): List<String> {
        val ext = firstTrack.substringAfterLast('.', "bin")
        val m = Regex("""\(Track (\d+)\)""").find(firstTrack)
        if (count == 1) return listOf(safe(firstTrack).takeIf { m == null } ?: "$game.$ext")
        val width = m?.groupValues?.get(1)?.length ?: if (count >= 10) 2 else 1
        return (1..count).map { "$game (Track ${it.toString().padStart(width, '0')}).$ext" }
    }

    /** No-Intro and Redump names are already safe on every file system; this guards against the odd one that isn't. */
    internal fun safe(name: String) = name.replace(Regex("""[\\/:*?"<>|]"""), "_").trim()

    /** "(Rev 1)", "(Rev A)", "(v1.1)": the revision tags short names leave out. */
    private val revisionTag = Regex("""\s*\((?:Rev\s[^)]*|v\d+(?:\.\d+)*[a-z]?)\)""", RegexOption.IGNORE_CASE)

    fun withoutRevision(name: String): String = revisionTag.replace(name, "")

    /**
     * The same results with revision tags left out of the new names ("Advance Wars (USA).zip", not "… (Rev 1).zip"),
     * for one-file games (a cue sheet's tracks keep theirs). Where two versions of a game would end up with the same
     * short name, or another file already has it, each keeps its full name.
     */
    fun withoutRevisions(results: List<Identified>, exists: (String) -> Boolean): List<Identified> {
        val short = results.map { r ->
            val full = when (r.outcome) {
                IdentifyOutcome.RENAME -> r.newPath?.takeIf { r.renames.size == 1 }
                IdentifyOutcome.NAMED_RIGHT -> r.candidate.relativePath
                else -> null
            }
            if (full == null || full.endsWith(".cue", true)) return@map null
            join(parentOf(full), withoutRevision(nameOf(full))).takeIf { it != full }
        }
        // Where each game would end up, to find two landing on one name.
        val landing = results.zip(short).map { (r, s) -> (s ?: r.newPath ?: r.candidate.relativePath).lowercase() }
        val counts = landing.groupingBy { it }.eachCount()
        val sources = results.map { it.candidate.relativePath.lowercase() }.toSet()
        return results.zip(short).mapIndexed { i, (r, s) ->
            when {
                s == null -> r
                counts.getValue(landing[i]) > 1 -> r
                s.equals(r.candidate.relativePath, true) -> r.copy(outcome = IdentifyOutcome.NAMED_RIGHT, newPath = null, renames = emptyList())
                exists(s) && s.lowercase() !in sources -> r
                else -> r.copy(outcome = IdentifyOutcome.RENAME, newPath = s, renames = listOf(r.candidate.relativePath to s))
            }
        }
    }

    /**
     * Drops renames that would clash: two games that would get the same name (the second is a duplicate), or a name
     * another file in the folder already has.
     */
    fun resolveClashes(results: List<Identified>, exists: (String) -> Boolean): List<Identified> {
        val taken = mutableSetOf<String>()
        val moving = results.filter { it.outcome == IdentifyOutcome.RENAME }.flatMap { r -> r.renames.map { it.first.lowercase() } }.toSet()
        return results.map { r ->
            if (r.outcome != IdentifyOutcome.RENAME) return@map r
            val targets = r.renames.map { it.second }
            val clash = targets.firstOrNull { t ->
                val key = t.lowercase()
                key in taken || (exists(t) && key !in moving && r.renames.none { it.first.equals(t, true) })
            }
            if (clash != null) r.copy(outcome = IdentifyOutcome.SKIPPED, reason = "${nameOf(clash)} is already there (a duplicate?)")
            else { taken += targets.map { it.lowercase() }; r }
        }
    }
}

/** Renames identified games, keeping cue sheets, gamelist.xml and .m3u playlists pointing at them. */
class Renamer(private val files: CollectionFiles) {
    class Result(val renamed: Int, val moves: List<Pair<String, String>>, val problems: List<String>)

    fun run(jobs: List<Identified>, journal: Journal, cancelled: () -> Boolean): Result {
        val problems = mutableListOf<String>()
        val moves = mutableListOf<Pair<String, String>>()
        val playlists = mutableMapOf<String, List<String>>()
        for (job in jobs) {
            if (cancelled()) break
            val c = job.candidate
            val newPath = job.newPath ?: continue
            try {
                for ((from, to) in job.renames) renameOne(from, to, journal)
                if (c.relativePath.endsWith(".cue", true)) fixCue(newPath, job.renames, journal)
                updateReferences(c, newPath, journal, problems, playlists)
                moves += c.relativePath to newPath
            } catch (e: IOException) {
                problems += "${nameOf(c.relativePath)}: ${e.message}"
            }
        }
        return Result(moves.size, moves, problems)
    }

    /** A rename that only changes letter case goes through a temporary name, for file systems that ignore case. */
    private fun renameOne(from: String, to: String, journal: Journal) {
        if (from.equals(to, ignoreCase = true)) {
            val temp = "$from.retroforge-rename"
            files.rename(from, nameOf(temp))
            journal.record(Step.Renamed(from, temp))
            files.rename(temp, nameOf(to))
            journal.record(Step.Renamed(temp, to))
        } else {
            if (files.exists(to)) throw IOException("${nameOf(to)} is already there")
            files.rename(from, nameOf(to))
            journal.record(Step.Renamed(from, to))
        }
    }

    private fun fixCue(cue: String, renames: List<Pair<String, String>>, journal: Journal) {
        val names = renames.associate { nameOf(it.first) to nameOf(it.second) }
        TextFiles.edit(files, cue, journal, backup = false) { text -> CueEditor.retarget(text, names) }
    }

    private fun updateReferences(c: IdentifyCandidate, newPath: String, journal: Journal, problems: MutableList<String>, playlists: MutableMap<String, List<String>>) {
        val gamelist = join(c.systemFolder, "gamelist.xml")
        val inSystem = { p: String -> p.removePrefix(c.systemFolder).trimStart('/') }
        if (files.exists(gamelist)) {
            try {
                TextFiles.edit(files, gamelist, journal, backup = true) { text ->
                    val (out, change) = GamelistEditor.retarget(text, inSystem(c.relativePath), inSystem(newPath))
                    out to listOfNotNull(change)
                }
            } catch (e: IOException) {
                problems += "$gamelist: ${e.message}"
            }
        }
        val folder = parentOf(c.relativePath)
        val m3us = playlists.getOrPut(folder) {
            runCatching { files.walk(folder) }.getOrDefault(emptyList()).map { it.first }
                .filter { parentOf(it) == folder && it.endsWith(".m3u", true) }
        }
        for (m3u in m3us) {
            try {
                TextFiles.edit(files, m3u, journal, backup = false) { text -> PlaylistEditor.retarget(text, nameOf(c.relativePath), nameOf(newPath)) }
            } catch (e: IOException) {
                problems += "$m3u: ${e.message}"
            }
        }
    }
}

/** Points a cue sheet's FILE lines at renamed track files. */
object CueEditor {
    private val fileLine = Regex("""^(\s*FILE\s+)("[^"]+"|\S+)(.*)$""", RegexOption.IGNORE_CASE)

    fun retarget(text: String, names: Map<String, String>): Pair<String, List<TextChange>> {
        val changes = mutableListOf<TextChange>()
        val out = text.split('\n').joinToString("\n") { raw ->
            val line = raw.trimEnd('\r')
            val m = fileLine.find(line) ?: return@joinToString raw
            val name = m.groupValues[2].trim('"')
            val to = names[name] ?: return@joinToString raw
            val changed = m.groupValues[1] + "\"" + to + "\"" + m.groupValues[3]
            changes += TextChange.LineChanged(line, changed)
            changed + raw.substring(line.length)
        }
        return out to changes
    }
}
