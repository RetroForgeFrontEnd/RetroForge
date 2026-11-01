package com.retroforge.app.ui.pc

import com.retroforge.core.ui.str
import com.retroforge.app.R

import androidx.compose.ui.res.stringResource

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.retroforge.app.ui.CardBody
import com.retroforge.app.ui.CardTitle
import com.retroforge.app.ui.ChipRow
import com.retroforge.app.ui.DownloadBar
import com.retroforge.core.ui.FocusCard
import com.retroforge.core.ui.FocusChip
import com.retroforge.core.ui.StatusPill
import com.retroforge.core.ui.StatusTone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Free games: shareware, freeware and homebrew that anyone may share, downloaded from RetroArch's own content server
 * into the user's game folder (in its system folder), then played in the built-in player like any other game.
 */
@Composable
fun FreeGamesSection(library: com.retroforge.core.library.LibraryRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs = remember { context.getSharedPreferences("free_games", Context.MODE_PRIVATE) }
    var changes by remember { mutableIntStateOf(0) }
    val folder = remember(changes) { prefs.getString("folder", null)?.let(Uri::parse) }
    val got = remember(changes) { prefs.getStringSet("got", emptySet()).orEmpty() }
    var busy by remember { mutableStateOf<String?>(null) }
    var progress by remember { mutableStateOf<Float?>(null) }
    var status by remember { mutableStateOf<String?>(null) }

    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        runCatching { context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION) }
        prefs.edit().putString("folder", uri.toString()).putString("prefix", FreeGames.prefixFor(context.contentResolver, uri)).apply()
        changes++
        status = str(R.string.free_games_free_games_will_go_in_that)
    }

    fun get(game: FreeGame) {
        val target = folder ?: return
        busy = game.title
        status = str(R.string.free_games_downloading, game.title)
        scope.launch {
            status = withContext(Dispatchers.IO) {
                runCatching { FreeGames.install(game, context.contentResolver, target, prefs.getString("prefix", "").orEmpty()) { progress = it } }.fold(
                    onSuccess = {
                        prefs.edit().putStringSet("got", got + game.title).apply()
                        // The game folder it went in is scanned again, so it shows up with the rest. A folder inside one
                        // already in the library isn't added again (its games would show twice).
                        val pickedId = android.provider.DocumentsContract.getTreeDocumentId(target)
                        val holder = library.roots.first().firstOrNull { root ->
                            val uri = Uri.parse(root.treeUri)
                            uri.authority == target.authority && runCatching { android.provider.DocumentsContract.getTreeDocumentId(uri) }.getOrNull()
                                ?.let { pickedId == it || pickedId.startsWith("$it/") } == true
                        }
                        if (holder != null) library.scan(holder.id) else library.addFolder(target)
                        str(R.string.free_games_is_in_your_library_folder_find, game.title, game.system)
                    },
                    onFailure = { str(R.string.free_games_couldn_t_get, game.title, it.message) },
                )
            }
            busy = null
            progress = null
            changes++
        }
    }

    FocusCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle(stringResource(R.string.free_games_free_games_to_download)) { StatusPill(stringResource(R.string.free_games_games, FreeGames.all.size), StatusTone.GOOD) }
        CardBody(
            stringResource(R.string.free_games_games_their_makers_let_anyone_share),
        )
        if (folder == null) {
            CardBody(stringResource(R.string.free_games_first_choose_where_they_go_your))
            FocusChip(stringResource(R.string.free_games_choose_a_folder), onClick = { pickFolder.launch(null) })
        } else {
            CardBody(stringResource(R.string.free_games_saving_to_the_folder_you_chose, folder.lastPathSegment?.substringAfter(':')?.ifBlank { stringResource(R.string.free_games_the_folder_you_chose) } ?: stringResource(R.string.free_games_the_folder_you_chose)))
            FocusChip(stringResource(R.string.free_games_choose_a_different_folder), onClick = { pickFolder.launch(null) })
        }
        if (busy != null) DownloadBar(progress)
        status?.let { CardBody(it) }
    }
    FreeGames.all.groupBy { it.kind to it.system }.toList().groupBy { it.first.first }.forEach { (kind, groups) ->
        FocusCard(modifier = Modifier.fillMaxWidth()) {
            CardTitle(kind)
            groups.forEach { (_, games) ->
                games.forEach { game ->
                    CardBody("${game.title}: ${game.about}")
                }
                ChipRow {
                    games.forEach { game ->
                        val have = game.title in got
                        FocusChip(
                            if (have) "✓ ${game.title}" else str(R.string.cloud_apps_get, game.title),
                            enabled = folder != null && busy == null,
                            onClick = { get(game) },
                        )
                    }
                }
            }
        }
    }
}

/** Free games on a screen of their own, opened from the Retro tab and from Home when the library is empty. */
@Composable
fun FreeGamesScreen(library: com.retroforge.core.library.LibraryRepository) {
    com.retroforge.app.ui.ScreenFrame(title = stringResource(R.string.pc_free_games), subtitle = stringResource(R.string.pc_shareware_freeware_and_homebrew_games_to, FreeGames.all.size)) {
        FreeGamesSection(library)
    }
}
