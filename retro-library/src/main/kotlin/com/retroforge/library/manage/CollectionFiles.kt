package com.retroforge.library.manage

import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.nio.channels.SeekableByteChannel

/**
 * Write access to one collection folder, for the ROM manager only. Paths are relative to the folder, "/"-separated.
 * There is deliberately no general delete: files are moved aside, never removed. The only removal is
 * [discardPartial], for an unfinished file this run created itself.
 */
interface CollectionFiles {
    fun exists(path: String): Boolean
    fun size(path: String): Long?
    fun open(path: String): InputStream
    fun makeFolders(path: String)
    /** Moves a file into [folder] keeping its name. Fails if that folder already has a file of that name. */
    fun move(path: String, folder: String)
    /** Fails if a file called [newName] already exists next to it. */
    fun rename(path: String, newName: String)
    /** Creates a new, empty file (fails if it exists) and opens it for writing. */
    fun create(path: String): SeekableByteChannel
    fun openRead(path: String): SeekableByteChannel
    /** Replaces the contents of an existing small text file (gamelist.xml, .m3u). */
    fun replace(path: String, bytes: ByteArray)
    /** Removes a file this run created and didn't finish (a ".retroforge-part" file). */
    fun discardPartial(path: String)
}

const val PARTIAL_SUFFIX = ".retroforge-part"

fun parentOf(path: String): String = path.substringBeforeLast('/', "")
fun nameOf(path: String): String = path.substringAfterLast('/')
fun join(folder: String, name: String): String = if (folder.isEmpty()) name else "$folder/$name"

/** Over java.io.File, for tests and the opt-in runs over scratch copies. */
class JvmCollectionFiles(private val root: File) : CollectionFiles {
    private fun f(path: String) = File(root, path)
    override fun exists(path: String) = f(path).exists()
    override fun size(path: String) = f(path).takeIf { it.isFile }?.length()
    override fun open(path: String): InputStream = f(path).inputStream()
    override fun makeFolders(path: String) { if (!f(path).isDirectory && !f(path).mkdirs()) throw IOException("can't create $path") }
    override fun move(path: String, folder: String) {
        val target = File(f(folder), nameOf(path))
        if (target.exists()) throw IOException("${nameOf(path)} is already in ${folder.ifEmpty { "the folder" }}")
        if (!f(path).renameTo(target)) throw IOException("couldn't move $path")
    }
    override fun rename(path: String, newName: String) {
        val target = File(f(path).parentFile, newName)
        if (target.exists()) throw IOException("$newName already exists")
        if (!f(path).renameTo(target)) throw IOException("couldn't rename $path")
    }
    override fun create(path: String): SeekableByteChannel {
        if (!f(path).createNewFile()) throw IOException("$path already exists")
        return RandomAccessFile(f(path), "rw").channel
    }
    override fun openRead(path: String): SeekableByteChannel = RandomAccessFile(f(path), "r").channel
    override fun replace(path: String, bytes: ByteArray) {
        if (!f(path).isFile) throw IOException("$path doesn't exist")
        f(path).writeBytes(bytes)
    }
    override fun discardPartial(path: String) {
        require(path.endsWith(PARTIAL_SUFFIX))
        f(path).delete()
    }
}
