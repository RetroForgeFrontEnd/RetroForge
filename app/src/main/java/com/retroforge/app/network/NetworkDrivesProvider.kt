package com.retroforge.app.network

import com.retroforge.core.ui.str
import android.database.Cursor
import android.database.MatrixCursor
import android.os.CancellationSignal
import android.os.Handler
import android.os.HandlerThread
import android.os.ParcelFileDescriptor
import android.os.ProxyFileDescriptorCallback
import android.os.storage.StorageManager
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import android.system.ErrnoException
import android.system.OsConstants
import android.webkit.MimeTypeMap
import com.retroforge.app.R
import java.io.FileNotFoundException

/**
 * Shows RetroForge's network drives in Android's folder picker, read-only. Document ids are "<share id>/<path>", the
 * path from the share's top with "/" between folders ("<share id>/" is the top). Files are read on demand over SMB
 * through a seekable file descriptor, so emulators and the film player can jump about in them.
 */
class NetworkDrivesProvider : DocumentsProvider() {

    private val reader by lazy { HandlerThread("network-drives").apply { start() } }

    override fun onCreate(): Boolean {
        // Lets NetworkDrives save a share's new address when its PC moves (see NetworkDrives.connect).
        NetworkDrives.appContext = context?.applicationContext
        return true
    }

    private fun share(documentId: String) =
        NetworkDrives.byId(context!!, documentId.substringBefore('/')) ?: throw FileNotFoundException(str(R.string.network_drives_provider_that_network_drive_was_removed))

    private fun path(documentId: String) = documentId.substringAfter('/', "")

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val cursor = MatrixCursor(projection ?: ROOT_COLUMNS)
        NetworkDrives.all(context!!).forEach { share ->
            cursor.newRow()
                .add(Root.COLUMN_ROOT_ID, share.id)
                .add(Root.COLUMN_DOCUMENT_ID, "${share.id}/")
                .add(Root.COLUMN_TITLE, share.name)
                .add(Root.COLUMN_SUMMARY, share.address)
                .add(Root.COLUMN_FLAGS, Root.FLAG_SUPPORTS_IS_CHILD)
                .add(Root.COLUMN_ICON, R.mipmap.ic_launcher)
        }
        cursor.setNotificationUri(context!!.contentResolver, android.provider.DocumentsContract.buildRootsUri(NetworkDrives.authority(context!!)))
        return cursor
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        val cursor = MatrixCursor(projection ?: DOCUMENT_COLUMNS)
        val share = share(documentId)
        val entry = runCatching { onNetwork { NetworkDrives.stat(share, path(documentId)) } }.getOrElse { throw notFound(it) }
        addRow(cursor, share.id, entry)
        return cursor
    }

    override fun queryChildDocuments(parentDocumentId: String, projection: Array<out String>?, sortOrder: String?): Cursor {
        val cursor = MatrixCursor(projection ?: DOCUMENT_COLUMNS)
        val share = share(parentDocumentId)
        val entries = runCatching { onNetwork { NetworkDrives.list(share, path(parentDocumentId)) } }
            .getOrElse { throw FileNotFoundException(str(R.string.network_drives_provider_couldn_t_open, share.address, it.message)) }
        entries.sortedWith(compareBy({ !it.isFolder }, { it.name.lowercase() })).forEach { addRow(cursor, share.id, it) }
        return cursor
    }

    private fun addRow(cursor: MatrixCursor, shareId: String, entry: NetworkEntry) {
        cursor.newRow()
            .add(Document.COLUMN_DOCUMENT_ID, "$shareId/${entry.path}")
            .add(Document.COLUMN_DISPLAY_NAME, entry.name)
            .add(Document.COLUMN_MIME_TYPE, if (entry.isFolder) Document.MIME_TYPE_DIR else mimeOf(entry.name))
            .add(Document.COLUMN_SIZE, if (entry.isFolder) null else entry.size)
            .add(Document.COLUMN_LAST_MODIFIED, entry.modified)
            .add(Document.COLUMN_FLAGS, 0)
    }

    /**
     * Lets [block] use the network. A request from another app arrives with that app's thread rules: Eden asks from
     * its main thread, which forbids network use, so every look-up failed (NetworkOnMainThreadException) and Eden said
     * "No game found". The answer has to come from the share, so the network is allowed for this call.
     */
    private inline fun <T> onNetwork(block: () -> T): T {
        val rules = android.os.StrictMode.getThreadPolicy()
        android.os.StrictMode.setThreadPolicy(android.os.StrictMode.ThreadPolicy.Builder(rules).permitNetwork().build())
        return try {
            block()
        } finally {
            android.os.StrictMode.setThreadPolicy(rules)
        }
    }

    /** The real reason, kept in the log and the message (an SMB error often has no message of its own). */
    private fun notFound(e: Throwable) = FileNotFoundException(e.message ?: e.javaClass.simpleName).also {
        it.initCause(e)
        android.util.Log.w("NetworkDrives", "Couldn't read from the network drive", e)
    }

    private fun mimeOf(name: String): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase()) ?: "application/octet-stream"

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean =
        documentId.substringBefore('/') == parentDocumentId.substringBefore('/') &&
            (path(parentDocumentId).isEmpty() || path(documentId).startsWith(path(parentDocumentId) + "/"))

    override fun openDocument(documentId: String, mode: String, signal: CancellationSignal?): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException(str(R.string.network_drives_provider_network_drives_are_read_only_in))
        val share = share(documentId)
        val path = path(documentId)
        val size = runCatching { onNetwork { NetworkDrives.stat(share, path).size } }.getOrElse { throw notFound(it) }
        val file = runCatching { onNetwork { NetworkDrives.openRead(share, path) } }.getOrElse { throw notFound(it) }
        val storage = context!!.getSystemService(StorageManager::class.java)
        return storage.openProxyFileDescriptor(ParcelFileDescriptor.MODE_READ_ONLY, object : ProxyFileDescriptorCallback() {
            // Android asks for small pieces (often 128 KB); each is a round trip to the PC. Reading ahead a whole
            // window at once and answering from it makes copying and streaming many times faster.
            private val window = ByteArray(READ_AHEAD)
            private var windowStart = -1L
            private var windowLength = 0

            override fun onGetSize() = size

            override fun onRead(offset: Long, length: Int, data: ByteArray): Int {
                try {
                    var done = 0
                    while (done < length && offset + done < size) {
                        val at = offset + done
                        if (windowStart < 0 || at < windowStart || at >= windowStart + windowLength) fill(at)
                        if (windowLength <= 0) break
                        val from = (at - windowStart).toInt()
                        val n = minOf(length - done, windowLength - from)
                        System.arraycopy(window, from, data, done, n)
                        done += n
                    }
                    return done
                } catch (e: Exception) {
                    throw ErrnoException("read", OsConstants.EIO)
                }
            }

            private fun fill(at: Long) {
                windowStart = at
                windowLength = 0
                while (windowLength < window.size) {
                    val n = file.read(window, at + windowLength, windowLength, window.size - windowLength)
                    if (n <= 0) break
                    windowLength += n
                }
            }

            override fun onRelease() { runCatching { file.close() } }
        }, Handler(reader.looper))
    }

    private companion object {
        const val READ_AHEAD = 1 shl 20

        val ROOT_COLUMNS = arrayOf(Root.COLUMN_ROOT_ID, Root.COLUMN_DOCUMENT_ID, Root.COLUMN_TITLE, Root.COLUMN_SUMMARY, Root.COLUMN_FLAGS, Root.COLUMN_ICON)
        val DOCUMENT_COLUMNS = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED, Document.COLUMN_FLAGS)
    }
}
