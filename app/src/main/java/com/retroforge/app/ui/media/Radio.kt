package com.retroforge.app.ui.media

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.retroforge.app.BuildConfig
import com.retroforge.app.R
import com.retroforge.app.ui.CardBody
import com.retroforge.app.ui.ChipRow
import com.retroforge.core.ui.ControllerTextField
import com.retroforge.core.ui.FocusCard
import com.retroforge.core.ui.FocusChip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** An internet radio station from Radio Browser (radio-browser.info: a free, open list, no account or key). */
data class Station(val id: String, val name: String, val url: String, val logo: String?, val tags: String, val country: String)

object RadioBrowser {
    // Radio Browser's servers, tried in turn (any one has the whole list).
    private val servers = listOf("de1", "de2", "fi1", "nl1").map { "https://$it.api.radio-browser.info" }

    /** The most-listened stations in [countryCode] (ISO, "GB"), or the ones whose name matches [search] anywhere. */
    suspend fun stations(countryCode: String?, search: String? = null): List<Station> = withContext(Dispatchers.IO) {
        val query = buildString {
            append("/json/stations/search?hidebroken=true&order=clickcount&reverse=true&limit=80")
            if (!search.isNullOrBlank()) append("&name=").append(URLEncoder.encode(search.trim(), "UTF-8"))
            else if (!countryCode.isNullOrBlank()) append("&countrycode=").append(countryCode)
        }
        val text = servers.firstNotNullOfOrNull { runCatching { get(it + query) }.getOrNull() } ?: throw java.io.IOException("Radio Browser didn't answer")
        parse(text)
    }

    internal fun parse(json: String): List<Station> {
        val array = JSONArray(json)
        return (0 until array.length()).map { array.getJSONObject(it) }
            // Its own players (HLS playlists) aren't something RetroForge's player opens; plain streams are.
            .filter { it.optInt("hls") == 0 && it.optString("url_resolved").startsWith("http") }
            .map {
                Station(it.optString("stationuuid"), it.optString("name").trim(), it.optString("url_resolved"),
                    it.optString("favicon").takeIf { f -> f.startsWith("http") }, it.optString("tags"), it.optString("country"))
            }
            .distinctBy { it.name.lowercase() }
    }

    /** Tells Radio Browser a station was played, as it asks apps to (it ranks stations by this). */
    suspend fun counted(station: Station) = withContext(Dispatchers.IO) {
        runCatching { servers.firstNotNullOfOrNull { runCatching { get("$it/json/url/${station.id}") }.getOrNull() } }
    }

    private fun get(url: String): String {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", "RetroForge/${BuildConfig.VERSION_NAME}")
        }
        return try {
            if (c.responseCode != 200) throw java.io.IOException("HTTP ${c.responseCode}")
            c.inputStream.use { it.readBytes().decodeToString() }
        } finally {
            c.disconnect()
        }
    }

    /** The country this phone is in: its mobile network's, else its SIM's, else the language setting's. */
    fun country(context: Context): String {
        val phone = context.getSystemService(android.telephony.TelephonyManager::class.java)
        return listOfNotNull(phone?.networkCountryIso, phone?.simCountryIso, java.util.Locale.getDefault().country)
            .firstOrNull { it.length == 2 }?.uppercase() ?: "GB"
    }
}

private const val FAVOURITES = "radio_favourites"

private fun favourites(prefs: SharedPreferences): List<Station> = runCatching {
    val a = JSONArray(prefs.getString(FAVOURITES, "[]"))
    (0 until a.length()).map { a.getJSONObject(it) }.map {
        Station(it.getString("id"), it.getString("name"), it.getString("url"), it.optString("logo").ifEmpty { null }, it.optString("tags"), it.optString("country"))
    }
}.getOrDefault(emptyList())

private fun saveFavourites(prefs: SharedPreferences, list: List<Station>) {
    val a = JSONArray()
    list.forEach { a.put(JSONObject().put("id", it.id).put("name", it.name).put("url", it.url).put("logo", it.logo ?: "").put("tags", it.tags).put("country", it.country)) }
    prefs.edit().putString(FAVOURITES, a.toString()).apply()
}

private fun Station.asTrack(context: Context) = Track(url, name, tags.split(',').take(3).joinToString(", ").ifBlank { null },
    context.getString(R.string.radio_title), null, logo, "radio")

/** The Music tab's radio: favourites, the most-listened stations in this country, and a search of the whole world's. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RadioSection(prefs: SharedPreferences) {
    val context = LocalContext.current
    val country = remember { RadioBrowser.country(context) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var draft by remember { mutableStateOf("") }
    var search by remember { mutableStateOf<String?>(null) }
    var saved by remember { mutableStateOf(favourites(prefs)) }
    var retry by remember { mutableIntStateOf(0) }
    // A dozen at a time, so the page isn't one long list.
    var showing by remember(search) { mutableIntStateOf(12) }
    val stations by produceState<Result<List<Station>>?>(null, search, retry) {
        value = null
        value = runCatching { RadioBrowser.stations(country, search) }
    }
    fun play(station: Station) {
        MusicService.play(context, listOf(station.asTrack(context)))
        scope.launch { RadioBrowser.counted(station) }
    }
    fun toggle(station: Station) {
        saved = if (saved.any { it.id == station.id }) saved.filter { it.id != station.id } else saved + station
        saveFavourites(prefs, saved)
    }

    CardBody(stringResource(R.string.radio_body))
    ControllerTextField(value = draft, onValueChange = { draft = it }, singleLine = true, label = { Text(stringResource(R.string.radio_search)) }, modifier = Modifier.fillMaxWidth())
    ChipRow {
        FocusChip(stringResource(R.string.radio_find), enabled = draft.isNotBlank(), onClick = { search = draft })
        if (search != null) FocusChip(stringResource(R.string.radio_near_me), onClick = { search = null; draft = "" })
    }
    if (saved.isNotEmpty() && search == null) {
        Text(stringResource(R.string.radio_favourites), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
        saved.forEach { StationRow(it, favourite = true, onPlay = { play(it) }, onFavourite = { toggle(it) }) }
    }
    Text(
        if (search != null) stringResource(R.string.radio_results, search!!) else stringResource(R.string.radio_popular_in, java.util.Locale("", country).displayCountry),
        style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground,
    )
    val result = stations
    when {
        result == null -> CardBody(stringResource(R.string.radio_looking))
        result.isFailure -> {
            CardBody(stringResource(R.string.radio_offline))
            FocusChip(stringResource(R.string.radio_try_again), onClick = { retry++ })
        }
        result.getOrThrow().isEmpty() -> CardBody(stringResource(R.string.radio_none))
        else -> {
            val all = result.getOrThrow()
            all.take(showing).forEach { s -> StationRow(s, favourite = saved.any { it.id == s.id }, onPlay = { play(s) }, onFavourite = { toggle(s) }) }
            if (all.size > showing) FocusChip(stringResource(R.string.media_see_more, all.size - showing), onClick = { showing += 12 })
        }
    }
}

@Composable
private fun StationRow(station: Station, favourite: Boolean, onPlay: () -> Unit, onFavourite: () -> Unit) {
    FocusCard(modifier = Modifier.fillMaxWidth(), onClick = onPlay) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                Text("♪", color = MaterialTheme.colorScheme.primary)
                station.logo?.let { AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            }
            Column(Modifier.weight(1f)) {
                Text(station.name, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val about = listOf(station.country, station.tags.split(',').take(3).joinToString(", ")).filter { it.isNotBlank() }.joinToString(" · ")
                if (about.isNotBlank()) Text(about, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            FocusChip(if (favourite) "★" else "☆", onClick = onFavourite)
        }
    }
}
