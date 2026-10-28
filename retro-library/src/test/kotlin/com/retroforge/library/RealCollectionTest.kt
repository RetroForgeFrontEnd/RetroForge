package com.retroforge.library

import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Opt-in: scans a real collection read-only when RETROFORGE_REAL_ROMS points at it (e.g. G:\Retrobat\roms).
 * Prints statistics; the only assertion is that the scan completes. The collection is never written to.
 */
class RealCollectionTest {

    @Test
    fun scanRealCollection() {
        val path = System.getenv("RETROFORGE_REAL_ROMS")
        assumeTrue("RETROFORGE_REAL_ROMS not set", !path.isNullOrBlank() && File(path).isDirectory)
        val start = System.nanoTime()
        val result = LibraryScanner(SystemRules.bundled()).scan(FileNode(File(path)))
        val seconds = (System.nanoTime() - start) / 1e9
        val games = result.games
        println("REAL-SCAN layout=${result.layout} systems=${games.map { it.system }.distinct().size} games=${games.size} in %.1fs".format(seconds))
        println("REAL-SCAN withMetadata=${games.count { it.metadata != null }} withArt=${games.count { it.imageNodeId != null }} hacks=${games.count { it.isHack }} duplicates=${games.count { it.duplicateKey != null }} m3u=${games.count { it.discs != null }}")
        println("REAL-SCAN unrecognisedFolders=${result.unrecognisedFolders.size}: ${result.unrecognisedFolders.take(40)}")
        println("REAL-SCAN problems=${result.problems.size}")
        result.problems.take(25).forEach { println("REAL-SCAN problem ${it.path}: ${it.message}") }
        games.filter { it.isHack }.groupBy { it.system to it.hackFolder }.forEach { (k, v) -> println("REAL-SCAN hacks ${k.first}/${k.second}: ${v.size}") }
        games.groupBy { it.system }.entries.sortedByDescending { it.value.size }.take(15)
            .forEach { (s, v) -> println("REAL-SCAN system $s: ${v.size} games, ${v.count { it.metadata != null }} with metadata, ${v.count { it.imageNodeId != null }} with art") }
        games.filter { it.duplicateKey != null }.groupBy { it.duplicateKey }.values.take(5)
            .forEach { println("REAL-SCAN duplicate: ${it.map { g -> g.relativePath }}") }
    }
}
