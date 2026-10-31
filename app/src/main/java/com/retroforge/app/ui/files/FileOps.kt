package com.retroforge.app.ui.files

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import com.retroforge.app.R
import com.retroforge.core.ui.str
import java.io.IOException

/** A file or folder inside a folder the user granted (a "tree"), with the folder it sits in. */
data class FileEntry(
    val tree: Uri,
    val id: String,
    val parentId: String,
    val name: String,
    val isFolder: Boolean,
    val size: Long,
    val modified: Long,
    val mime: String,
    val flags: Int,
) {
    val uri: Uri get() = DocumentsContract.buildDocumentUriUsingTree(tree, id)
}

/**
 * File operations over the Storage Access Framework, for the file browser. RetroForge has no all-files permission,
 * so it works inside folders the user picked: game collections and any folder added in the browser.
 */
class FileOps(private val resolver: ContentResolver) {

    fun rootId(tree: Uri): String = DocumentsContract.getTreeDocumentId(tree)

    fun list(tree: Uri, folderId: String): List<FileEntry> {
        val cursor = resolver.query(
            DocumentsContract.buildChildDocumentsUriUsingTree(tree, folderId),
            arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED, Document.COLUMN_FLAGS),
            null, null, null,
        ) ?: throw IOException(str(R.string.files_nothing_from_storage))
        return cursor.use { c ->
            buildList {
                while (c.moveToNext()) {
                    val mime = c.getString(2) ?: ""
                    add(FileEntry(tree, c.getString(0), folderId, c.getString(1) ?: "", mime == Document.MIME_TYPE_DIR,
                        if (c.isNull(3)) 0 else c.getLong(3), if (c.isNull(4)) 0 else c.getLong(4), mime, if (c.isNull(5)) 0 else c.getInt(5)))
                }
            }
        }
    }

    fun createFolder(tree: Uri, folderId: String, name: String) {
        checkName(name)
        if (list(tree, folderId).any { it.name.equals(name, ignoreCase = true) }) throw IOException(str(R.string.files_name_taken, name))
        DocumentsContract.createDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, folderId), Document.MIME_TYPE_DIR, name)
            ?: throw IOException(str(R.string.files_couldnt_create, name))
    }

    fun rename(entry: FileEntry, name: String) {
        checkName(name)
        if (name == entry.name) return
        if (list(entry.tree, entry.parentId).any { it.id != entry.id && it.name.equals(name, ignoreCase = true) }) throw IOException(str(R.string.files_name_taken, name))
        DocumentsContract.renameDocument(resolver, entry.uri, name) ?: throw IOException(str(R.string.files_couldnt_rename, entry.name))
    }

    /** Deletes for good; folders with everything in them. */
    fun delete(entry: FileEntry) {
        if (!DocumentsContract.deleteDocument(resolver, entry.uri)) throw IOException(str(R.string.files_couldnt_delete, entry.name))
    }

    /** Bytes in [entries], folders counted with everything in them. */
    fun totalSize(entries: List<FileEntry>): Long = entries.sumOf { if (it.isFolder) totalSize(list(it.tree, it.id)) else it.size }

    /**
     * Copies (or moves, when [move]) [entries] into a folder. A name already there gets " (2)" and so on, so nothing
     * is overwritten. Returns a line for each item that failed; the rest are done.
     */
    fun transfer(
        entries: List<FileEntry>, tree: Uri, folderId: String, move: Boolean,
        onProgress: (done: Long, total: Long, name: String) -> Unit, isCancelled: () -> Boolean,
    ): List<String> {
        val total = totalSize(entries)
        var done = 0L
        val problems = mutableListOf<String>()
        val target = DocumentsContract.buildDocumentUriUsingTree(tree, folderId)

        fun copy(entry: FileEntry, intoTree: Uri, intoId: String) {
            if (isCancelled()) throw CancelledException()
            val name = freeName(intoTree, intoId, entry.name)
            val into = DocumentsContract.buildDocumentUriUsingTree(intoTree, intoId)
            if (entry.isFolder) {
                val made = DocumentsContract.createDocument(resolver, into, Document.MIME_TYPE_DIR, name) ?: throw IOException(str(R.string.files_couldnt_create, name))
                val madeId = DocumentsContract.getDocumentId(made)
                list(entry.tree, entry.id).forEach { copy(it, intoTree, madeId) }
                return
            }
            onProgress(done, total, entry.name)
            val made = DocumentsContract.createDocument(resolver, into, entry.mime.ifEmpty { "application/octet-stream" }, name)
                ?: throw IOException(str(R.string.files_couldnt_create, name))
            try {
                (resolver.openInputStream(entry.uri) ?: throw IOException(str(R.string.files_couldnt_open, entry.name))).use { input ->
                    (resolver.openOutputStream(made, "w") ?: throw IOException(str(R.string.files_couldnt_create, name))).use { output ->
                        val buffer = ByteArray(1 shl 20)
                        var lastReport = 0L
                        while (true) {
                            if (isCancelled()) throw CancelledException()
                            val n = input.read(buffer)
                            if (n < 0) break
                            output.write(buffer, 0, n)
                            done += n
                            if (done - lastReport >= 4 shl 20) { lastReport = done; onProgress(done, total, entry.name) }
                        }
                    }
                }
            } catch (e: Exception) {
                // Don't leave half a file behind.
                runCatching { DocumentsContract.deleteDocument(resolver, made) }
                throw e
            }
        }

        for (entry in entries) {
            if (entry.isFolder && entry.tree.authority == tree.authority && (folderId == entry.id || folderId.startsWith(entry.id + "/"))) {
                problems += str(R.string.files_into_itself, entry.name)
                continue
            }
            if (move && entry.parentId == folderId && entry.tree.authority == tree.authority) continue
            try {
                // Moving within one storage is instant when the storage can do it; otherwise copy, then delete.
                val moved = move && entry.tree.authority == tree.authority && entry.flags and Document.FLAG_SUPPORTS_MOVE != 0 &&
                    list(tree, folderId).none { it.name.equals(entry.name, ignoreCase = true) } &&
                    runCatching {
                        DocumentsContract.moveDocument(resolver, entry.uri, DocumentsContract.buildDocumentUriUsingTree(entry.tree, entry.parentId), target)
                    }.getOrNull() != null
                if (moved) {
                    done += if (entry.isFolder) 0 else entry.size
                    onProgress(done, total, entry.name)
                    continue
                }
                copy(entry, tree, folderId)
                if (move) delete(entry)
            } catch (e: CancelledException) {
                throw e
            } catch (e: Exception) {
                problems += "${entry.name}: ${e.message ?: e.javaClass.simpleName}"
            }
        }
        return problems
    }

    private fun freeName(tree: Uri, folderId: String, name: String): String {
        val taken = list(tree, folderId).map { it.name.lowercase() }.toSet()
        if (name.lowercase() !in taken) return name
        val dot = name.lastIndexOf('.').takeIf { it > 0 } ?: name.length
        val (stem, ext) = name.substring(0, dot) to name.substring(dot)
        return generateSequence(2) { it + 1 }.map { "$stem ($it)$ext" }.first { it.lowercase() !in taken }
    }

    private fun checkName(name: String) {
        if (name.isBlank() || name == "." || name == ".." || name.any { it == '/' || it == '\u0000' }) throw IOException(str(R.string.files_bad_name))
    }

    class CancelledException : Exception()
}
