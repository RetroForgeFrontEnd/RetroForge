package com.retroforge.patch

import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.apache.commons.compress.utils.SeekableInMemoryByteChannel
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipInputStream

data class NamedBytes(val name: String, val bytes: ByteArray)

/** A readme shipped alongside a patch, already decoded for display. */
data class TextFile(val name: String, val text: String)

sealed interface ArchiveResult {
    /** [readmes] is filled only for patch archives. */
    data class Ok(val file: NamedBytes, val readmes: List<TextFile> = emptyList()) : ArchiveResult
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

    private const val MAX_README_BYTES = 64 * 1024
    private const val MAX_READMES = 5

    private val patchExtensions = PatchFormat.entries.map { it.extension }.toSet()
    private val readmeExtensions = setOf("txt", "nfo", "diz", "md")
    private val ignoredExtensions = readmeExtensions + setOf("htm", "html", "pdf", "jpg", "png", "gif", "url", "xml", "doc")

    fun readRom(name: String, bytes: ByteArray): ArchiveResult =
        unwrap(name, bytes, collectReadmes = false) { entry -> extensionOf(entry) !in ignoredExtensions + patchExtensions }

    /** [entryName] picks one patch from an archive that holds several (see [ArchiveResult.Ambiguous]). */
    fun readPatch(name: String, bytes: ByteArray, entryName: String? = null): ArchiveResult =
        unwrap(name, bytes, collectReadmes = true) { entry ->
            extensionOf(entry) in patchExtensions && (entryName == null || entry == entryName)
        }

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

    /** Readmes are often old DOS text; anything that isn't valid UTF-8 is shown as Latin-1 rather than rejected. */
    fun decodeText(bytes: ByteArray): String {
        val text = try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) {
            String(bytes, Charsets.ISO_8859_1)
        }
        return text.removePrefix("﻿")
            .replace("\r\n", "\n")
            .filter { it == '\n' || it == '\t' || !it.isISOControl() }
    }

    private fun extensionOf(name: String) = name.substringAfterLast('.', "").lowercase()

    private class Entries(val matches: List<NamedBytes>, val readmes: List<TextFile>)

    private fun unwrap(name: String, bytes: ByteArray, collectReadmes: Boolean, wanted: (String) -> Boolean): ArchiveResult {
        val entries = try {
            when (extensionOf(name)) {
                "zip" -> zipEntries(bytes, collectReadmes, wanted)
                "7z" -> sevenZipEntries(bytes, collectReadmes, wanted)
                else -> return ArchiveResult.Ok(NamedBytes(name, bytes))
            }
        } catch (_: EntryTooLarge) {
            return ArchiveResult.TooLarge(name)
        } catch (e: Exception) {
            return ArchiveResult.Unreadable(name, e.message ?: e.javaClass.simpleName)
        }
        return when (entries.matches.size) {
            0 -> ArchiveResult.NothingSuitable(name)
            1 -> ArchiveResult.Ok(entries.matches.single(), entries.readmes)
            else -> ArchiveResult.Ambiguous(name, entries.matches.map { it.name })
        }
    }

    private class EntryTooLarge : Exception()

    private class Collector(private val collectReadmes: Boolean, private val wanted: (String) -> Boolean) {
        val matches = mutableListOf<NamedBytes>()
        val readmes = mutableListOf<TextFile>()

        fun offer(path: String, isDirectory: Boolean, read: (Long) -> ByteArray?) {
            val entryName = path.substringAfterLast('/')
            if (isDirectory) return
            when {
                wanted(entryName) -> matches += NamedBytes(entryName, read(MAX_OUTPUT_BYTES) ?: throw EntryTooLarge())
                collectReadmes && extensionOf(entryName) in readmeExtensions && readmes.size < MAX_READMES ->
                    // An oversized readme is skipped rather than failing the whole patch.
                    read(MAX_README_BYTES.toLong())?.let { readmes += TextFile(entryName, decodeText(it)) }
            }
        }

        fun result() = Entries(matches, readmes)
    }

    private fun zipEntries(bytes: ByteArray, collectReadmes: Boolean, wanted: (String) -> Boolean): Entries {
        val collector = Collector(collectReadmes, wanted)
        ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                collector.offer(entry.name, entry.isDirectory) { limit -> readLimited(zip, entry.name, limit) }
            }
        }
        return collector.result()
    }

    private fun sevenZipEntries(bytes: ByteArray, collectReadmes: Boolean, wanted: (String) -> Boolean): Entries {
        val collector = Collector(collectReadmes, wanted)
        SevenZFile.builder().setSeekableByteChannel(SeekableInMemoryByteChannel(bytes)).get().use { archive ->
            for (entry in archive.entries) {
                collector.offer(entry.name, entry.isDirectory) { limit ->
                    if (entry.size > limit) null else readLimited(archive.getInputStream(entry), entry.name, limit)
                }
            }
        }
        return collector.result()
    }
}
