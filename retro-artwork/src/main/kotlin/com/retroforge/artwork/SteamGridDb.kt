package com.retroforge.artwork

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.URLEncoder

@Serializable
data class SgdbGame(val id: Long, val name: String, val verified: Boolean = false)

@Serializable
data class SgdbAuthor(val name: String? = null)

@Serializable
data class SgdbImage(
    val id: Long,
    val url: String,
    val thumb: String,
    val style: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val author: SgdbAuthor? = null,
)

@Serializable
private data class SgdbList<T>(val success: Boolean, val data: List<T> = emptyList())

sealed interface SgdbResult<out T> {
    data class Ok<T>(val value: T) : SgdbResult<T>
    data object InvalidKey : SgdbResult<Nothing>
    data class Failed(val reason: String) : SgdbResult<Nothing>
}

enum class SgdbArtKind(val path: String, val dimensions: String?) {
    /** Portrait box-style covers. */
    COVER("grids", "600x900,342x482,660x930"),
    /** Wide banners for list layouts. */
    BANNER("grids", "460x215,920x430"),
    HERO("heroes", null),
    LOGO("logos", null),
}

/**
 * SteamGridDB API v2 (https://www.steamgriddb.com/api/v2), used with the user's own free API key. Requests are made
 * only when the user asks (search, open a game's covers), never in bulk, to respect SteamGridDB's terms.
 * NSFW, humour and epilepsy-warning images are filtered out.
 */
class SteamGridDb(private val http: HttpGet, private val apiKey: () -> String?) {

    private val json = Json { ignoreUnknownKeys = true }

    fun search(term: String): SgdbResult<List<SgdbGame>> =
        call("search/autocomplete/${URLEncoder.encode(term.trim(), "UTF-8").replace("+", "%20")}") {
            json.decodeFromString(SgdbList.serializer(SgdbGame.serializer()), it).data
        }

    fun images(gameId: Long, kind: SgdbArtKind = SgdbArtKind.COVER): SgdbResult<List<SgdbImage>> {
        val query = buildList {
            kind.dimensions?.let { add("dimensions=$it") }
            add("nsfw=false"); add("humor=false"); add("epilepsy=false")
            add("mimes=image/png,image/jpeg,image/webp")
        }.joinToString("&")
        return call("${kind.path}/game/$gameId?$query") { json.decodeFromString(SgdbList.serializer(SgdbImage.serializer()), it).data }
    }

    private fun <T> call(path: String, parse: (String) -> T): SgdbResult<T> {
        val key = apiKey()?.trim().orEmpty()
        if (key.isEmpty()) return SgdbResult.InvalidKey
        return try {
            val response = http.get("$BASE/$path", mapOf("Authorization" to "Bearer $key", "Accept" to "application/json"), JvmHttpGet.MAX_JSON_BYTES)
            when (response.code) {
                200 -> SgdbResult.Ok(parse(response.body.toString(Charsets.UTF_8)))
                401, 403 -> SgdbResult.InvalidKey
                404 -> SgdbResult.Ok(parse("""{"success":true,"data":[]}"""))
                429 -> SgdbResult.Failed("SteamGridDB is busy (too many requests). Try again in a minute.")
                else -> SgdbResult.Failed("SteamGridDB answered HTTP ${response.code}.")
            }
        } catch (e: Exception) {
            SgdbResult.Failed("Couldn't reach SteamGridDB: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    companion object {
        const val BASE = "https://www.steamgriddb.com/api/v2"
        const val KEY_PAGE = "https://www.steamgriddb.com/profile/preferences/api"
    }
}
