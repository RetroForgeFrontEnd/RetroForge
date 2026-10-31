package com.retroforge.app.ui.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MusicTest {
    @Test
    fun numberAndTitleFromFileNameArtistFromFolder() {
        val t = Music.parse("u", "03 - Clint Eastwood.mp3", "Gorillaz", "Gorillaz", "k")
        assertEquals(3, t.number)
        assertEquals("Clint Eastwood", t.title)
        assertEquals("Gorillaz", t.artist)
        assertEquals("Gorillaz", t.album)
    }

    @Test
    fun artistInFileNameWins() {
        val t = Music.parse("u", "07. Daft Punk - One More Time.flac", "Now 48", "Compilations", "k")
        assertEquals(7, t.number)
        assertEquals("Daft Punk", t.artist)
        assertEquals("One More Time", t.title)
    }

    @Test
    fun discAndTrackPrefixAndPlainNames() {
        assertEquals(12, Music.parse("u", "2-12 Song.m4a", "A", null, "k").number)
        val plain = Music.parse("u", "Song_Name.ogg", null, null, "k")
        assertNull(plain.number)
        assertEquals("Song Name", plain.title)
        assertEquals("Song Name", plain.album)
    }

    @Test
    fun albumsSortTracksAndDropMixedArtists() {
        val a = Music.parse("1", "02 - B.mp3", "Album", "X", "k")
        val b = Music.parse("2", "01 - A.mp3", "Album", "X", "k")
        val c = Music.parse("3", "Y - C.mp3", "Album", "X", "k")
        val album = Music.albums(listOf(a, b, c)).single()
        assertEquals(listOf("A", "B", "C"), album.tracks.map { it.title })
        assertNull(album.artist)
        assertEquals(3, Music.trackNumber(1003))
        assertNull(Music.trackNumber(0))
    }
}
