package com.retroforge.app.player

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Calendar

/**
 * PlayStation 2 memory cards for the built-in PS2 player (LRPS2): 8 MB raw images (pcsx2/memcards/Mcd00N.ps2), 16384
 * pages of 512 bytes, each followed by 16 spare bytes holding the page's ECC.
 *
 * LRPS2 makes new cards blank (all 0xFF), which games are meant to format, but some games' own format never reaches
 * the card (Hot Shots Golf Fore! asks to format again after Yes), so they can't save. Cards that are missing or still
 * blank are given the layout the PS2's own format writes (as mcman does: superblock, FAT, an empty root folder).
 */
object Ps2MemoryCard {
    private const val PAGE = 512
    private const val RAW_PAGE = PAGE + 16
    private const val PAGES_PER_CLUSTER = 2
    private const val PAGES_PER_BLOCK = 16
    private const val PAGES = 16384
    private const val CLUSTER = PAGE * PAGES_PER_CLUSTER
    private const val CLUSTERS = PAGES / PAGES_PER_CLUSTER
    private const val ALLOC_OFFSET = 41
    private const val ALLOC_END = 8135
    private const val IFC = 8
    private val FAT_CLUSTERS = 9..40
    const val SIZE = PAGES * RAW_PAGE

    /** Formats [systemDir]'s PS2 cards that are missing or blank; cards holding anything are left alone. */
    fun prepare(systemDir: File, now: Calendar = Calendar.getInstance()) {
        val dir = File(systemDir, "pcsx2/memcards")
        for (name in listOf("Mcd001.ps2", "Mcd002.ps2")) {
            val card = File(dir, name)
            if (card.exists() && !isBlank(card)) continue
            dir.mkdirs()
            card.writeBytes(formatted(now))
        }
    }

    private fun isBlank(card: File): Boolean {
        if (card.length() != SIZE.toLong()) return false
        val buf = ByteArray(64 * 1024)
        RandomAccessFile(card, "r").use { f ->
            while (true) {
                val n = f.read(buf)
                if (n < 0) return true
                for (i in 0 until n) if (buf[i] != 0xFF.toByte()) return false
            }
        }
    }

    /** A formatted, empty 8 MB card; [now] dates the root folder. */
    fun formatted(now: Calendar): ByteArray {
        val img = ByteArray(SIZE) { 0xFF.toByte() }

        fun cluster(c: Int, data: ByteArray) {
            val full = data.copyOf(CLUSTER).also { java.util.Arrays.fill(it, data.size, CLUSTER, 0xFF.toByte()) }
            for (k in 0 until PAGES_PER_CLUSTER) {
                val o = (c * PAGES_PER_CLUSTER + k) * RAW_PAGE
                System.arraycopy(full, k * PAGE, img, o, PAGE)
                for (chunk in 0 until PAGE / 128) ecc(full, k * PAGE + chunk * 128, img, o + PAGE + chunk * 3)
            }
        }
        fun le(size: Int) = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)

        // Superblock: the format's magic and version, geometry, where the FAT and data start, backup blocks.
        val sb = le(CLUSTER)
        sb.put("Sony PS2 Memory Card Format 1.2.0.0".toByteArray(Charsets.US_ASCII)).put(ByteArray(5))
        sb.putShort(PAGE.toShort()).putShort(PAGES_PER_CLUSTER.toShort()).putShort(PAGES_PER_BLOCK.toShort()).putShort(0xFF00.toShort())
        sb.putInt(CLUSTERS).putInt(ALLOC_OFFSET).putInt(ALLOC_END).putInt(0).putInt(1023).putInt(1022)
        sb.put(ByteArray(8)).putInt(IFC)
        repeat(31) { sb.putInt(0) }
        repeat(32) { sb.putInt(-1) } // no bad blocks
        sb.put(2).put(0x52)
        while (sb.hasRemaining()) sb.put(0xFF.toByte())
        cluster(0, sb.array())

        // Indirect FAT: the clusters holding the FAT.
        cluster(IFC, le(CLUSTER).apply { FAT_CLUSTERS.forEach { putInt(it) } }.array())

        // FAT, counted from the first data cluster: the root folder's cluster ends its chain, the rest are free.
        val fat = IntArray(CLUSTERS) { if (it < ALLOC_END) 0x7FFFFFFF else -1 }
        fat[0] = -1
        FAT_CLUSTERS.forEachIndexed { n, c -> cluster(c, le(CLUSTER).apply { for (i in 0 until 256) putInt(fat[n * 256 + i]) }.array()) }

        // Root folder: "." and "..".
        val time = byteArrayOf(
            0, now.get(Calendar.SECOND).toByte(), now.get(Calendar.MINUTE).toByte(), now.get(Calendar.HOUR_OF_DAY).toByte(),
            now.get(Calendar.DAY_OF_MONTH).toByte(), (now.get(Calendar.MONTH) + 1).toByte(),
            (now.get(Calendar.YEAR) and 0xFF).toByte(), (now.get(Calendar.YEAR) shr 8).toByte(),
        )
        fun entry(mode: Int, length: Int, name: String) = le(PAGE).apply {
            putShort(mode.toShort()).putShort(0).putInt(length).put(time)
            position(0x18); put(time)
            position(0x40); put(name.toByteArray(Charsets.US_ASCII))
        }.array()
        cluster(ALLOC_OFFSET, entry(0x8427, 2, ".") + entry(0xA426, 0, ".."))
        return img
    }

    /** The 3-byte ECC of the 128 bytes at [data][from], as the PS2's card controller computes it. */
    private fun ecc(data: ByteArray, from: Int, out: ByteArray, at: Int) {
        var e0 = 0; var e1 = 0; var e2 = 0
        for (i in 0 until 128) {
            val c = ECC_TABLE[data[from + i].toInt() and 0xFF]
            e0 = e0 xor c
            if (c and 0x80 != 0) { e1 = e1 xor (i.inv() and 0xFF); e2 = e2 xor i }
        }
        out[at] = (e0.inv() and 0x77).toByte()
        out[at + 1] = (e1.inv() and 0x7F).toByte()
        out[at + 2] = (e2.inv() and 0x7F).toByte()
    }

    private val ECC_TABLE: IntArray = run {
        val half = intArrayOf(
            0x00, 0x87, 0x96, 0x11, 0xa5, 0x22, 0x33, 0xb4, 0xb4, 0x33, 0x22, 0xa5, 0x11, 0x96, 0x87, 0x00,
            0xc3, 0x44, 0x55, 0xd2, 0x66, 0xe1, 0xf0, 0x77, 0x77, 0xf0, 0xe1, 0x66, 0xd2, 0x55, 0x44, 0xc3,
            0xd2, 0x55, 0x44, 0xc3, 0x77, 0xf0, 0xe1, 0x66, 0x66, 0xe1, 0xf0, 0x77, 0xc3, 0x44, 0x55, 0xd2,
            0x11, 0x96, 0x87, 0x00, 0xb4, 0x33, 0x22, 0xa5, 0xa5, 0x22, 0x33, 0xb4, 0x00, 0x87, 0x96, 0x11,
            0xe1, 0x66, 0x77, 0xf0, 0x44, 0xc3, 0xd2, 0x55, 0x55, 0xd2, 0xc3, 0x44, 0xf0, 0x77, 0x66, 0xe1,
            0x22, 0xa5, 0xb4, 0x33, 0x87, 0x00, 0x11, 0x96, 0x96, 0x11, 0x00, 0x87, 0x33, 0xb4, 0xa5, 0x22,
            0x33, 0xb4, 0xa5, 0x22, 0x96, 0x11, 0x00, 0x87, 0x87, 0x00, 0x11, 0x96, 0x22, 0xa5, 0xb4, 0x33,
            0xf0, 0x77, 0x66, 0xe1, 0x55, 0xd2, 0xc3, 0x44, 0x44, 0xc3, 0xd2, 0x55, 0xe1, 0x66, 0x77, 0xf0,
        )
        half + half.reversedArray() // the second half mirrors the first
    }
}
