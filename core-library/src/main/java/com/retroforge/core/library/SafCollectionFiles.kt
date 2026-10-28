package com.retroforge.core.library

import android.content.ContentResolver
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import com.retroforge.library.manage.CollectionFiles
import com.retroforge.library.manage.PARTIAL_SUFFIX
import com.retroforge.library.manage.nameOf
import com.retroforge.library.manage.parentOf
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.nio.channels.SeekableByteChannel

/**
 * [CollectionFiles] over a folder the user granted write access to. Folder listings are cached (a ROM folder can
 * hold thousands of files) and dropped for each folder this changes.
 */
class SafCollectionFiles(private val resolver: ContentResolver, private val treeUri: Uri) : CollectionFiles {

    private data class Child(val id: String, val name: String, val isDirectory: Boolean, val size: Long)

    private val rootId = DocumentsContract.getTreeDocumentId(treeUri)
    private val listings = HashMap<String, List<Child>>()

    private fun uri(id: String) = DocumentsContract.buildDocumentUriUsingTree(treeUri, id)

    private fun children(folderId: String): List<Child> = listings.getOrPut(folderId) {
        val cursor = try {
            resolver.query(
                DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, folderId),
                arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE),
                null, null, null,
            )
        } catch (e: SecurityException) {
            throw IOException("RetroForge no longer has access to this folder", e)
        } ?: throw IOException("the storage provider returned nothing")
        cursor.use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(Child(c.getString(0), c.getString(1) ?: "", c.getString(2) == Document.MIME_TYPE_DIR, if (c.isNull(3)) 0 else c.getLong(3)))
                }
            }
        }
    }

    private fun childNamed(folderId: String, name: String): Child? {
        val list = children(folderId)
        return list.firstOrNull { it.name == name } ?: list.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }

    /** Document id of [path], or null when it doesn't exist. */
    private fun find(path: String): Child? {
        if (path.isEmpty()) return Child(rootId, "", true, 0)
        var folder = rootId
        val parts = path.split('/')
        for ((i, part) in parts.withIndex()) {
            val child = childNamed(folder, part) ?: return null
            if (i == parts.lastIndex) return child
            if (!child.isDirectory) return null
            folder = child.id
        }
        return null
    }

    private fun need(path: String): Child = find(path) ?: throw FileNotFoundException("$path doesn't exist")

    private fun changed(folderId: String) { listings.remove(folderId) }

    override fun exists(path: String) = find(path) != null

    override fun size(path: String): Long? = find(path)?.takeIf { !it.isDirectory }?.size

    override fun open(path: String): InputStream =
        resolver.openInputStream(uri(need(path).id)) ?: throw IOException("can't open $path")

    override fun makeFolders(path: String) {
        if (path.isEmpty()) return
        var folder = rootId
        for (part in path.split('/')) {
            val existing = childNamed(folder, part)
            folder = when {
                existing == null -> {
                    val created = DocumentsContract.createDocument(resolver, uri(folder), Document.MIME_TYPE_DIR, part)
                        ?: throw IOException("couldn't create folder $part")
                    changed(folder)
                    DocumentsContract.getDocumentId(created)
                }
                existing.isDirectory -> existing.id
                else -> throw IOException("$part is a file, not a folder")
            }
        }
    }

    override fun move(path: String, folder: String) {
        val source = need(path)
        val sourceParent = need(parentOf(path))
        val target = need(folder)
        if (childNamed(target.id, source.name) != null) throw IOException("${source.name} is already in ${folder.ifEmpty { "the folder" }}")
        val moved = try {
            DocumentsContract.moveDocument(resolver, uri(source.id), uri(sourceParent.id), uri(target.id))
        } catch (e: UnsupportedOperationException) {
            throw IOException("this storage can't move files", e)
        } catch (e: IllegalStateException) {
            throw IOException("this storage can't move files", e)
        }
        changed(sourceParent.id)
        changed(target.id)
        if (moved == null) throw IOException("couldn't move $path")
    }

    override fun rename(path: String, newName: String) {
        val source = need(path)
        val parent = need(parentOf(path))
        if (childNamed(parent.id, newName) != null) throw IOException("$newName already exists")
        val renamed = DocumentsContract.renameDocument(resolver, uri(source.id), newName) ?: throw IOException("couldn't rename $path")
        changed(parent.id)
        // Some providers adjust names (e.g. add an extension); a CHD or zip with the wrong name would be lost.
        if (childNamed(parent.id, newName) == null) throw IOException("the storage renamed $path to something else ($renamed)")
    }

    override fun create(path: String): SeekableByteChannel {
        val parent = need(parentOf(path))
        val name = nameOf(path)
        if (childNamed(parent.id, name) != null) throw IOException("$path already exists")
        val created = DocumentsContract.createDocument(resolver, uri(parent.id), "application/octet-stream", name)
            ?: throw IOException("couldn't create $path")
        changed(parent.id)
        if (childNamed(parent.id, name)?.id != DocumentsContract.getDocumentId(created)) throw IOException("the storage created $path under another name")
        val pfd = resolver.openFileDescriptor(created, "rw") ?: throw IOException("couldn't open $path for writing")
        return ParcelFileDescriptor.AutoCloseOutputStream(pfd).channel
    }

    override fun openRead(path: String): SeekableByteChannel {
        val pfd = resolver.openFileDescriptor(uri(need(path).id), "r") ?: throw IOException("can't open $path")
        return ParcelFileDescriptor.AutoCloseInputStream(pfd).channel
    }

    override fun replace(path: String, bytes: ByteArray) {
        val file = need(path)
        (resolver.openOutputStream(uri(file.id), "wt") ?: throw IOException("can't write $path")).use { it.write(bytes) }
        changed(need(parentOf(path)).id)
    }

    override fun discardPartial(path: String) {
        require(path.endsWith(PARTIAL_SUFFIX)) { "only unfinished files can be removed" }
        val file = find(path) ?: return
        DocumentsContract.deleteDocument(resolver, uri(file.id))
        find(parentOf(path))?.let { changed(it.id) }
    }
}
