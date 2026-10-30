package com.retroforge.pcplayer.steam

import android.content.Context
import org.json.JSONObject
import java.io.File

/**
 * Which of RetroForge's two ways a Steam game starts, remembered per game (by Steam app id) in files/pc:
 *  - Steam method 1, RetroForge Steam: RetroForge's own Steam sign-in, with Goldberg's stand-in beside the game. Quick
 *    and light; most games.
 *  - Steam method 2, the Steam client: Valve's own Windows Steam ([WindowsSteamClient]), for games that won't start
 *    without it (Steam's CEG copy protection hands the game back to Steam and it quits).
 * A game is switched to method 2 by the player when it sees that hand-back, or by the user in the PC tab.
 */
object SteamMethod {

    private fun file(context: Context) = File(context.filesDir, "pc/steam_method.json")

    private fun read(context: Context): JSONObject =
        runCatching { JSONObject(file(context).readText()) }.getOrDefault(JSONObject())

    private fun write(context: Context, json: JSONObject) {
        file(context).apply { parentFile?.mkdirs() }.writeText(json.toString())
    }

    /** Whether [appId] starts through the Steam client. */
    fun usesClient(context: Context, appId: Int): Boolean = read(context).optJSONObject("$appId")?.optBoolean("client") == true

    /** Whether the player saw [appId] hand itself back to Steam (so it needs the Steam client). */
    fun needsClient(context: Context, appId: Int): Boolean = read(context).optJSONObject("$appId")?.optBoolean("needs") == true

    fun setClient(context: Context, appId: Int, client: Boolean) {
        val json = read(context)
        val game = json.optJSONObject("$appId") ?: JSONObject()
        game.put("client", client)
        json.put("$appId", game)
        write(context, json)
    }

    /** The game handed itself back to Steam: it starts through the Steam client from now on. */
    fun markNeedsClient(context: Context, appId: Int) {
        val json = read(context)
        val game = json.optJSONObject("$appId") ?: JSONObject()
        game.put("needs", true)
        game.put("client", true)
        json.put("$appId", game)
        write(context, json)
    }

    /**
     * Whether the game in [exeDir] handed itself back to Steam since [since] (ms): Steam's CEG copy protection writes
     * <exe>.<process id>.STEAMSTART beside the .exe, asking Steam to start it, then the game quits.
     */
    fun handedBackToSteam(exeDir: File, since: Long): Boolean =
        exeDir.listFiles { f -> f.name.endsWith(".STEAMSTART", true) && f.lastModified() >= since - 2_000 }?.isNotEmpty() == true
}
