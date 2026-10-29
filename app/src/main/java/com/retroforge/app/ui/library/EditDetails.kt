package com.retroforge.app.ui.library

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.retroforge.app.ui.ScreenFrame
import com.retroforge.core.library.GameDetails
import com.retroforge.core.library.details
import com.retroforge.core.ui.ControllerTextField
import com.retroforge.core.ui.FocusChip
import kotlinx.coroutines.launch

/**
 * Edit a game's title and details by hand. Edits are kept apart from what the scan found, put back after every
 * rescan, and follow the file through ROM Manager changes; "Reset" goes back to the scanned details. The game file
 * and any gamelist.xml are never changed.
 */
@Composable
fun EditDetailsScreen(viewModel: LibraryViewModel, gameId: Long, onDone: () -> Unit) {
    val game by remember(gameId) { viewModel.game(gameId) }.collectAsStateWithLifecycle(initialValue = null)
    val g = game ?: run {
        ScreenFrame(title = "Edit details") { CardBody("Loading…") }
        return
    }
    val edit by remember(g.rootId, g.relativePath) { viewModel.editFor(g) }.collectAsStateWithLifecycle(initialValue = null)
    val start = remember(g.id) { g.details() }
    var title by remember(g.id) { mutableStateOf(start.title) }
    var description by remember(g.id) { mutableStateOf(start.description.orEmpty()) }
    var released by remember(g.id) { mutableStateOf(start.releaseDate.orEmpty()) }
    var developer by remember(g.id) { mutableStateOf(start.developer.orEmpty()) }
    var publisher by remember(g.id) { mutableStateOf(start.publisher.orEmpty()) }
    var genre by remember(g.id) { mutableStateOf(start.genre.orEmpty()) }
    var players by remember(g.id) { mutableStateOf(start.players.orEmpty()) }
    val scope = rememberCoroutineScope()

    ScreenFrame(title = "Edit details", subtitle = g.fileName) {
        CardBody("Changes are saved in RetroForge only. The game file and any gamelist.xml stay as they are, and rescans keep your changes.")
        val wide = Modifier.fillMaxWidth()
        ControllerTextField(title, { title = it.take(200) }, wide, label = { Text("Title") })
        ControllerTextField(description, { description = it.take(4000) }, wide, label = { Text("Description") }, singleLine = false)
        ControllerTextField(released, { released = it.take(40) }, wide, label = { Text("Release date (e.g. 1994 or 1994-11-21)") })
        ControllerTextField(developer, { developer = it.take(120) }, wide, label = { Text("Developer") })
        ControllerTextField(publisher, { publisher = it.take(120) }, wide, label = { Text("Publisher") })
        ControllerTextField(genre, { genre = it.take(120) }, wide, label = { Text("Genre") })
        ControllerTextField(players, { players = it.take(20) }, wide, label = { Text("Players") })
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FocusChip("Save", enabled = title.isNotBlank(), onClick = {
                val details = GameDetails(
                    title = title.trim(),
                    description = description.trim().ifEmpty { null },
                    releaseDate = released.trim().ifEmpty { null },
                    developer = developer.trim().ifEmpty { null },
                    publisher = publisher.trim().ifEmpty { null },
                    genre = genre.trim().ifEmpty { null },
                    players = players.trim().ifEmpty { null },
                )
                scope.launch {
                    viewModel.saveDetails(g, details)
                    onDone()
                }
            })
            FocusChip("Cancel", onClick = onDone)
        }
        edit?.let { e ->
            CardBody("Scanned title: ${e.scanned.title}")
            FocusChip("Reset to the scanned details", onClick = {
                scope.launch {
                    viewModel.resetDetails(g)
                    onDone()
                }
            })
        }
    }
}
