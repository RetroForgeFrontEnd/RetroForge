package com.retroforge.app.player

import java.io.File
import java.util.Properties

/**
 * Time played per game in the built-in player, in [file] (`<system>/<file name>` = milliseconds). Only the player
 * process writes it; the library reads it when it shows a game.
 */
class Playtime(private val file: File) {

    private fun read() = Properties().apply { runCatching { file.inputStream().use { load(it) } } }

    fun get(system: String, fileName: String): Long = read().getProperty("$system/$fileName")?.toLongOrNull() ?: 0L

    @Synchronized
    fun add(system: String, fileName: String, millis: Long) {
        if (millis <= 0) return
        val all = read()
        val key = "$system/$fileName"
        all.setProperty(key, ((all.getProperty(key)?.toLongOrNull() ?: 0L) + millis).toString())
        file.parentFile?.mkdirs()
        val part = File(file.path + ".part")
        part.outputStream().use { all.store(it, null) }
        if (!part.renameTo(file)) {
            file.delete()
            part.renameTo(file)
        }
    }

    companion object {
        /** "2 h 5 min", "12 min", "under a minute". */
        fun format(millis: Long): String {
            val minutes = millis / 60_000
            return when {
                minutes < 1 -> "under a minute"
                minutes < 60 -> "$minutes min"
                else -> "${minutes / 60} h ${minutes % 60} min"
            }
        }
    }
}
