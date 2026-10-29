package com.retroforge.app.ui.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FilmArtworkTest {
    @Test
    fun findsPostersAndNfoNextToTheFilm() {
        val siblings = listOf("Avatar (2009).mkv", "Avatar (2009)-poster.jpg", "Avatar (2009).nfo", "fanart.jpg")
        assertEquals("Avatar (2009)-poster.jpg" to "Avatar (2009).nfo", Films.companions("Avatar (2009).mkv", siblings, 1))
    }

    @Test
    fun folderWideNamesOnlyForAFilmInItsOwnFolder() {
        val siblings = listOf("Heat.mkv", "poster.jpg", "movie.nfo")
        assertEquals("poster.jpg" to "movie.nfo", Films.companions("Heat.mkv", siblings, 1))
        assertEquals(null to null, Films.companions("Heat.mkv", siblings + "Ronin.mkv", 2))
    }

    @Test
    fun readsKodiNfo() {
        val info = Films.nfo(
            """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<movie>
  <title>Avatar</title>
  <plot>A paraplegic Marine is sent to Pandora.</plot>
  <ratings><rating name="imdb"><value>7.9</value></rating><rating name="themoviedb" default="true"><value>7.6</value></rating></ratings>
  <premiered>2009-12-15</premiered>
  <runtime>162</runtime>
  <uniqueid type="tmdb">19995</uniqueid><uniqueid type="imdb">tt0499549</uniqueid>
</movie>""",
        )!!
        assertEquals("Avatar", info.title)
        assertEquals(7.6, info.rating!!, 0.001)
        assertEquals("2009", info.year)
        assertEquals(162, info.runtime)
        assertEquals("tt0499549", info.imdb)
    }

    @Test
    fun readsOlderNfoAndRejectsOthers() {
        val info = Films.nfo("<movie><title>Heat</title><rating>8.3</rating><year>1995</year><id>tt0113277</id></movie>")!!
        assertEquals(8.3, info.rating!!, 0.001)
        assertEquals("1995", info.year)
        assertEquals("tt0113277", info.imdb)
        assertNull(Films.nfo("<tvshow><title>Not a film</title></tvshow>"))
        assertNull(Films.nfo("https://www.themoviedb.org/movie/19995"))
    }
}
