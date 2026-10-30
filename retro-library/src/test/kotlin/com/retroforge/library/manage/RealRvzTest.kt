package com.retroforge.library.manage

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile

/**
 * Opt-in: converts the GameCube ISO named by RETROFORGE_RVZ_TEST to RETROFORGE_RVZ_OUT (a scratch file, never in a
 * real collection), checks it against the ISO and prints the size and time.
 */
class RealRvzTest {
    @Test
    fun convertScratchDisc() {
        val src = System.getenv("RETROFORGE_RVZ_TEST")?.let(::File)
        val out = System.getenv("RETROFORGE_RVZ_OUT")?.let(::File)
        assumeTrue("RETROFORGE_RVZ_TEST / RETROFORGE_RVZ_OUT not set", src != null && src.isFile && out != null)
        out!!.delete()
        val start = System.nanoTime()
        RandomAccessFile(out, "rw").channel.use { ch -> RandomAccessFile(src, "r").channel.use { Rvz.write(ch, it, src!!.length()) } }
        val secs = (System.nanoTime() - start) / 1e9
        RandomAccessFile(out, "r").channel.use { RvzVerifier.verify(it, src!!.inputStream()) }
        println("RVZ ${src!!.name}: ${src.length()} -> ${out.length()} bytes in %.1fs, checked".format(secs))
    }
}
