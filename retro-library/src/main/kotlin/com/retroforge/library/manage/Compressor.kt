package com.retroforge.library.manage

import com.retroforge.library.SystemRules
import java.io.IOException
import java.nio.channels.Channels
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** A game in the library, as the compressor sees it. */
data class CompressCandidate(
    val system: String,
    val relativePath: String,
    val systemFolder: String,
    val title: String,
    val size: Long,
) {
    val extension: String get() = relativePath.substringAfterLast('.', "").lowercase()
}

enum class CompressFormat(val label: String) {
    ZIP("ZIP"),
    CD_CHD("CHD (CD)"),
    GD_CHD("CHD (GD-ROM)"),
    DVD_CHD("CHD (DVD)"),
    RVZ("RVZ"),
    Z3DS("Z3DS"),
}

/** One conversion: [inputs] (all files of the game) become [output]; [playlists] are .m3u files naming [source]. */
data class CompressJob(
    val system: String,
    val title: String,
    val format: CompressFormat,
    val source: String,
    val systemFolder: String,
    val inputs: List<String>,
    val inputBytes: Long,
    val output: String,
    val playlists: List<String> = emptyList(),
)

data class CompressSkip(val system: String, val title: String, val path: String, val reason: String)

object CompressPlanner {

    /** Systems where CHD holds a DVD image. */
    val dvdSystems = setOf("ps2", "psp")
    private val alreadyCompressed = setOf("zip", "7z", "chd", "cso", "pbp", "rvz", "gcz", "wbfs", "wia", "ciso", "gz", "squashfs", "rar", "xz", "zst")
    private val discFiles = setOf("cue", "gdi", "ccd", "toc", "m3u", "iso", "img", "bin", "mdf", "mds", "cdi")
    private val gameCube = setOf("gamecube", "gc")
    private val threeDs = setOf("3ds", "n3ds")
    /**
     * Cartridge systems whose RetroArch cores load .zip (or RetroArch unpacks it for them). Not every system that
     * lists .zip: zipping Visual Pinball tables, 3DS games or ScummVM folders would break them.
     */
    val zipSystems = setOf(
        "nes", "fds", "snes", "satellaview", "sufami", "gb", "gbc", "gba", "megadrive", "mastersystem", "gamegear", "sg1000",
        "sega32x", "pcengine", "supergrafx", "n64", "atari2600", "atari5200", "atari7800", "lynx", "jaguar", "ngp", "ngpc",
        "wswan", "wswanc", "virtualboy", "colecovision", "intellivision", "msx1", "msx2", "pokemini", "vectrex", "odyssey2",
        "channelf", "gamecom", "megaduck", "supervision", "arduboy", "uzebox",
    )
    const val MAX_CARTRIDGE_BYTES = 1L shl 30

    fun plan(games: List<CompressCandidate>, files: CollectionFiles, rules: SystemRules): Pair<List<CompressJob>, List<CompressSkip>> {
        val jobs = mutableListOf<CompressJob>()
        val skipped = mutableListOf<CompressSkip>()
        for (game in games) {
            if (game.relativePath.substringBefore('/') in HOLDING_FOLDERS) continue
            val rule = rules.byId(game.system) ?: continue
            fun skip(reason: String) { skipped += CompressSkip(game.system, game.title, game.relativePath, reason) }
            val chd = "chd" in rule.extensions
            when {
                game.extension in alreadyCompressed -> Unit
                "msu" in game.system -> skip("MSU games need their original files.")
                game.system in gameCube && game.extension in setOf("iso", "gcm") -> rvzJob(game, files).fold(jobs::add) { skip(it.message ?: "") }
                game.system == "wii" && game.extension in setOf("iso", "gcm") ->
                    skip("Wii discs need Nintendo's key to convert, which RetroForge doesn't include. Use Dolphin's converter: long-press the game in Dolphin and choose Convert File.")
                game.system in threeDs && game.extension in setOf("3ds", "cci", "cxi", "3dsx") -> z3dsJob(game, files).fold(jobs::add) { skip(it.message ?: "") }
                game.extension == "cue" && chd -> cdJob(game, files, emptyList()).fold(jobs::add) { skip(it.message ?: "") }
                game.extension == "m3u" && chd -> {
                    val discs = runCatching { TextFiles.read(files, game.relativePath) }.getOrNull()
                        ?.lineSequence()?.map { it.trim().replace('\\', '/').removePrefix("./") }?.filter { it.endsWith(".cue", true) || it.endsWith(".gdi", true) }?.toList().orEmpty()
                    for (disc in discs) {
                        val cue = join(parentOf(game.relativePath), disc)
                        cdJob(game.copy(relativePath = cue), files, listOf(game.relativePath)).fold(jobs::add) { skip("${nameOf(cue)}: ${it.message}") }
                    }
                }
                game.extension == "iso" && chd && game.system in dvdSystems -> {
                    if (game.size % Chd.DVD_SECTOR != 0L) skip("Not a whole number of 2048-byte sectors.")
                    else dvdJob(game, files).fold(jobs::add) { skip(it.message ?: "") }
                }
                game.extension == "gdi" && chd -> cdJob(game, files, emptyList()).fold(jobs::add) { skip(it.message ?: "") }
                game.extension == "cdi" -> skip("CDI (DiscJuggler) images can't become CHD: chdman doesn't read them either. Flycast plays .cdi as it is.")
                game.extension in discFiles -> Unit
                game.size <= 0 -> Unit
                game.system in zipSystems && "zip" in rule.extensions -> {
                    val output = replaceExtension(game.relativePath, "zip")
                    when {
                        game.size > MAX_CARTRIDGE_BYTES -> skip("Larger than 1 GB.")
                        files.exists(output) -> skip("${nameOf(output)} already exists.")
                        else -> jobs += CompressJob(game.system, game.title, CompressFormat.ZIP, game.relativePath, game.systemFolder, listOf(game.relativePath), game.size, output)
                    }
                }
            }
        }
        return jobs to skipped
    }

    private fun cdJob(game: CompressCandidate, files: CollectionFiles, playlists: List<String>): Result<CompressJob> {
        val folder = parentOf(game.relativePath)
        val output = replaceExtension(game.relativePath, "chd")
        if (files.exists(output)) return Result.failure(IOException("${nameOf(output)} already exists."))
        return try {
            val gdi = game.relativePath.endsWith(".gdi", true)
            val tracks = discTracks(files, game.relativePath)
            val inputs = listOf(game.relativePath) + tracks.map { join(folder, it.file) }.distinct()
            Result.success(
                CompressJob(game.system, game.title, if (gdi) CompressFormat.GD_CHD else CompressFormat.CD_CHD, game.relativePath, game.systemFolder, inputs,
                    inputs.sumOf { files.size(it) ?: 0 }, output, playlists),
            )
        } catch (e: IOException) {
            Result.failure(e)
        }
    }

    private fun dvdJob(game: CompressCandidate, files: CollectionFiles): Result<CompressJob> {
        val output = replaceExtension(game.relativePath, "chd")
        if (files.exists(output)) return Result.failure(IOException("${nameOf(output)} already exists."))
        return Result.success(CompressJob(game.system, game.title, CompressFormat.DVD_CHD, game.relativePath, game.systemFolder, listOf(game.relativePath), game.size, output))
    }

    private fun rvzJob(game: CompressCandidate, files: CollectionFiles): Result<CompressJob> {
        val output = replaceExtension(game.relativePath, "rvz")
        if (files.exists(output)) return Result.failure(IOException("${nameOf(output)} already exists."))
        val header = runCatching { files.openRead(game.relativePath).use { Rvz.read(it, 0, 0x20) } }.getOrNull()
        if (header == null || !Rvz.isGameCube(header)) return Result.failure(IOException("Not a GameCube disc (Wii discs need Dolphin's converter)."))
        return Result.success(CompressJob(game.system, game.title, CompressFormat.RVZ, game.relativePath, game.systemFolder, listOf(game.relativePath), game.size, output))
    }

    private fun z3dsJob(game: CompressCandidate, files: CollectionFiles): Result<CompressJob> {
        val (kind, encrypted) = runCatching {
            files.openRead(game.relativePath).use { ch -> Z3ds.kindOf(Rvz.read(ch, 0, 0x200))?.let { it to Z3ds.isEncrypted(ch, it) } }
        }.getOrNull() ?: return Result.failure(IOException("Not a 3DS cartridge, NCCH or 3DSX file Azahar can compress."))
        if (encrypted) return Result.failure(IOException("Encrypted: Azahar only plays and compresses decrypted 3DS games."))
        val output = replaceExtension(game.relativePath, kind.extension)
        if (files.exists(output)) return Result.failure(IOException("${nameOf(output)} already exists."))
        return Result.success(CompressJob(game.system, game.title, CompressFormat.Z3DS, game.relativePath, game.systemFolder, listOf(game.relativePath), game.size, output))
    }

    /** The tracks of a .cue or .gdi sheet, with file names relative to the sheet's folder. */
    fun discTracks(files: CollectionFiles, sheet: String): List<CdTrack> {
        val folder = parentOf(sheet)
        val text = TextFiles.read(files, sheet)
        val size = { name: String -> files.size(join(folder, name)) }
        return if (sheet.endsWith(".gdi", true)) GdiSheet.tracks(text, size) else CueSheet.tracks(text, size)
    }

    fun replaceExtension(path: String, ext: String) = path.substringBeforeLast('.') + "." + ext
}

data class CompressProgress(val job: Int, val jobs: Int, val title: String, val stage: String, val done: Long, val total: Long)

/** [renamed]: each converted game's path before and after (e.g. ".cue" to ".chd"), for the app's own records. */
data class CompressResult(val converted: Int, val bytesBefore: Long, val bytesAfter: Long, val problems: List<String>, val renamed: List<Pair<String, String>> = emptyList())

/**
 * Converts games one at a time. Each output is written as "<name>.retroforge-part", read back and checked, and only
 * then renamed; the originals then move to _originals/ and gamelist.xml / .m3u entries point at the new file.
 */
class Compressor(private val files: CollectionFiles) {

    fun run(jobs: List<CompressJob>, journal: Journal, onProgress: (CompressProgress) -> Unit = {}, isCancelled: () -> Boolean = { false }): CompressResult {
        val problems = mutableListOf<String>()
        val renamed = mutableListOf<Pair<String, String>>()
        var converted = 0
        var before = 0L
        var after = 0L
        for ((i, job) in jobs.withIndex()) {
            if (isCancelled()) break
            fun progress(stage: String, done: Long) = onProgress(CompressProgress(i, jobs.size, job.title, stage, done, job.inputBytes))
            val partial = job.output + PARTIAL_SUFFIX
            try {
                progress("Compressing", 0)
                if (files.exists(partial)) files.discardPartial(partial)
                val size = try {
                    val written = write(job, partial, { progress("Compressing", it) }, isCancelled)
                    progress("Checking", 0)
                    verify(job, partial) { progress("Checking", it) }
                    written
                } catch (e: Exception) {
                    runCatching { files.discardPartial(partial) }
                    throw e
                }
                files.rename(partial, nameOf(job.output))
                journal.record(Step.Created(job.output))
                for (input in job.inputs) {
                    val holding = join(ORIGINALS_FOLDER, parentOf(input))
                    files.makeFolders(holding)
                    files.move(input, holding)
                    journal.record(Step.Moved(input, join(holding, nameOf(input))))
                }
                updateReferences(job, journal, problems)
                converted++
                // Playlist (.m3u) games keep their path; only the disc inside changed.
                if (job.playlists.isEmpty()) renamed += job.source to job.output
                before += job.inputBytes
                after += size
            } catch (_: CancelledException) {
                break
            } catch (e: Exception) {
                problems += "${job.title}: ${e.message ?: e.javaClass.simpleName}"
            }
        }
        return CompressResult(converted, before, after, problems, renamed)
    }

    /** Returns the size of the new file. */
    private fun write(job: CompressJob, partial: String, onBytes: (Long) -> Unit, isCancelled: () -> Boolean): Long =
        files.create(partial).use { channel ->
            when (job.format) {
                CompressFormat.ZIP -> ZipOutputStream(Channels.newOutputStream(channel)).let { zip ->
                    zip.setLevel(Deflater.BEST_COMPRESSION)
                    zip.putNextEntry(ZipEntry(nameOf(job.source)))
                    files.open(job.source).use { input ->
                        val buf = ByteArray(256 * 1024)
                        var n = 0L
                        while (true) {
                            if (isCancelled()) throw CancelledException()
                            val r = input.read(buf)
                            if (r < 0) break
                            zip.write(buf, 0, r)
                            n += r
                            onBytes(n)
                        }
                    }
                    zip.closeEntry()
                    // finish(), not close(): closing would close the channel before its size is read.
                    zip.finish()
                }
                CompressFormat.CD_CHD, CompressFormat.GD_CHD -> {
                    val folder = parentOf(job.source)
                    val tracks = CompressPlanner.discTracks(files, job.source)
                    val metadata = if (job.format == CompressFormat.GD_CHD) Chd::gdTrackMetadata else Chd::cdTrackMetadata
                    CueSheet.cdStream(tracks) { files.open(join(folder, it)) }.use { data ->
                        Chd.write(channel, data, CueSheet.logicalBytes(tracks), Chd.CD_HUNK, Chd.CD_FRAME, cd = true,
                            tracks.mapIndexed(metadata), onBytes, isCancelled)
                    }
                }
                CompressFormat.DVD_CHD -> files.open(job.source).use { data ->
                    Chd.write(channel, data, job.inputBytes, Chd.DVD_HUNK, Chd.DVD_SECTOR, cd = false, listOf(Chd.dvdMetadata), onBytes, isCancelled)
                }
                CompressFormat.RVZ -> files.openRead(job.source).use { input ->
                    Rvz.write(channel, input, job.inputBytes, onBytes, isCancelled)
                }
                CompressFormat.Z3DS -> files.openRead(job.source).use { input ->
                    val kind = Z3ds.kindOf(Rvz.read(input, 0, 0x200)) ?: throw IOException("not a 3DS file Azahar can compress")
                    Z3ds.write(channel, input, job.inputBytes, kind, onBytes, isCancelled)
                }
            }
            channel.size()
        }

    /** Reads the new file back. A zip must give back the exact ROM; a CHD must decode with every checksum intact. */
    private fun verify(job: CompressJob, partial: String, onBytes: (Long) -> Unit) {
        when (job.format) {
            CompressFormat.ZIP -> {
                val expected = CRC32().also { c -> files.open(job.source).use { s -> val b = ByteArray(256 * 1024); while (true) { val r = s.read(b); if (r < 0) break; c.update(b, 0, r) } } }.value
                ZipInputStream(files.open(partial)).use { zip ->
                    val entry = zip.nextEntry ?: throw IOException("the new zip is empty")
                    val c = CRC32()
                    val b = ByteArray(256 * 1024)
                    var n = 0L
                    while (true) { val r = zip.read(b); if (r < 0) break; c.update(b, 0, r); n += r; onBytes(n) }
                    if (entry.name != nameOf(job.source) || n != job.inputBytes || c.value != expected) throw IOException("the new zip didn't match the original")
                }
            }
            CompressFormat.CD_CHD, CompressFormat.GD_CHD, CompressFormat.DVD_CHD -> {
                // The verifier recomputes the data SHA-1 from the compressed hunks and checks it against the header,
                // which the writer filled from the original data.
                files.openRead(partial).use { ChdVerifier.verify(it, onBytes) }
            }
            CompressFormat.RVZ -> files.openRead(partial).use { rvz ->
                files.open(job.source).use { original -> RvzVerifier.verify(rvz, original, onBytes) }
            }
            CompressFormat.Z3DS -> files.openRead(partial).use { z ->
                files.open(job.source).use { original -> Z3dsVerifier.verify(z, original, onBytes) }
            }
        }
    }

    private fun updateReferences(job: CompressJob, journal: Journal, problems: MutableList<String>) {
        val gamelist = join(job.systemFolder, "gamelist.xml")
        val inSystem = { p: String -> p.removePrefix(job.systemFolder).trimStart('/') }
        if (job.playlists.isEmpty() && files.exists(gamelist)) {
            try {
                TextFiles.edit(files, gamelist, journal, backup = true) { text ->
                    val (out, change) = GamelistEditor.retarget(text, inSystem(job.source), inSystem(job.output))
                    out to listOfNotNull(change)
                }
            } catch (e: IOException) {
                problems += "$gamelist: ${e.message}"
            }
        }
        for (m3u in job.playlists) {
            val folder = parentOf(m3u)
            val rel = { p: String -> p.removePrefix(folder).trimStart('/') }
            try {
                TextFiles.edit(files, m3u, journal, backup = false) { text -> PlaylistEditor.retarget(text, rel(job.source), rel(job.output)) }
            } catch (e: IOException) {
                problems += "$m3u: ${e.message}"
            }
        }
    }
}
