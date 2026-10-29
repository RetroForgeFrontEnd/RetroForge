package com.retroforge.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EsdeCollectionsTest {
    @Test
    fun readsCustomCollections() {
        assertEquals("Couch co-op", EsdeCollections.nameOf("custom-Couch co-op.cfg"))
        assertNull(EsdeCollections.nameOf("es_settings.xml"))
        val c = EsdeCollections.parse(
            "Favourites",
            "%ROMPATH%/snes/Super Mario World (USA).zip\n/storage/emulated/0/ROMs/gba/Minish Cap (USA).gba\n\n",
        )
        assertEquals(listOf("snes/super mario world (usa).zip", "gba/minish cap (usa).gba"), c.games)
    }

    @Test
    fun matchesLibraryPaths() {
        assertTrue(EsdeCollections.matches("roms/snes/Super Mario World (USA).zip", "snes/super mario world (usa).zip"))
        assertTrue(EsdeCollections.matches("snes/Super Mario World (USA).zip", "snes/super mario world (usa).zip"))
        assertFalse(EsdeCollections.matches("roms/snes/Super Mario World 2.zip", "snes/super mario world (usa).zip"))
    }
}
