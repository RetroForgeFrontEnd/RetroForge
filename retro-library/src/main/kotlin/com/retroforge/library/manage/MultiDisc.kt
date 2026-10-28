package com.retroforge.library.manage

import com.retroforge.library.SystemRules
import java.io.IOException
import java.nio.ByteBuffer

/** An .m3u to create so a multi-disc (or multi-floppy) game shows and plays as one game, with disc swapping. */
data class PlaylistJob(val system: String, val title: String, val playlist: String, val discs: List<String>)

data class PlaylistSkip(val system: String, val title: String, val reason: String)

object MultiDiscPlanner {

    // "(Disc 1)", "(Disk 2 of 4)", "(CD2)", "(Side A)"; whatever follows it ("(Allies)", "[cr XOR]") differs per disc.
    private val discTag = Regex("""\s*\((?:Disc|Disk|CD|Side)\s*([0-9]+|[A-Z])(?:\s*of\s*\d+)?\).*$""", RegexOption.IGNORE_CASE)

    fun plan(games: List<CompressCandidate>, files: CollectionFiles, rules: SystemRules): Pair<List<PlaylistJob>, List<PlaylistSkip>> {
        val jobs = mutableListOf<PlaylistJob>()
        val skipped = mutableListOf<PlaylistSkip>()
        val candidates = games.filter { g ->
            g.relativePath.substringBefore('/') !in HOLDING_FOLDERS && g.extension != "m3u" &&
                rules.byId(g.system)?.extensions?.contains("m3u") == true
        }
        for ((key, group) in candidates.groupBy { g -> discTag.find(nameOf(g.relativePath).substringBeforeLast('.'))?.let { parentOf(g.relativePath) + "/" + nameOf(g.relativePath).substringBeforeLast('.').substring(0, it.range.first) } }) {
            if (key == null) continue
            val first = group.first()
            val title = first.title.replace(discTag, "").trim().ifEmpty { nameOf(key) }
            val byIndex = group.groupBy { index(it) }
            val indices = byIndex.keys.sorted()
            val playlist = "$key.m3u"
            val reason = when {
                group.size < 2 -> "Only one disc found."
                byIndex.values.any { it.size > 1 } -> "Several versions of the same disc; pick one first."
                indices != (1..indices.size).toList() -> "Disc ${(1..indices.size + 1).first { it !in indices }} is missing."
                group.map { it.extension }.distinct().size > 1 -> "The discs are in different formats."
                files.exists(playlist) -> "${nameOf(playlist)} already exists."
                else -> null
            }
            if (reason != null) skipped += PlaylistSkip(first.system, title, reason)
            else jobs += PlaylistJob(first.system, title, playlist, indices.map { nameOf(byIndex.getValue(it).single().relativePath) })
        }
        return jobs.sortedWith(compareBy({ it.system }, { it.title.lowercase() })) to skipped
    }

    private fun index(game: CompressCandidate): Int {
        val raw = discTag.find(nameOf(game.relativePath).substringBeforeLast('.'))!!.groupValues[1]
        return raw.toIntOrNull() ?: (raw.uppercase()[0] - 'A' + 1)
    }
}

class PlaylistMaker(private val files: CollectionFiles) {
    /** Writes each playlist (disc file names, one per line). Returns problems. */
    fun run(jobs: List<PlaylistJob>, journal: Journal, isCancelled: () -> Boolean = { false }): List<String> {
        val problems = mutableListOf<String>()
        for (job in jobs) {
            if (isCancelled()) break
            try {
                files.create(job.playlist).use { ch ->
                    val buf = ByteBuffer.wrap(job.discs.joinToString("\n", postfix = "\n").toByteArray(Charsets.UTF_8))
                    while (buf.hasRemaining()) ch.write(buf)
                }
                journal.record(Step.Created(job.playlist))
            } catch (e: IOException) {
                problems += "${job.title}: ${e.message}"
            }
        }
        return problems
    }
}
