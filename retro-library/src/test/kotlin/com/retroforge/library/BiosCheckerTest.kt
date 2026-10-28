package com.retroforge.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class BiosCheckerTest {

    private fun md5(s: String) = MessageDigest.getInstance("MD5").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    // Synthetic "BIOS" contents; real BIOS files never appear in tests.
    private val usa = "fake-ps1-usa"
    private val eur = "fake-ps1-eur"
    private val cdU = "fake-segacd-usa"

    private val checker = BiosChecker(
        listOf(
            BiosRule(
                listOf("psx"), "SwanStation", BiosNeed.REQUIRED, "any",
                files = listOf(BiosFile("scph5501.bin", listOf(md5(usa)), usa.length.toLong()), BiosFile("scph5502.bin", listOf(md5(eur)), eur.length.toLong())),
            ),
            BiosRule(listOf("megacd"), "Genesis Plus GX", BiosNeed.REQUIRED, "any", files = listOf(BiosFile("bios_CD_U.bin", listOf(md5(cdU)), cdU.length.toLong()))),
            BiosRule(
                listOf("nds"), "melonDS DS", BiosNeed.OPTIONAL, "all",
                files = listOf(BiosFile("bios7.bin", listOf(md5("b7")), 2), BiosFile("bios9.bin", listOf(md5("b9")), 2)),
            ),
            BiosRule(listOf("dreamcast"), "Flycast", BiosNeed.RECOMMENDED, "all", files = listOf(BiosFile("dc/dc_boot.bin", listOf(md5("dcb")), 3))),
        ),
    )

    @Test
    fun verifiedByChecksumEvenUnderAnotherName() {
        val folder = dir("bios", file("SCPH-5501 (USA).BIN", usa), file("notes.txt", "hello"))
        val report = checker.check(folder, setOf("psx")).single()
        assertEquals(BiosStatus.OK, report.status)
        assertEquals("scph5501.bin", report.found.single().wanted.name)
        assertEquals("SCPH-5501 (USA).BIN", report.found.single().foundAs)
        assertTrue(report.found.single().verified)
    }

    @Test
    fun rightNameWrongContentsIsUnverified() {
        val report = checker.check(dir("bios", file("bios_CD_U.bin", "some other dump")), setOf("megacd")).single()
        assertEquals(BiosStatus.UNVERIFIED, report.status)
        assertFalse(report.found.single().verified)
    }

    @Test
    fun allModeReportsPartialAndMissing() {
        val partial = checker.check(dir("bios", file("bios7.bin", "b7")), setOf("nds")).single()
        assertEquals(BiosStatus.PARTIAL, partial.status)
        assertEquals(listOf("bios9.bin"), partial.missing.map { it.name })
        assertEquals(BiosStatus.MISSING, checker.check(dir("bios"), setOf("nds")).single().status)
    }

    @Test
    fun filesInSubFoldersAreFound() {
        val report = checker.check(dir("bios", dir("dc", file("dc_boot.bin", "dcb"))), setOf("dreamcast")).single()
        assertEquals(BiosStatus.OK, report.status)
    }

    @Test
    fun onlySystemsInTheLibraryAreChecked() {
        val reports = checker.check(dir("bios", file("x.bin", usa)), setOf("snes", "psx"))
        assertEquals(listOf(listOf("psx")), reports.map { it.rule.systems })
        assertTrue(checker.check(dir("bios"), setOf("snes")).isEmpty())
    }

    @Test
    fun bundledRulesLoadAndCoverTheMainBiosSystems() {
        val bundled = BiosChecker.bundled()
        listOf("psx", "megacd", "pcenginecd", "fds", "gba", "nds", "dreamcast").forEach { system ->
            assertTrue(system, bundled.rulesFor(listOf(system)).isNotEmpty())
        }
        assertTrue(bundled.rules.all { rule -> rule.files.all { f -> f.md5.all { it.matches(Regex("[0-9a-f]{32}")) } } })
    }
}
