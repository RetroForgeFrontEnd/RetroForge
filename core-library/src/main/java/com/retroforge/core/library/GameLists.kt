package com.retroforge.core.library

import com.retroforge.artwork.HttpGet
import com.retroforge.artwork.LibretroThumbnails
import com.retroforge.library.manage.DatParser
import com.retroforge.library.manage.GameList
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.net.URLEncoder

/**
 * No-Intro (cartridges) and Redump (discs) game lists, from libretro-database (CC BY-SA 4.0), fetched for a system the
 * first time its games are checked and kept for a month. A list the user put in [dir] as "<system id>.dat" (from
 * DAT-o-MATIC or redump.org) is used instead.
 */
class GameLists(private val http: HttpGet, private val dir: File, private val clock: () -> Long = System::currentTimeMillis) {
    private val lock = Mutex()
    private val parsed = mutableMapOf<String, GameList?>()

    /** The list for [system], or null when there's none for it. Throws when it couldn't be downloaded. */
    suspend fun forSystem(system: String): GameList? = lock.withLock {
        if (system in parsed) return parsed[system]
        val own = File(dir, "$system.dat")
        val list = if (own.isFile) DatParser.parse(own.readText()) else download(system)
        list.also { parsed[system] = it }
    }

    private fun download(system: String): GameList? {
        val folder = LibretroThumbnails.folderFor(system) ?: return null
        val cache = File(dir, "libretro/$folder.dat")
        if (cache.isFile && clock() - cache.lastModified() < MAX_AGE_MS) return DatParser.parse(cache.readText())
        val name = URLEncoder.encode("$folder.dat", "UTF-8").replace("+", "%20")
        var failure: Exception? = null
        for (kind in listOf("no-intro", "redump")) {
            val response = try { http.get("$BASE/$kind/$name", emptyMap(), MAX_BYTES) } catch (e: Exception) { failure = e; continue }
            if (response.code != 200) continue
            val text = response.body.toString(Charsets.UTF_8)
            cache.parentFile?.mkdirs()
            cache.writeText(text)
            return DatParser.parse(text)
        }
        // Offline: an old copy is better than none.
        if (cache.isFile) return DatParser.parse(cache.readText())
        failure?.let { throw it }
        return null
    }

    companion object {
        private const val BASE = "https://raw.githubusercontent.com/libretro/libretro-database/master/metadat"
        private const val MAX_BYTES = 96L * 1024 * 1024
        private const val MAX_AGE_MS = 30L * 24 * 60 * 60 * 1000
    }
}
