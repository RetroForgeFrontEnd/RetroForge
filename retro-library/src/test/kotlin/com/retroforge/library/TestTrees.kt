package com.retroforge.library

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream

/** In-memory folder tree. Files are empty unless given content; nothing here needs real ROMs. */
class MemNode(
    override val name: String,
    private val content: String? = null,
    private val kids: List<MemNode>? = null,
    private val denied: Boolean = false,
    var reads: Int = 0,
    private val bytes: ByteArray? = null,
) : ScanNode {
    override val id: String get() = "mem:$name@${System.identityHashCode(this)}"
    override val isDirectory: Boolean get() = kids != null
    override val size: Long get() = bytes?.size?.toLong() ?: content?.length?.toLong() ?: 1024
    override val lastModified: Long get() = 0
    override fun children(): List<ScanNode> = if (denied) throw IOException("permission denied") else kids.orEmpty()
    override fun open(): InputStream { reads++; return ByteArrayInputStream(bytes ?: (content ?: "").toByteArray()) }
}

fun dir(name: String, vararg kids: MemNode) = MemNode(name, kids = kids.toList())
fun file(name: String, content: String? = null) = MemNode(name, content = content)
fun binary(name: String, bytes: ByteArray) = MemNode(name, bytes = bytes)
