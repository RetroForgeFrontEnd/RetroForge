package com.retroforge.app.ui.settings

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.retroforge.app.ui.CardBody
import com.retroforge.app.ui.CardTitle
import com.retroforge.core.ui.FocusCard
import com.retroforge.core.ui.FocusChip
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * N64 high-resolution texture packs for the built-in player (Mupen64Plus-Next): each pack is a folder named after
 * the game's internal name ("SUPER MARIO 64"), kept in the player's system/Mupen64plus/hires_texture folder.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TexturePacksCard(systemDir: File) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val packs = remember { File(systemDir, "Mupen64plus/hires_texture") }
    var changes by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf<String?>(null) }
    val installed = remember(changes) { packs.listFiles()?.filter { it.isDirectory }?.map { it.name }?.sorted().orEmpty() }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        tree ?: return@rememberLauncherForActivityResult
        status = "Copying the texture pack…"
        scope.launch {
            status = withContext(Dispatchers.IO) {
                runCatching {
                    val name = copyTree(context, tree, packs) { n -> status = "Copying the texture pack… $n files" }
                    "Added $name. N64 games with this internal name use it next time they start."
                }.getOrElse { "Couldn't copy it: ${it.message}" }
            }
            changes++
        }
    }
    FocusCard(modifier = Modifier.fillMaxWidth()) {
        CardTitle("Texture packs (N64)")
        CardBody(
            "High-resolution texture packs for N64 games in the built-in player. A pack is a folder named after the game's " +
                "internal name, for example SUPER MARIO 64; pick that folder and RetroForge copies it where the emulator looks. " +
                "They're switched on automatically when a pack is installed.",
        )
        CardBody(if (installed.isEmpty()) "No packs installed." else "Installed: ${installed.joinToString()}")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FocusChip("Add a texture pack folder", onClick = { pick.launch(null) })
            installed.forEach { name ->
                FocusChip("Remove $name", onClick = {
                    scope.launch { withContext(Dispatchers.IO) { File(packs, name).deleteRecursively() }; changes++ }
                })
            }
        }
        status?.let { CardBody(it) }
    }
}

/** Copies the picked folder (with everything in it) into [into]; returns the folder's name. */
private fun copyTree(context: Context, tree: Uri, into: File, onProgress: (Int) -> Unit): String {
    val resolver = context.contentResolver
    val rootId = DocumentsContract.getTreeDocumentId(tree)
    var copied = 0
    fun nameOf(id: String) = resolver.query(DocumentsContract.buildDocumentUriUsingTree(tree, id),
        arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null } ?: "pack"
    fun walk(id: String, target: File, depth: Int) {
        if (depth > 6) return
        target.mkdirs()
        resolver.query(DocumentsContract.buildChildDocumentsUriUsingTree(tree, id),
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE),
            null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val child = c.getString(0)
                val name = c.getString(1)?.replace('/', '_') ?: continue
                if (c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) {
                    walk(child, File(target, name), depth + 1)
                } else {
                    resolver.openInputStream(DocumentsContract.buildDocumentUriUsingTree(tree, child))?.use { input ->
                        File(target, name).outputStream().use { input.copyTo(it) }
                    }
                    if (++copied % 50 == 0) onProgress(copied)
                }
            }
        }
    }
    val name = nameOf(rootId).replace('/', '_')
    walk(rootId, File(into, name), 0)
    return name
}
