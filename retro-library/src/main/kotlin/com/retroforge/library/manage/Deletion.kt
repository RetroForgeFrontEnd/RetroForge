package com.retroforge.library.manage

import java.io.IOException

const val DELETED_FOLDER = "_deleted"

/** Every file a game is made of: an .m3u's discs, a .cue's or .gdi's tracks, or just the file (or game folder). */
object GameFiles {
    fun of(files: CollectionFiles, path: String): List<String> {
        val folder = parentOf(path)
        return when (path.substringAfterLast('.', "").lowercase()) {
            "m3u" -> listOf(path) + TextFiles.read(files, path).lineSequence()
                .map { it.trim().replace('\\', '/').removePrefix("./") }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .flatMap { of(files, join(folder, it)) }
            "cue" -> listOf(path) + CueSheet.files(TextFiles.read(files, path)).map { join(folder, it) }
            "gdi" -> listOf(path) + TextFiles.read(files, path).lineSequence().drop(1)
                .mapNotNull { line -> Regex("""^\s*\d+\s+\d+\s+\d+\s+\d+\s+("[^"]+"|\S+)""").find(line)?.groupValues?.get(1)?.trim('"') }
                .map { join(folder, it) }
            else -> listOf(path)
        }.distinct().filter(files::exists)
    }
}

/** A game the user asked to delete: its files go to _deleted/ (undoable); only "Delete permanently" removes them. */
class GameDeleter(private val files: CollectionFiles) {

    fun moveToDeleted(path: String, systemFolder: String, journal: Journal): List<String> {
        val problems = mutableListOf<String>()
        val parts = GameFiles.of(files, path)
        if (parts.isEmpty()) throw IOException("$path is no longer there")
        for (part in parts) {
            try {
                val holding = join(DELETED_FOLDER, parentOf(part))
                files.makeFolders(holding)
                files.move(part, holding)
                journal.record(Step.Moved(part, join(holding, nameOf(part))))
            } catch (e: IOException) {
                problems += "${nameOf(part)}: ${e.message}"
            }
        }
        val gamelist = join(systemFolder, "gamelist.xml")
        if (files.exists(gamelist)) {
            try {
                TextFiles.edit(files, gamelist, journal, backup = true) { text ->
                    val (out, change) = GamelistEditor.remove(text, path.removePrefix(systemFolder).trimStart('/'))
                    out to listOfNotNull(change)
                }
            } catch (e: IOException) {
                problems += "$gamelist: ${e.message}"
            }
        }
        return problems
    }
}

data class HoldingSummary(val folder: String, val files: Int, val bytes: Long)

object Holding {
    val folders = listOf(DUPLICATES_FOLDER, ORIGINALS_FOLDER, DELETED_FOLDER)

    fun summaries(files: CollectionFiles): List<HoldingSummary> = folders.filter(files::exists).map { folder ->
        val all = files.walk(folder)
        HoldingSummary(folder, all.size, all.sumOf { it.second })
    }.filter { it.files > 0 }

    /** True when undoing [log] needs files that were in [folder]. */
    fun dependsOn(log: OperationLog, folder: String): Boolean = log.steps.any { step ->
        step is Step.Moved && step.to.substringBefore('/') == folder
    }
}
