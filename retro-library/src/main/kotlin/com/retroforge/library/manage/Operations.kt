package com.retroforge.library.manage

import com.retroforge.library.GamelistParser
import com.retroforge.library.matchesIn
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/** Where moved-aside files go, relative to the collection folder. RetroBat and ES-DE ignore these top-level folders. */
const val DUPLICATES_FOLDER = "_duplicates"
const val ORIGINALS_FOLDER = "_originals"
val HOLDING_FOLDERS = setOf(DUPLICATES_FOLDER, ORIGINALS_FOLDER, DELETED_FOLDER)
const val BACKUP_SUFFIX = ".retroforge-backup"

@Serializable
sealed interface TextChange {
    /** A whole `<game>` entry removed from gamelist.xml (exact text, so undo can put it back). */
    @Serializable @SerialName("removed_entry")
    data class RemovedEntry(val block: String, val afterPath: String? = null) : TextChange

    /** A gamelist `<path>` changed; raw XML text on both sides. */
    @Serializable @SerialName("path_changed")
    data class PathChanged(val from: String, val to: String) : TextChange

    /** One line of an .m3u playlist. */
    @Serializable @SerialName("line_changed")
    data class LineChanged(val from: String, val to: String) : TextChange
}

@Serializable
sealed interface Step {
    @Serializable @SerialName("moved")
    data class Moved(val from: String, val to: String) : Step

    @Serializable @SerialName("renamed")
    data class Renamed(val from: String, val to: String) : Step

    /** A file this run made (e.g. a .chd). Undo moves it into _duplicates rather than deleting it. */
    @Serializable @SerialName("created")
    data class Created(val path: String) : Step

    @Serializable @SerialName("edited")
    data class Edited(val path: String, val changes: List<TextChange>) : Step

    @Serializable @SerialName("backed_up")
    data class BackedUp(val path: String) : Step

    /**
     * RetroForge's own records (favourites, playlists, play history, chosen cover) for the game at [from] were moved
     * to [to]. No file changes. With [reverseOnUndo] the app moves them back on undo (a compressed game's original
     * returns); without it they stay, because the kept copy of a duplicate still exists and may have had its own.
     */
    @Serializable @SerialName("relinked")
    data class Relinked(val from: String, val to: String, val reverseOnUndo: Boolean = true) : Step
}

@Serializable
data class OperationLog(
    val id: String,
    val kind: String,
    val rootId: Long,
    val startedAt: Long,
    val summary: String,
    val steps: List<Step> = emptyList(),
    val finished: Boolean = false,
    val undoneAt: Long? = null,
    /** Set when files this log would put back were permanently deleted, so it can no longer be undone. */
    val purgedAt: Long? = null,
) {
    companion object {
        val json = Json { ignoreUnknownKeys = true; classDiscriminator = "type" }
        fun decode(text: String): OperationLog = json.decodeFromString(serializer(), text)
    }

    fun encode(): String = json.encodeToString(serializer(), this)
}

/** Records every change as it happens (saved after each step), so an interrupted run can still be undone. */
class Journal(start: OperationLog, private val save: (OperationLog) -> Unit) {
    var log: OperationLog = start
        private set

    init { save(log) }

    fun record(step: Step) {
        log = log.copy(steps = log.steps + step)
        save(log)
    }

    fun finish(summary: String) {
        log = log.copy(finished = true, summary = summary)
        save(log)
    }
}

/** Text-level edits to gamelist.xml: everything except the changed entry stays byte-for-byte the same. */
object GamelistEditor {
    private val gameBlock = Regex("""(?s)[ \t]*<game\b[^>]*>.*?</game>[ \t]*(\r?\n)?""")
    private val pathTag = Regex("""(?s)<path>(.*?)</path>""")

    private fun pathOf(block: String): String? = pathTag.find(block)?.groupValues?.get(1)

    private fun find(text: String, path: String): MatchResult? {
        val want = GamelistParser.normalizePath(path)
        val at = gameBlock.matchesIn(text).firstOrNull { m -> pathOf(m.group())?.let { GamelistParser.normalizePath(unescape(it)) == want } == true }
        return at?.let { gameBlock.find(text, it.start()) }
    }

    /**
     * The game at [oldPath] (relative to the system folder) is now [newPath]. If [newPath] already has an entry,
     * the old entry is removed (the kept copy's metadata wins); otherwise the old entry is pointed at [newPath].
     */
    fun retarget(text: String, oldPath: String, newPath: String): Pair<String, TextChange?> {
        val old = find(text, oldPath) ?: return text to null
        if (find(text, newPath) != null) return remove(text, oldPath)
        val from = pathOf(old.value)!!
        val to = escape("./$newPath")
        val block = old.value.replaceFirst("<path>$from</path>", "<path>$to</path>")
        return text.replaceRange(old.range, block) to TextChange.PathChanged(from, to)
    }

    /** Removes the entry for [path] (e.g. a deleted game); undo puts it back in place. */
    fun remove(text: String, path: String): Pair<String, TextChange?> {
        val old = find(text, path) ?: return text to null
        val previous = gameBlock.matchesIn(text).lastOrNull { it.end() <= old.range.first }
        return text.removeRange(old.range) to TextChange.RemovedEntry(old.value, previous?.let { pathOf(it.group()) })
    }

    fun undo(text: String, change: TextChange): String = when (change) {
        is TextChange.RemovedEntry -> {
            val p = pathOf(change.block)?.let(::unescape)
            if (p != null && find(text, p) != null) text
            else {
                // Back where it was: after the entry that preceded it, else before the first entry, else at the end.
                val anchor = change.afterPath?.let { find(text, unescape(it)) }
                val first = gameBlock.find(text)
                val at = when {
                    anchor != null -> anchor.range.last + 1
                    change.afterPath == null && first != null -> first.range.first
                    else -> {
                        val end = text.lastIndexOf("</gameList>")
                        if (end < 0) throw IOException("gamelist.xml has no </gameList>")
                        val lineStart = text.lastIndexOf('\n', end - 1) + 1
                        if (text.substring(lineStart, end).isBlank()) lineStart else end
                    }
                }
                text.substring(0, at) + change.block + text.substring(at)
            }
        }
        is TextChange.PathChanged -> {
            val m = find(text, unescape(change.to))
            if (m == null) text else text.replaceRange(m.range, m.value.replaceFirst("<path>${change.to}</path>", "<path>${change.from}</path>"))
        }
        is TextChange.LineChanged -> text
    }

    fun unescape(s: String): String = s.replace(Regex("&(#x?[0-9a-fA-F]+|amp|lt|gt|quot|apos);")) { m ->
        when (val e = m.groupValues[1]) {
            "amp" -> "&"; "lt" -> "<"; "gt" -> ">"; "quot" -> "\""; "apos" -> "'"
            else -> (if (e.startsWith("#x")) e.drop(2).toInt(16) else e.drop(1).toInt()).let { String(Character.toChars(it)) }
        }
    }

    fun escape(s: String): String = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
}

/** .m3u playlists list one disc file per line, relative to the playlist's folder. */
object PlaylistEditor {
    /** Lines naming [oldPath] (relative to the playlist folder) are changed to [newPath], keeping their style. */
    fun retarget(text: String, oldPath: String, newPath: String): Pair<String, List<TextChange>> {
        val changes = mutableListOf<TextChange>()
        val out = text.split('\n').joinToString("\n") { raw ->
            val line = raw.trimEnd('\r')
            val normalized = line.trim().replace('\\', '/').removePrefix("./")
            if (normalized.equals(oldPath, ignoreCase = true)) {
                val prefix = line.substring(0, line.length - line.trimStart().length) + (if (line.trim().startsWith("./")) "./" else "")
                val replaced = prefix + (if (line.contains('\\')) newPath.replace('/', '\\') else newPath)
                changes += TextChange.LineChanged(line, replaced)
                replaced + raw.substring(line.length)
            } else raw
        }
        return out to changes
    }

    fun undo(text: String, change: TextChange.LineChanged): String =
        text.split('\n').joinToString("\n") { raw -> if (raw.trimEnd('\r') == change.to) change.from + raw.substring(raw.trimEnd('\r').length) else raw }
}

object TextFiles {
    const val MAX_BYTES = 32L * 1024 * 1024

    fun read(files: CollectionFiles, path: String): String {
        val size = files.size(path) ?: throw IOException("$path is missing")
        if (size > MAX_BYTES) throw IOException("$path is too large")
        val bytes = files.open(path).use { it.readBytes() }
        return try {
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: CharacterCodingException) {
            throw IOException("$path isn't UTF-8 text, so it was left unchanged")
        }
    }

    /**
     * Applies [transform] to a text file and records the change. With [backup], the first time a file is changed
     * its original is copied to "<name>.retroforge-backup" (never overwritten later).
     */
    fun edit(files: CollectionFiles, path: String, journal: Journal, backup: Boolean, transform: (String) -> Pair<String, List<TextChange>>) {
        val before = read(files, path)
        val (after, changes) = transform(before)
        if (changes.isEmpty()) return
        val backupPath = path + BACKUP_SUFFIX
        if (backup && !files.exists(backupPath)) {
            files.create(backupPath).use { ch -> val buf = ByteBuffer.wrap(before.toByteArray(Charsets.UTF_8)); while (buf.hasRemaining()) ch.write(buf) }
            journal.record(Step.BackedUp(backupPath))
        }
        files.replace(path, after.toByteArray(Charsets.UTF_8))
        journal.record(Step.Edited(path, changes))
    }
}

/** Reverses a log, newest step first. Returns problems; steps that can't be reversed are skipped, not forced. */
object Undo {
    fun run(files: CollectionFiles, log: OperationLog): List<String> {
        val problems = mutableListOf<String>()
        for (step in log.steps.asReversed()) {
            try {
                when (step) {
                    is Step.Moved -> when {
                        !files.exists(step.to) -> problems += "${step.to} is no longer there"
                        files.exists(step.from) -> problems += "${step.from} exists again, so ${step.to} was left where it is"
                        else -> { files.makeFolders(parentOf(step.from)); files.move(step.to, parentOf(step.from)) }
                    }
                    is Step.Renamed -> when {
                        !files.exists(step.to) -> problems += "${step.to} is no longer there"
                        else -> files.rename(step.to, nameOf(step.from))
                    }
                    is Step.Created -> if (files.exists(step.path)) {
                        val holding = join(DUPLICATES_FOLDER, parentOf(step.path))
                        files.makeFolders(holding)
                        files.move(step.path, holding)
                    }
                    is Step.Edited -> {
                        val text = TextFiles.read(files, step.path)
                        val restored = step.changes.asReversed().fold(text) { t, c ->
                            if (c is TextChange.LineChanged) PlaylistEditor.undo(t, c) else GamelistEditor.undo(t, c)
                        }
                        if (restored != text) files.replace(step.path, restored.toByteArray(Charsets.UTF_8))
                    }
                    is Step.BackedUp, is Step.Relinked -> Unit
                }
            } catch (e: IOException) {
                problems += e.message ?: "couldn't undo a step"
            }
        }
        return problems
    }
}
