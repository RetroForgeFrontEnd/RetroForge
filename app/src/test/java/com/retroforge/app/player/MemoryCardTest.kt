package com.retroforge.app.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MemoryCardTest {
    /** A card holding one two-block save in blocks 1-2, titled in full-width Shift-JIS like real games. */
    private fun cardWithSave(): MemoryCard {
        val card = MemoryCard.blank()
        val d = card.data
        fun entry(block: Int, state: Int, next: Int) {
            val o = block * 128
            d[o] = state.toByte()
            d[o + 8] = (next and 0xFF).toByte(); d[o + 9] = (next shr 8).toByte()
        }
        entry(1, 0x51, 1) // next block is 2 (stored as 1)
        "BASLUS-00067ABC".toByteArray().copyInto(d, 128 + 0x0A)
        entry(2, 0x53, 0xFFFF)
        d[8192] = 'S'.code.toByte(); d[8193] = 'C'.code.toByte()
        "ＦＦ７／セーブ".toByteArray(charset("Shift_JIS")).copyInto(d, 8192 + 4)
        return card
    }

    @Test
    fun listsSavesWithTitles() {
        val save = cardWithSave().saves.single()
        assertEquals(listOf(1, 2), save.blocks)
        assertEquals("BASLUS-00067ABC", save.fileName)
        assertEquals("FF7/セーブ", save.title)
        assertEquals(13, cardWithSave().freeBlocks)
    }

    @Test
    fun copiesOntoAnotherCardAndDeletes() {
        val from = cardWithSave()
        val to = MemoryCard.blank()
        assertTrue(to.copy(from, from.saves.single()))
        assertTrue(to.copy(from, from.saves.single()))
        val copies = to.saves
        assertEquals(listOf(listOf(1, 2), listOf(3, 4)), copies.map { it.blocks })
        assertEquals("FF7/セーブ", copies[1].title)
        // The copy's data block came across.
        assertEquals(from.data[8192 + 5], to.data[3 * 8192 + 5])
        to.delete(copies[0])
        assertEquals(listOf(listOf(3, 4)), to.saves.map { it.blocks })
        assertEquals(13, to.freeBlocks)
    }

    @Test
    fun refusesWhenFull() {
        val from = cardWithSave()
        val to = MemoryCard.blank()
        repeat(7) { assertTrue(to.copy(from, from.saves.single())) }
        assertEquals(1, to.freeBlocks)
        assertFalse(to.copy(from, from.saves.single()))
    }

    @Test
    fun readsDexDriveFiles() {
        val gme = ByteArray(3904 + MemoryCard.SIZE)
        "123-456-STD".toByteArray().copyInto(gme)
        cardWithSave().data.copyInto(gme, 3904)
        assertEquals(1, MemoryCard.read(gme).saves.size)
    }
}
