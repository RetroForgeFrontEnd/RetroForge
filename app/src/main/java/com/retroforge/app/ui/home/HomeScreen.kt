package com.retroforge.app.ui.home

import com.retroforge.core.ui.str
import androidx.compose.ui.res.stringResource

import kotlinx.coroutines.launch
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.retroforge.app.BuildConfig
import com.retroforge.app.R
import com.retroforge.app.ui.CardBody
import com.retroforge.app.ui.CardTitle
import com.retroforge.app.ui.ScreenFrame
import com.retroforge.app.ui.Section
import com.retroforge.app.ui.library.GameHero
import com.retroforge.app.ui.library.GameTile
import com.retroforge.app.ui.library.LibraryViewModel
import com.retroforge.app.ui.library.SystemsView
import com.retroforge.app.ui.library.tagLine
import com.retroforge.core.library.GameEntity
import com.retroforge.core.ui.FocusCard
import com.retroforge.core.ui.LocalThemeSpec
import com.retroforge.core.ui.StatusPill
import com.retroforge.core.ui.StatusTone
import com.retroforge.core.ui.Wordmark
import com.retroforge.theme.HomeLayout

/**
 * The dashboard: the last game played, big; then recent games, favourites and systems as swipeable rows. Before
 * any folder is added it explains where to start instead.
 */
@Composable
fun HomeScreen(
    showPc: Boolean,
    library: LibraryViewModel,
    onOpen: (Section) -> Unit,
    onOpenApps: () -> Unit = {},
    onOpenGame: (Long) -> Unit,
    onOpenSystem: (String) -> Unit,
    onOpenPlaylist: (Long) -> Unit = {},
) {
    // The theme decides the structure of home; the dashboard below is the default.
    when (LocalThemeSpec.current.homeLayout) {
        HomeLayout.CROSSBAR -> return CrossBarHome(library, onOpenGame, onOpenSystem)
        HomeLayout.HERO -> return HeroHome(library, onOpenGame, onOpenSystem)
        HomeLayout.BLADES -> return BladesHome(library, onOpenGame, onOpenSystem, onOpenPlaylist)
        HomeLayout.DASHBOARD -> Unit
    }
    val recent by library.recentlyPlayed.collectAsStateWithLifecycle()
    val favourites by library.favourites.collectAsStateWithLifecycle()
    val systems by library.systems.collectAsStateWithLifecycle()
    val roots by library.roots.collectAsStateWithLifecycle()
    val rootsById = remember(roots) { roots.orEmpty().associateBy { it.id } }
    val chosen by library.chosenCovers.collectAsStateWithLifecycle()

    ScreenFrame(title = null) {
        com.retroforge.app.ui.settings.UpdateBanner()
        if (LocalThemeSpec.current.banner == "key-art") {
            // RetroForge's key art already carries the logo and tagline, so it replaces the text wordmark.
            Image(
                painterResource(R.drawable.key_art), contentDescription = str(R.string.home_retroforge_merging_the_past_building_the),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(1584f / 672f).clip(MaterialTheme.shapes.large),
            )
            // The art's own lettering is too small to read on a phone, so the name and tagline follow as text.
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Wordmark(fontSize = 28.sp)
                Text(
                    stringResource(R.string.home_merging_the_past_building_the_future),
                    style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(R.string.home_the_ultimate_hybrid_gaming_experience),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center,
                )
            }
        } else {
            Wordmark(fontSize = 32.sp)
        }
        StatusPill(stringResource(R.string.home_preview, BuildConfig.VERSION_NAME), StatusTone.CAUTION)
        if (systems.isNotEmpty()) {
            // A random game from the whole collection: a way back into games you forgot you had.
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            com.retroforge.core.ui.FocusChip(stringResource(R.string.home_surprise_me), onClick = {
                scope.launch { library.randomGameId()?.let(onOpenGame) }
            })
        }

        val last = recent.firstOrNull()
        if (last != null) {
            GameHero(
                last, library.art(rootsById, chosen, last),
                eyebrow = str(R.string.home_jump_back_in), subtitle = listOfNotNull(library.systemName(last.system), tagLine(last)).joinToString(" · "),
                actionLabel = str(R.string.home_layouts_open), onAction = { onOpenGame(last.id) },
            )
        } else if (systems.isEmpty()) {
            FocusCard(modifier = Modifier.fillMaxWidth(), onClick = { onOpen(Section.RETRO) }) {
                CardTitle(stringResource(R.string.home_welcome_to_retroforge))
                CardBody(stringResource(R.string.home_add_your_games_folder_in_retro))
            }
        }

        GameRow(str(R.string.library_recently_played), recent.drop(1), library, rootsById, chosen, onOpenGame)
        GameRow(str(R.string.playlists_favourites), favourites, library, rootsById, chosen, onOpenGame)

        if (systems.isNotEmpty()) {
            CardTitle(stringResource(R.string.home_your_systems))
            SystemsView(systems, onOpenSystem)
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            FocusCard(modifier = Modifier.weight(1f), onClick = { onOpen(Section.RETRO) }) {
                CardTitle(stringResource(R.string.home_retro))
                CardBody(stringResource(R.string.home_search_systems_playlists_rom_tools))
            }
            if (showPc) {
                FocusCard(modifier = Modifier.weight(1f), onClick = { onOpen(Section.PC) }) {
                    CardTitle(stringResource(R.string.home_pc))
                    CardBody(stringResource(R.string.home_ports_of_your_games_and_windows))
                }
            }
        }
        FocusCard(modifier = Modifier.fillMaxWidth(), onClick = onOpenApps) {
            CardTitle(stringResource(R.string.home_android_apps))
            CardBody(stringResource(R.string.home_your_other_apps_and_android_s))
        }
    }
}

@Composable
private fun GameRow(
    title: String,
    games: List<GameEntity>,
    library: LibraryViewModel,
    rootsById: Map<Long, com.retroforge.core.library.RootEntity>,
    chosen: Map<Pair<Long, String>, java.io.File>,
    onOpenGame: (Long) -> Unit,
) {
    if (games.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            items(games, key = { it.id }) { g ->
                GameTile(g, library.art(rootsById, chosen, g), onClick = { onOpenGame(g.id) }, modifier = Modifier.width(170.dp).padding(bottom = 4.dp))
            }
        }
    }
}
