package com.retroforge.library.manage

/**
 * The lagged Fibonacci generator (f = xor, j = 32, k = 521) that GameCube and Wii discs use for their padding
 * ("junk") data. Ported from Dolphin's LaggedFibonacciGenerator (CC0-1.0). RVZ stores a run of junk as its 17-word
 * seed instead of the bytes, so finding the seed that produced some data is what makes RVZ files small.
 *
 * Each word of [buf] holds four output bytes, most significant first, already in the shifted form the discs use
 * (bits taken by a shift of 18, not 16), so output is a plain big-endian read of the buffer.
 */
internal class Lfg {
    private val buf = IntArray(K)
    private var pos = 0

    fun setSeed(seed: IntArray) {
        seed.copyInto(buf, 0, 0, SEED_WORDS)
        pos = 0
        initialize(check = false)
    }

    /** Moves to [offset] bytes after the start of the seed's output. */
    fun skip(offset: Long) {
        var p = pos + offset
        while (p >= BUFFER_BYTES) {
            forward()
            p -= BUFFER_BYTES
        }
        pos = p.toInt()
    }

    fun nextByte(): Int {
        val b = (buf[pos ushr 2] ushr (24 - 8 * (pos and 3))) and 0xFF
        if (++pos == BUFFER_BYTES) {
            forward()
            pos = 0
        }
        return b
    }

    fun fill(out: ByteArray, from: Int, count: Int) {
        for (i in from until from + count) out[i] = nextByte().toByte()
    }

    private fun forward() {
        for (i in 0 until J) buf[i] = buf[i] xor buf[i + K - J]
        for (i in J until K) buf[i] = buf[i] xor buf[i - J]
    }

    private fun backward(start: Int = 0, end: Int = K) {
        val loopEnd = maxOf(J, start)
        var i = minOf(end, K)
        while (i > loopEnd) { buf[i - 1] = buf[i - 1] xor buf[i - 1 - J]; i-- }
        i = minOf(end, J)
        while (i > start) { buf[i - 1] = buf[i - 1] xor buf[i - 1 + K - J]; i-- }
    }

    private fun initialize(check: Boolean): Boolean {
        for (i in SEED_WORDS until K) {
            val calculated = (buf[i - 17] shl 23) xor (buf[i - 16] ushr 9) xor buf[i - 1]
            if (check) {
                val actual = (buf[i] and 0xFF00FFFF.toInt()) or ((buf[i] shl 2) and 0x00FC0000)
                if ((calculated and 0xFFFCFFFF.toInt()) != actual) return false
            }
            buf[i] = calculated
        }
        for (i in 0 until K) buf[i] = (buf[i] and 0xFF00FFFF.toInt()) or ((buf[i] ushr 2) and 0x00FF0000)
        repeat(4) { forward() }
        return true
    }

    private fun reinitialize(seedOut: IntArray): Boolean {
        repeat(4) { backward() }
        // Bits 16 and 17 are lost to the shift by 18; rebuild them from the words that produced them.
        for (i in 0 until SEED_WORDS) {
            buf[i] = (buf[i] and 0xFF00FFFF.toInt()) or ((buf[i] shl 2) and 0x00FC0000) or
                (((buf[i + 16] xor buf[i + 15]) shl 9) and 0x00030000)
        }
        buf.copyInto(seedOut, 0, 0, SEED_WORDS)
        return initialize(check = true)
    }

    companion object {
        const val K = 521
        const val J = 32
        const val SEED_WORDS = 17
        const val SEED_BYTES = SEED_WORDS * 4
        private const val BUFFER_BYTES = K * 4

        /**
         * Looks for junk at [data] from [from] for [size] bytes, where [offsetInBlock] is the position of [from]
         * within its 32 KiB disc block. Returns how many bytes the recovered seed reproduces (0 if none) and fills
         * [seedOut].
         */
        fun findSeed(data: ByteArray, from: Int, size: Int, offsetInBlock: Int, seedOut: IntArray): Int {
            val skip = ((offsetInBlock + 3) and 3.inv()) - offsetInBlock
            val words = (size - skip) / 4
            if (words < K) return 0
            val wordOffset = (offsetInBlock + skip) / 4
            val start = from + skip
            fun word(i: Int): Int {
                val p = start + i * 4
                return ((data[p].toInt() and 0xFF) shl 24) or ((data[p + 1].toInt() and 0xFF) shl 16) or
                    ((data[p + 2].toInt() and 0xFF) shl 8) or (data[p + 3].toInt() and 0xFF)
            }
            for (i in 0 until K) {
                val v = word(i)
                if ((v and 0x00C00000) != ((v ushr 2) and 0x00C00000)) return 0
            }
            val lfg = Lfg()
            val modK = wordOffset % K
            val divK = wordOffset / K
            for (i in 0 until K - modK) lfg.buf[modK + i] = word(i)
            for (i in 0 until modK) lfg.buf[i] = word(K - modK + i)
            lfg.backward(0, modK)
            repeat(divK) { lfg.backward() }
            if (!lfg.reinitialize(seedOut)) return 0
            repeat(divK) { lfg.forward() }
            lfg.pos = offsetInBlock % BUFFER_BYTES
            var n = 0
            while (n < size && lfg.nextByte() == (data[from + n].toInt() and 0xFF)) n++
            return n
        }
    }
}
