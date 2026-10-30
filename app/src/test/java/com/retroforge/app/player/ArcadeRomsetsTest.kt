package com.retroforge.app.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ArcadeRomsetsTest {
    @Test
    fun `missing files named by the emulator are picked out`() {
        val log = listOf(
            "Loading stvbios.zip",
            "epr-23603.ic8 NOT FOUND (tried in stv stvbios)",
            "315-5881_populated.bin NOT FOUND",
            "Required files are missing, the machine cannot be run.",
        )
        assertEquals(listOf("epr-23603.ic8", "315-5881_populated.bin"), ArcadeRomsets.missingFiles(log))
        assertTrue(ArcadeRomsets.explain("MAME", log)!!.contains("epr-23603.ic8"))
    }

    @Test
    fun `no named files means no arcade message`() {
        assertNull(ArcadeRomsets.explain("MAME", listOf("Video mode set", "Required files are missing")))
    }
}
