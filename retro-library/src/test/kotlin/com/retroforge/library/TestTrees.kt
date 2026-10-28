package com.retroforge.library

import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream

/** In-memory folder tree. Files are empty unless given content; nothing here needs real ROMs. */
class MemNode(
    override val name: String,
    private val content: String? = null,
    private val kids: List<MemNode>? = null,
    private val denied: Boolean = false,
    var reads: Int = 0,
) : ScanNode {
    override val id: String get() = "mem:$name@${System.identityHashCode(this)}"
    override val isDirectory: Boolean get() = kids != null
    override val size: Long get() = content?.length?.toLong() ?: 1024
    override val lastModified: Long get() = 0
    override fun children(): List<ScanNode> = if (denied) throw IOException("permission denied") else kids.orEmpty()
    override fun open(): InputStream { reads++; return ByteArrayInputStream((content ?: "").toByteArray()) }
}

fun dir(name: String, vararg kids: MemNode) = MemNode(name, kids = kids.toList())
fun file(name: String, content: String? = null) = MemNode(name, content = content)

/** Read-only view of a real folder, used for the opt-in run over an actual collection. */
class FileNode(private val file: File) : ScanNode {
    override val id: String get() = file.absolutePath
    override val name: String get() = file.name
    override val isDirectory: Boolean get() = file.isDirectory
    override val size: Long get() = file.length()
    override val lastModified: Long get() = file.lastModified()
    override fun children(): List<ScanNode> = (file.listFiles() ?: throw IOException("can't list ${file.path}")).map(::FileNode)
    override fun open(): InputStream = file.inputStream()
}
