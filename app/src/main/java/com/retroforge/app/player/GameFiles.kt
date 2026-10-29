package com.retroforge.app.player

import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import java.io.File
import java.io.IOException

/**
 * Gives a core that opens games by path the game's files: each file (a .cue and its tracks, the discs of an .m3u, or
 * a single .chd/.pbp/.nds …) either opened where it is, for cores that read through the player's VFS ([open]:
 * streaming, nothing copied), or copied from its folder into [dir] under its own name ([expose]), so names inside cue
 * sheets and playlists still resolve. (A plain link to the open file isn't possible: Android re-checks folder access
 * when a core opens a path itself.) Files already copied with the same size aren't copied again.
 */
class GameFiles(private val resolver: ContentResolver, private val dir: File) {

    /**
     * The game's files opened where they are, for streaming (no copy): each name with its open file. The caller
     * closes them.
     */
    fun open(game: Uri, fileName: String, optional: List<String> = emptyList(), fallback: File? = null): List<Pair<String, android.os.ParcelFileDescriptor>> {
        val opened = mutableListOf<Pair<String, android.os.ParcelFileDescriptor>>()
        try {
            sources(game, fileName, optional, fallback).forEach { (name, uri) ->
                opened += name to (resolver.openFileDescriptor(uri, "r") ?: throw IOException("couldn't open $name"))
            }
        } catch (e: Exception) {
            opened.forEach { it.second.close() }
            throw e
        }
        return opened
    }

    /** Copies what's missing; returns the main file's path. [onProgress] gets 0..100 while copying. */
    fun expose(game: Uri, fileName: String, optional: List<String> = emptyList(), fallback: File? = null, onProgress: (Int) -> Unit = {}): String {
        dir.mkdirs()
        val files = sources(game, fileName, optional, fallback)
        val sizes = files.map { (_, uri) -> size(uri) }
        val total = sizes.sum().coerceAtLeast(1)
        var done = 0L
        files.forEachIndexed { i, (name, uri) ->
            val target = File(dir, name)
            if (target.isFile && target.length() == sizes[i] && sizes[i] > 0) {
                done += sizes[i]
                return@forEachIndexed
            }
            val part = File(dir, "$name.part")
            resolver.openInputStream(uri)?.use { input ->
                part.outputStream().use { output ->
                    val buffer = ByteArray(1 shl 20)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        done += n
                        onProgress((done * 100 / total).toInt())
                    }
                }
            } ?: throw IOException("couldn't open $name")
            if (!part.renameTo(target)) throw IOException("couldn't save $name")
        }
        dir.setLastModified(System.currentTimeMillis())
        return File(dir, fileName).path
    }

    /** The game's files: the main one, what its .cue or .m3u names, and optional companions (from [fallback] if not beside it). */
    private fun sources(game: Uri, fileName: String, optional: List<String>, fallback: File?): List<Pair<String, Uri>> {
        val wanted = linkedSetOf(fileName)
        val queue = ArrayDeque(listOf(fileName))
        val files = mutableListOf<Pair<String, Uri>>()
        while (queue.isNotEmpty()) {
            val name = queue.removeFirst()
            val uri = sibling(game, fileName, name)
            files += name to uri
            // Cue sheets and playlists name further files in the same folder.
            when (name.substringAfterLast('.').lowercase()) {
                "cue" -> cueFiles(readText(uri))
                "m3u" -> readText(uri).lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
                else -> emptyList()
            }.filter { it !in wanted && '/' !in it && '\\' !in it && it != ".." }.forEach {
                wanted += it
                queue += it
            }
        }
        // Optional companions (e.g. neogeo.zip) come along when they exist next to the game, else from [fallback]
        // (the player's BIOS folder: RetroBat keeps stvbios.zip with its BIOS files).
        optional.filter { it != fileName && it !in wanted }.forEach { name ->
            val uri = sibling(game, fileName, name)
            if (size(uri) > 0) files += name to uri
            else fallback?.let { File(it, name) }?.takeIf { it.isFile }?.let { files += name to Uri.fromFile(it) }
        }
        return files
    }

    private fun readText(uri: Uri) = resolver.openInputStream(uri)?.use { it.readBytes().take(256 * 1024).toByteArray().decodeToString() }
        ?: throw IOException("couldn't open the game")

    private fun size(uri: Uri): Long = runCatching { resolver.openFileDescriptor(uri, "r")?.use { it.statSize } }.getOrNull() ?: 0L

    /** The document next to [game] called [name] (same folder), for providers whose document ids are paths. */
    private fun sibling(game: Uri, gameName: String, name: String): Uri {
        if (name == gameName) return game
        val id = DocumentsContract.getDocumentId(game)
        val parent = id.substringBeforeLast('/', missingDelimiterValue = id.substringBeforeLast(':') + ":")
        val siblingId = if (parent.endsWith(":")) parent + name else "$parent/$name"
        return DocumentsContract.buildDocumentUriUsingTree(game, siblingId)
    }

    companion object {
        /** File names a cue sheet refers to: `FILE "Track 01.bin" BINARY`. */
        fun cueFiles(cue: String): List<String> =
            Regex("""(?im)^\s*FILE\s+(?:"([^"]+)"|(\S+))""").findAll(cue).map { it.groupValues[1].ifEmpty { it.groupValues[2] } }.toList()

        /** Keeps the [keep] most recently played games' copies in [root] and deletes the rest. */
        fun prune(root: File, keep: Int, current: File) {
            root.listFiles { f -> f.isDirectory && f != current }.orEmpty()
                .sortedByDescending { it.lastModified() }
                .drop(keep - 1)
                .forEach { it.deleteRecursively() }
        }
    }
}
