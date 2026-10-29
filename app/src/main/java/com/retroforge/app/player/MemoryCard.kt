package com.retroforge.app.player

import java.text.Normalizer

/**
 * A PlayStation memory card image (128 KB: the built-in player's .srm for PlayStation games, and .mcr/.mcd/.mc files
 * from other emulators): 16 blocks of 8 KB, block 0 holding the directory. Lists the saves, copies one onto another
 * card and deletes one. [data] is changed in place; write it back afterwards.
 */
class MemoryCard(val data: ByteArray) {
    /** One save: its first block, how many blocks it takes, and the title the PlayStation shows. */
    data class Save(val firstBlock: Int, val blocks: List<Int>, val fileName: String, val title: String)

    init {
        require(data.size == SIZE) { "not a PlayStation memory card (${data.size} bytes)" }
    }

    val saves: List<Save>
        get() = (1..15).filter { state(it) == FIRST }.mapNotNull { first ->
            val chain = mutableListOf(first)
            var next = nextOf(first)
            while (next in 1..15 && next !in chain && state(next).let { it == MIDDLE || it == LAST }) {
                chain += next
                next = nextOf(next)
            }
            Save(first, chain, fileName(first), title(first))
        }

    val freeBlocks: Int get() = (1..15).count { state(it) !in IN_USE }

    /** Copies [save] from [from] onto this card; false if there isn't room. */
    fun copy(from: MemoryCard, save: Save): Boolean {
        val free = (1..15).filter { state(it) !in IN_USE }
        if (free.size < save.blocks.size) return false
        val targets = free.take(save.blocks.size)
        save.blocks.forEachIndexed { i, block ->
            val target = targets[i]
            System.arraycopy(from.data, block * BLOCK, data, target * BLOCK, BLOCK)
            val entry = dirOffset(target)
            System.arraycopy(from.data, dirOffset(block), data, entry, FRAME)
            data[entry] = when {
                save.blocks.size == 1 || i == 0 -> FIRST.toByte()
                i == save.blocks.lastIndex -> LAST.toByte()
                else -> MIDDLE.toByte()
            }
            val next = if (i == save.blocks.lastIndex) 0xFFFF else targets[i + 1] - 1
            data[entry + 8] = (next and 0xFF).toByte()
            data[entry + 9] = (next shr 8).toByte()
            if (i > 0) for (k in 4..7) data[entry + k] = 0 // only the first block carries the size
            if (i > 0) for (k in 0x0A until 0x1F) data[entry + k] = 0
            checksum(target)
        }
        return true
    }

    /** Frees [save]'s blocks, as the PlayStation's own card manager does. */
    fun delete(save: Save) {
        save.blocks.forEach { block ->
            val entry = dirOffset(block)
            java.util.Arrays.fill(data, entry, entry + FRAME, 0)
            data[entry] = FREE.toByte()
            data[entry + 8] = 0xFF.toByte()
            data[entry + 9] = 0xFF.toByte()
            checksum(block)
        }
    }

    private fun dirOffset(block: Int) = block * FRAME
    private fun state(block: Int) = data[dirOffset(block)].toInt() and 0xFF
    private fun nextOf(block: Int): Int {
        val o = dirOffset(block)
        val raw = (data[o + 8].toInt() and 0xFF) or ((data[o + 9].toInt() and 0xFF) shl 8)
        return if (raw == 0xFFFF) -1 else raw + 1
    }

    private fun fileName(block: Int): String {
        val o = dirOffset(block) + 0x0A
        return String(data, o, 20, Charsets.US_ASCII).substringBefore('\u0000')
    }

    /** The save's title (Shift-JIS, usually full-width letters) as plain text. */
    private fun title(block: Int): String {
        val o = block * BLOCK + 4
        var end = o
        while (end < o + 64 && data[end] != 0.toByte()) end++
        val raw = runCatching { String(data, o, end - o, charset("Shift_JIS")) }.getOrDefault("")
        return Normalizer.normalize(raw, Normalizer.Form.NFKC).replace(Regex("\\s+"), " ").trim()
            .ifEmpty { fileName(block) }
    }

    private fun checksum(block: Int) {
        val o = dirOffset(block)
        var x = 0
        for (k in 0 until FRAME - 1) x = x xor data[o + k].toInt()
        data[o + FRAME - 1] = x.toByte()
    }

    companion object {
        const val SIZE = 128 * 1024
        private const val BLOCK = 8192
        private const val FRAME = 128
        private const val FIRST = 0x51
        private const val MIDDLE = 0x52
        private const val LAST = 0x53
        private const val FREE = 0xA0
        private val IN_USE = setOf(FIRST, MIDDLE, LAST)

        /** A formatted, empty card. */
        fun blank(): MemoryCard {
            val data = ByteArray(SIZE)
            data[0] = 'M'.code.toByte(); data[1] = 'C'.code.toByte(); data[FRAME - 1] = 0x0E
            val card = MemoryCard(data)
            for (block in 1..15) {
                val o = block * FRAME
                data[o] = FREE.toByte(); data[o + 8] = 0xFF.toByte(); data[o + 9] = 0xFF.toByte()
                card.checksum(block)
            }
            return card
        }

        /** Card files from other emulators: raw 128 KB images, or DexDrive .gme (3904-byte header in front). */
        fun read(bytes: ByteArray): MemoryCard = when {
            bytes.size == SIZE -> MemoryCard(bytes)
            bytes.size == SIZE + 3904 && String(bytes, 0, 12, Charsets.US_ASCII).startsWith("123-456-STD") ->
                MemoryCard(bytes.copyOfRange(3904, bytes.size))
            else -> throw IllegalArgumentException("not a PlayStation memory card")
        }
    }
}
