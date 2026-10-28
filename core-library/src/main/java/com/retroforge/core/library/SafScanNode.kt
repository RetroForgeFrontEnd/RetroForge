package com.retroforge.core.library

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import com.retroforge.library.ScanNode
import java.io.IOException
import java.io.InputStream

/**
 * A folder or file inside a tree the user picked with the system folder picker. Uses direct DocumentsContract
 * queries (one per folder) rather than DocumentFile, which is much slower on large collections. Read-only.
 */
class SafScanNode(
    private val resolver: ContentResolver,
    private val treeUri: Uri,
    override val id: String,
    override val name: String,
    override val isDirectory: Boolean,
    override val size: Long,
    override val lastModified: Long,
) : ScanNode {

    override fun children(): List<ScanNode> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, id)
        val cursor = try {
            resolver.query(childrenUri, PROJECTION, null, null, null)
        } catch (e: SecurityException) {
            throw IOException("access was withdrawn", e)
        } catch (e: IllegalArgumentException) {
            throw IOException("folder no longer exists", e)
        } ?: throw IOException("the storage provider returned nothing")
        return cursor.use { c ->
            buildList {
                while (c.moveToNext()) {
                    add(
                        SafScanNode(
                            resolver, treeUri,
                            id = c.getString(0),
                            name = c.getString(1) ?: "",
                            isDirectory = c.getString(2) == Document.MIME_TYPE_DIR,
                            size = if (c.isNull(3)) 0 else c.getLong(3),
                            lastModified = if (c.isNull(4)) 0 else c.getLong(4),
                        ),
                    )
                }
            }
        }
    }

    override fun open(): InputStream = try {
        resolver.openInputStream(DocumentsContract.buildDocumentUriUsingTree(treeUri, id)) ?: throw IOException("can't open $name")
    } catch (e: SecurityException) {
        throw IOException("access was withdrawn", e)
    }

    companion object {
        private val PROJECTION = arrayOf(
            Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED,
        )

        fun root(resolver: ContentResolver, treeUri: Uri, displayName: String): SafScanNode =
            SafScanNode(resolver, treeUri, DocumentsContract.getTreeDocumentId(treeUri), displayName, true, 0, 0)

        fun documentUri(treeUri: String, documentId: String): Uri =
            DocumentsContract.buildDocumentUriUsingTree(Uri.parse(treeUri), documentId)
    }
}
