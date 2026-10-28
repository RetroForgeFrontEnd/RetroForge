package com.retroforge.app.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.retroforge.app.BuildConfig
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
import com.retroforge.core.ui.StatusPill
import com.retroforge.core.ui.StatusTone
import com.retroforge.core.ui.Wordmark
import com.retroforge.core.ui.LocalThemeSpec
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
        Wordmark(fontSize = 32.sp)
        StatusPill("Preview ${BuildConfig.VERSION_NAME}", StatusTone.CAUTION)

        val last = recent.firstOrNull()
        if (last != null) {
            GameHero(
                last, library.art(rootsById, chosen, last),
                eyebrow = "Jump back in", subtitle = listOfNotNull(library.systemName(last.system), tagLine(last)).joinToString(" · "),
                actionLabel = "Open", onAction = { onOpenGame(last.id) },
            )
        } else if (systems.isEmpty()) {
            FocusCard(modifier = Modifier.fillMaxWidth(), onClick = { onOpen(Section.RETRO) }) {
                CardTitle("Welcome to RetroForge")
                CardBody("Add your games folder in Retro (or run Settings → Set up RetroForge), and your collection shows up here.")
            }
        }

        GameRow("Recently played", recent.drop(1), library, rootsById, chosen, onOpenGame)
        GameRow("Favourites", favourites, library, rootsById, chosen, onOpenGame)

        if (systems.isNotEmpty()) {
            CardTitle("Your systems")
            SystemsView(systems, onOpenSystem)
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            FocusCard(modifier = Modifier.weight(1f), onClick = { onOpen(Section.RETRO) }) {
                CardTitle("Retro")
                CardBody("Search, systems, playlists, ROM tools.")
            }
            if (showPc) {
                FocusCard(modifier = Modifier.weight(1f), onClick = { onOpen(Section.PC) }) {
                    CardTitle("PC")
                    CardBody("Windows games on supported devices.")
                }
            }
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
