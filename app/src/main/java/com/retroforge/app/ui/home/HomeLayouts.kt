package com.retroforge.app.ui.home

import com.retroforge.core.ui.str
import com.retroforge.app.R

import androidx.compose.ui.res.stringResource

import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.retroforge.app.ui.CardBody
import com.retroforge.app.ui.library.GameRow
import com.retroforge.app.ui.library.GameTile
import com.retroforge.app.ui.library.LibraryViewModel
import com.retroforge.app.ui.library.SystemsView
import com.retroforge.app.ui.library.plural
import com.retroforge.app.ui.library.tagLine
import com.retroforge.core.library.GameEntity
import com.retroforge.core.ui.FocusCard
import com.retroforge.core.ui.FocusChip
import com.retroforge.core.ui.ThemeBackdrop
import com.retroforge.core.ui.Wordmark
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/** A group of games a layout can show: recently played, favourites, a playlist or a system. */
private class Category(val key: String, val label: String, val games: @Composable () -> List<GameEntity>, val systemId: String? = null)

@Composable
private fun categories(library: LibraryViewModel): List<Category> {
    val recent by library.recentlyPlayed.collectAsStateWithLifecycle()
    val favourites by library.favourites.collectAsStateWithLifecycle()
    val playlists by library.playlists.collectAsStateWithLifecycle()
    val systems by library.systems.collectAsStateWithLifecycle()
    return buildList {
        if (recent.isNotEmpty()) add(Category("recent", str(R.string.library_recently_played), { recent }))
        if (favourites.isNotEmpty()) add(Category("fav", str(R.string.playlists_favourites), { favourites }))
        playlists.forEach { p -> add(Category("pl-${p.id}", p.name, { flowGames(library.playlistGames(p.id)) })) }
        systems.forEach { s -> add(Category("sys-${s.id}", s.name, { flowGames(library.games(s.id)) }, systemId = s.id)) }
    }
}

@Composable
private fun flowGames(flow: Flow<List<GameEntity>>): List<GameEntity> {
    val games by remember(flow) { flow }.collectAsStateWithLifecycle(initialValue = emptyList())
    return games
}

@Composable
private fun art(library: LibraryViewModel, game: GameEntity): Any? {
    val roots by library.roots.collectAsStateWithLifecycle()
    val chosen by library.chosenCovers.collectAsStateWithLifecycle()
    val rootsById = remember(roots) { roots.orEmpty().associateBy { it.id } }
    return library.art(rootsById, chosen, game)
}

/** The focused game's art filling the background, blurred where Android allows, fading into the page. */
@Composable
private fun ArtBackdrop(image: Any?, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Crossfade(targetState = image, label = "backdrop", modifier = modifier) { img ->
        Box(Modifier.fillMaxSize()) {
            if (img != null) {
                AsyncImage(
                    model = img, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().then(if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Modifier.blur(18.dp).alpha(0.75f) else Modifier.alpha(0.4f)),
                )
            }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(colors.background.copy(alpha = 0.2f), colors.background))))
        }
    }
}

/**
 * Cross Bar: categories run sideways across the top (Recently played, Favourites, playlists, each system); the
 * selected one's games list underneath. Left/right changes category, up/down moves through its games.
 */
@Composable
fun CrossBarHome(library: LibraryViewModel, onOpenGame: (Long) -> Unit, onOpenSystem: (String) -> Unit) {
    val cats = categories(library)
    var selected by remember { mutableIntStateOf(0) }
    val colors = MaterialTheme.colorScheme
    ThemeBackdrop {
        Column(Modifier.fillMaxSize().padding(top = 20.dp)) {
            Wordmark(fontSize = 26.sp, modifier = Modifier.padding(horizontal = 24.dp))
            if (cats.isEmpty()) {
                CardBody(stringResource(R.string.home_layouts_add_your_games_folder_in_retro))
                return@Column
            }
            val current = cats[selected.coerceIn(0, cats.lastIndex)]
            LazyRow(contentPadding = PaddingValues(horizontal = 24.dp, vertical = 18.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                itemsIndexed(cats, key = { _, c -> c.key }) { i, c ->
                    val on = i == selected
                    val scale by animateFloatAsState(if (on) 1.15f else 0.9f, label = "barScale")
                    Text(
                        c.label,
                        style = MaterialTheme.typography.headlineSmall,
                        color = if (on) colors.onBackground else colors.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        modifier = Modifier
                            .graphicsLayer { scaleX = scale; scaleY = scale }
                            .onFocusChanged { if (it.isFocused) selected = i }
                            .clickable { selected = i },
                    )
                }
            }
            Box(Modifier.padding(start = 24.dp).width(64.dp).height(3.dp).background(colors.primary))
            val games = current.games()
            LazyColumn(contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(games.take(60), key = { it.id }) { g -> GameRow(g, art(library, g), onClick = { onOpenGame(g.id) }) }
                if (current.systemId != null && games.size > 60) {
                    item { FocusChip(stringResource(R.string.home_layouts_all, plural(games.size, "game")), onClick = { onOpenSystem(current.systemId) }) }
                }
                if (games.isEmpty()) item { CardBody(stringResource(R.string.home_layouts_nothing_here_yet)) }
            }
        }
    }
}

/**
 * Hero: the focused game fills the screen (its art behind, title large), with a row of game tiles along the
 * bottom. Moving through the row changes the hero; selecting a tile opens the game.
 */
@Composable
fun HeroHome(library: LibraryViewModel, onOpenGame: (Long) -> Unit, onOpenSystem: (String) -> Unit) {
    val recent by library.recentlyPlayed.collectAsStateWithLifecycle()
    val favourites by library.favourites.collectAsStateWithLifecycle()
    val systems by library.systems.collectAsStateWithLifecycle()
    val tiles = remember(recent, favourites) { (recent + favourites).distinctBy { it.id } }
    var focused by remember { mutableStateOf<GameEntity?>(null) }
    val hero = focused ?: tiles.firstOrNull()
    val colors = MaterialTheme.colorScheme
    ThemeBackdrop {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val heroHeight = maxHeight * 0.55f
            hero?.let { ArtBackdrop(art(library, it), Modifier.fillMaxWidth().height(heroHeight + 80.dp)) }
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Box(Modifier.fillMaxWidth().height(heroHeight).padding(24.dp)) {
                    Wordmark(fontSize = 22.sp, modifier = Modifier.align(Alignment.TopStart))
                    if (hero != null) {
                        Column(Modifier.align(Alignment.BottomStart), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(library.systemName(hero.system).uppercase(), style = MaterialTheme.typography.labelLarge, color = colors.primary)
                            Text(hero.title, style = MaterialTheme.typography.displaySmall, color = colors.onBackground, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            tagLine(hero)?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant) }
                            FocusChip(stringResource(R.string.home_layouts_open), onClick = { onOpenGame(hero.id) })
                        }
                    } else {
                        Text(stringResource(R.string.home_layouts_play_a_game_and_it_appears), style = MaterialTheme.typography.headlineSmall, color = colors.onBackground, modifier = Modifier.align(Alignment.BottomStart))
                    }
                }
                LazyRow(contentPadding = PaddingValues(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(tiles, key = { it.id }) { g ->
                        GameTile(g, art(library, g), onClick = { onOpenGame(g.id) }, modifier = Modifier.width(150.dp).onFocusChanged { if (it.hasFocus) focused = g })
                    }
                }
                if (systems.isNotEmpty()) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(stringResource(R.string.home_layouts_systems), style = MaterialTheme.typography.titleLarge, color = colors.onBackground)
                        SystemsView(systems, onOpenSystem)
                    }
                }
            }
        }
    }
}

/**
 * Blades: full-height panels side by side (Home, Favourites, Systems, Playlists) with their names as tabs across
 * the top. Swipe or select a tab to slide between them.
 */
@Composable
fun BladesHome(library: LibraryViewModel, onOpenGame: (Long) -> Unit, onOpenSystem: (String) -> Unit, onOpenPlaylist: (Long) -> Unit) {
    val recent by library.recentlyPlayed.collectAsStateWithLifecycle()
    val favourites by library.favourites.collectAsStateWithLifecycle()
    val systems by library.systems.collectAsStateWithLifecycle()
    val playlists by library.playlists.collectAsStateWithLifecycle()
    val blades = listOf(str(R.string.home_layouts_home), str(R.string.playlists_favourites), str(R.string.home_layouts_systems), str(R.string.playlists_playlists))
    val pager = rememberPagerState { blades.size }
    val scope = rememberCoroutineScope()
    val colors = MaterialTheme.colorScheme
    ThemeBackdrop {
        Column(Modifier.fillMaxSize().padding(top = 16.dp)) {
            Row(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(22.dp), verticalAlignment = Alignment.Bottom) {
                blades.forEachIndexed { i, name ->
                    val on = pager.currentPage == i
                    Text(
                        name.uppercase(),
                        style = if (on) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleSmall,
                        color = if (on) colors.primary else colors.onSurfaceVariant,
                        modifier = Modifier.clickable { scope.launch { pager.animateScrollToPage(i) } }.onFocusChanged { if (it.isFocused) scope.launch { pager.animateScrollToPage(i) } },
                    )
                }
            }
            HorizontalPager(state = pager, contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp), pageSpacing = 14.dp, modifier = Modifier.fillMaxSize()) { page ->
                // Each blade is a tall panel; the ones beside the current one show at the edges.
                Box(Modifier.fillMaxSize().clip(MaterialTheme.shapes.large).background(colors.surface.copy(alpha = 0.92f))) {
                    LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        when (page) {
                            0 -> {
                                item { Wordmark(fontSize = 24.sp) }
                                if (recent.isEmpty()) item { CardBody(stringResource(R.string.home_layouts_play_a_game_and_it_shows)) }
                                items(recent, key = { it.id }) { g -> GameRow(g, art(library, g), onClick = { onOpenGame(g.id) }) }
                            }
                            1 -> {
                                if (favourites.isEmpty()) item { CardBody(stringResource(R.string.home_layouts_favourite_a_game_from_its_page)) }
                                items(favourites, key = { it.id }) { g -> GameRow(g, art(library, g), onClick = { onOpenGame(g.id) }) }
                            }
                            2 -> items(systems, key = { it.id }) { s ->
                                FocusCard(modifier = Modifier.fillMaxWidth(), onClick = { onOpenSystem(s.id) }) {
                                    Text(s.name, style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
                                    CardBody(plural(s.games, "game"))
                                }
                            }
                            else -> {
                                if (playlists.isEmpty()) item { CardBody(stringResource(R.string.home_layouts_make_playlists_from_any_game_s)) }
                                items(playlists, key = { it.id }) { p ->
                                    FocusCard(modifier = Modifier.fillMaxWidth(), onClick = { onOpenPlaylist(p.id) }) {
                                        Text(p.name, style = MaterialTheme.typography.titleLarge, color = colors.onSurface)
                                        CardBody(plural(p.games, "game"))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
