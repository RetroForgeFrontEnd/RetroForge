package com.retroforge.patch

import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

data class NamedBytes(val name: String, val bytes: ByteArray)

sealed interface ArchiveResult {
    data class Ok(val file: NamedBytes) : ArchiveResult
    data class NothingSuitable(val archiveName: String) : ArchiveResult
    data class Ambiguous(val archiveName: String, val candidates: List<String>) : ArchiveResult
    data class Unreadable(val archiveName: String, val detail: String) : ArchiveResult
    data class TooLarge(val name: String) : ArchiveResult
}

/**
 * Unwraps a ROM or patch the user picked. Zip and 7z archives must contain exactly one suitable file;
 * entries are read into memory with a size cap so a malformed or hostile archive cannot exhaust memory.
 * Nothing is extracted to disk and nothing inside an archive is executed.
 */
object ArchiveInput {

    private val patchExtensions = PatchFormat.entries.map { it.extension }.toSet()
    private val ignoredExtensions = setOf("txt", "nfo", "diz", "md", "htm", "html", "pdf", "jpg", "png", "gif", "url", "xml", "doc")

    fun readRom(name: String, bytes: ByteArray): ArchiveResult =
        unwrap(name, bytes) { entry -> entry.substringAfterLast('.', "").lowercase() !in ignoredExtensions + patchExtensions }

    fun readPatch(name: String, bytes: ByteArray): ArchiveResult =
        unwrap(name, bytes) { entry -> entry.substringAfterLast('.', "").lowercase() in patchExtensions }

    fun readLimited(input: InputStream, name: String, limit: Long = MAX_OUTPUT_BYTES): ByteArray? {
        val buffer = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val read = input.read(chunk)
            if (read < 0) return buffer.toByteArray()
            total += read
            if (total > limit) return null
            buffer.write(chunk, 0, read)
        }
    }

    private fun unwrap(name: String, bytes: ByteArray, wanted: (String) -> Boolean): ArchiveResult {
        val entries = try {
            when (name.substringAfterLast('.', "").lowercase()) {
                "zip" -> zipEntries(bytes, wanted)
                "7z" -> sevenZipEntries(bytes, wanted)
                else -> return ArchiveResult.Ok(NamedBytes(name, bytes))
            }
        } catch (_: EntryTooLarge) {
            return ArchiveResult.TooLarge(name)
        } catch (e: Exception) {
            return ArchiveResult.Unreadable(name, e.message ?: e.javaClass.simpleName)
        }
        return when (entries.size) {
            0 -> ArchiveResult.NothingSuitable(name)
            1 -> ArchiveResult.Ok(entries.single())
            else -> ArchiveResult.Ambiguous(name, entries.map { it.name })
        }
    }

    private class EntryTooLarge : Exception()

    private fun zipEntries(bytes: ByteArray, wanted: (String) -> Boolean): List<NamedBytes> {
        val result = mutableListOf<NamedBytes>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val entryName = entry.name.substringAfterLast('/')
                if (entry.isDirectory || !wanted(entryName)) continue
                result += NamedBytes(entryName, readLimited(zip, entryName) ?: throw EntryTooLarge())
            }
        }
        return result
    }

    private fun sevenZipEntries(bytes: ByteArray, wanted: (String) -> Boolean): List<NamedBytes> {
        val result = mutableListOf<NamedBytes>()
        SevenZFile.builder().setSeekableByteChannel(SeekableInMemoryByteChannel(bytes)).get().use { archive ->
            for (entry in archive.entries) {
                val entryName = entry.name.substringAfterLast('/')
                if (entry.isDirectory || !wanted(entryName)) continue
                if (entry.size > MAX_OUTPUT_BYTES) throw EntryTooLarge()
                result += NamedBytes(entryName, readLimited(archive.getInputStream(entry), entryName) ?: throw EntryTooLarge())
            }
        }
        return result
    }
}
