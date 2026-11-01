package com.retroforge.app.player

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest
import java.util.Calendar

class Ps2MemoryCardTest {
    @get:Rule val tmp = TemporaryFolder()

    private val time = Calendar.getInstance().apply { clear(); set(2026, Calendar.NOVEMBER, 1, 12, 0, 0) }

    private fun md5(b: ByteArray) = MessageDigest.getInstance("MD5").digest(b).joinToString("") { "%02x".format(it) }

    @Test
    fun formattedCardMatchesTheOneFore() {
        // The same card Hot Shots Golf Fore! saved to and loaded from in LRPS2 on 1 Nov 2026.
        val card = Ps2MemoryCard.formatted(time)
        assertEquals(8650752, card.size)
        assertEquals("57672881f811b01f6abad14ba87f46ee", md5(card))
    }

    @Test
    fun blankAndMissingCardsAreFormattedOthersLeftAlone() {
        val dir = File(tmp.root, "pcsx2/memcards").apply { mkdirs() }
        File(dir, "Mcd001.ps2").writeBytes(ByteArray(Ps2MemoryCard.SIZE) { 0xFF.toByte() })
        val used = Ps2MemoryCard.formatted(time).also { it[100000] = 1 }
        File(dir, "Mcd002.ps2").writeBytes(used)

        Ps2MemoryCard.prepare(tmp.root, time)
        assertArrayEquals(Ps2MemoryCard.formatted(time), File(dir, "Mcd001.ps2").readBytes())
        assertArrayEquals(used, File(dir, "Mcd002.ps2").readBytes())

        File(dir, "Mcd001.ps2").delete()
        Ps2MemoryCard.prepare(tmp.root, time)
        assertEquals(Ps2MemoryCard.SIZE.toLong(), File(dir, "Mcd001.ps2").length())
    }
}
