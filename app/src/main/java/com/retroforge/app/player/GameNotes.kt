package com.retroforge.app.player

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File

/**
 * The user's own notes for a game, as plain text in `notes/<system>/<file name>.txt` in the app's external files
 * folder, next to the built-in player's saves and states, so save syncing can carry them too. Written from the game's
 * page and from the player's menu (another process), so each read goes to the file.
 */
object GameNotes {
    fun file(context: Context, system: String, fileName: String): File =
        File(File(context.getExternalFilesDir(null) ?: context.filesDir, "notes/$system"), "$fileName.txt")

    fun read(context: Context, system: String, fileName: String): String =
        runCatching { file(context, system, fileName).readText() }.getOrDefault("")

    /** Saves [text]; blank notes remove the file. */
    fun write(context: Context, system: String, fileName: String, text: String) {
        val target = file(context, system, fileName)
        if (text.isBlank()) {
            target.delete()
            return
        }
        target.parentFile?.mkdirs()
        val part = File(target.path + ".part")
        part.writeText(text)
        if (!part.renameTo(target)) {
            target.delete()
            part.renameTo(target)
        }
    }

    /** A guide or map from the user's own files. */
    data class Guide(val name: String, val uri: Uri, val isPdf: Boolean)

    private val GUIDE_TYPES = setOf("pdf", "png", "jpg", "jpeg", "webp", "gif")

    /**
     * Guides and maps for the game at [gameUri] (a document in a granted folder): images and PDFs in a `guides`
     * folder beside it whose names start with the game's name, and everything in `guides/<game name>/`. Folders whose
     * document ids aren't paths (some network drives) have none. Call off the main thread.
     */
    fun findGuides(resolver: ContentResolver, gameUri: Uri, fileName: String): List<Guide> = runCatching {
        val id = DocumentsContract.getDocumentId(gameUri)
        val parent = when {
            '/' in id -> id.substringBeforeLast('/')
            ':' in id -> id.substringBefore(':') + ":"
            else -> return emptyList()
        }
        fun child(path: String) = if (parent.endsWith(":")) parent + path else "$parent/$path"
        val base = fileName.substringBeforeLast('.')
        // The game's name without its tags: "Zelda (USA) (Rev 1)" also finds "Zelda map.png".
        val plain = base.substringBefore(" (").substringBefore(" [").trim().lowercase().ifEmpty { base.lowercase() }
        fun list(folderId: String): List<Pair<String, String>> {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(gameUri, folderId)
            val found = mutableListOf<Pair<String, String>>()
            resolver.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
                while (c.moveToNext()) found += c.getString(0) to c.getString(1)
            }
            return found
        }
        fun usable(name: String) = name.substringAfterLast('.').lowercase() in GUIDE_TYPES
        val inGuides = runCatching { list(child("guides")) }.getOrDefault(emptyList())
        val beside = inGuides.filter { (_, name) -> name.lowercase().startsWith(plain) && usable(name) }
        val own = inGuides.firstOrNull { (_, name) -> !usable(name) && (name.equals(base, true) || name.equals(plain, true)) }
            ?.let { (folderId, _) -> runCatching { list(folderId) }.getOrDefault(emptyList()).filter { usable(it.second) } }
            .orEmpty()
        (beside + own).distinctBy { it.first }.sortedBy { it.second.lowercase() }.map { (docId, name) ->
            Guide(name, DocumentsContract.buildDocumentUriUsingTree(gameUri, docId), name.endsWith(".pdf", true))
        }
    }.getOrDefault(emptyList())
}
