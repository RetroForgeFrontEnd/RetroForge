package com.retroforge.app.player

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import com.retroforge.app.network.NetworkDrives
import com.retroforge.app.network.NetworkShare
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** A file in the sync folder: its size and when it last changed there. */
data class SyncedFile(val size: Long, val modified: Long)

/**
 * Where saves are synced: a folder on a network drive (a PC or NAS), or an Android folder: on the phone or SD card, or
 * one a cloud app keeps in step with the cloud (Syncthing, FolderSync, Autosync, Nextcloud, OneDrive …). Paths are
 * relative to the folder, with "/" between parts.
 */
interface SyncTarget {
    /** Every file in the folder, by path. */
    fun list(): Map<String, SyncedFile>
    fun read(path: String): ByteArray
    /** Writes the file (making its folders); returns its modified time in the folder afterwards. */
    fun write(path: String, bytes: ByteArray): Long
}

/** A folder on a network drive; [folder] is inside the share ("" for its top). */
class NetworkTarget(private val share: NetworkShare, private val folder: String) : SyncTarget {
    private fun full(path: String) = listOf(folder, path).filter { it.isNotEmpty() }.joinToString("/")
    override fun list() = NetworkDrives.listAll(share, folder).associate { it.path.removePrefix(folder).trimStart('/') to SyncedFile(it.size, it.modified) }
    override fun read(path: String) = NetworkDrives.readBytes(share, full(path))
    override fun write(path: String, bytes: ByteArray) = NetworkDrives.writeBytes(share, full(path), bytes)
}

/** An Android folder the user picked (a tree URI with lasting read and write permission). */
class FolderTarget(private val resolver: ContentResolver, private val tree: Uri) : SyncTarget {
    private data class Child(val id: String, val name: String, val folder: Boolean, val size: Long, val modified: Long)

    private val rootId = DocumentsContract.getTreeDocumentId(tree)
    /** Document ids by path, found while listing or making folders, so they aren't searched for again. */
    private val ids = mutableMapOf("" to rootId)

    private fun children(parentId: String): List<Child> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val columns = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED)
        val out = mutableListOf<Child>()
        resolver.query(uri, columns, null, null, null)?.use { c ->
            while (c.moveToNext()) out += Child(c.getString(0), c.getString(1), c.getString(2) == Document.MIME_TYPE_DIR, c.getLong(3), c.getLong(4))
        }
        return out
    }

    override fun list(): Map<String, SyncedFile> {
        val found = mutableMapOf<String, SyncedFile>()
        fun walk(id: String, prefix: String) {
            for (child in children(id)) {
                val path = if (prefix.isEmpty()) child.name else "$prefix/${child.name}"
                ids[path] = child.id
                if (child.folder) walk(child.id, path) else found[path] = SyncedFile(child.size, child.modified)
            }
        }
        walk(rootId, "")
        return found
    }

    /** The document at [path], or null; with [make], missing folders on the way (and [path] itself, as a folder) are made. */
    private fun find(path: String, make: Boolean): String? {
        if (path.isEmpty()) return rootId
        ids[path]?.let { return it }
        val parentId = find(path.substringBeforeLast('/', ""), make) ?: return null
        val name = path.substringAfterLast('/')
        children(parentId).firstOrNull { it.name == name }?.let { ids[path] = it.id; return it.id }
        if (!make) return null
        return DocumentsContract.createDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, parentId), Document.MIME_TYPE_DIR, name)
            ?.let { DocumentsContract.getDocumentId(it) }?.also { ids[path] = it } ?: throw IOException("couldn't make the folder $name")
    }

    override fun read(path: String): ByteArray {
        val id = find(path, make = false) ?: throw FileNotFoundException(path)
        return resolver.openInputStream(DocumentsContract.buildDocumentUriUsingTree(tree, id))?.use { it.readBytes() } ?: throw FileNotFoundException(path)
    }

    override fun write(path: String, bytes: ByteArray): Long {
        val id = find(path, make = false) ?: run {
            val parentId = find(path.substringBeforeLast('/', ""), make = true)!!
            DocumentsContract.createDocument(
                resolver, DocumentsContract.buildDocumentUriUsingTree(tree, parentId), "application/octet-stream", path.substringAfterLast('/'),
            )?.let { DocumentsContract.getDocumentId(it) }?.also { ids[path] = it } ?: throw IOException("couldn't make $path")
        }
        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
        resolver.openOutputStream(uri, "wt")?.use { it.write(bytes) } ?: throw IOException("couldn't write $path")
        return resolver.query(uri, arrayOf(Document.COLUMN_LAST_MODIFIED), null, null, null)?.use { if (it.moveToFirst()) it.getLong(0) else 0L } ?: 0L
    }
}

/**
 * Two-way sync of the built-in player's saves (and, if chosen, save states) with a [SyncTarget]. A file changed on
 * one side only is copied to the other; changed on both since the last sync, the newer wins and the other is kept in
 * [conflicts]. Nothing is ever deleted. [manifest] remembers each file's times at the last sync.
 */
class SaveSync(
    private val folders: Map<String, File>,
    private val target: SyncTarget,
    private val manifest: File,
    private val conflicts: File,
) {
    data class Report(val pulled: Int, val pushed: Int, val conflicts: Int)

    enum class Direction { BOTH, PULL, PUSH }

    private data class Seen(val local: Long, val remote: Long)

    /**
     * Syncs every file whose path starts with one of [only] (e.g. "saves/snes/"), or all of them when it's empty.
     * [onProgress] gets the share done, 0 to 1.
     */
    fun sync(direction: Direction = Direction.BOTH, only: List<String> = emptyList(), onProgress: (Float) -> Unit = {}): Report {
        val seen = load()
        val remote = target.list().filterKeys { wanted(it, only) }
        val local = folders.flatMap { (name, root) ->
            root.walkTopDown().onEnter { it == root || it.name !in SKIP_FOLDERS }
                .filter { it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".tmp") && it.length() <= MAX_BYTES }
                .map { "$name/" + it.relativeTo(root).invariantSeparatorsPath to it }.toList()
        }.toMap().filterKeys { wanted(it, only) }
        var pulled = 0
        var pushed = 0
        var conflicted = 0
        val paths = (local.keys + remote.keys).sorted()
        paths.forEachIndexed { i, path ->
            val file = local[path] ?: fileFor(path)
            val there = remote[path]
            val before = seen[path]
            val here = file != null && file.isFile
            val localChanged = here && (before == null || file!!.lastModified() != before.local)
            val remoteChanged = there != null && (before == null || there.modified != before.remote)
            val action = when {
                file == null -> null
                there == null -> if (here) Direction.PUSH else null
                !here -> Direction.PULL
                localChanged && remoteChanged -> {
                    if (before == null && file.length() == there.size && target.read(path).contentEquals(file.readBytes())) {
                        // The same file on both sides (a first sync, say): only remembered.
                        seen[path] = Seen(file.lastModified(), there.modified)
                        null
                    } else {
                        conflicted++
                        if (file.lastModified() >= there.modified) Direction.PUSH else Direction.PULL
                    }
                }
                localChanged -> Direction.PUSH
                remoteChanged -> Direction.PULL
                else -> null
            }
            if (action == Direction.PUSH && direction != Direction.PULL) {
                if (remoteChanged) keep(path, target.read(path), "the other copy")
                val modified = target.write(path, file!!.readBytes())
                seen[path] = Seen(file.lastModified(), modified)
                pushed++
            } else if (action == Direction.PULL && direction != Direction.PUSH) {
                if (localChanged) keep(path, file!!.readBytes(), "this device")
                val bytes = target.read(path)
                file!!.parentFile?.mkdirs()
                val part = File(file.path + ".part")
                part.writeBytes(bytes)
                if (!part.renameTo(file)) {
                    file.delete()
                    if (!part.renameTo(file)) throw IOException("couldn't save ${file.name}")
                }
                file.setLastModified(there!!.modified)
                seen[path] = Seen(file.lastModified(), there.modified)
                pulled++
            }
            onProgress((i + 1f) / paths.size)
        }
        store(seen)
        return Report(pulled, pushed, conflicted)
    }

    private fun wanted(path: String, only: List<String>) =
        folders.keys.any { path.startsWith("$it/") } && (only.isEmpty() || only.any { path.startsWith(it) }) &&
            path.split('/').none { it in SKIP_FOLDERS }

    /** Where a synced path lives on this device (null if it would fall outside the save folders). */
    private fun fileFor(path: String): File? = folders[path.substringBefore('/')]?.let { root ->
        File(root, path.substringAfter('/')).takeIf { it.canonicalPath.startsWith(root.canonicalPath + File.separator) }
    }

    /** The version that lost a conflict, kept in case it was the one wanted. */
    private fun keep(path: String, bytes: ByteArray, whose: String) {
        val stamp = SimpleDateFormat("yyyy-MM-dd HH-mm", Locale.US).format(Date())
        File(conflicts, "$stamp ($whose)/$path").apply { parentFile?.mkdirs() }.writeBytes(bytes)
    }

    /** The manifest: one line per file, "local time, tab, sync folder's time, tab, path". */
    private fun load(): MutableMap<String, Seen> = runCatching {
        manifest.readLines().mapNotNull { line ->
            val parts = line.split('\t', limit = 3)
            if (parts.size == 3) parts[2] to Seen(parts[0].toLong(), parts[1].toLong()) else null
        }.toMap().toMutableMap()
    }.getOrElse { mutableMapOf() }

    private fun store(seen: Map<String, Seen>) {
        manifest.parentFile?.mkdirs()
        manifest.writeText(seen.entries.joinToString("\n") { (path, v) -> "${v.local}\t${v.remote}\t$path" })
    }

    companion object {
        /** Emulators' working folders inside saves (unpacked games, texture packs): never synced. */
        val SKIP_FOLDERS = setOf("TEMP", "TEXTURES", "cache", "Cache")
        private const val MAX_BYTES = 64L * 1024 * 1024

        /** Settings: on or off, where to sync, and whether save states go too. */
        fun settings(context: Context) = context.getSharedPreferences("save_sync", Context.MODE_PRIVATE)

        /** The chosen sync folder, or null when save syncing is off. */
        fun target(context: Context): SyncTarget? {
            val prefs = settings(context)
            if (!prefs.getBoolean("on", false)) return null
            return when (prefs.getString("kind", null)) {
                "network" -> NetworkDrives.byId(context, prefs.getString("share", null) ?: return null)
                    ?.let { NetworkTarget(it, prefs.getString("folder", "").orEmpty()) }
                "folder" -> prefs.getString("uri", null)?.let { FolderTarget(context.contentResolver, Uri.parse(it)) }
                else -> null
            }
        }

        /** The player's save folders by their name in the sync folder. */
        fun folders(context: Context): Map<String, File> {
            val base = context.getExternalFilesDir(null) ?: context.filesDir
            return buildMap {
                put("saves", File(base, "saves"))
                if (settings(context).getBoolean("states", true)) put("states", File(base, "states"))
            }
        }

        /** This device's save sync, or null when it's off. */
        fun of(context: Context): SaveSync? = target(context)?.let {
            val base = context.getExternalFilesDir(null) ?: context.filesDir
            SaveSync(folders(context), it, File(context.filesDir, "save_sync_manifest.txt"), File(base, "sync conflicts"))
        }
    }
}
