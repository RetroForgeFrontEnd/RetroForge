package com.retroforge.app.player

import com.retroforge.app.R
import com.retroforge.core.ui.str
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * The built-in player's saves as one zip: in-game saves (`saves/`), save states (`states/`) and cheats (`cheats/`).
 * [folders] maps each name in the zip to the folder on the device.
 */
class SaveBackup(private val folders: Map<String, File>) {

    /** Writes every file into [out]; returns how many. */
    fun export(out: OutputStream): Int {
        var count = 0
        ZipOutputStream(out.buffered()).use { zip ->
            folders.forEach { (name, root) ->
                root.walkTopDown().filter { it.isFile && !it.name.endsWith(".part") }.forEach { file ->
                    zip.putNextEntry(ZipEntry("$name/" + file.relativeTo(root).invariantSeparatorsPath))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                    count++
                }
            }
        }
        return count
    }

    /**
     * Puts the files from a backup back, replacing files with the same name and keeping all others. Entries outside
     * the known folders (or trying to climb out of them) are skipped. Returns how many files were restored.
     */
    fun restore(input: InputStream): Int {
        var count = 0
        ZipInputStream(input.buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.isDirectory) continue
                val name = entry.name.substringBefore('/')
                val root = folders[name] ?: continue
                val target = File(root, entry.name.substringAfter('/'))
                if (!target.canonicalPath.startsWith(root.canonicalPath + File.separator)) continue
                target.parentFile?.mkdirs()
                val part = File(target.path + ".part")
                part.outputStream().use { zip.copyTo(it) }
                if (!part.renameTo(target)) {
                    target.delete()
                    if (!part.renameTo(target)) throw IOException(str(R.string.save_backup_couldn_t_write, target.name))
                }
                count++
            }
        }
        return count
    }
}
