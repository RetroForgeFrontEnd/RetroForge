package com.retroforge.patch

import java.util.zip.CRC32

/** Upper bound for any patched output. Cartridge ROMs are far smaller; this stops hostile patches exhausting memory. */
const val MAX_OUTPUT_BYTES: Long = 256L * 1024 * 1024

internal fun crc32(data: ByteArray, length: Int = data.size): Long =
    CRC32().apply { update(data, 0, length) }.value

internal fun readLe32(data: ByteArray, offset: Int): Long =
    (data[offset].toLong() and 0xFF) or
        ((data[offset + 1].toLong() and 0xFF) shl 8) or
        ((data[offset + 2].toLong() and 0xFF) shl 16) or
        ((data[offset + 3].toLong() and 0xFF) shl 24)

internal fun startsWith(data: ByteArray, magic: String): Boolean =
    data.size >= magic.length && magic.indices.all { data[it] == magic[it].code.toByte() }

/** Sequential reader with bounds checks; every overrun is reported as a malformed patch. */
internal class PatchReader(private val data: ByteArray, private val end: Int = data.size) {
    var position = 0
        private set

    val remaining: Int get() = end - position

    fun skip(count: Int) {
        if (count < 0 || count > remaining) malformed("unexpected end of patch")
        position += count
    }

    fun u8(): Int {
        if (position >= end) malformed("unexpected end of patch")
        return data[position++].toInt() and 0xFF
    }

    fun u16be(): Int = (u8() shl 8) or u8()

    fun u24be(): Int = (u8() shl 16) or (u8() shl 8) or u8()

    fun bytes(count: Int): ByteArray {
        if (count < 0 || count > remaining) malformed("unexpected end of patch")
        return data.copyOfRange(position, position + count).also { position += count }
    }

    fun matches(text: String): Boolean =
        remaining >= text.length && text.indices.all { data[position + it] == text[it].code.toByte() }

    /** The variable-length integer encoding shared by UPS and BPS (byuu's format specs). */
    fun varint(): Long {
        var result = 0L
        var shift = 1L
        while (true) {
            val byte = u8()
            result += (byte and 0x7F) * shift
            if (byte and 0x80 != 0) return result
            shift = shift shl 7
            if (shift > (1L shl 49)) malformed("number too large")
            result += shift
        }
    }
}

internal fun checkedSize(value: Long): Int {
    if (value < 0 || value > MAX_OUTPUT_BYTES) fail(PatchError.TooLarge(value))
    return value.toInt()
}
