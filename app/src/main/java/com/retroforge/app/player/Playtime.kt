package com.retroforge.app.player

import com.retroforge.app.R
import com.retroforge.core.ui.str
import java.io.File
import java.util.Properties

/**
 * Time played per game in the built-in player, in [file] (`<system>/<file name>` = milliseconds), and per week
 * (`@week/<Monday's date>` = milliseconds, counted from when RetroForge started keeping weeks). Only the player
 * process writes it; the library reads it when it shows a game and on the Playtime page.
 */
class Playtime(private val file: File) {

    private fun read() = Properties().apply { runCatching { file.inputStream().use { load(it) } } }

    fun get(system: String, fileName: String): Long = read().getProperty("$system/$fileName")?.toLongOrNull() ?: 0L

    /** Every game's time: (system, file name) to milliseconds. */
    fun all(): Map<Pair<String, String>, Long> = read().let { all ->
        all.stringPropertyNames().filterNot { it.startsWith(WEEK) }.mapNotNull { key ->
            val slash = key.indexOf('/')
            val millis = all.getProperty(key)?.toLongOrNull()
            if (slash <= 0 || millis == null) null else (key.substring(0, slash) to key.substring(slash + 1)) to millis
        }.toMap()
    }

    /** Time played each week, by the week's Monday. */
    fun weeks(): Map<java.time.LocalDate, Long> = read().let { all ->
        all.stringPropertyNames().filter { it.startsWith(WEEK) }.mapNotNull { key ->
            val day = runCatching { java.time.LocalDate.parse(key.removePrefix(WEEK)) }.getOrNull() ?: return@mapNotNull null
            all.getProperty(key)?.toLongOrNull()?.let { day to it }
        }.toMap()
    }

    @Synchronized
    fun add(system: String, fileName: String, millis: Long) {
        if (millis <= 0) return
        val all = read()
        val key = "$system/$fileName"
        all.setProperty(key, ((all.getProperty(key)?.toLongOrNull() ?: 0L) + millis).toString())
        val week = WEEK + weekOf(java.time.LocalDate.now())
        all.setProperty(week, ((all.getProperty(week)?.toLongOrNull() ?: 0L) + millis).toString())
        file.parentFile?.mkdirs()
        val part = File(file.path + ".part")
        part.outputStream().use { all.store(it, null) }
        if (!part.renameTo(file)) {
            file.delete()
            part.renameTo(file)
        }
    }

    companion object {
        private const val WEEK = "@week/"

        /** The Monday that starts [day]'s week. */
        fun weekOf(day: java.time.LocalDate): java.time.LocalDate = day.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))

        /** "2 h 5 min", "12 min", "under a minute". */
        fun format(millis: Long): String {
            val minutes = millis / 60_000
            return when {
                minutes < 1 -> str(R.string.playtime_under_a_minute)
                minutes < 60 -> "$minutes min"
                else -> str(R.string.playtime_h_min, minutes / 60, minutes % 60)
            }
        }
    }
}
