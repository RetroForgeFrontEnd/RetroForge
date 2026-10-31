package com.retroforge.app.player

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveImportTest {
    /** A one-block .mcs: the save's directory entry, then its block (with its "SC" header and a title). */
    private fun mcs(title: String, fill: Byte): ByteArray {
        val bytes = ByteArray(128 + 8192)
        bytes[0] = 0x51
        bytes[4] = 0x00; bytes[5] = 0x20 // 8192 bytes
        bytes[8] = 0xFF.toByte(); bytes[9] = 0xFF.toByte()
        "BASLUS-01234SAVE".toByteArray().copyInto(bytes, 0x0A)
        bytes[128] = 'S'.code.toByte(); bytes[129] = 'C'.code.toByte()
        title.toByteArray(charset("Shift_JIS")).copyInto(bytes, 128 + 4)
        java.util.Arrays.fill(bytes, 128 + 0x200, bytes.size, fill)
        return bytes
    }

    @Test
    fun singlePlayStationSaveGoesOntoTheGamesCardBesideItsSaves() {
        val detected = SaveImport.detect("ff.mcs", mcs("FINAL FANTASY", 7))!!
        assertEquals(SaveImport.Kind.PS1_SINGLE, detected.kind)
        assertEquals(listOf("FINAL FANTASY"), detected.titles)
        val first = SaveImport.convert(detected, mcs("FINAL FANTASY", 7), null)
        val second = SaveImport.convert(SaveImport.detect("b.mcs", mcs("OTHER GAME", 9))!!, mcs("OTHER GAME", 9), first)
        val card = MemoryCard(second)
        assertEquals(listOf("FINAL FANTASY", "OTHER GAME"), card.saves.map { it.title })
        assertEquals(13, card.freeBlocks)
        assertEquals(9.toByte(), card.data[2 * 8192 + 0x300])
    }

    @Test
    fun wholeCardsAreFoundByTheirMarkWhateverTheirName() {
        val card = SaveImport.convert(SaveImport.detect("x.mcs", mcs("GAME", 1))!!, mcs("GAME", 1), null)
        val asSrm = SaveImport.detect("Game.srm", card)!!
        assertEquals(SaveImport.Kind.PS1_CARD, asSrm.kind)
        // A 128 KB cartridge save without the card's mark stays a cartridge save.
        assertEquals(SaveImport.Kind.BATTERY, SaveImport.detect("Game.srm", ByteArray(128 * 1024))!!.kind)
    }

    @Test
    fun fullCardsSayNo() {
        var card: ByteArray? = null
        repeat(15) { card = SaveImport.convert(SaveImport.detect("a.mcs", mcs("G$it", 1))!!, mcs("G$it", 1), card) }
        val result = runCatching { SaveImport.convert(SaveImport.detect("a.mcs", mcs("ONE MORE", 1))!!, mcs("ONE MORE", 1), card) }
        assertTrue(result.exceptionOrNull()?.message!!.startsWith("full:0:1"))
    }

    @Test
    fun n64PartsGoIntoTheirPlaceInMupensSave() {
        val eep = ByteArray(0x200) { 1 }
        val withEeprom = SaveImport.convert(SaveImport.detect("Mario.eep", eep)!!, eep, null)
        assertEquals(SaveImport.N64_SRM_SIZE, withEeprom.size)
        assertEquals(1.toByte(), withEeprom[0x1FF])
        assertEquals(0.toByte(), withEeprom[0x200])
        val pak = ByteArray(0x8000) { 2 }
        val withPak = SaveImport.convert(SaveImport.detect("Mario.mpk", pak)!!, pak, withEeprom)
        assertEquals(1.toByte(), withPak[0])
        assertEquals(2.toByte(), withPak[0x800])
        val sra = ByteArray(0x8000).also { it[0] = 1; it[1] = 2; it[2] = 3; it[3] = 4 }
        val swapped = SaveImport.convert(SaveImport.detect("Zelda.sra", sra)!!, sra, null, swapBytes = true)
        assertArrayEquals(byteArrayOf(4, 3, 2, 1), swapped.copyOfRange(0x20800, 0x20804))
    }

    @Test
    fun cartridgeSavesStayAsTheyAreAndOddFilesAreRefused() {
        val sav = ByteArray(0x8000) { 5 }
        val detected = SaveImport.detect("Pokemon Red.sav", sav)!!
        assertTrue("gb" in detected.systems)
        assertArrayEquals(sav, SaveImport.convert(detected, sav, ByteArray(0x8000)))
        assertNull(SaveImport.detect("notes.txt", ByteArray(100)))
        assertNull(SaveImport.detect("Zelda.sra", ByteArray(100)))
    }

    @Test
    fun namesMatchWithoutTagsOrCase() {
        assertEquals(100, SaveImport.score("Final Fantasy VII (USA) (Disc 1).chd", listOf("FINAL FANTASY VII")))
        assertEquals(100, SaveImport.score("Pokemon - Red Version (USA).gb", listOf("Pokemon - Red Version (USA, Europe).sav")))
        assertEquals(80, SaveImport.score("Pokemon - Red Version (USA).gb", listOf("Pokemon Red.sav")))
        assertTrue(SaveImport.score("Super Mario World (USA).sfc", listOf("Mario Kart 64.eep")) < 30)
    }
}
