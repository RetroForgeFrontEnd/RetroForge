package com.retroforge.artwork

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenScraperTest {

    private val xml = """<?xml version="1.0" encoding="UTF-8"?>
<Data>
  <ssuser><id>someone</id><requeststoday>12</requeststoday><maxrequestsperday>20000</maxrequestsperday></ssuser>
  <jeu id="3020" romid="1">
    <noms><nom region="ss">Super Mario World</nom><nom region="jp">Super Mario World: Super Mario Bros. 4</nom></noms>
    <medias>
      <media type="ss" region="wor" format="png">https://neoclone.screenscraper.fr/api2/mediaJeu.php?devid=D&amp;devpassword=P&amp;softname=S&amp;ssid=U&amp;sspassword=X&amp;systemeid=4&amp;jeuid=3020&amp;media=ss(wor)</media>
      <media type="box-3D" region="us" format="png">https://neoclone.screenscraper.fr/api2/mediaJeu.php?media=box-3D(us)</media>
      <media type="box-2D" region="jp" format="png">https://neoclone.screenscraper.fr/api2/mediaJeu.php?media=box-2D(jp)</media>
      <media type="box-2D" region="us" format="png">https://neoclone.screenscraper.fr/api2/mediaJeu.php?media=box-2D(us)</media>
      <media type="box-2D" region="eu" format="png">https://neoclone.screenscraper.fr/api2/mediaJeu.php?media=box-2D(eu)</media>
    </medias>
  </jeu>
</Data>"""

    private class Recording(private val code: Int, private val body: String) : HttpGet {
        var lastUrl: String? = null
        override fun get(url: String, headers: Map<String, String>, maxBytes: Long): HttpResponse {
            lastUrl = url
            return HttpResponse(code, body.toByteArray())
        }
    }

    private val app = ScreenScraperApp("dev", "devpass", "RetroForge")

    @Test
    fun parsesGameAndOrdersCovers() {
        val result = ScreenScraper.parse(xml.toByteArray()) as SsResult.Found
        assertEquals("3020", result.game.id)
        assertEquals("Super Mario World", result.game.name)
        assertEquals(SsQuota(12, 20000), result.quota)
        val covers = ScreenScraper.covers(result.game)
        assertEquals(listOf("box-2D/us", "box-2D/eu", "box-2D/jp", "box-3D/us"), covers.map { "${it.type}/${it.region}" })
        assertEquals("box-2D/eu", ScreenScraper.covers(result.game, preferredRegion = "eu").first().let { "${it.type}/${it.region}" })
    }

    @Test
    fun stripsCredentialsFromMediaUrls() {
        val url = (ScreenScraper.parse(xml.toByteArray()) as SsResult.Found).game.medias.first().url
        assertEquals("https://neoclone.screenscraper.fr/api2/mediaJeu.php?systemeid=4&jeuid=3020&media=ss(wor)", ScreenScraper.withoutCredentials(url))
    }

    @Test
    fun buildsRequestLikeEsDe() {
        val http = Recording(200, xml)
        val ss = ScreenScraper(http, { app }, { ScreenScraperUser("me", "p&ss") })
        assertTrue(ss.gameInfo(4, "snes/Super Mario World (USA).sfc", 524288, "abc123") is SsResult.Found)
        val url = http.lastUrl!!
        assertTrue(url.startsWith("https://api.screenscraper.fr/api2/jeuInfos.php?devid=dev&devpassword=devpass&softname=RetroForge&ssid=me&sspassword=p%26ss"))
        assertTrue(url.contains("&output=xml&romtype=rom&systemeid=4&romnom=Super+Mario+World+%28USA%29.sfc&romtaille=524288&md5=abc123"))
    }

    @Test
    fun withoutDeveloperIdNothingIsSent() {
        val http = Recording(200, xml)
        assertEquals(SsResult.NotAvailable, ScreenScraper(http, { null }, { null }).gameInfo(4, "a.sfc", 1, null))
        assertEquals(null, http.lastUrl)
    }

    @Test
    fun errorsAndNotFound() {
        assertEquals(SsResult.NotFound, ScreenScraper(Recording(404, "Erreur : Rom/Iso/Dossier non trouvée !"), { app }, { null }).gameInfo(4, "a.sfc", 1, null))
        assertTrue(ScreenScraper(Recording(430, ""), { app }, { null }).gameInfo(4, "a.sfc", 1, null) is SsResult.Failed)
    }

    @Test
    fun refusesDoctype() {
        val evil = """<?xml version="1.0"?><!DOCTYPE x [<!ENTITY e SYSTEM "file:///etc/passwd">]><Data><jeu id="1"><noms><nom>&e;</nom></noms></jeu></Data>"""
        assertTrue(ScreenScraper.parse(evil.toByteArray()) is SsResult.Failed)
    }

    @Test
    fun systemIds() {
        assertEquals(4, ScreenScraper.systemIds["snes"])
        assertEquals(57, ScreenScraper.systemIds["psx"])
        assertEquals(1, ScreenScraper.systemIds["megadrive"])
    }
}
