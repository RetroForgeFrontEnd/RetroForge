package com.retroforge.core.library

import com.retroforge.core.ui.str
import android.content.ContentResolver
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import com.retroforge.library.BiosReport
import com.retroforge.library.FoundBios

data class CopyResult(val copied: List<String>, val alreadyThere: List<String>, val failed: List<Pair<String, String>>)

/**
 * Copies the user's own BIOS files from their BIOS folder into the folder RetroArch reads them from, both picked
 * by the user. Uses the names the cores expect (e.g. "coleco.rom" → "colecovision.rom"). Never overwrites, never
 * deletes, never downloads.
 */
class BiosInstaller(private val resolver: ContentResolver) {

    /** Verified files, plus an unverified one only where nothing verified covers the same need. */
    fun toCopy(reports: List<BiosReport>): List<FoundBios> = reports.flatMap { report ->
        if (report.rule.mode == "any" && report.found.any { it.verified }) report.found.filter { it.verified } else report.found
    }.distinctBy { it.wanted.name }

    fun copy(sourceTree: Uri, files: List<FoundBios>, targetTree: Uri): CopyResult {
        val copied = mutableListOf<String>()
        val existing = mutableListOf<String>()
        val failed = mutableListOf<Pair<String, String>>()
        val rootId = DocumentsContract.getTreeDocumentId(targetTree)
        for (file in files) {
            val target = file.wanted.name
            try {
                val parentId = target.substringBeforeLast('/', "").split('/').filter { it.isNotEmpty() }
                    .fold(rootId) { parent, dir -> childId(targetTree, parent, dir) ?: createDir(targetTree, parent, dir) }
                val name = target.substringAfterLast('/')
                if (childId(targetTree, parentId, name) != null) {
                    existing += target
                    continue
                }
                val created = DocumentsContract.createDocument(
                    resolver, DocumentsContract.buildDocumentUriUsingTree(targetTree, parentId), "application/octet-stream", name,
                ) ?: error(str(R.string.bios_installer_couldn_t_create_the_file))
                resolver.openInputStream(DocumentsContract.buildDocumentUriUsingTree(sourceTree, file.nodeId)).use { input ->
                    resolver.openOutputStream(created, "w").use { output ->
                        checkNotNull(input) { str(R.string.bios_installer_couldn_t_read, file.foundAs) }.copyTo(checkNotNull(output) { str(R.string.bios_installer_couldn_t_write, name) })
                    }
                }
                copied += target
            } catch (e: Exception) {
                failed += target to (e.message ?: e.javaClass.simpleName)
            }
        }
        return CopyResult(copied, existing, failed)
    }

    /**
     * The same copy into a folder of RetroForge's own (the built-in player's BIOS folder), so no folder needs picking.
     * Never overwrites: files already there are left alone.
     */
    fun copyToFolder(sourceTree: Uri, files: List<FoundBios>, targetDir: java.io.File): CopyResult {
        val copied = mutableListOf<String>()
        val existing = mutableListOf<String>()
        val failed = mutableListOf<Pair<String, String>>()
        for (file in files) {
            val target = file.wanted.name
            try {
                val out = java.io.File(targetDir, target)
                // BIOS names come from RetroForge's own rules; this keeps any odd name inside the folder.
                require(out.canonicalPath.startsWith(targetDir.canonicalPath + java.io.File.separator)) { str(R.string.bios_installer_unexpected_file_name) }
                if (out.exists()) {
                    existing += target
                    continue
                }
                out.parentFile?.mkdirs()
                val part = java.io.File(out.path + ".part")
                resolver.openInputStream(DocumentsContract.buildDocumentUriUsingTree(sourceTree, file.nodeId)).use { input ->
                    part.outputStream().use { output -> checkNotNull(input) { str(R.string.bios_installer_couldn_t_read, file.foundAs) }.copyTo(output) }
                }
                check(part.renameTo(out)) { str(R.string.bios_installer_couldn_t_save, target) }
                copied += target
            } catch (e: Exception) {
                failed += target to (e.message ?: e.javaClass.simpleName)
            }
        }
        return CopyResult(copied, existing, failed)
    }

    private fun childId(tree: Uri, parentId: String, name: String): String? {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        return resolver.query(uri, arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            generateSequence { if (c.moveToNext()) c.getString(0) to c.getString(1) else null }
                .firstOrNull { it.second.equals(name, ignoreCase = true) }?.first
        }
    }

    private fun createDir(tree: Uri, parentId: String, name: String): String {
        val uri = DocumentsContract.createDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, parentId), Document.MIME_TYPE_DIR, name)
            ?: error(str(R.string.bios_installer_couldn_t_create_folder, name))
        return DocumentsContract.getDocumentId(uri)
    }
}
