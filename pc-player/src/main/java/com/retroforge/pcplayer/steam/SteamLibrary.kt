package com.retroforge.pcplayer.steam

import com.retroforge.pcplayer.R
import com.retroforge.core.ui.str
import android.util.Log
import `in`.dragonbra.javasteam.steam.handlers.steamapps.PICSRequest
import `in`.dragonbra.javasteam.steam.handlers.steamapps.SteamApps
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException

/** A game in the signed-in account's Steam library. */
data class SteamOwnedGame(val appId: Int, val name: String, val windows: Boolean) {
    /** Steam's own box art (no account needed). */
    val coverUrl get() = "https://cdn.cloudflare.steamstatic.com/steam/apps/$appId/library_600x900.jpg"
}

/**
 * The signed-in account's Steam library: its licences (packages) → the apps in them → the ones that are games, with
 * their names and whether they run on Windows, from Steam's app info. Kept in [cacheFile] so it shows at once next
 * time; [refresh] asks Steam again.
 */
class SteamLibrary(private val account: SteamAccount, private val cacheFile: File) {

    fun cached(): List<SteamOwnedGame> = runCatching {
        val array = JSONArray(cacheFile.readText())
        (0 until array.length()).map { array.getJSONObject(it) }
            .map { SteamOwnedGame(it.getInt("appId"), it.getString("name"), it.optBoolean("windows", true)) }
    }.getOrDefault(emptyList())

    /** Asks Steam for the library and keeps it. Null when not signed in. */
    suspend fun refresh(): List<SteamOwnedGame>? = account.withLicenses { client, licenses ->
        val apps = client.getHandler(SteamApps::class.java) ?: throw IOException(str(R.string.steam_account_steam_s_app_handler_is_missing))

        val appIds = linkedSetOf<Int>()
        for (chunk in licenses.map { PICSRequest(it.packageID, it.accessToken) }.chunked(CHUNK)) {
            val reply = withTimeout(TIMEOUT) { apps.picsGetProductInfo(emptyList(), chunk, false).await() }
            for (callback in reply.results) for (pkg in callback.packages.values) {
                pkg.keyValues["appids"].children.mapNotNullTo(appIds) { it.value?.toIntOrNull() }
            }
        }

        val tokens = runCatching { withTimeout(TIMEOUT) { apps.picsGetAccessTokens(appIds, emptyList()).await() }.appTokens }
            .getOrDefault(emptyMap())
        val games = mutableListOf<SteamOwnedGame>()
        for (chunk in appIds.map { PICSRequest(it, tokens[it] ?: 0L) }.chunked(CHUNK)) {
            val reply = withTimeout(TIMEOUT) { apps.picsGetProductInfo(chunk, emptyList(), false).await() }
            for (callback in reply.results) for ((id, app) in callback.apps) {
                val common = app.keyValues["common"]
                if (!common["type"].value.equals("game", ignoreCase = true)) continue
                val name = common["name"].value?.takeIf { it.isNotBlank() } ?: continue
                val os = common["oslist"].value.orEmpty().lowercase()
                games += SteamOwnedGame(id, name, windows = os.isEmpty() || "windows" in os)
            }
        }
        val sorted = games.distinctBy { it.appId }.sortedBy { it.name.lowercase() }
        Log.i(TAG, "${licenses.size} licences, ${appIds.size} apps, ${sorted.size} games")
        save(sorted)
        sorted
    }

    private fun save(games: List<SteamOwnedGame>) {
        cacheFile.parentFile?.mkdirs()
        cacheFile.writeText(JSONArray(games.map { JSONObject().put("appId", it.appId).put("name", it.name).put("windows", it.windows) }).toString())
    }

    private companion object {
        const val TAG = "PcSteamLibrary"
        const val CHUNK = 200
        const val TIMEOUT = 60_000L
    }
}
