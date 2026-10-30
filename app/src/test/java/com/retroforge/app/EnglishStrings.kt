package com.retroforge.app

import com.retroforge.core.ui.AppStrings
import java.io.File
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/** Lets unit tests (no Android resources) use str() and pluralStr(): the texts come from the app's English strings.xml. */
object EnglishStrings {
    private val strings = HashMap<Int, String>()
    private val plurals = HashMap<Int, Map<String, String>>()

    fun install() {
        if (strings.isEmpty()) load()
        AppStrings.testLookup = { id, count, args ->
            val text = if (count == null) strings.getValue(id) else plurals.getValue(id).let { it[if (count == 1) "one" else "other"] ?: it.getValue("other") }
            if (args.isEmpty()) text else String.format(Locale.ROOT, text, *args)
        }
    }

    private fun load() {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/res/values/strings.xml"))
        fun ids(kind: String) = Class.forName("com.retroforge.app.R\$$kind").fields.associate { it.name to it.getInt(null) }
        val stringIds = ids("string")
        val pluralIds = ids("plurals")
        val nodes = doc.documentElement.childNodes
        for (i in 0 until nodes.length) {
            val e = nodes.item(i) as? Element ?: continue
            val name = e.getAttribute("name")
            when (e.tagName) {
                "string" -> stringIds[name]?.let { strings[it] = unescape(e.textContent) }
                "plurals" -> pluralIds[name]?.let { id ->
                    val items = e.getElementsByTagName("item")
                    plurals[id] = (0 until items.length).map { items.item(it) as Element }.associate { it.getAttribute("quantity") to unescape(it.textContent) }
                }
            }
        }
    }

    private fun unescape(s: String): String {
        val raw = s.trim().let { if (it.length >= 2 && it.startsWith('"') && it.endsWith('"')) it.substring(1, it.length - 1) else it }
        return raw.replace("\\n", "\n").replace("\\'", "'").replace("\\\"", "\"").replace("\\@", "@").replace("\\?", "?")
    }
}
