package com.retroforge.library.manage

import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel
import java.io.IOException
import java.io.InputStream
import java.util.zip.CRC32
import java.util.zip.ZipInputStream

/** One file the scanner marked as another copy of a game. */
data class DuplicateCandidate(
    val system: String,
    /** Path from the collection folder. */
    val relativePath: String,
    /** Folder holding this system's gamelist.xml, from the collection folder ("snes", or "" for a single-system folder). */
    val systemFolder: String,
    val title: String,
    val size: Long,
    val duplicateKey: String,
    val hasMetadata: Boolean,
    val hasArt: Boolean,
) {
    val fileName: String get() = nameOf(relativePath)
    val extension: String get() = fileName.substringAfterLast('.', "").lowercase()
}

enum class KeepRule { MOST_COMPATIBLE, SMALLEST }

enum class Sameness {
    /** Every copy holds the same game data (checksums match). */
    SAME_DATA,
    /** The copies hold different data (another revision, a header, a hack …). */
    DIFFERENT_DATA,
    NOT_CHECKED,
}

data class DuplicateSet(val system: String, val title: String, val files: List<DuplicateCandidate>, val sameness: Sameness = Sameness.NOT_CHECKED) {
    fun keep(rule: KeepRule): DuplicateCandidate = files.minWith(DuplicatePlanner.preference(rule))
}

data class SkippedSet(val system: String, val title: String, val files: List<DuplicateCandidate>, val reason: String)

object DuplicatePlanner {

    private val archives = setOf("zip", "7z")
    private val multiFile = setOf("cue", "gdi", "ccd", "toc", "m3u", "bin", "img", "sub")

    fun plan(candidates: List<DuplicateCandidate>): Pair<List<DuplicateSet>, List<SkippedSet>> {
        val sets = mutableListOf<DuplicateSet>()
        val skipped = mutableListOf<SkippedSet>()
        for (group in candidates.filter { it.relativePath.substringBefore('/') !in HOLDING_FOLDERS }.groupBy { it.duplicateKey }.values) {
            if (group.size < 2) continue
            val first = group.first()
            val reason = when {
                group.any { it.extension in multiFile } ->
                    "Disc games made of several files (.cue, .bin …) aren't tidied automatically. Some, like MSU-MD games, need all their files."
                group.any { it.size <= 0L } -> "One copy is a folder, not a file."
                group.map { it.fileName.lowercase() }.distinct().size == 1 ->
                    "Same file name in different folders. These are usually different games or add-ons (like Quake mission packs)."
                else -> null
            }
            if (reason != null) skipped += SkippedSet(first.system, first.title, group, reason)
            else sets += DuplicateSet(first.system, first.title, group.sortedBy { it.relativePath.lowercase() })
        }
        return sets.sortedWith(compareBy({ it.system }, { it.title.lowercase() })) to skipped
    }

    /** Best copy first: the one with RetroBat artwork/metadata, then by [rule], then the shallowest path. */
    fun preference(rule: KeepRule): Comparator<DuplicateCandidate> = compareBy<DuplicateCandidate>(
        { -(if (it.hasArt) 2 else 0) - (if (it.hasMetadata) 1 else 0) },
        { if (rule == KeepRule.SMALLEST) it.size else compatibilityRank(it.extension).toLong() },
        { if (rule == KeepRule.SMALLEST) compatibilityRank(it.extension).toLong() else it.size },
        { it.relativePath.count { c -> c == '/' } },
        { it.relativePath.lowercase() },
    )

    /** Plain ROMs work everywhere; .zip works in nearly every emulator; .7z in fewer standalone ones. */
    private fun compatibilityRank(ext: String) = when (ext) {
        "zip" -> 1
        "7z" -> 2
        else -> 0
    }

    fun isArchive(ext: String) = ext in archives
}

/**
 * What a file contains, independent of how it's packed: "size:crc" of each game file inside a .zip/.7z (readmes
 * ignored), or of the file itself. Equal fingerprints mean the same game data.
 */
object ContentFingerprint {
    const val MAX_BYTES = 64L * 1024 * 1024
    private val ignored = setOf("txt", "nfo", "diz", "htm", "html", "url", "pdf", "jpg", "png")

    fun of(fileName: String, size: Long, open: () -> InputStream): String? {
        if (size > MAX_BYTES) return null
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return try {
            when (ext) {
                "zip" -> open().use(::zip)
                "7z" -> sevenZip(open().use { it.readBytes() })
                else -> open().use(::one)
            }
        } catch (_: IOException) {
            null
        }
    }

    private fun one(input: InputStream): String = crc(input).let { (n, c) -> entry(n, c) }

    private fun entry(size: Long, crc: Long) = "%d:%08x".format(size, crc)

    private fun crc(input: InputStream): Pair<Long, Long> {
        val c = CRC32()
        val buf = ByteArray(64 * 1024)
        var n = 0L
        while (true) {
            val r = input.read(buf)
            if (r < 0) break
            c.update(buf, 0, r)
            n += r
        }
        return n to c.value
    }

    private fun wanted(name: String) = name.substringAfterLast('.', "").lowercase() !in ignored

    private fun zip(input: InputStream): String? {
        val entries = mutableListOf<String>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                if (!e.isDirectory && wanted(e.name)) entries += crc(zip).let { (n, c) -> entry(n, c) }
            }
        }
        return entries.sorted().joinToString(" ").ifEmpty { null }
    }

    private fun sevenZip(bytes: ByteArray): String? {
        val entries = mutableListOf<String>()
        SevenZFile.builder().setSeekableByteChannel(SeekableInMemoryByteChannel(bytes)).get().use { archive ->
            while (true) {
                val e = archive.nextEntry ?: break
                if (e.isDirectory || !wanted(e.name)) continue
                entries += if (e.hasCrc) entry(e.size, e.crcValue) else crc(archive.getInputStream(e)).let { (n, c) -> entry(n, c) }
            }
        }
        return entries.sorted().joinToString(" ").ifEmpty { null }
    }

    fun sameness(fingerprints: List<String?>): Sameness = when {
        fingerprints.any { it == null } -> Sameness.NOT_CHECKED
        fingerprints.distinct().size == 1 -> Sameness.SAME_DATA
        else -> Sameness.DIFFERENT_DATA
    }
}

/** [replacedBy]: each moved copy's path and the kept copy that stands in for it, for the app's own records. */
data class DuplicateRunResult(val moved: Int, val gamelistsUpdated: Int, val problems: List<String>, val replacedBy: List<Pair<String, String>> = emptyList())

/** Moves the copies the user chose not to keep into _duplicates/, and points gamelist.xml entries at the kept copy. */
class DuplicateMover(private val files: CollectionFiles) {

    fun run(selections: List<Pair<DuplicateSet, DuplicateCandidate>>, journal: Journal, isCancelled: () -> Boolean = { false }): DuplicateRunResult {
        val problems = mutableListOf<String>()
        val replacedBy = mutableListOf<Pair<String, String>>()
        var moved = 0
        val gamelistChanges = mutableMapOf<String, MutableList<Pair<String, String>>>()
        for ((set, keep) in selections) {
            if (isCancelled()) break
            for (file in set.files) {
                if (file == keep) continue
                try {
                    val holding = join(DUPLICATES_FOLDER, parentOf(file.relativePath))
                    files.makeFolders(holding)
                    files.move(file.relativePath, holding)
                    journal.record(Step.Moved(file.relativePath, join(holding, file.fileName)))
                    moved++
                    replacedBy += file.relativePath to keep.relativePath
                    gamelistChanges.getOrPut(file.systemFolder) { mutableListOf() } +=
                        file.relativePath.removePrefix(file.systemFolder).trimStart('/') to keep.relativePath.removePrefix(keep.systemFolder).trimStart('/')
                } catch (e: IOException) {
                    problems += "${file.relativePath}: ${e.message}"
                }
            }
        }
        var updated = 0
        for ((folder, changes) in gamelistChanges) {
            val gamelist = join(folder, "gamelist.xml")
            if (!files.exists(gamelist)) continue
            try {
                TextFiles.edit(files, gamelist, journal, backup = true) { text ->
                    changes.fold(text to emptyList<TextChange>()) { (t, acc), (old, new) ->
                        val (out, change) = GamelistEditor.retarget(t, old, new)
                        out to (acc + listOfNotNull(change))
                    }
                }
                updated++
            } catch (e: IOException) {
                problems += "$gamelist: ${e.message}"
            }
        }
        return DuplicateRunResult(moved, updated, problems, replacedBy)
    }
}
