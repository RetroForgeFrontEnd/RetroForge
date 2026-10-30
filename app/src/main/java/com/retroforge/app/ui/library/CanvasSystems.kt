package com.retroforge.app.ui.library

import com.retroforge.app.R

import androidx.compose.ui.res.stringResource

import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.transformations
import coil3.size.Size
import coil3.transform.Transformation
import com.retroforge.core.library.SystemArtIndex
import com.retroforge.core.library.SystemArtwork
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

/**
 * The Retro tab's systems, in the style of canvas-es: one big rounded tile in the middle with its neighbours
 * peeking in, and the system's logo, year and game count, on a faded background. In portrait the details sit under
 * the tiles; in landscape beside them, so everything fits on one screen. Artwork comes from canvas-es (downloaded)
 * or the user's own theme folder ([art]); without it each tile is a name plate in RetroForge's colours.
 * Swipe, d-pad left/right or tap to browse; tap the middle tile or press A to open.
 */
@Composable
fun CanvasSystemsView(systems: List<SystemSummary>, art: SystemArtIndex?, style: TileStyle, onOpenSystem: (String) -> Unit) {
    if (systems.isEmpty()) return
    val pager = rememberPagerState { systems.size }

    // Full width: undo the screen's side padding so the neighbours reach the edges.
    BoxWithConstraints(
        Modifier.layout { measurable, constraints ->
            val extra = 48.dp.roundToPx()
            val placeable = measurable.measure(constraints.copy(minWidth = constraints.maxWidth + extra, maxWidth = constraints.maxWidth + extra))
            layout(constraints.maxWidth, placeable.height) { placeable.place(-extra / 2, 0) }
        },
    ) {
        val screenHeight = LocalConfiguration.current.screenHeightDp.dp
        val fullWidth = maxWidth
        val landscape = maxWidth > screenHeight
        val current = systems.getOrNull(pager.currentPage) ?: systems.first()
        val artwork = art?.systems?.get(current.id)?.takeIf { style != TileStyle.PLAIN }

        if (landscape) {
            // Tiles on the left, details on the right; sized from the screen height (title and system bars allowed for).
            val tilesWidth = maxWidth * 0.6f
            val tile = minOf(tilesWidth * 0.62f, screenHeight - 195.dp).coerceIn(120.dp, 320.dp)
            Box(Modifier.fillMaxWidth().height(tile + 28.dp)) {
                Background(art)
                Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    Tiles(systems, art, style, pager, tile, tilesWidth, onOpenSystem)
                    Details(current, artwork, Modifier.weight(1f).padding(end = 24.dp), compact = true)
                }
                NamePopup(current.name, pager, Modifier.align(Alignment.CenterStart).width(tilesWidth))
            }
        } else {
            val tile = minOf(maxWidth * 0.58f, screenHeight - 370.dp).coerceIn(150.dp, 320.dp)
            Box(Modifier.fillMaxWidth().height(tile + 230.dp)) {
                Background(art)
                Column(Modifier.fillMaxSize().padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Tiles(systems, art, style, pager, tile, fullWidth, onOpenSystem)
                    Details(current, artwork, Modifier.fillMaxWidth().padding(top = 18.dp), compact = false)
                }
                NamePopup(current.name, pager, Modifier.align(Alignment.TopCenter).padding(top = 16.dp + tile / 2 - 32.dp))
            }
        }
    }
}

/**
 * The system's name popping up over the tiles while they're swiped, fading out a moment after they stop, so a fast
 * swipe through a hundred systems still shows where it is. Not shown when the page first opens.
 */
@Composable
private fun NamePopup(name: String, pager: androidx.compose.foundation.pager.PagerState, modifier: Modifier) =
    ScrollNamePopup(name, pager.currentPage, pager.isScrollInProgress, modifier)

/** A name shown while something scrolls ([position] changing or [scrolling]), fading out a moment after it stops. */
@Composable
internal fun ScrollNamePopup(name: String, position: Any, scrolling: Boolean, modifier: Modifier) {
    var shown by remember { mutableStateOf(false) }
    var first by remember { mutableStateOf(true) }
    LaunchedEffect(position, scrolling) {
        if (first) {
            first = false
            return@LaunchedEffect
        }
        shown = true
        if (!scrolling) {
            kotlinx.coroutines.delay(900)
            shown = false
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        androidx.compose.animation.AnimatedVisibility(
            visible = shown,
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut(),
        ) {
            Text(
                name,
                color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center, maxLines = 2,
                modifier = Modifier
                    .background(Color.Black.copy(alpha = 0.72f), androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
                    .padding(horizontal = 22.dp, vertical = 10.dp),
            )
        }
    }
}

/** The theme's wallpaper when the user's own theme has one, else RetroForge's key art; faded either way. */
@Composable
private fun Background(art: SystemArtIndex?) {
    val colors = MaterialTheme.colorScheme
    art?.wallpaper?.let {
        AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, alpha = 0.35f, modifier = Modifier.fillMaxSize())
    } ?: run {
        androidx.compose.foundation.Image(
            painter = painterResource(com.retroforge.app.R.drawable.key_art), contentDescription = null,
            contentScale = ContentScale.Crop, alpha = 0.3f, modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(colors.background.copy(alpha = 0.25f), colors.background.copy(alpha = 0.6f), colors.background.copy(alpha = 0.97f))),
            ),
        )
    }
}

@Composable
private fun Tiles(
    systems: List<SystemSummary>,
    art: SystemArtIndex?,
    style: TileStyle,
    pager: PagerState,
    tile: Dp,
    width: Dp,
    onOpenSystem: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme
    HorizontalPager(
        state = pager,
        pageSize = PageSize.Fixed(tile),
        contentPadding = PaddingValues(horizontal = (width - tile) / 2),
        pageSpacing = 16.dp,
        beyondViewportPageCount = 2,
        modifier = Modifier.width(width).height(tile),
    ) { page ->
        val system = systems[page]
        val distance = pager.getOffsetDistanceInPages(page).absoluteValue.coerceIn(0f, 1f)
        val interaction = remember { MutableInteractionSource() }
        val focused by interaction.collectIsFocusedAsState()
        Box(
            Modifier
                .size(tile)
                .graphicsLayer {
                    val scale = 1f - 0.18f * distance
                    scaleX = scale
                    scaleY = scale
                    alpha = 1f - 0.35f * distance
                }
                .onFocusChanged { if (it.isFocused && pager.currentPage != page) scope.launch { pager.animateScrollToPage(page) } }
                .clip(RoundedCornerShape(28.dp))
                .border(if (focused) 4.dp else 0.dp, if (focused) colors.primary else Color.Transparent, RoundedCornerShape(28.dp))
                .clickable(interactionSource = interaction, indication = null) {
                    if (pager.currentPage == page) onOpenSystem(system.id) else scope.launch { pager.animateScrollToPage(page) }
                }
                .focusable(interactionSource = interaction),
            contentAlignment = Alignment.Center,
        ) {
            val artwork = art?.systems?.get(system.id)?.takeIf { style != TileStyle.PLAIN }
            val console = artwork?.console
            if (style == TileStyle.CONSOLES && console != null) {
                // The console, trimmed of the empty space around it, centred at the same scale on every tile.
                NamePlate("")
                AsyncImage(
                    model = trimmed(console), contentDescription = system.name, contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize().padding(tile * 0.14f),
                )
            } else if (style == TileStyle.ART_AND_CONSOLE && artwork?.tile != null && console != null) {
                // The game art, darkened towards the bottom, with the console standing in front of it.
                AsyncImage(model = artwork.tile, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)))))
                AsyncImage(
                    model = trimmed(console), contentDescription = system.name, contentScale = ContentScale.Fit,
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(0.62f).fillMaxHeight(0.5f).padding(bottom = tile * 0.06f),
                )
            } else if (artwork?.tile != null) {
                AsyncImage(model = artwork.tile, contentDescription = system.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                NamePlate(system.name)
            }
        }
    }
}

/** Logo (or name), year, a line, the game count and the hints, centred. */
@Composable
private fun Details(system: SystemSummary, artwork: SystemArtwork?, modifier: Modifier, compact: Boolean) {
    val colors = MaterialTheme.colorScheme
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.height(if (compact) 64.dp else 72.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
            val logo = artwork?.logo
            if (logo != null) {
                AsyncImage(
                    model = trimmed(logo), contentDescription = system.name, contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth(if (compact) 0.85f else 0.7f).height(if (compact) 60.dp else 64.dp),
                )
            } else {
                Text(
                    system.name, color = colors.onBackground, fontSize = if (compact) 24.sp else 30.sp, fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center, maxLines = 2, lineHeight = 30.sp,
                )
            }
        }
        artwork?.year?.let { Text(it, color = colors.onBackground.copy(alpha = 0.8f), fontSize = 15.sp, modifier = Modifier.padding(top = 4.dp)) }
        Box(Modifier.padding(vertical = 8.dp).width(120.dp).height(1.dp).background(colors.onBackground.copy(alpha = 0.4f)))
        Text(
            plural(system.games, "game").uppercase() + if (system.hacks > 0) " · ${plural(system.hacks, "hack").uppercase()}" else "",
            color = colors.onBackground, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
        )
        Text(stringResource(R.string.canvas_systems_browse_a_open), color = colors.onBackground.copy(alpha = 0.55f), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
    }
}

/** An image request that crops away transparent borders, so differently padded artwork lines up. */
@Composable
private fun trimmed(uri: String) = ImageRequest.Builder(LocalContext.current)
    .data(uri)
    .allowHardware(false)
    .transformations(TrimTransparent)
    .build()

private object TrimTransparent : Transformation() {
    override val cacheKey = "trim-transparent"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val w = input.width
        val h = input.height
        val row = IntArray(w)
        var top = h
        var bottom = -1
        var left = w
        var right = -1
        for (y in 0 until h) {
            input.getPixels(row, 0, w, 0, y, w, 1)
            for (x in 0 until w) {
                if ((row[x] ushr 24) > 16) {
                    if (y < top) top = y
                    bottom = y
                    if (x < left) left = x
                    if (x > right) right = x
                }
            }
        }
        if (bottom < 0 || (left == 0 && top == 0 && right == w - 1 && bottom == h - 1)) return input
        return Bitmap.createBitmap(input, left, top, right - left + 1, bottom - top + 1)
    }
}

/** A tile backdrop in RetroForge's own theme colours, with the system's name when there's no artwork. */
@Composable
private fun NamePlate(name: String) {
    val colors = MaterialTheme.colorScheme
    Box(
        Modifier.fillMaxSize().background(Brush.linearGradient(listOf(colors.primary.copy(alpha = 0.85f), colors.secondary.copy(alpha = 0.6f), colors.surface))),
        contentAlignment = Alignment.Center,
    ) {
        if (name.isNotEmpty()) {
            Text(
                name, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center,
                lineHeight = 28.sp, modifier = Modifier.padding(20.dp),
            )
        }
    }
}
