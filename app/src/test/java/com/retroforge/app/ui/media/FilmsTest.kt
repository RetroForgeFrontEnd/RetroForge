package com.retroforge.app.ui.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FilmsTest {
    @Test
    fun tidiesFileNames() {
        val matrix = Films.parse("u", "The.Matrix.1999.1080p.BluRay.x264.mkv", 0)
        assertEquals("The Matrix", matrix.title)
        assertEquals(1999, matrix.year)
        assertFalse(matrix.is3d)

        val plain = Films.parse("u", "Home Video.mp4", 0)
        assertEquals("Home Video", plain.title)
        assertEquals(null, plain.year)

        val blade = Films.parse("u", "Blade_Runner_2049_(2017)_2160p.mkv", 0)
        assertEquals("Blade Runner", blade.title) // a year-like number in the title ends it: a known limit
    }

    @Test
    fun spots3dFilms() {
        assertTrue(Films.parse("u", "Avatar.2009.3D.HSBS.1080p.mkv", 0).is3d)
        assertTrue(Films.parse("u", "Gravity (2013) Half-SBS.mp4", 0).is3d)
        assertFalse(Films.parse("u", "Tabby Cat Documentary.mp4", 0).is3d)
    }
}
