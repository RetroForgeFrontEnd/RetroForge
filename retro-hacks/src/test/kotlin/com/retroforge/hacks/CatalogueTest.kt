package com.retroforge.hacks

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CatalogueTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun hack(id: String, extra: String = "", pageUrl: String = "https://example.org/hacks/$id") = """
        {"id":"$id","title":"Hack $id","system":"snes","baseGame":"Game","summary":"A hack.","pageUrl":"$pageUrl"$extra}
    """.trimIndent()

    private fun catalogue(id: String = "test", vararg hacks: String, format: String = CatalogueParser.FORMAT, version: Int = 1) = """
        {"format":"$format","version":$version,"id":"$id","name":"Test $id","hacks":[${hacks.joinToString(",")}],"futureField":true}
    """.trimIndent()

    private fun valid(text: String) = CatalogueParser.parse(text) as ParseResult.Valid

    @Test
    fun parsesAValidCatalogueAndIgnoresUnknownFields() {
        val result = valid(catalogue("test", hack("a"), hack("b", ""","requires":["a"],"baseRom":{"crc32":"B19ED489"}""")))
        assertEquals(listOf("a", "b"), result.catalogue.hacks.map { it.id })
        assertEquals(listOf("a"), result.catalogue.hacks[1].requires)
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun rejectsWrongFormatNewerVersionAndGarbage() {
        assertTrue(CatalogueParser.parse(catalogue(format = "something-else")) is ParseResult.Invalid)
        assertTrue(CatalogueParser.parse(catalogue(version = 2)) is ParseResult.Invalid)
        assertTrue(CatalogueParser.parse("{not json") is ParseResult.Invalid)
        val page = CatalogueParser.parse("<!doctype html><html></html>") as ParseResult.Invalid
        assertTrue(page.reason.startsWith("That's a web page"))
        assertTrue(CatalogueParser.parse(catalogue(id = "../escape")) is ParseResult.Invalid)
    }

    @Test
    fun badEntriesAreDroppedWithWarningsNotTheWholeCatalogue() {
        val result = valid(
            catalogue(
                "test",
                hack("good"),
                hack("plain-http", pageUrl = "http://example.org/x"),
                hack("script", pageUrl = "javascript:alert(1)"),
                hack("bad-crc", ""","baseRom":{"crc32":"XYZ"}"""),
                hack("good"),
                hack("needs", ""","requires":["missing"]"""),
            ),
        )
        assertEquals(listOf("good", "needs"), result.catalogue.hacks.map { it.id })
        assertEquals(5, result.warnings.size)
    }

    @Test
    fun theBuiltInStarterCatalogueIsValid() {
        val text = File("../app/src/main/assets/catalogues/retroforge-starter.json").readText()
        val result = valid(text)
        assertTrue(result.catalogue.hacks.isNotEmpty())
        assertTrue(result.warnings.toString(), result.warnings.isEmpty())
        // Official pages only: romhacking.net or the hack's own GitHub project (which also gives direct patch downloads).
        assertTrue(result.catalogue.hacks.all { it.pageUrl.startsWith("https://www.romhacking.net/") || it.pageUrl.startsWith("https://github.com/") })
        assertTrue(result.catalogue.hacks.filter { it.patchRepo != null }.all { it.pageUrl == "https://github.com/${it.patchRepo}" })
    }

    @Test
    fun storeAddsRefreshesRemovesAndKeepsBuiltInFirst() {
        var now = 1000L
        val store = CatalogueStore(tmp.newFolder("cat"), builtIn = { catalogue("builtin", hack("x")) }, clock = { now })
        val added = store.add(catalogue("mine", hack("y")), url = "https://example.org/mine.json") as AddResult.Added
        assertEquals("https://example.org/mine.json", added.source.url)

        now = 2000L
        val refreshed = store.add(catalogue("mine", hack("y"), hack("z")), url = "https://example.org/mine.json") as AddResult.Added
        assertEquals(1000L, refreshed.source.addedAt)
        assertEquals(2000L, refreshed.source.refreshedAt)

        val (loaded, problems) = store.load()
        assertTrue(problems.isEmpty())
        assertEquals(listOf("builtin", "mine"), loaded.map { it.catalogue.id })
        assertEquals(listOf("x", "y", "z"), CatalogueStore.merge(loaded).map { it.entry.id })

        store.remove("mine")
        assertEquals(listOf("builtin"), store.load().first.map { it.catalogue.id })
    }

    @Test
    fun storeRejectsInvalidAndBuiltInIdClash() {
        val store = CatalogueStore(tmp.newFolder("cat"), builtIn = { catalogue("builtin", hack("x")) })
        assertTrue(store.add("nonsense", null) is AddResult.Rejected)
        assertTrue(store.add(catalogue("builtin", hack("q")), null) is AddResult.Rejected)
        assertTrue(store.sources().isEmpty())
    }

    @Test
    fun withoutABuiltInOnlyTheUsersOwnCatalogues() {
        val store = CatalogueStore(tmp.newFolder("cat"), builtIn = null)
        assertTrue(store.load().first.isEmpty())
        assertTrue(store.add(catalogue("mine", hack("y")), null) is AddResult.Added)
        val (loaded, problems) = store.load()
        assertTrue(problems.isEmpty())
        assertEquals(listOf("mine"), loaded.map { it.catalogue.id })
    }

    @Test
    fun mergeShowsTheSameOfficialPageOnce() {
        val a = LoadedCatalogue(null, valid(catalogue("a", hack("one", pageUrl = "https://example.org/h/1/"))).catalogue, emptyList())
        val b = LoadedCatalogue(null, valid(catalogue("b", hack("uno", pageUrl = "https://EXAMPLE.org/h/1"))).catalogue, emptyList())
        assertEquals(listOf("a/one"), CatalogueStore.merge(listOf(a, b)).map { it.key })
    }
}
