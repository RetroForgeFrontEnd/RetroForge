package com.retroforge.core.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SystemArtTest {
    @Test
    fun readsSystemDetailsFromThemeMetadata() {
        val xml = """
            <theme><variables>
              <systemName>Game Boy</systemName>
              <systemManufacturer>Nintendo</systemManufacturer>
              <systemReleaseYear>1989</systemReleaseYear>
              <systemColor>3E518A</systemColor>
            </variables></theme>
        """.trimIndent()
        val meta = parseMetadata(xml)
        assertEquals("1989", meta["systemReleaseYear"])
        assertEquals("Nintendo", meta["systemManufacturer"])
        assertEquals("3E518A", meta["systemColor"])
        assertNull(meta["systemDescription"])
    }
}
