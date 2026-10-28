package com.retroforge.library

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Opt-in: checks a real BIOS folder read-only when RETROFORGE_REAL_BIOS points at it (e.g. G:\Retrobat\bios). */
class RealBiosTest {

    @Test
    fun checkRealBiosFolder() {
        val path = System.getenv("RETROFORGE_REAL_BIOS")
        assumeTrue("RETROFORGE_REAL_BIOS not set", !path.isNullOrBlank() && File(path).isDirectory)
        val checker = BiosChecker.bundled()
        val systems = checker.rules.flatMap { it.systems }
        checker.check(FileNode(File(path)), systems).forEach { r ->
            println("REAL-BIOS ${r.rule.systems.joinToString("/")}: ${r.status} found=${r.found.map { "${it.foundAs}${if (it.verified) "" else " (unverified)"}" }} missing=${r.missing.map { it.name }}")
        }
    }
}
