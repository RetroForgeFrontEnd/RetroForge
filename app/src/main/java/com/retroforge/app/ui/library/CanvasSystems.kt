package com.retroforge.app.ui.library

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.retroforge.core.library.SystemArtIndex
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

/**
 * The Retro tab's systems, in the style of canvas-es: one big rounded artwork tile in the middle with its
 * neighbours peeking in, the system's logo, year and game count underneath, on the theme's wallpaper. Artwork
 * comes from a theme folder the user chose ([art]); without it each tile is a coloured name plate.
 * Swipe, d-pad or L1/R1-style left/right to browse; tap the middle tile or press A to open.
 */
@Composable
fun CanvasSystemsView(systems: List<SystemSummary>, art: SystemArtIndex?, style: TileStyle, onOpenSystem: (String) -> Unit) {
    if (systems.isEmpty()) return
    val pager = rememberPagerState { systems.size }
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme

    // Full width: undo the screen's side padding so the neighbours reach the edges.
    BoxWithConstraints(
        Modifier.layout { measurable, constraints ->
            val extra = 48.dp.roundToPx()
            val placeable = measurable.measure(constraints.copy(minWidth = constraints.maxWidth + extra, maxWidth = constraints.maxWidth + extra))
            layout(constraints.maxWidth, placeable.height) { placeable.place(-extra / 2, 0) }
        },
    ) {
        val tile = (maxWidth * 0.58f).coerceIn(150.dp, 320.dp)
        val side = (maxWidth - tile) / 2
        val logoSize = (maxWidth.value / 9f).sp
        Box(Modifier.fillMaxWidth().height(tile + 280.dp)) {
            // The theme's wallpaper, faded so the artwork stands out.
            art?.wallpaper?.let {
                AsyncImage(model = it, contentDescription = null, contentScale = ContentScale.Crop, alpha = 0.35f, modifier = Modifier.fillMaxSize())
            } ?: run {
                // The RetroForge logo, large and faded, behind the systems (in the theme's own logo colours).
                Box(Modifier.fillMaxSize().padding(bottom = 4.dp), contentAlignment = Alignment.BottomCenter) {
                    com.retroforge.core.ui.Wordmark(fontSize = logoSize, modifier = Modifier.graphicsLayer { alpha = 0.12f })
                }
            }

            Column(Modifier.fillMaxSize().padding(top = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                HorizontalPager(
                    state = pager,
                    pageSize = PageSize.Fixed(tile),
                    contentPadding = PaddingValues(horizontal = side),
                    pageSpacing = 16.dp,
                    beyondViewportPageCount = 2,
                    modifier = Modifier.fillMaxWidth().height(tile),
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
                    ) {
                        val artwork = art?.systems?.get(system.id)?.takeIf { style != TileStyle.PLAIN }
                        if (style == TileStyle.CONSOLES && artwork?.console != null) {
                            // The console itself, on a backdrop in RetroForge's colours.
                            NamePlate("")
                            AsyncImage(
                                model = artwork.console, contentDescription = system.name, contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize().padding(6.dp),
                            )
                        } else if (artwork?.tile != null) {
                            AsyncImage(model = artwork.tile, contentDescription = system.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        } else {
                            NamePlate(system.name)
                        }
                    }
                }

                val current = systems.getOrNull(pager.currentPage) ?: return@Column
                val artwork = art?.systems?.get(current.id)?.takeIf { style != TileStyle.PLAIN }
                Box(Modifier.padding(top = 18.dp).height(72.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    if (artwork?.logo != null) {
                        AsyncImage(
                            model = artwork.logo, contentDescription = current.name, contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxWidth(0.7f).height(64.dp),
                        )
                    } else {
                        Text(
                            current.name, color = colors.onBackground, fontSize = 30.sp, fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center, maxLines = 2, lineHeight = 32.sp,
                        )
                    }
                }
                artwork?.year?.let { Text(it, color = colors.onBackground.copy(alpha = 0.8f), fontSize = 15.sp) }
                Box(Modifier.padding(vertical = 8.dp).width(120.dp).height(1.dp).background(colors.onBackground.copy(alpha = 0.4f)))
                Text(
                    plural(current.games, "game").uppercase() + if (current.hacks > 0) " · ${plural(current.hacks, "hack").uppercase()}" else "",
                    color = colors.onBackground, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                )
                Text(
                    "◀ ▶ browse   ·   A open",
                    color = colors.onBackground.copy(alpha = 0.55f), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
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
        Text(
            name, color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Black, textAlign = TextAlign.Center,
            lineHeight = 28.sp, modifier = Modifier.padding(20.dp),
        )
    }
}
