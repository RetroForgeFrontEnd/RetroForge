package com.retroforge.patch

import com.retroforge.patch.TestPatches.Command.SourceCopy
import com.retroforge.patch.TestPatches.Command.SourceRead
import com.retroforge.patch.TestPatches.Command.TargetCopy
import com.retroforge.patch.TestPatches.Command.TargetRead
import com.retroforge.patch.TestPatches.bytes
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import kotlin.random.Random

class RomPatcherTest {

    private val rom = Random(42).nextBytes(4096)

    private fun success(outcome: PatchOutcome): PatchOutcome.Success =
        outcome as? PatchOutcome.Success ?: run { fail("expected success, got $outcome"); error("unreachable") }

    private fun error(outcome: PatchOutcome): PatchError =
        (outcome as? PatchOutcome.Failure)?.error ?: run { fail("expected failure, got $outcome"); error("unreachable") }

    // --- IPS ---

    @Test
    fun ipsWritesRecordsAndRle() {
        val patch = TestPatches.ips(10 to bytes(1, 2, 3), rle = listOf(Triple(100, 5, 0xAA)))
        val result = success(RomPatcher.apply(rom, patch))
        val expected = rom.copyOf().also { bytes(1, 2, 3).copyInto(it, 10); it.fill(0xAA.toByte(), 100, 105) }
        assertArrayEquals(expected, result.output)
        assertEquals(setOf(PatchNote.SOURCE_NOT_VERIFIABLE), result.notes)
    }

    @Test
    fun ipsCanExtendAndTruncate() {
        val extended = success(RomPatcher.apply(rom, TestPatches.ips(4096 to bytes(7, 8)))).output
        assertEquals(4098, extended.size)
        assertEquals(8, extended[4097].toInt())
        val truncated = success(RomPatcher.apply(rom, TestPatches.ips(0 to bytes(9), truncateTo = 1024))).output
        assertEquals(1024, truncated.size)
    }

    @Test
    fun ipsWithoutEofIsMalformed() {
        val patch = TestPatches.ips(0 to bytes(1)).let { it.copyOf(it.size - 3) }
        assertTrue(error(RomPatcher.apply(rom, patch)) is PatchError.Malformed)
    }

    // --- UPS ---

    @Test
    fun upsRoundTripsGrowingAndShrinkingTargets() {
        for (targetSize in listOf(4096, 5000, 3000)) {
            val target = rom.copyOf(targetSize).also { it[17] = 1; it[2000] = 2; if (targetSize > 4096) it[4500] = 3 }
            val result = success(RomPatcher.apply(rom, TestPatches.ups(rom, target)))
            assertArrayEquals("target size $targetSize", target, result.output)
        }
    }

    @Test
    fun upsRejectsWrongRomAndDamagedPatch() {
        val target = rom.copyOf().also { it[0] = (it[0] + 1).toByte() }
        val patch = TestPatches.ups(rom, target)
        val otherRom = rom.copyOf().also { it[5] = (it[5] + 1).toByte() }
        assertTrue(error(RomPatcher.apply(otherRom, patch)) is PatchError.WrongSourceRom)
        val damaged = patch.copyOf().also { it[6] = (it[6] + 1).toByte() }
        assertEquals(PatchError.PatchDamaged, error(RomPatcher.apply(rom, damaged)))
    }

    // --- BPS ---

    private val bpsTarget: ByteArray = ByteArray(0).let {
        // source[0..99], "HELLO", "HELLO" twice more via a target copy that reads bytes it is still writing, then source[200..259].
        rom.copyOfRange(0, 100) + "HELLOHELLOHELLO".toByteArray() + rom.copyOfRange(200, 260)
    }

    private val bpsCommands = listOf(
        SourceRead(100),
        TargetRead("HELLO".toByteArray()),
        TargetCopy(100, 10),
        SourceCopy(200, 60),
    )

    @Test
    fun bpsAppliesAllFourCommandsIncludingOverlappingCopy() {
        val result = success(RomPatcher.apply(rom, TestPatches.bps(rom, bpsTarget, bpsCommands)))
        assertArrayEquals(bpsTarget, result.output)
        assertTrue(result.notes.isEmpty())
    }

    @Test
    fun bpsSupportsNegativeSourceOffsets() {
        val target = rom.copyOfRange(500, 510) + rom.copyOfRange(100, 110)
        val patch = TestPatches.bps(rom, target, listOf(SourceCopy(500, 10), SourceCopy(-410, 10)))
        assertArrayEquals(target, success(RomPatcher.apply(rom, patch)).output)
    }

    @Test
    fun bpsMetadataAndChecksumsAreInspectable() {
        val patch = TestPatches.bps(rom, bpsTarget, bpsCommands, metadata = "<title>Test Hack</title>")
        val info = RomPatcher.inspect(patch).getOrThrow()
        assertEquals(PatchFormat.BPS, info.format)
        assertEquals("<title>Test Hack</title>", info.metadata)
        assertEquals(4096L, info.expectedSourceSize)
        assertEquals(RomMatch.MATCH, RomPatcher.checkRom(rom, info))
    }

    @Test
    fun bpsOutOfRangeTargetCopyIsMalformedNotACrash() {
        val patch = TestPatches.bps(rom, bpsTarget, listOf(TargetCopy(5, 10)))
        assertTrue(error(RomPatcher.apply(rom, patch)) is PatchError.Malformed)
    }

    // --- SNES copier header ---

    @Test
    fun headeredRomIsMatchedAndPatchedAfterRemovingHeader() {
        val patch = TestPatches.bps(rom, bpsTarget, bpsCommands)
        val headered = ByteArray(512) + rom
        assertEquals(RomMatch.MATCH_WITHOUT_HEADER, RomPatcher.checkRom(headered, RomPatcher.inspect(patch).getOrThrow()))
        val result = success(RomPatcher.apply(headered, patch))
        assertArrayEquals(bpsTarget, result.output)
        assertEquals(setOf(PatchNote.COPIER_HEADER_REMOVED), result.notes)
    }

    @Test
    fun genuinelyWrongRomStillFailsAfterHeaderRetry() {
        val patch = TestPatches.bps(rom, bpsTarget, bpsCommands)
        val wrong = ByteArray(512) + Random(7).nextBytes(4096)
        assertTrue(error(RomPatcher.apply(wrong, patch)) is PatchError.WrongSourceRom)
        assertEquals(RomMatch.MISMATCH, RomPatcher.checkRom(wrong, RomPatcher.inspect(patch).getOrThrow()))
    }

    // --- chains ---

    @Test
    fun chainAppliesEachPatchToThePreviousResult() {
        // Step 1 (IPS) changes byte 0; step 2 (BPS) was built against step 1's output, so its source check must pass.
        val step1 = rom.copyOf().also { it[0] = 0x55 }
        val step2 = step1.copyOf().also { it[1000] = 0x66 }
        val chain = RomPatcher.applyChain(rom, listOf(TestPatches.ips(0 to bytes(0x55)), TestPatches.ups(step1, step2)))
        val success = chain as ChainOutcome.Success
        assertArrayEquals(step2, success.output)
        assertEquals(listOf(setOf(PatchNote.SOURCE_NOT_VERIFIABLE), emptySet()), success.stepNotes)
    }

    @Test
    fun chainInWrongOrderReportsTheFailingStep() {
        val step1 = rom.copyOf().also { it[0] = 0x55 }
        val step2 = step1.copyOf().also { it[1000] = 0x66 }
        // The UPS patch expects step 1's output; given the original ROM first it must fail at index 0.
        val chain = RomPatcher.applyChain(rom, listOf(TestPatches.ups(step1, step2), TestPatches.ips(0 to bytes(0x55))))
        val failure = chain as ChainOutcome.Failure
        assertEquals(0, failure.step)
        assertTrue(failure.error is PatchError.WrongSourceRom)
    }

    @Test
    fun chainOutputIsNamedAfterTheLastPatch() {
        assertEquals("SMB [2B].nes", RomPatcher.outputName("SMB.nes", listOf("Giant Mario.ips", "2B.ips")))
    }

    // --- general ---

    @Test
    fun originalRomIsNeverModified() {
        val before = rom.copyOf()
        RomPatcher.apply(rom, TestPatches.ips(0 to bytes(1, 2, 3, 4)))
        RomPatcher.apply(rom, TestPatches.bps(rom, bpsTarget, bpsCommands))
        assertArrayEquals(before, rom)
    }

    @Test
    fun unknownDataIsRejected() {
        assertEquals(PatchError.UnknownFormat, error(RomPatcher.apply(rom, "not a patch".toByteArray())))
        assertTrue(RomPatcher.inspect(ByteArray(0)).isFailure)
    }

    @Test
    fun outputNameKeepsRomExtensionAndSanitisesPatchName() {
        assertEquals("Super Mario World (USA) [Kaizo].sfc", RomPatcher.outputName("Super Mario World (USA).sfc", "Kaizo.bps"))
        assertEquals("Game [a_b_c].smc", RomPatcher.outputName("Game.smc", "a/b:c.ips"))
    }
}
