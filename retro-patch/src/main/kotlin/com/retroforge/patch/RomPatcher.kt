package com.retroforge.patch

/** What can be learned about a patch without a ROM. Checksum fields are null for IPS, which has none. */
data class PatchInfo(
    val format: PatchFormat,
    val expectedSourceSize: Long?,
    val expectedSourceCrc32: Long?,
    val targetSize: Long?,
    val metadata: String?,
)

enum class RomMatch {
    /** The ROM is exactly the one the patch expects. */
    MATCH,
    /** The ROM matches once its 512-byte copier header is removed (common with SNES dumps). */
    MATCH_WITHOUT_HEADER,
    /** The patch was made for a different ROM or revision. */
    MISMATCH,
    /** IPS patches carry no checksum, so the ROM cannot be verified before applying. */
    UNVERIFIABLE,
}

enum class PatchNote { COPIER_HEADER_REMOVED, SOURCE_NOT_VERIFIABLE }

sealed interface PatchOutcome {
    data class Success(val output: ByteArray, val format: PatchFormat, val notes: Set<PatchNote>) : PatchOutcome
    data class Failure(val error: PatchError) : PatchOutcome
}

object RomPatcher {

    private const val COPIER_HEADER = 512

    fun detect(patch: ByteArray): PatchFormat? = when {
        startsWith(patch, "PATCH") -> PatchFormat.IPS
        startsWith(patch, "UPS1") -> PatchFormat.UPS
        startsWith(patch, "BPS1") -> PatchFormat.BPS
        else -> null
    }

    fun inspect(patch: ByteArray): Result<PatchInfo> = try {
        val info = when (val format = detect(patch) ?: fail(PatchError.UnknownFormat)) {
            PatchFormat.IPS -> PatchInfo(format, null, null, null, null)
            PatchFormat.UPS, PatchFormat.BPS -> {
                val header = (if (format == PatchFormat.UPS) Ups else Bps).header(patch)
                PatchInfo(format, header.sourceSize, header.sourceCrc32, header.targetSize, header.metadata)
            }
        }
        Result.success(info)
    } catch (failure: PatchFailure) {
        Result.failure(PatchException(failure.error))
    }

    fun checkRom(rom: ByteArray, info: PatchInfo): RomMatch {
        val crc = info.expectedSourceCrc32 ?: return RomMatch.UNVERIFIABLE
        return when {
            rom.size.toLong() == info.expectedSourceSize && crc32(rom) == crc -> RomMatch.MATCH
            hasCopierHeader(rom) && crc32(rom.copyOfRange(COPIER_HEADER, rom.size)) == crc -> RomMatch.MATCH_WITHOUT_HEADER
            else -> RomMatch.MISMATCH
        }
    }

    /** Applies [patch] to a copy of [rom]; the input array is never modified. */
    fun apply(rom: ByteArray, patch: ByteArray): PatchOutcome {
        val format = detect(patch) ?: return PatchOutcome.Failure(PatchError.UnknownFormat)
        return try {
            when (format) {
                PatchFormat.IPS -> PatchOutcome.Success(Ips.apply(rom, patch), format, setOf(PatchNote.SOURCE_NOT_VERIFIABLE))
                PatchFormat.UPS, PatchFormat.BPS -> applyChecksummed(rom, patch, format)
            }
        } catch (failure: PatchFailure) {
            PatchOutcome.Failure(failure.error)
        }
    }

    private fun applyChecksummed(rom: ByteArray, patch: ByteArray, format: PatchFormat): PatchOutcome {
        val engine = if (format == PatchFormat.UPS) Ups else Bps
        return try {
            PatchOutcome.Success(engine.apply(rom, patch), format, emptySet())
        } catch (failure: PatchFailure) {
            if (failure.error !is PatchError.WrongSourceRom || !hasCopierHeader(rom)) throw failure
            val headerless = rom.copyOfRange(COPIER_HEADER, rom.size)
            try {
                PatchOutcome.Success(engine.apply(headerless, patch), format, setOf(PatchNote.COPIER_HEADER_REMOVED))
            } catch (_: PatchFailure) {
                throw failure
            }
        }
    }

    fun crc32Of(data: ByteArray): Long = crc32(data)

    private fun hasCopierHeader(rom: ByteArray) = rom.size > COPIER_HEADER && rom.size % 1024 == COPIER_HEADER

    /** "Super Mario World (USA).sfc" + "Kaizo.bps" → "Super Mario World (USA) [Kaizo].sfc". */
    fun outputName(romName: String, patchName: String): String {
        val romBase = romName.substringBeforeLast('.', romName)
        val romExt = romName.substringAfterLast('.', "")
        val patchBase = patchName.substringBeforeLast('.', patchName)
        val safePatch = patchBase.replace(Regex("""[\\/:*?"<>|\[\]]"""), "_").trim().ifEmpty { "patched" }
        return if (romExt.isEmpty()) "$romBase [$safePatch]" else "$romBase [$safePatch].$romExt"
    }
}

/** Public wrapper so callers of [RomPatcher.inspect] can read the structured error. */
class PatchException(val error: PatchError) : Exception(error.toString())
