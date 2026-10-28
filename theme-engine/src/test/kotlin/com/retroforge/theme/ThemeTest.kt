package com.retroforge.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

class ThemeTest {

    @Test
    fun builtInThemesParse() {
        assertEquals(BuiltInThemes.ids, BuiltInThemes.all.map { it.id })
        val phosphor = BuiltInThemes.all.first { it.id == "phosphor" }
        assertEquals(FontStyle.PIXEL, phosphor.font)
        assertTrue(phosphor.scanlines && phosphor.vignette)
        assertEquals(BackgroundStyle.RADIAL, phosphor.background.style)
        assertEquals(0, phosphor.cornerRadius)
        // A dark-only theme uses its dark palette for light mode too.
        assertEquals(phosphor.dark, phosphor.palette(dark = false))
        assertEquals(GameView.LIST, BuiltInThemes.all.first { it.id == "paper" }.gameView)
    }

    @Test
    fun roundTrips() {
        for (theme in BuiltInThemes.all) assertEquals(theme, ThemeFormat.parse(ThemeFormat.write(theme)))
    }

    @Test
    fun fillsMissingColoursReadably() {
        val t = ThemeFormat.parse(
            """{"format":"retroforge-theme","version":1,"id":"mini","name":"Mini",
               "light":{"background":"#FFFFFF","surface":"#F0F0F0","primary":"#FFEE00","secondary":"#003366","text":"#111111"}}""",
        )
        val p = t.palette(dark = true)
        assertEquals(0xFF111111, p.onPrimary) // yellow gets dark text
        assertEquals(0xFFFFFFFF, p.onSecondary) // navy gets white text
    }

    private fun rejects(json: String, why: String) {
        val e = runCatching { ThemeFormat.parse(json) }.exceptionOrNull()
        assertTrue("expected rejection: $why", e is ThemeFormatException)
    }

    @Test
    fun rejectsBadThemes() {
        val ok = """"format":"retroforge-theme","version":1,"name":"X","dark":{"background":"#000000","surface":"#111111","primary":"#FF0000","secondary":"#00FF00","text":"#FFFFFF"}"""
        rejects("""{$ok,"id":"Bad Id"}""", "id")
        rejects("""{"format":"retroforge-theme","version":1,"id":"x1","name":"X"}""", "no palette")
        rejects("""{${ok.replace("#FF0000", "red")},"id":"x2"}""", "colour")
        rejects("""{${ok.replace("\"version\":1", "\"version\":9")},"id":"x3"}""", "newer version")
        rejects("<theme></theme>", "XML")
        rejects("""{$ok,"id":"x4","layout":{"gameView":"carousel3d"}}""", "layout")
    }

    @Test
    fun importsEsColours() {
        val xml = """<theme><variables>
            <backgroundColor>373737</backgroundColor>
            <gamelistPrimaryColor>EEEEEEFF</gamelistPrimaryColor>
            <gamelistSecondaryColor>999999</gamelistSecondaryColor>
            <gamelistSelectorColor>E04040</gamelistSelectorColor>
            <logoSize>0.5</logoSize>
        </variables></theme>"""
        val (theme, report) = EsThemeColors.import("Canvas", listOf(xml), "CC0-1.0")
        assertNotNull(theme)
        val p = theme!!.palette(dark = true)
        assertEquals(0xFF373737, p.background)
        assertEquals(0xFFEEEEEE, p.text)
        assertEquals(0xFFE04040, p.primary)
        assertNull(theme.light)
        assertTrue(report.used.any { it.first == "primary" && it.second == "gamelistSelectorColor" })
        assertEquals("imported-canvas", theme.id)
        // An imported theme saves in the normal format and reads back the same.
        assertEquals(theme, ThemeFormat.parse(ThemeFormat.write(theme)))
    }

    @Test
    fun replacesUnreadableCardColours() {
        // canvas-es: grey background, white menu colour, no readable text colour of its own.
        val xml = "<theme><variables><backgroundColor>373737</backgroundColor><menuBackgroundColor>ffffff</menuBackgroundColor>" +
            "<gamelistSecondaryColor>666666</gamelistSecondaryColor><gamelistSelectorColor>404040</gamelistSelectorColor></variables></theme>"
        val p = EsThemeColors.import("canvas", listOf(xml), null).first!!.palette(dark = true)
        assertEquals(0xFFFFFFFF, p.text)
        assertTrue(kotlin.math.abs(ThemeFormat.luminance(p.surface) - ThemeFormat.luminance(p.text)) >= 0.3)
        assertTrue(kotlin.math.abs(ThemeFormat.luminance(p.textMuted) - ThemeFormat.luminance(p.surface)) >= 0.2)
        // Its dark-grey selector colour would vanish on the dark background, so a readable accent is used.
        assertTrue(kotlin.math.abs(ThemeFormat.luminance(p.primary) - ThemeFormat.luminance(p.background)) >= 0.12)
    }

    @Test
    fun licences() {
        assertEquals("CC0-1.0", EsThemeColors.licenseOf("Creative Commons Legal Code\n\nCC0 1.0 Universal"))
        assertEquals("CC BY-NC-SA (non-commercial)", EsThemeColors.licenseOf("Attribution-NonCommercial-ShareAlike 4.0 International"))
    }

    /** Opt-in: imports colours from every theme in RETROFORGE_ES_THEMES (e.g. RetroBat's themes folder), read-only. */
    @Test
    fun realThemes() {
        val dir = System.getenv("RETROFORGE_ES_THEMES")?.let(::File)
        assumeTrue(dir != null && dir.isDirectory)
        for (themeDir in dir!!.listFiles()!!.filter { it.isDirectory }) {
            val xmls = themeDir.walk().maxDepth(3).filter { it.extension.equals("xml", true) && it.length() < 512 * 1024 }.take(200).map { it.readText() }.toList()
            val licenseText = themeDir.listFiles()!!.filter { it.name.lowercase().let { n -> n.startsWith("licen") || n.startsWith("readme") } }.joinToString("\n") { it.readText().take(20000) }
            val (theme, report) = EsThemeColors.import(themeDir.name, xmls, EsThemeColors.licenseOf(licenseText))
            println("THEME ${themeDir.name}: ${if (theme == null) "no colours" else "%06X on %06X".format(theme.palette(true).text and 0xFFFFFF, theme.palette(true).background and 0xFFFFFF)} used=${report.used} licence=${report.license}")
        }
    }
}
