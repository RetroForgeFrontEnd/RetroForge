package com.retroforge.app.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import com.retroforge.core.ui.ControllerTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.retroforge.app.ui.CardBody
import com.retroforge.app.ui.CardTitle
import com.retroforge.app.ui.ScreenFrame
import com.retroforge.artwork.SgdbGame
import com.retroforge.artwork.SgdbImage
import com.retroforge.artwork.SgdbResult
import com.retroforge.artwork.ThumbnailMatch
import com.retroforge.core.library.CoverRepository
import com.retroforge.core.library.GameEntity
import com.retroforge.core.ui.FocusCard
import com.retroforge.core.ui.FocusChip
import com.retroforge.core.ui.StatusPill
import com.retroforge.core.ui.StatusTone
import kotlinx.coroutines.launch
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import com.retroforge.artwork.ScreenScraper
import com.retroforge.artwork.SsMedia

/** The Cover Project has no API and blocks automated access, so it's opened in the browser. */
private const val COVER_PROJECT = "https://www.thecoverproject.net/"

/** One candidate cover: a preview to show and the full image to save. */
private data class Candidate(val key: String, val previewUrl: String, val fullUrl: String, val label: String, val source: String, val author: String?)

@Composable
fun CoverPickerScreen(viewModel: LibraryViewModel, gameId: Long, hasSteamGridDbKey: Boolean, onOpenSettings: () -> Unit, onDone: () -> Unit) {
    val game by remember(gameId) { viewModel.game(gameId) }.collectAsStateWithLifecycle(initialValue = null)
    val g = game ?: run {
        ScreenFrame(title = "Change cover") { CardBody("Loading…") }
        return
    }
    val covers = viewModel.covers
    val scope = rememberCoroutineScope()
    val previews = remember { mutableStateMapOf<String, ByteArray?>() }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<Pair<String, Boolean>?>(null) }

    var libretro by remember { mutableStateOf<Result<List<ThumbnailMatch>>?>(null) }
    LaunchedEffect(g.id) { libretro = covers.libretroMatches(g) }

    var query by remember(g.id) { mutableStateOf(g.title) }
    var sgdbGames by remember { mutableStateOf<SgdbResult<List<SgdbGame>>?>(null) }
    var sgdbPicked by remember { mutableStateOf<SgdbGame?>(null) }
    var sgdbImages by remember { mutableStateOf<SgdbResult<List<SgdbImage>>?>(null) }
    LaunchedEffect(g.id, hasSteamGridDbKey) {
        if (hasSteamGridDbKey) sgdbGames = covers.searchSteamGridDb(g.title)
    }

    var ssCovers by remember(g.id) { mutableStateOf<Result<List<SsMedia>>?>(null) }
    var ssLoading by remember(g.id) { mutableStateOf(false) }
    val context = LocalContext.current
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && !saving) {
            saving = true
            scope.launch {
                covers.saveFromDevice(g, uri).fold(
                    onSuccess = { message = "Cover saved." to false; onDone() },
                    onFailure = { message = "Couldn't use that image: ${it.message}" to true },
                )
                saving = false
            }
        }
    }

    fun choose(c: Candidate) {
        if (saving) return
        saving = true
        message = null
        scope.launch {
            val stored = if (c.source == CoverRepository.SOURCE_SCREENSCRAPER) ScreenScraper.withoutCredentials(c.fullUrl) else c.fullUrl
            covers.save(g, c.fullUrl, c.source, c.author, stored).fold(
                onSuccess = { message = "Cover saved." to false; onDone() },
                onFailure = { message = "Couldn't save that cover: ${it.message}" to true },
            )
            saving = false
        }
    }

    ScreenFrame(title = "Change cover", subtitle = g.title) {
        if (saving) LinearProgressIndicator(Modifier.fillMaxWidth())
        message?.let { (text, isError) ->
            StatusPill(if (isError) "Problem" else "Done", if (isError) StatusTone.BAD else StatusTone.GOOD)
            CardBody(text)
        }

        // Free box art.
        CardTitle("Box art")
        CardBody("From libretro's free thumbnail library, matched to your file name.")
        when (val r = libretro) {
            null -> LinearProgressIndicator(Modifier.fillMaxWidth())
            else -> r.fold(
                onSuccess = { matches ->
                    if (matches.isEmpty()) CardBody(if (covers.hasLibretroSource(g.system)) "No box art found for this game." else "No free box art source for this system yet.")
                    CandidateRow(matches.map { Candidate(it.url, it.url, it.url, it.name, CoverRepository.SOURCE_LIBRETRO, null) }, previews, covers, ::choose)
                },
                onFailure = { CardBody("Couldn't reach the box-art server: ${it.message}") },
            )
        }

        // Custom covers.
        CardTitle("Custom covers from SteamGridDB")
        if (!hasSteamGridDbKey) {
            FocusCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenSettings) {
                CardBody("SteamGridDB has thousands of community-made covers. Add your own free API key in Settings to browse them here.")
                CardBody("Select to open Settings.")
            }
        } else {
            ControllerTextField(value = query, onValueChange = { query = it }, singleLine = true, label = { Text("Search SteamGridDB") }, modifier = Modifier.fillMaxWidth())
            FocusChip("Search", onClick = {
                sgdbPicked = null; sgdbImages = null; sgdbGames = null
                scope.launch { sgdbGames = covers.searchSteamGridDb(query) }
            }, enabled = query.isNotBlank())
            when (val r = sgdbGames) {
                null -> if (sgdbPicked == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                is SgdbResult.InvalidKey -> CardBody("SteamGridDB didn't accept your API key. Check it in Settings.")
                is SgdbResult.Failed -> CardBody(r.reason)
                is SgdbResult.Ok -> {
                    if (r.value.isEmpty()) CardBody("No games found. Try a shorter or different name.")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(r.value.take(12), key = { it.id }) { sg ->
                            FocusChip(
                                (if (sgdbPicked?.id == sg.id) "• " else "") + sg.name,
                                onClick = {
                                    sgdbPicked = sg; sgdbImages = null
                                    scope.launch { sgdbImages = covers.steamGridDbCovers(sg.id) }
                                },
                            )
                        }
                    }
                }
            }
            if (sgdbPicked != null) {
                when (val r = sgdbImages) {
                    null -> LinearProgressIndicator(Modifier.fillMaxWidth())
                    is SgdbResult.InvalidKey -> CardBody("SteamGridDB didn't accept your API key. Check it in Settings.")
                    is SgdbResult.Failed -> CardBody(r.reason)
                    is SgdbResult.Ok -> {
                        if (r.value.isEmpty()) CardBody("No covers for this game yet.")
                        CandidateRow(
                            r.value.take(30).map { Candidate("sgdb-${it.id}", it.thumb, it.url, it.style ?: "cover", CoverRepository.SOURCE_STEAMGRIDDB, it.author?.name) },
                            previews, covers, ::choose,
                        )
                        CardBody("Covers are made by SteamGridDB's community; the artist is credited under each one.")
                    }
                }
            }
        }

        // ScreenScraper: one lookup, only when asked.
        CardTitle("ScreenScraper")
        if (!covers.screenScraper.available) {
            CardBody("Not available in this build: ScreenScraper needs RetroForge's own developer ID, which ScreenScraper issues on request.")
        } else when (val r = ssCovers) {
            null -> {
                CardBody("Front and 3D boxes from ScreenScraper's database, matched by your file. Add your ScreenScraper account in Settings for a higher daily limit.")
                FocusChip("Look up on ScreenScraper", onClick = { ssCovers = Result.success(emptyList<SsMedia>()).also { ssLoading = true }; scope.launch { ssCovers = covers.screenScraperCovers(g); ssLoading = false } })
            }
            else -> if (ssLoading) LinearProgressIndicator(Modifier.fillMaxWidth()) else r.fold(
                onSuccess = { list ->
                    if (list.isEmpty()) CardBody("ScreenScraper has no box art for this file.")
                    CandidateRow(
                        list.take(12).map { m ->
                            Candidate("ss-${m.type}-${m.region}", m.url, m.url, listOfNotNull(m.type.replace("box-", "Box "), m.region?.uppercase()).joinToString(" · "), CoverRepository.SOURCE_SCREENSCRAPER, null)
                        },
                        previews, covers, ::choose,
                    )
                },
                onFailure = { CardBody(it.message ?: "ScreenScraper lookup failed.") },
            )
        }

        // The user's own image.
        CardTitle("Your own image")
        CardBody(
            "Use any picture saved on this device, for example a cover scan from The Cover Project (thecoverproject.net), " +
                "which RetroForge can't download from directly.",
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FocusChip("Choose an image", onClick = { pickImage.launch("image/*") })
            FocusChip("Open The Cover Project", onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, COVER_PROJECT.toUri())) } })
        }

        FocusCard(modifier = Modifier.fillMaxWidth(), onClick = { scope.launch { covers.reset(g); onDone() } }) {
            CardTitle("Use the original artwork")
            CardBody("Removes the chosen cover. The game shows its gamelist artwork again, if it has any.")
        }
    }
}

@Composable
private fun CandidateRow(candidates: List<Candidate>, previews: MutableMap<String, ByteArray?>, covers: CoverRepository, onChoose: (Candidate) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(candidates, key = { it.key }) { c ->
            LaunchedEffect(c.key) {
                if (!previews.containsKey(c.key)) previews[c.key] = covers.preview(c.previewUrl)
            }
            FocusCard(modifier = Modifier.width(170.dp), onClick = { onChoose(c) }) {
                Column {
                    val bytes = previews[c.key]
                    if (bytes != null) {
                        AsyncImage(
                            model = bytes,
                            contentDescription = c.label,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)),
                        )
                    } else {
                        Text(if (previews.containsKey(c.key)) "Preview unavailable" else "Loading…", style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f).padding(8.dp))
                    }
                    Text(c.label, style = MaterialTheme.typography.bodySmall, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
                    c.author?.let { Text("by $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
    }
}

/** Game page entry point and the source line under a chosen cover. */
@Composable
fun CoverControls(game: GameEntity, viewModel: LibraryViewModel, onChangeCover: () -> Unit) {
    val chosen by viewModel.covers.covers.collectAsStateWithLifecycle(initialValue = emptyList())
    val cover = chosen.firstOrNull { it.rootId == game.rootId && it.relativePath == game.relativePath }
    FocusChip("Change cover", onClick = onChangeCover)
    cover?.let {
        CardBody(
            when (it.source) {
                CoverRepository.SOURCE_STEAMGRIDDB -> "Cover from SteamGridDB" + (it.author?.let { a -> " by $a" } ?: "")
                CoverRepository.SOURCE_SCREENSCRAPER -> "Box art from ScreenScraper"
                CoverRepository.SOURCE_DEVICE -> "Your own image"
                else -> "Box art from libretro thumbnails"
            },
        )
    }
}
