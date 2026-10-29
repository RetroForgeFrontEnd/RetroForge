package com.retroforge.app.ui.library

import com.retroforge.app.ui.ChipRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import com.retroforge.core.ui.ControllerTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.retroforge.app.ui.CardBody
import com.retroforge.app.ui.CardTitle
import com.retroforge.app.ui.ScreenFrame
import com.retroforge.core.library.GameEntity
import com.retroforge.core.library.PlaylistSummary
import com.retroforge.core.library.RootEntity
import com.retroforge.core.ui.FocusCard
import com.retroforge.core.ui.FocusChip
import kotlinx.coroutines.launch

/** On a game's page: favourite it, and tick the playlists it's in (or make a new one). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FavouriteAndPlaylists(game: GameEntity, viewModel: LibraryViewModel) {
    val favourite by remember(game.id) { viewModel.isFavourite(game) }.collectAsStateWithLifecycle(initialValue = false)
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val containing by remember(game.id) { viewModel.playlistsContaining(game) }.collectAsStateWithLifecycle(initialValue = emptyList())
    var showPlaylists by remember(game.id) { mutableStateOf(false) }
    var newName by remember(game.id) { mutableStateOf("") }
    val scope = rememberCoroutineScope()

    ChipRow {
        FocusChip(if (favourite) "★ Favourite" else "☆ Add to favourites", onClick = { viewModel.setFavourite(game, !favourite) })
        FocusChip(
            if (containing.isEmpty()) "Add to a playlist" else "In ${plural(containing.size, "playlist")}",
            onClick = { showPlaylists = !showPlaylists },
        )
    }
    if (showPlaylists) {
        FocusCard(modifier = Modifier.fillMaxWidth()) {
            CardTitle("Playlists")
            CardBody(if (playlists.isEmpty()) "No playlists yet. Name one below." else "Select a playlist to add or remove this game.")
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            playlists.forEach { p ->
                val inIt = p.id in containing
                FocusChip((if (inIt) "☑ " else "☐ ") + p.name, onClick = { viewModel.setInPlaylist(p.id, game, !inIt) })
            }
        }
        ControllerTextField(value = newName, onValueChange = { newName = it.take(60) }, singleLine = true, label = { Text("New playlist name") }, modifier = Modifier.fillMaxWidth())
        FocusChip("Make playlist and add this game", enabled = newName.isNotBlank(), onClick = {
            val name = newName
            newName = ""
            scope.launch { viewModel.setInPlaylist(viewModel.createPlaylist(name), game, true) }
        })
    }
}

/** On the Retro screen: the favourites row and the playlist cards. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FavouritesAndPlaylistsSection(
    viewModel: LibraryViewModel,
    rootsById: Map<Long, RootEntity>,
    onOpenGame: (Long) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
) {
    val favourites by viewModel.favourites.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val chosen by viewModel.chosenCovers.collectAsStateWithLifecycle()
    if (favourites.isNotEmpty()) {
        CardTitle("Favourites")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            items(favourites, key = { it.id }) { game ->
                GameTile(game, viewModel.art(rootsById, chosen, game), onClick = { onOpenGame(game.id) }, modifier = Modifier.width(190.dp))
            }
        }
    }
    if (playlists.isNotEmpty()) {
        CardTitle("Playlists")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            playlists.forEach { p -> PlaylistCard(p) { onOpenPlaylist(p.id) } }
        }
    }
}

@Composable
private fun PlaylistCard(p: PlaylistSummary, onClick: () -> Unit) {
    FocusCard(modifier = Modifier.width(220.dp).heightIn(min = 90.dp), onClick = onClick) {
        Text(p.name, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface, maxLines = 2)
        CardBody(plural(p.games, "game"))
    }
}

/** One playlist: its games in order, with reordering, removal, renaming and deleting. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlaylistScreen(viewModel: LibraryViewModel, playlistId: Long, onOpenGame: (Long) -> Unit, onDeleted: () -> Unit) {
    val playlist by remember(playlistId) { viewModel.playlist(playlistId) }.collectAsStateWithLifecycle(initialValue = null)
    val games by remember(playlistId) { viewModel.playlistGames(playlistId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val roots by viewModel.roots.collectAsStateWithLifecycle()
    val rootsById = remember(roots) { roots.orEmpty().associateBy { it.id } }
    val chosen by viewModel.chosenCovers.collectAsStateWithLifecycle()
    var renaming by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val p = playlist ?: run {
        ScreenFrame(title = "Playlist") { CardBody("This playlist no longer exists.") }
        return
    }

    ScreenFrame(title = p.name, subtitle = plural(games.size, "game") + ". Add games from any game's page.") {
        if (games.isEmpty()) CardBody("Empty so far. Open a game and use \"Add to a playlist\".")
        games.forEachIndexed { i, game ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                GameTile(game, viewModel.art(rootsById, chosen, game), onClick = { onOpenGame(game.id) }, modifier = Modifier.width(150.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FocusChip("Up", enabled = i > 0, onClick = { viewModel.movePlaylistGame(playlistId, game, -1) })
                    FocusChip("Down", enabled = i < games.lastIndex, onClick = { viewModel.movePlaylistGame(playlistId, game, 1) })
                    FocusChip("Remove", onClick = { viewModel.setInPlaylist(playlistId, game, false) })
                }
            }
        }

        val name = renaming
        if (name != null) {
            ControllerTextField(value = name, onValueChange = { renaming = it.take(60) }, singleLine = true, label = { Text("Playlist name") }, modifier = Modifier.fillMaxWidth())
            ChipRow {
                FocusChip("Save name", enabled = name.isNotBlank(), onClick = { viewModel.renamePlaylist(playlistId, name); renaming = null })
                FocusChip("Cancel", onClick = { renaming = null })
            }
        } else if (confirmDelete) {
            FocusCard(modifier = Modifier.fillMaxWidth()) {
                CardTitle("Delete \"${p.name}\"?")
                CardBody("Only the playlist goes. The games stay in your library and on your storage.")
            }
            ChipRow {
                FocusChip("Delete playlist", onClick = { viewModel.deletePlaylist(playlistId); onDeleted() })
                FocusChip("Cancel", onClick = { confirmDelete = false })
            }
        } else {
            ChipRow {
                FocusChip("Rename", onClick = { renaming = p.name })
                FocusChip("Delete playlist", onClick = { confirmDelete = true })
            }
        }
    }
}
