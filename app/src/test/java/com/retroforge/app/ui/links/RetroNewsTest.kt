package com.retroforge.app.ui.links

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class RetroNewsTest {
    private val news = RetroNews.Source("Test site", "https://example.com/feed", RetroNews.Kind.NEWS)

    private val rss = """<?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0" xmlns:content="http://purl.org/rss/1.0/modules/content/">
          <channel><title>Test site</title>
            <item>
              <title>Older story &amp; more</title>
              <link>https://example.com/older</link>
              <pubDate>Wed, 28 Oct 2026 09:00:00 +0000</pubDate>
              <description><![CDATA[<p>A <b>short</b> line&#8230; with&nbsp;tags</p><script>x()</script>]]></description>
            </item>
            <item>
              <title>Newer story</title>
              <link>https://example.com/newer</link>
              <pubDate>Fri, 30 Oct 2026 18:30:00 GMT</pubDate>
              <content:encoded><![CDATA[<p>Body</p>]]></content:encoded>
            </item>
            <item><title>No link</title></item>
          </channel>
        </rss>""".trimIndent().toByteArray()

    private val atom = """<?xml version="1.0" encoding="utf-8"?>
        <feed xmlns="http://www.w3.org/2005/Atom">
          <title>Releases</title>
          <entry>
            <title>v2.0</title>
            <link rel="alternate" type="text/html" href="https://example.com/releases/v2.0"/>
            <updated>2026-10-29T12:00:00Z</updated>
            <content type="html">&lt;h2&gt;What's new&lt;/h2&gt;&lt;ul&gt;&lt;li&gt;Faster&lt;/li&gt;&lt;/ul&gt;</content>
          </entry>
        </feed>""".trimIndent().toByteArray()

    @Test
    fun rssStoriesComeNewestFirstAsPlainText() {
        val stories = RetroNews.parse(news, rss)
        assertEquals(listOf("Newer story", "Older story & more"), stories.map { it.title })
        assertEquals("https://example.com/older", stories[1].link)
        assertEquals("A short line… with tags", stories[1].summary)
        assertEquals("Body", stories[0].summary)
        assertEquals(1_793_385_000_000L, stories[0].time)
    }

    @Test
    fun atomEntriesUseTheirAlternateLink() {
        val story = RetroNews.parse(news.copy(kind = RetroNews.Kind.EMULATORS), atom).single()
        assertEquals("v2.0", story.title)
        assertEquals("https://example.com/releases/v2.0", story.link)
        assertEquals("What's new Faster", story.summary)
        assertEquals(RetroNews.Kind.EMULATORS, story.kind)
    }

    @Test
    fun feedsWithADoctypeAreRefused() {
        val evil = """<?xml version="1.0"?><!DOCTYPE rss [<!ENTITY x SYSTEM "file:///etc/passwd">]><rss><channel><item><title>&x;</title><link>https://e.com</link></item></channel></rss>""".toByteArray()
        assertTrue(runCatching { RetroNews.parse(news, evil) }.getOrNull().let { it == null || it.none { s -> s.title.contains("root") } })
    }

    @Test
    fun aSourceThatFailsIsNamedAndTheRestStillShow() = runBlocking {
        val fetched = RetroNews.fetch { url -> if (url.contains("dolphin")) throw IOException("down") else rss }
        assertEquals(listOf("Dolphin"), fetched.failed)
        assertEquals((RetroNews.sources.size - 1) * 2, fetched.stories.size)
    }
}
