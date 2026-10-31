package com.retroforge.library

import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Reads a PlayStation `param.sfo` (PS3, PS4, PSP, Vita): a small key/value table holding the game's TITLE, TITLE_ID,
 * CATEGORY and so on. Text values come back as strings, numbers as their decimal text. Anything malformed gives an
 * empty map rather than an error: a bad file only costs the game its proper title.
 */
object ParamSfo {
    const val MAX_BYTES = 64 * 1024

    fun parse(bytes: ByteArray): Map<String, String> = runCatching {
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (bytes.size < 20 || b.getInt(0) != 0x46535000) return emptyMap() // "\0PSF"
        val keys = b.getInt(8)
        val data = b.getInt(12)
        val count = b.getInt(16)
        if (count !in 0..256) return emptyMap()
        buildMap {
            for (i in 0 until count) {
                val at = 20 + i * 16
                val keyAt = keys + (b.getShort(at).toInt() and 0xFFFF)
                val format = b.getShort(at + 2).toInt() and 0xFFFF
                val length = b.getInt(at + 4)
                val valueAt = data + b.getInt(at + 12)
                val key = cString(bytes, keyAt) ?: continue
                if (valueAt < 0 || length < 0 || valueAt + length > bytes.size) continue
                put(key, when (format) {
                    0x0404 -> (b.getInt(valueAt).toLong() and 0xFFFFFFFFL).toString()
                    else -> String(bytes, valueAt, length, Charsets.UTF_8).trimEnd('\u0000')
                })
            }
        }
    }.getOrDefault(emptyMap())

    private fun cString(bytes: ByteArray, from: Int): String? {
        if (from !in bytes.indices) return null
        var end = from
        while (end < bytes.size && bytes[end] != 0.toByte()) end++
        return String(bytes, from, end - from, Charsets.US_ASCII)
    }
}
