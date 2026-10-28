package com.retroforge.library

import java.io.File
import java.io.IOException
import java.io.InputStream

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
