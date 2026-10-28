package com.retroforge.app.player

import org.junit.Assert.assertEquals
import org.junit.Test

class GameFilesTest {
    @Test
    fun cueSheetsNameTheirTracks() {
        val cue = """
            FILE "Batman Forever (Track 1).bin" BINARY
              TRACK 01 MODE2/2352
                INDEX 01 00:00:00
            FILE "Batman Forever (Track 2).bin" BINARY
              TRACK 02 AUDIO
            file track3.bin binary
        """.trimIndent()
        assertEquals(listOf("Batman Forever (Track 1).bin", "Batman Forever (Track 2).bin", "track3.bin"), GameFiles.cueFiles(cue))
    }

    @Test
    fun aCueWithoutFilesNamesNothing() {
        assertEquals(emptyList<String>(), GameFiles.cueFiles("REM just a comment\nTRACK 01 AUDIO"))
    }
}
