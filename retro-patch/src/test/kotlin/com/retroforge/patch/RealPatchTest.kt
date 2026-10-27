package com.retroforge.patch

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/**
 * Applies real-world patches kept in the git-ignored `patch-test/` folder (ROMs and patches are never committed).
 * Skipped when the folder is empty. Each `<name>.ips|bps|ups` (or a .zip holding one) is applied to the ROM named in
 * `patch-test/<name>.base`, and the SHA-1 of the result is printed for comparison with an independent tool.
 */
class RealPatchTest {

    private val dir = File("../patch-test")

    @Test
    fun applyRealPatches() {
        val bases = dir.listFiles { file -> file.extension == "base" }.orEmpty()
        assumeTrue("no real patches in ${dir.absolutePath}", bases.isNotEmpty())
        for (base in bases) {
            val patchFile = dir.listFiles { f -> f.nameWithoutExtension == base.nameWithoutExtension && f.extension != "base" }!!.single()
            val patch = (ArchiveInput.readPatch(patchFile.name, patchFile.readBytes()) as ArchiveResult.Ok).file.bytes
            val romFile = File(base.readText().trim())
            val rom = (ArchiveInput.readRom(romFile.name, romFile.readBytes()) as ArchiveResult.Ok).file.bytes
            val outcome = RomPatcher.apply(rom, patch)
            check(outcome is PatchOutcome.Success) { "${patchFile.name}: $outcome" }
            val sha1 = MessageDigest.getInstance("SHA-1").digest(outcome.output).joinToString("") { "%02x".format(it) }
            println("REAL-PATCH ${patchFile.name}: ${outcome.output.size} bytes sha1=$sha1 notes=${outcome.notes}")
        }
    }
}
