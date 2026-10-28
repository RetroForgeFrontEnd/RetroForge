package com.retroforge.app.player

import org.junit.Assert.assertEquals
import org.junit.Test

class CheatsTest {
    @Test
    fun readsRetroArchCheatFiles() {
        val cht = """
            cheats = 3

            cheat0_desc = "Infinite Health"
            cheat0_code = "7E0DB2:99+7E0DB3:00"
            cheat0_enable = false

            cheat1_desc = "Start on Level 5"
            cheat1_code = "DD62-3B2E"
            cheat1_enable = false

            cheat2_desc = "Memory search result (no code)"
            cheat2_code = ""
            cheat2_enable = false
        """.trimIndent()
        val cheats = CheatStore.parse(cht)
        assertEquals(2, cheats.size)
        assertEquals("Infinite Health", cheats[0].description)
        assertEquals("7E0DB2:99+7E0DB3:00", cheats[0].code)
        assertEquals("DD62-3B2E", cheats[1].code)
    }
}
