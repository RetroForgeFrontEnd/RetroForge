package com.retroforge.pcplayer

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * How each Windows game has gone on this device, kept by the PC player (its own process) in a small file the PC tab
 * reads: whether it got as far as showing the game, and for how long it's been played. Nothing leaves the device.
 */
object PcHistory {

    data class Entry(
        /** Minutes played, counted from when the game's window first showed. */
        val minutesPlayed: Int,
        /** The last start didn't get to the game (Wine stopped before the game showed, or failed). */
        val lastFailed: Boolean,
    )

    private fun file(context: Context) = File(context.filesDir, "pc/history.json")

    /** Every game's history, by the path of its .exe. */
    fun all(context: Context): Map<String, Entry> = runCatching {
        val json = JSONObject(file(context).readText())
        json.keys().asSequence().associateWith { key ->
            json.getJSONObject(key).let { Entry(it.optInt("minutes"), it.optBoolean("failed")) }
        }
    }.getOrDefault(emptyMap())

    /** Records one run of the game at [exe]: whether it reached the game, and for how long it showed. */
    @Synchronized
    fun record(context: Context, exe: File, reachedGame: Boolean, secondsShown: Long) {
        val f = file(context)
        val json = runCatching { JSONObject(f.readText()) }.getOrDefault(JSONObject())
        val old = json.optJSONObject(exe.path)
        val minutes = (old?.optInt("minutes") ?: 0) + (secondsShown / 60).toInt()
        json.put(exe.path, JSONObject().put("minutes", minutes).put("failed", !reachedGame))
        f.parentFile?.mkdirs()
        val part = File(f.path + ".part")
        part.writeText(json.toString())
        part.renameTo(f)
    }
}
