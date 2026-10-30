package com.retroforge.app.ui.settings

import com.retroforge.core.ui.pluralStr
import com.retroforge.core.ui.str
import com.retroforge.app.R

import androidx.compose.ui.res.stringResource

import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.retroforge.app.ui.CardBody
import com.retroforge.app.ui.CardTitle
import com.retroforge.core.library.LibraryRepository
import com.retroforge.core.ui.FocusCard
import com.retroforge.core.ui.FocusChip
import com.retroforge.library.EsdeCollections
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Brings ES-DE's custom collections across as RetroForge playlists (ES-DE's files are only read). */
@Composable
fun EsdeImportCard(library: LibraryRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        tree ?: return@rememberLauncherForActivityResult
        status = str(R.string.esde_import_reading_collections)
        scope.launch {
            status = withContext(Dispatchers.IO) { runCatching { import(context.contentResolver, tree, library) }.getOrElse { str(R.string.esde_import_couldn_t_read_them, it.message) } }
        }
    }
    FocusCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle(stringResource(R.string.esde_import_import_collections_from_es_de))
        CardBody(
            stringResource(R.string.esde_import_turns_your_es_de_custom_collections),
        )
        FocusChip(stringResource(R.string.esde_import_choose_the_es_de_folder), onClick = { pick.launch(null) })
        status?.let { CardBody(it) }
    }
}

private suspend fun import(resolver: android.content.ContentResolver, tree: Uri, library: LibraryRepository): String {
    // custom-*.cfg in the picked folder or its "collections" folder.
    fun children(id: String) = resolver.query(
        DocumentsContract.buildChildDocumentsUriUsingTree(tree, id),
        arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE),
        null, null, null,
    )?.use { c -> buildList { while (c.moveToNext()) add(Triple(c.getString(0), c.getString(1).orEmpty(), c.getString(2))) } }.orEmpty()
    val top = children(DocumentsContract.getTreeDocumentId(tree))
    val folder = top.firstOrNull { it.third == DocumentsContract.Document.MIME_TYPE_DIR && it.second.equals("collections", ignoreCase = true) }
    val files = (folder?.let { children(it.first) } ?: top).filter { EsdeCollections.nameOf(it.second) != null }
    if (files.isEmpty()) return str(R.string.esde_import_no_custom_collections_custom_cfg_found)
    val games = library.roots.first().flatMap { library.gamesIn(it.id) }
    var made = 0
    var added = 0
    var missed = 0
    for ((id, name, _) in files) {
        val text = resolver.openInputStream(DocumentsContract.buildDocumentUriUsingTree(tree, id))?.use { it.readBytes().decodeToString() } ?: continue
        val collection = EsdeCollections.parse(EsdeCollections.nameOf(name)!!, text)
        val found = collection.games.mapNotNull { entry -> games.firstOrNull { EsdeCollections.matches(it.relativePath, entry) } }
        missed += collection.games.size - found.size
        if (found.isEmpty()) continue
        val playlist = library.createPlaylist(collection.name)
        found.forEach { library.setInPlaylist(playlist, it, inPlaylist = true) }
        made++
        added += found.size
    }
    fun n(count: Int, word: String) = com.retroforge.app.ui.countOf(count, word)
    return str(R.string.esde_import_made_with, n(made, "playlist"), n(added, "game")) +
        if (missed > 0) pluralStr(R.plurals.esde_import_missed, missed, n(missed, "game")) else ""
}
