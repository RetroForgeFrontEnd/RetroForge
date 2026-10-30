package com.retroforge.library.manage

/**
 * CD-ROM sector error correction (the P and Q Reed-Solomon parity of ECMA-130), ported from MAME's cdrom.cpp
 * (BSD-3-Clause). CHD's CD codecs leave out the sync header and parity of every sector they can rebuild, as chdman
 * does: parity looks random to Deflate, so storing it costs most of the space in data tracks and GD-ROM filler.
 */
internal object CdEcc {
    private val SYNC = byteArrayOf(0, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0)
    private const val MODE_OFFSET = 0x00F
    private const val P_OFFSET = 0x81C
    private const val P_BYTES = 86
    private const val P_COMP = 24
    private const val Q_OFFSET = P_OFFSET + 2 * P_BYTES
    private const val Q_BYTES = 52
    private const val Q_COMP = 43

    // Multiply by 2 in GF(2^8) (polynomial 0x11D), and its counterpart: high[i xor low[i]] = i.
    private val low = IntArray(256) { ((it shl 1) xor (if (it and 0x80 != 0) 0x11D else 0)) and 0xFF }
    private val high = IntArray(256).also { t -> for (i in 0 until 256) t[i xor low[i]] = i }
    private val pOffsets = Array(P_BYTES) { b -> IntArray(P_COMP) { c -> 86 * c + b } }
    private val qOffsets = Array(Q_BYTES) { b -> IntArray(Q_COMP) { c -> ((b / 2) * 86 + (b % 2) + 88 * c) % 2236 } }

    fun hasSync(sector: ByteArray, at: Int): Boolean = (0 until 12).all { sector[at + it] == SYNC[it] }

    /** True when the sector at [at] carries sync and parity that [generate] would rebuild exactly. */
    fun verify(sector: ByteArray, at: Int): Boolean {
        if (!hasSync(sector, at)) return false
        for (b in 0 until P_BYTES) {
            val v = compute(sector, at, pOffsets[b])
            if (sector[at + P_OFFSET + b] != (v shr 8).toByte() || sector[at + P_OFFSET + P_BYTES + b] != v.toByte()) return false
        }
        for (b in 0 until Q_BYTES) {
            val v = compute(sector, at, qOffsets[b])
            if (sector[at + Q_OFFSET + b] != (v shr 8).toByte() || sector[at + Q_OFFSET + Q_BYTES + b] != v.toByte()) return false
        }
        return true
    }

    fun clear(sector: ByteArray, at: Int) {
        sector.fill(0, at, at + 12)
        sector.fill(0, at + P_OFFSET, at + P_OFFSET + 2 * P_BYTES)
        sector.fill(0, at + Q_OFFSET, at + Q_OFFSET + 2 * Q_BYTES)
    }

    /** Puts back the sync header and parity that [clear] removed. */
    fun generate(sector: ByteArray, at: Int) {
        SYNC.copyInto(sector, at)
        for (b in 0 until P_BYTES) {
            val v = compute(sector, at, pOffsets[b])
            sector[at + P_OFFSET + b] = (v shr 8).toByte()
            sector[at + P_OFFSET + P_BYTES + b] = v.toByte()
        }
        for (b in 0 until Q_BYTES) {
            val v = compute(sector, at, qOffsets[b])
            sector[at + Q_OFFSET + b] = (v shr 8).toByte()
            sector[at + Q_OFFSET + Q_BYTES + b] = v.toByte()
        }
    }

    /** One pair of parity bytes (first in the high byte) over the bytes at [row], counted from the end of sync. */
    private fun compute(sector: ByteArray, at: Int, row: IntArray): Int {
        val mode2 = sector[at + MODE_OFFSET].toInt() == 2
        var v1 = 0
        var v2 = 0
        for (o in row) {
            // In mode 2 the address header counts as zeros.
            val s = if (mode2 && o < 4) 0 else sector[at + 12 + o].toInt() and 0xFF
            v1 = low[v1 xor s]
            v2 = v2 xor s
        }
        v1 = high[low[v1] xor v2]
        v2 = v2 xor v1
        return (v1 shl 8) or v2
    }
}
