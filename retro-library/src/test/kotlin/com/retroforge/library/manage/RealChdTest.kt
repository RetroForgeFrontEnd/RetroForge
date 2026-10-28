package com.retroforge.library.manage

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile

/**
 * Opt-in: converts every .cue / .iso in RETROFORGE_CHD_TEST (a scratch folder, never a real collection) to
 * "<name>.rf.chd", verifies it and prints the data SHA-1 for comparison with `chdman info`.
 */
class RealChdTest {
    @Test
    fun convertScratchDiscs() {
        val dir = System.getenv("RETROFORGE_CHD_TEST")?.let(::File)
        assumeTrue("RETROFORGE_CHD_TEST not set", dir != null && dir.isDirectory)
        dir!!.walk().filter { it.extension.lowercase() in setOf("cue", "iso") }.forEach { src ->
            val out = File(src.parentFile, src.nameWithoutExtension + ".rf.chd")
            out.delete()
            val start = System.nanoTime()
            val written = RandomAccessFile(out, "rw").channel.use { ch ->
                if (src.extension.equals("cue", true)) {
                    val tracks = CueSheet.tracks(src.readText()) { File(src.parentFile, it).takeIf(File::isFile)?.length() }
                    CueSheet.cdStream(tracks) { File(src.parentFile, it).inputStream() }.use { data ->
                        Chd.write(ch, data, CueSheet.logicalBytes(tracks), Chd.CD_HUNK, Chd.CD_FRAME, cd = true, tracks.mapIndexed(Chd::cdTrackMetadata))
                    }
                } else {
                    src.inputStream().use { Chd.write(ch, it, src.length(), Chd.DVD_HUNK, Chd.DVD_SECTOR, cd = false, listOf(Chd.dvdMetadata)) }
                }
            }
            val secs = (System.nanoTime() - start) / 1e9
            val report = RandomAccessFile(out, "r").channel.use { ChdVerifier.verify(it) }
            println("CHD ${src.name}: ${src.length()} -> ${out.length()} bytes in %.1fs, data SHA1 ${report.dataSha1.joinToString("") { "%02x".format(it) }}".format(secs))
            check(report.dataSha1.contentEquals(written.dataSha1))
        }
    }
}
