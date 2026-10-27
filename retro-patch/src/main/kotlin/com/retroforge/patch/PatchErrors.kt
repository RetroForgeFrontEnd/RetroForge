package com.retroforge.patch

enum class PatchFormat(val extension: String, val label: String) {
    IPS("ips", "IPS"),
    UPS("ups", "UPS"),
    BPS("bps", "BPS"),
}

/** Why a patch could not be applied. Every case is something the UI can explain to the user. */
sealed interface PatchError {
    data object UnknownFormat : PatchError
    data class Malformed(val detail: String) : PatchError
    /** The patch file's own checksum is wrong: it is damaged or incomplete. */
    data object PatchDamaged : PatchError
    /** The ROM is not the one this patch was made for. */
    data class WrongSourceRom(val expectedCrc32: Long, val actualCrc32: Long, val expectedSize: Long) : PatchError
    /** The patch applied but the result does not match the checksum the patch promises. */
    data object OutputMismatch : PatchError
    data class TooLarge(val bytes: Long) : PatchError
}

internal class PatchFailure(val error: PatchError) : Exception(error.toString())

internal fun fail(error: PatchError): Nothing = throw PatchFailure(error)

internal fun malformed(detail: String): Nothing = fail(PatchError.Malformed(detail))
