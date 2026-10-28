package com.retroforge.library.manage

import com.retroforge.library.FileNode
import com.retroforge.library.LibraryScanner
import com.retroforge.library.SystemRules
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Opt-in dry run over RETROFORGE_REAL_ROMS: plans duplicate tidying and compression without changing anything
 * (every write method throws).
 */
class RealPlanTest {
    private class ReadOnly(private val inner: CollectionFiles) : CollectionFiles by inner {
        override fun makeFolders(path: String) = error("read-only")
        override fun move(path: String, folder: String) = error("read-only")
        override fun rename(path: String, newName: String) = error("read-only")
        override fun create(path: String) = error("read-only")
        override fun replace(path: String, bytes: ByteArray) = error("read-only")
        override fun discardPartial(path: String) = error("read-only")
    }

    @Test
    fun dryRun() {
        val path = System.getenv("RETROFORGE_REAL_ROMS")
        assumeTrue(!path.isNullOrBlank() && File(path).isDirectory)
        val rules = SystemRules.bundled()
        val root = File(path)
        val files = ReadOnly(JvmCollectionFiles(root))
        val games = LibraryScanner(rules).scan(FileNode(root)).games
        val systemFolder = { rel: String -> rel.substringBefore('/') }

        val (sets, skipped) = DuplicatePlanner.plan(games.filter { it.duplicateKey != null }.map {
            DuplicateCandidate(it.system, it.relativePath, systemFolder(it.relativePath), it.displayTitle, it.size, it.duplicateKey!!, it.metadata != null, it.imageNodeId != null)
        })
        println("PLAN duplicate sets=${sets.size} skipped=${skipped.size}")
        skipped.groupBy { it.reason.take(40) }.forEach { (r, v) -> println("PLAN skipped ${v.size}: $r") }
        for (set in sets) {
            val same = ContentFingerprint.sameness(set.files.map { f -> ContentFingerprint.of(f.fileName, f.size) { files.open(f.relativePath) } })
            println("PLAN set ${set.system}/${set.title}: $same keep=${set.keep(KeepRule.MOST_COMPATIBLE).fileName} files=${set.files.map { it.fileName }}")
        }

        val (jobs, cskip) = CompressPlanner.plan(games.map { CompressCandidate(it.system, it.relativePath, systemFolder(it.relativePath), it.displayTitle, it.size) }, files, rules)
        println("PLAN compress jobs=${jobs.size} inputGB=%.2f skipped=${cskip.size}".format(jobs.sumOf { it.inputBytes } / 1e9))
        jobs.groupBy { it.system to it.format }.forEach { (k, v) -> println("PLAN compress ${k.first} ${k.second}: ${v.size} (%.2f GB)".format(v.sumOf { it.inputBytes } / 1e9)) }
        cskip.groupBy { it.system + ": " + it.reason.take(60) }.forEach { (k, v) -> println("PLAN cskip ${v.size} $k e.g. ${v.first().path}") }
    }
}
