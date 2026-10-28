package com.retroforge.app.player

import com.retroforge.artwork.LibretroThumbnails
import com.retroforge.artwork.ThumbnailType
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** One cheat: [code] in the core's own format (codes joined with '+'); [custom] = typed in by the user. */
data class Cheat(val description: String, val code: String, val enabled: Boolean = false, val custom: Boolean = false)

/**
 * A game's cheats, kept in [file] (the cheat list plus which are switched on, and the user's own codes). The list
 * comes from libretro's cheat database (github.com/libretro/libretro-database, CC BY-SA 4.0), downloaded for this
 * game only when the Cheats page is first opened.
 */
class CheatStore(private val file: File) {

    fun load(): List<Cheat> = runCatching {
        val array = JSONArray(file.readText())
        (0 until array.length()).map { i ->
            array.getJSONObject(i).run { Cheat(getString("d"), getString("c"), optBoolean("on"), optBoolean("custom")) }
        }
    }.getOrDefault(emptyList())

    fun save(cheats: List<Cheat>) {
        file.parentFile?.mkdirs()
        val array = JSONArray()
        cheats.forEach { array.put(JSONObject().put("d", it.description).put("c", it.code).put("on", it.enabled).put("custom", it.custom)) }
        val part = File(file.path + ".part")
        part.writeText(array.toString())
        if (!part.renameTo(file)) {
            file.delete()
            part.renameTo(file)
        }
    }

    val downloaded: Boolean get() = File(file.path + ".fetched").isFile

    /** Downloads this game's cheats and merges them with what's saved (switches and own codes kept). Null: none found. */
    fun fetch(system: String, fileName: String, userAgent: String): List<Cheat>? {
        val folder = LibretroThumbnails.folderFor(system) ?: return null
        val names = list(folder, userAgent)
        val best = LibretroThumbnails.match(names, folder, ThumbnailType.BOXART, fileName, limit = 1).firstOrNull() ?: run {
            File(file.path + ".fetched").apply { parentFile?.mkdirs() }.writeText("")
            return null
        }
        val text = get("$RAW/${enc(folder)}/${enc(best.name + ".cht")}", userAgent)
        val found = parse(text)
        val saved = load()
        val merged = found.map { cheat -> saved.firstOrNull { !it.custom && it.code == cheat.code }?.let { cheat.copy(enabled = it.enabled) } ?: cheat } +
            saved.filter { it.custom }
        save(merged)
        File(file.path + ".fetched").writeText(best.name)
        return merged
    }

    /** Cheat file names (without .cht) in one system's folder, from GitHub's tree listing. */
    private fun list(folder: String, userAgent: String): List<String> {
        val json = JSONObject(get("$API/${enc("master:cht/$folder")}", userAgent))
        val tree = json.optJSONArray("tree") ?: return emptyList()
        return (0 until tree.length()).map { tree.getJSONObject(it).optString("path") }
            .filter { it.endsWith(".cht") }
            .map { it.removeSuffix(".cht") }
    }

    private fun get(url: String, userAgent: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("User-Agent", userAgent)
        }
        try {
            if (connection.responseCode != 200) throw IOException("the cheat database answered ${connection.responseCode}")
            return connection.inputStream.use { it.readBytes().decodeToString() }
        } finally {
            connection.disconnect()
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20").replace("%2F", "/").replace("%3A", ":")

    companion object {
        private const val API = "https://api.github.com/repos/libretro/libretro-database/git/trees"
        private const val RAW = "https://raw.githubusercontent.com/libretro/libretro-database/master/cht"

        /** RetroArch's .cht format: `cheats = 2`, `cheat0_desc = "…"`, `cheat0_code = "…"`, `cheat0_enable = false`. */
        fun parse(text: String): List<Cheat> {
            val values = Regex("""(?m)^\s*(cheat(\d+)_(desc|code|enable))\s*=\s*"?(.*?)"?\s*$""").findAll(text)
                .associate { it.groupValues[1] to it.groupValues[4] }
            val count = Regex("""(?m)^\s*cheats\s*=\s*(\d+)""").find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            return (0 until count).mapNotNull { i ->
                val code = values["cheat${i}_code"]?.trim().orEmpty()
                if (code.isEmpty()) return@mapNotNull null
                Cheat(values["cheat${i}_desc"]?.ifBlank { null } ?: "Cheat ${i + 1}", code)
            }
        }
    }
}
