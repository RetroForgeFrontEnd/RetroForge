package com.retroforge.patch

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * Applies real-world patches kept in the git-ignored `patch-test/` folder (ROMs and patches are never committed).
 * Skipped when the folder has no `.base` files. A `<name>.base` file's first line is the ROM path; any further
 * lines name patch files in `patch-test/` to apply in order. With no further lines, the patch is `<name>.*`.
 * The SHA-1 of each result is printed for comparison with an independent tool.
 */
class RealPatchTest {

    private val dir = File("../patch-test")

    @Test
    fun applyRealPatches() {
        val bases = dir.listFiles { file -> file.extension == "base" }.orEmpty()
        assumeTrue("no real patches in ${dir.absolutePath}", bases.isNotEmpty())
        for (base in bases) {
            val lines = base.readLines().map { it.trim() }.filter { it.isNotEmpty() }
            val patchFiles = lines.drop(1).map { File(dir, it) }.ifEmpty {
                dir.listFiles { f -> f.nameWithoutExtension == base.nameWithoutExtension && f.extension != "base" }!!.toList()
            }
            if (!patchFiles.all { it.exists() }) {
                println("REAL-PATCH ${base.name}: skipped, missing ${patchFiles.filterNot { it.exists() }.map { it.name }}")
                continue
            }
            val patches = patchFiles.map { (ArchiveInput.readPatch(it.name, it.readBytes()) as ArchiveResult.Ok).file.bytes }
            val romFile = File(lines.first())
            val rom = (ArchiveInput.readRom(romFile.name, romFile.readBytes()) as ArchiveResult.Ok).file.bytes
            val outcome = RomPatcher.applyChain(rom, patches)
            check(outcome is ChainOutcome.Success) { "${base.name}: $outcome" }
            val sha1 = MessageDigest.getInstance("SHA-1").digest(outcome.output).joinToString("") { "%02x".format(it) }
            println("REAL-PATCH ${base.name} (${patchFiles.joinToString(" -> ") { it.name }}): ${outcome.output.size} bytes sha1=$sha1")
        }
    }
}
