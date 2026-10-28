package com.retroforge.app.player

import android.content.ContentResolver
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.system.Os
import java.io.Closeable
import java.io.File
import java.io.IOException

/**
 * Hands a core that wants file paths the game's files without copying them: each file (a .cue and its tracks, the
 * discs of an .m3u, or a single .chd/.pbp/.nds …) is opened from its folder and linked into [dir] under its own name,
 * pointing at the open file (/proc/self/fd/N), so names inside cue sheets and playlists still resolve. Falls back to
 * copying a file only where a link can't be made. Keep this open while the game runs.
 */
class GameFiles(private val resolver: ContentResolver, private val dir: File) : Closeable {

    private val open = mutableListOf<ParcelFileDescriptor>()

    /** Makes the game (and the files it refers to) available in [dir]; returns the main file's path there. */
    fun expose(game: Uri, fileName: String): String {
        val wanted = linkedSetOf(fileName)
        val queue = ArrayDeque(listOf(fileName))
        while (queue.isNotEmpty()) {
            val name = queue.removeFirst()
            val local = place(sibling(game, fileName, name), name)
            // Cue sheets and playlists name further files in the same folder.
            when (name.substringAfterLast('.').lowercase()) {
                "cue" -> cueFiles(local.readText())
                "m3u" -> local.readLines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
                else -> emptyList()
            }.filter { it !in wanted && '/' !in it && '\\' !in it && it != ".." }.forEach {
                wanted += it
                queue += it
            }
        }
        return File(dir, fileName).path
    }

    /** The document next to [game] called [name] (same folder), for providers whose document ids are paths. */
    private fun sibling(game: Uri, gameName: String, name: String): Uri {
        if (name == gameName) return game
        val id = DocumentsContract.getDocumentId(game)
        val parent = id.substringBeforeLast('/', missingDelimiterValue = id.substringBeforeLast(':') + ":")
        val siblingId = if (parent.endsWith(":")) parent + name else "$parent/$name"
        return DocumentsContract.buildDocumentUriUsingTree(game, siblingId)
    }

    private fun place(uri: Uri, name: String): File {
        val target = File(dir, name)
        val pfd = resolver.openFileDescriptor(uri, "r") ?: throw IOException("couldn't open $name")
        try {
            Os.symlink("/proc/self/fd/${pfd.fd}", target.path)
            open += pfd
        } catch (_: Exception) {
            // No link possible here: copy this one file instead.
            pfd.use { ParcelFileDescriptor.AutoCloseInputStream(it).use { input -> target.outputStream().use { out -> input.copyTo(out) } } }
        }
        return target
    }

    override fun close() {
        open.forEach { runCatching { it.close() } }
        open.clear()
    }

    companion object {
        /** File names a cue sheet refers to: `FILE "Track 01.bin" BINARY`. */
        fun cueFiles(cue: String): List<String> =
            Regex("""(?im)^\s*FILE\s+(?:"([^"]+)"|(\S+))""").findAll(cue).map { it.groupValues[1].ifEmpty { it.groupValues[2] } }.toList()
    }
}
