package com.retroforge.app.player

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File
import java.io.IOException

/**
 * Games kept as a folder (RetroBat's "Gods.pc" DOS folders): copied once into the game cache, with everything in them,
 * and the file to start picked the way RetroBat does: its dosbox.bat, else a .bat or .exe named like the game, else
 * the only program there.
 */
object FolderGame {
    fun isFolder(resolver: ContentResolver, uri: Uri): Boolean =
        runCatching { resolver.getType(uri) == DocumentsContract.Document.MIME_TYPE_DIR }.getOrDefault(false)

    /** Copies the folder at [uri] into [dir] (skipped when a finished copy is already there) and returns the file to start. */
    fun expose(resolver: ContentResolver, uri: Uri, name: String, dir: File, onProgress: (Int) -> Unit): File {
        val target = File(dir, name.replace('/', '_'))
        val done = File(dir, ".complete")
        if (!done.exists()) {
            dir.deleteRecursively()
            val files = mutableListOf<Pair<Uri, File>>()
            var total = 0L
            fun walk(docId: String, into: File, depth: Int) {
                if (depth > 8) return
                into.mkdirs()
                resolver.query(
                    DocumentsContract.buildChildDocumentsUriUsingTree(uri, docId),
                    arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_SIZE),
                    null, null, null,
                )?.use { c ->
                    while (c.moveToNext()) {
                        val id = c.getString(0)
                        val child = c.getString(1)?.replace('/', '_') ?: continue
                        if (c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR) walk(id, File(into, child), depth + 1)
                        else {
                            files += DocumentsContract.buildDocumentUriUsingTree(uri, id) to File(into, child)
                            total += c.getLong(3)
                        }
                    }
                }
            }
            walk(DocumentsContract.getDocumentId(uri), target, 0)
            var copied = 0L
            files.forEach { (from, to) ->
                resolver.openInputStream(from)?.use { input ->
                    to.outputStream().use { out ->
                        val buffer = ByteArray(1 shl 16)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            out.write(buffer, 0, n)
                            copied += n
                        }
                    }
                } ?: throw IOException("couldn't read ${to.name}")
                if (total > 0) onProgress((copied * 100 / total).toInt())
            }
            done.createNewFile()
        }
        return entry(target) ?: throw IOException("no program to start in $name (a .bat, .exe or .com)")
    }

    private fun entry(folder: File): File? {
        val files = folder.listFiles().orEmpty().filter { it.isFile }
        fun named(ext: String) = files.filter { it.extension.equals(ext, true) }
        val game = folder.name.substringBeforeLast('.').lowercase().filter { it.isLetterOrDigit() }
        fun likeGame(f: File) = f.nameWithoutExtension.lowercase().filter { it.isLetterOrDigit() }.let { game.startsWith(it) || it.startsWith(game) }
        return files.firstOrNull { it.name.equals("dosbox.bat", true) }
            ?: named("bat").firstOrNull(::likeGame)
            ?: named("exe").firstOrNull(::likeGame)
            ?: (named("exe") + named("com")).singleOrNull()
            ?: named("bat").firstOrNull()
            ?: named("exe").firstOrNull()
    }
}
