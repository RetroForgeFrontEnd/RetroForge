package com.retroforge.app.ui.library

import com.retroforge.app.R
import com.retroforge.core.ui.str
import androidx.compose.ui.res.stringResource
import android.net.Uri
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.retroforge.app.player.GameNotes
import com.retroforge.app.ui.CardBody
import com.retroforge.app.ui.CardTitle
import com.retroforge.app.ui.ChipRow
import com.retroforge.core.ui.ControllerTextField
import com.retroforge.core.ui.FocusCard
import com.retroforge.core.ui.FocusChip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The notes on file for a game, and saving them (the file is read again after each save). */
private class NotesState(val text: String?, val save: (String) -> Unit)

/** Counts the times the screen came back to the front, so files changed elsewhere (the player's menu) are read again. */
@Composable
private fun resumes(): Int {
    val owner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var count by remember { mutableStateOf(0) }
    androidx.compose.runtime.DisposableEffect(owner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event -> if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) count++ }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return count
}

@Composable
private fun rememberNotes(system: String, fileName: String): NotesState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var version by remember { mutableStateOf(0) }
    val resumed = resumes()
    val text by produceState<String?>(null, system, fileName, version, resumed) {
        value = withContext(Dispatchers.IO) { GameNotes.read(context, system, fileName) }
    }
    return NotesState(text) { new ->
        scope.launch {
            withContext(Dispatchers.IO) { GameNotes.write(context, system, fileName, new) }
            version++
        }
    }
}

/** The game's guides and maps, found off the main thread. */
@Composable
fun rememberGuides(gameUri: Uri?, fileName: String): List<GameNotes.Guide> {
    val context = LocalContext.current
    val resumed = resumes()
    val guides by produceState(emptyList<GameNotes.Guide>(), gameUri, fileName, resumed) {
        value = gameUri?.let { withContext(Dispatchers.IO) { GameNotes.findGuides(context.contentResolver, it, fileName) } }.orEmpty()
    }
    return guides
}

/**
 * Notes text, read or written: [editing] shows the box and Save/Cancel, else the notes (or how to start). [button]
 * draws a button in the caller's style (the game page's chips, the player's menu buttons).
 */
@Composable
fun NotesEditor(
    system: String,
    fileName: String,
    textColor: Color,
    button: @Composable (String, () -> Unit) -> Unit,
) {
    val notes = rememberNotes(system, fileName)
    var draft by remember(system, fileName) { mutableStateOf<String?>(null) }
    val text = notes.text ?: return
    val editing = draft
    if (editing == null) {
        Text(text.ifBlank { stringResource(R.string.notes_empty) }, color = textColor, modifier = Modifier.fillMaxWidth())
        button(if (text.isBlank()) stringResource(R.string.notes_write) else stringResource(R.string.notes_edit)) { draft = text }
    } else {
        ControllerTextField(
            value = editing,
            onValueChange = { draft = it.take(20_000) },
            singleLine = false,
            label = { Text(stringResource(R.string.notes_field)) },
            modifier = Modifier.fillMaxWidth().heightIn(min = 140.dp),
        )
        button(stringResource(R.string.notes_save)) { notes.save(editing); draft = null }
        button(stringResource(R.string.notes_cancel)) { draft = null }
    }
}

/** A guide or map: a PDF in the manual viewer, a picture full screen (scrolls when it's taller than the screen). */
@Composable
fun GuideViewer(guide: GameNotes.Guide, onClose: () -> Unit) {
    if (guide.isPdf) {
        ManualViewer(guide.uri, guide.name, onClose)
        return
    }
    val scroll = rememberScrollState()
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().background(Color(0xFF111114)).focusRequester(focus).focusable()
                // A controller's D-pad scrolls a tall map; B (back) closes.
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val by = when (event.key.nativeKeyCode) {
                        KeyEvent.KEYCODE_DPAD_DOWN -> 400f
                        KeyEvent.KEYCODE_DPAD_UP -> -400f
                        else -> return@onPreviewKeyEvent false
                    }
                    scope.launch { scroll.animateScrollBy(by) }
                    true
                },
        ) {
            coil3.compose.AsyncImage(
                guide.uri, guide.name,
                contentScale = ContentScale.FillWidth,
                modifier = Modifier.fillMaxWidth().verticalScroll(scroll),
            )
            FocusChip(stringResource(R.string.notes_close), onClick = onClose, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp))
        }
    }
}

/** On a game's page: its notes, and its guides and maps when it has any. */
@Composable
fun GameNotesCard(game: com.retroforge.core.library.GameEntity, gameUri: Uri?) {
    val guides = rememberGuides(gameUri, game.fileName)
    var open by remember(game.id) { mutableStateOf<GameNotes.Guide?>(null) }
    FocusCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle(stringResource(R.string.notes_title))
        CardBody(stringResource(R.string.notes_body))
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        NotesEditor(game.system, game.fileName, androidx.compose.material3.MaterialTheme.colorScheme.onSurface) { label, onClick -> FocusChip(label, onClick = onClick) }
    }
    if (guides.isNotEmpty()) {
        CardTitle(stringResource(R.string.guides_title))
        ChipRow { guides.forEach { g -> FocusChip((if (g.isPdf) "📄 " else "🗺 ") + g.name, onClick = { open = g }) } }
    } else {
        CardBody(stringResource(R.string.guides_hint))
    }
    // Walkthroughs are their writers' own work, so they're searched for and open on the web, never copied in.
    val context = LocalContext.current
    FocusChip(stringResource(R.string.guides_find_walkthrough), onClick = {
        val search = "https://gamefaqs.gamespot.com/search?game=" + java.net.URLEncoder.encode(game.title, "UTF-8")
        runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(search))) }
    })
    open?.let { GuideViewer(it) { open = null } }
}

/** The player menu's Notes page: the same notes, and the guides and maps to look at mid-game. */
@Composable
fun NotesMenuPage(system: String, fileName: String, gameUri: Uri?, button: @Composable (String, () -> Unit) -> Unit) {
    val guides = rememberGuides(gameUri, fileName)
    var open by remember { mutableStateOf<GameNotes.Guide?>(null) }
    Text(str(R.string.notes_title), color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
    NotesEditor(system, fileName, Color(0xFFDDDDDD), button)
    if (guides.isNotEmpty()) {
        Text(str(R.string.guides_title), color = Color.White, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
        guides.forEach { g -> button((if (g.isPdf) "📄 " else "🗺 ") + g.name) { open = g } }
    }
    open?.let { GuideViewer(it) { open = null } }
}
