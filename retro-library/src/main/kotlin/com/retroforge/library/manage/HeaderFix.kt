package com.retroforge.library.manage

import org.apache.commons.compress.archivers.zip.ZipFile
import java.io.IOException
import java.nio.channels.Channels
import java.util.zip.CRC32
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** A header problem in a cartridge game that can be put right. */
enum class HeaderProblem {
    /** A 512-byte copier header in front of a SNES game (No-Intro and most emulators expect none). */
    SNES_COPIER_HEADER,
    /** "DiskDude!" (or other junk) written over the unused end of an iNES header, which confuses mapper detection. */
    NES_HEADER_JUNK,
    /** The checksum in a Mega Drive header doesn't match the game; some games show a red screen or refuse to start. */
    MD_CHECKSUM,
    /** The Game Boy header checksum is wrong; a real Game Boy (and strict emulators) won't start the game. */
    GB_HEADER_CHECKSUM,
    /** The Game Boy whole-cartridge checksum is wrong (only checked by a few tools; fixed along with the header). */
    GB_GLOBAL_CHECKSUM,
    /** The Game Boy Advance header complement is wrong; real hardware won't start the game. */
    GBA_COMPLEMENT,
}

sealed interface HeaderCheck

/** What fixing would change in one game; [inner] is the file inside a .zip. */
data class HeaderJob(val candidate: IdentifyCandidate, val problems: List<HeaderProblem>, val inner: String? = null) : HeaderCheck

/** A game whose header was looked at but can't or shouldn't be changed, and why. */
data class HeaderSkip(val candidate: IdentifyCandidate, val reason: String) : HeaderCheck

object HeaderFixer {
    private const val MAX_BYTES = 64L * 1024 * 1024
    private val snes = setOf("snes", "sfc", "snes-msu1", "satellaview", "sufami")
    private val megaDrive = setOf("megadrive", "genesis", "megadrive-msu", "sega32x")
    private val gameBoy = setOf("gb", "gbc")
    val systems = snes + megaDrive + gameBoy + setOf("nes", "gba")

    /** Problems that are about the data's checksums: only fixed when the game isn't a verified good copy. */
    private val checksums = setOf(HeaderProblem.MD_CHECKSUM, HeaderProblem.GB_HEADER_CHECKSUM, HeaderProblem.GB_GLOBAL_CHECKSUM, HeaderProblem.GBA_COMPLEMENT)

    /** The problems in [rom], a game for [system] stored as [fileName]. */
    fun problems(rom: ByteArray, system: String, fileName: String): List<HeaderProblem> {
        val out = mutableListOf<HeaderProblem>()
        when (system) {
            in snes -> if (rom.size % 1024 == 512) out += HeaderProblem.SNES_COPIER_HEADER
            "nes" -> if (nesJunk(rom)) out += HeaderProblem.NES_HEADER_JUNK
            in megaDrive -> if (!fileName.endsWith(".smd", true) && isMegaDrive(rom) && mdStored(rom) != mdChecksum(rom)) out += HeaderProblem.MD_CHECKSUM
            in gameBoy -> if (rom.size >= 0x150) {
                if (rom[0x14D] != gbHeader(rom)) out += HeaderProblem.GB_HEADER_CHECKSUM
                if (u16(rom, 0x14E) != gbGlobal(rom)) out += HeaderProblem.GB_GLOBAL_CHECKSUM
            }
            "gba" -> if (rom.size >= 0xC0 && rom[0xB2] == 0x96.toByte() && rom[0xBD] != gbaComplement(rom)) out += HeaderProblem.GBA_COMPLEMENT
        }
        return out
    }

    /** [rom] with [problems] put right (a new array; the input is left alone). */
    fun fix(rom: ByteArray, problems: List<HeaderProblem>): ByteArray {
        var b = rom.copyOf()
        for (p in problems) when (p) {
            HeaderProblem.SNES_COPIER_HEADER -> b = b.copyOfRange(512, b.size)
            HeaderProblem.NES_HEADER_JUNK -> {
                // "DiskDude!" starts at byte 7, so the mapper's upper half is junk too. Other tags ("NI 1.3") start at byte
                // 10, which iNES 1.0 leaves unofficial; bytes 8 and 9 (PRG RAM, TV system) are kept.
                val from = if (diskDude(b)) 7 else 10
                for (i in from..15) b[i] = 0
            }
            HeaderProblem.MD_CHECKSUM -> mdChecksum(b).let { b[0x18E] = (it shr 8).toByte(); b[0x18F] = it.toByte() }
            HeaderProblem.GB_HEADER_CHECKSUM -> b[0x14D] = gbHeader(b)
            HeaderProblem.GB_GLOBAL_CHECKSUM -> gbGlobal(b).let { b[0x14E] = (it shr 8).toByte(); b[0x14F] = it.toByte() }
            HeaderProblem.GBA_COMPLEMENT -> b[0xBD] = gbaComplement(b)
        }
        // The global checksum covers the header checksum, so it's worked out again last.
        if (HeaderProblem.GB_HEADER_CHECKSUM in problems || HeaderProblem.GB_GLOBAL_CHECKSUM in problems) {
            gbGlobal(b).let { b[0x14E] = (it shr 8).toByte(); b[0x14F] = it.toByte() }
        }
        return b
    }

    /**
     * Looks at one game. [isVerified] says whether data with this checksum and size is in the system's game list (null
     * when there is no list): a verified good copy keeps its checksums exactly as the cartridge had them.
     */
    fun check(files: CollectionFiles, c: IdentifyCandidate, isVerified: ((Long, Long) -> Boolean)?): HeaderCheck? {
        val path = c.relativePath
        val ext = path.substringAfterLast('.').lowercase()
        if (ext == "7z") return HeaderSkip(c, "7z archives can't be rewritten yet")
        val (data, inner) = read(files, path) ?: return HeaderSkip(c, "too large, or an archive with more than one file")
        var found = problems(data, c.system, inner ?: path)
        if (found.isEmpty()) return null
        if (found.any { it in checksums }) {
            val verified = isVerified?.invoke(crc(data), data.size.toLong())
            if (verified != false) {
                found = found - checksums
                if (found.isEmpty()) {
                    return if (verified == true) null
                    else HeaderSkip(c, "checksum fixes need the system's game list, to be sure it isn't a good copy")
                }
            }
        }
        return HeaderJob(c, found, inner)
    }

    /** The game's bytes, and the name inside a one-file .zip; null when it's too big or an archive of several files. */
    internal fun read(files: CollectionFiles, path: String, zipped: Boolean = path.endsWith(".zip", true)): Pair<ByteArray, String?>? {
        if (zipped) {
            return ZipFile.builder().setSeekableByteChannel(files.openRead(path)).get().use { zip ->
                val entries = zip.entries.toList().filter { !it.isDirectory }
                val e = entries.singleOrNull() ?: return null
                if (e.size > MAX_BYTES) return null
                zip.getInputStream(e).use { it.readBytes() } to e.name
            }
        }
        val size = files.size(path) ?: throw IOException("$path is missing")
        if (size > MAX_BYTES) return null
        return files.open(path).use { it.readBytes() } to null
    }

    internal fun crc(b: ByteArray) = CRC32().also { it.update(b) }.value

    private fun nesJunk(b: ByteArray): Boolean {
        if (b.size < 16 || b[0] != 'N'.code.toByte() || b[1] != 'E'.code.toByte() || b[2] != 'S'.code.toByte() || b[3] != 0x1A.toByte()) return false
        if ((b[7].toInt() and 0x0C) == 0x08) return false // NES 2.0 uses every byte
        return diskDude(b) || (12..15).any { b[it] != 0.toByte() }
    }

    private fun diskDude(b: ByteArray) = b.size >= 16 && String(b, 7, 9, Charsets.ISO_8859_1) == "DiskDude!"

    private fun isMegaDrive(b: ByteArray) = b.size > 0x200 &&
        (String(b, 0x100, 4, Charsets.ISO_8859_1) == "SEGA" || String(b, 0x101, 4, Charsets.ISO_8859_1) == "SEGA")

    private fun u16(b: ByteArray, at: Int) = ((b[at].toInt() and 0xFF) shl 8) or (b[at + 1].toInt() and 0xFF)

    private fun mdStored(b: ByteArray) = u16(b, 0x18E)

    internal fun mdChecksum(b: ByteArray): Int {
        var sum = 0
        var i = 0x200
        while (i + 1 < b.size) { sum += u16(b, i); i += 2 }
        if (i < b.size) sum += (b[i].toInt() and 0xFF) shl 8
        return sum and 0xFFFF
    }

    internal fun gbHeader(b: ByteArray): Byte {
        var x = 0
        for (i in 0x134..0x14C) x = x - (b[i].toInt() and 0xFF) - 1
        return x.toByte()
    }

    internal fun gbGlobal(b: ByteArray): Int {
        var sum = 0
        for (i in b.indices) if (i != 0x14E && i != 0x14F) sum += b[i].toInt() and 0xFF
        return sum and 0xFFFF
    }

    internal fun gbaComplement(b: ByteArray): Byte {
        var c = 0
        for (i in 0xA0..0xBC) c -= b[i].toInt() and 0xFF
        return (c - 0x19).toByte()
    }
}

/**
 * Writes fixed copies. Each game's fixed copy is written and read back first, then the original moves to _originals
 * (keeping its folder) and the copy takes its name, so the path, gamelist.xml and playlists stay as they were.
 */
class HeaderFixRunner(private val files: CollectionFiles) {
    class Result(val fixed: Int, val problems: List<String>)

    fun run(jobs: List<HeaderJob>, journal: Journal, cancelled: () -> Boolean): Result {
        val problems = mutableListOf<String>()
        var fixed = 0
        for (job in jobs) {
            if (cancelled()) break
            val path = job.candidate.relativePath
            val partial = path + PARTIAL_SUFFIX
            try {
                val (data, inner) = HeaderFixer.read(files, path) ?: throw IOException("can't be read any more")
                val out = HeaderFixer.fix(data, job.problems)
                if (files.exists(partial)) files.discardPartial(partial)
                files.create(partial).use { ch ->
                    if (inner != null) {
                        ZipOutputStream(Channels.newOutputStream(ch)).use { z -> z.putNextEntry(ZipEntry(inner)); z.write(out); z.closeEntry() }
                    } else {
                        Channels.newOutputStream(ch).use { it.write(out) }
                    }
                }
                val check = HeaderFixer.read(files, partial, zipped = inner != null)?.first
                if (check == null || !check.contentEquals(out)) {
                    files.discardPartial(partial)
                    throw IOException("the fixed copy didn't read back the same")
                }
                journal.record(Step.Created(partial))
                val holding = join(ORIGINALS_FOLDER, parentOf(path))
                files.makeFolders(holding)
                files.move(path, holding)
                journal.record(Step.Moved(path, join(holding, nameOf(path))))
                files.rename(partial, nameOf(path))
                journal.record(Step.Renamed(partial, path))
                fixed++
            } catch (e: IOException) {
                problems += "${nameOf(path)}: ${e.message}"
            }
        }
        return Result(fixed, problems)
    }
}
