package com.retroforge.artwork

import com.retroforge.library.FileNode
import com.retroforge.library.LibraryScanner
import com.retroforge.library.SystemRules
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Opt-in: RETROFORGE_REAL_ROMS=<roms folder> RETROFORGE_MATCH_SYSTEMS=gba,snes. Scans those system folders read-only,
 * downloads each system's libretro box-art index once, and reports how many games would get a cover.
 */
class RealMatchTest {

    @Test
    fun matchRealCollection() {
        val roms = System.getenv("RETROFORGE_REAL_ROMS")
        val systems = System.getenv("RETROFORGE_MATCH_SYSTEMS")?.split(",")?.map { it.trim() }.orEmpty()
        assumeTrue(!roms.isNullOrBlank() && systems.isNotEmpty())
        val http = JvmHttpGet("RetroForge-test")
        for (system in systems) {
            val folder = LibretroThumbnails.folderFor(system) ?: continue
            val games = LibraryScanner(SystemRules.bundled()).scan(FileNode(File(roms, system))).games
            val index = LibretroThumbnails.parseIndex(http.get(LibretroThumbnails.indexUrl(folder, ThumbnailType.BOXART), emptyMap(), 20_000_000).body.toString(Charsets.UTF_8))
            var exact = 0; var fuzzy = 0; var none = 0; var noArtCovered = 0; var noArt = 0
            val misses = mutableListOf<String>()
            for (g in games) {
                val best = LibretroThumbnails.match(index, folder, ThumbnailType.BOXART, g.fileName, 1).firstOrNull()
                when { best == null -> { none++; if (misses.size < 8) misses += g.fileName }; best.exact -> exact++; else -> fuzzy++ }
                if (g.imageNodeId == null) { noArt++; if (best != null) noArtCovered++ }
            }
            println("REAL-MATCH $system: ${games.size} games, index ${index.size}; exact=$exact fuzzy=$fuzzy none=$none; without art: $noArt, of which coverable: $noArtCovered")
            println("REAL-MATCH $system misses e.g.: $misses")
        }
    }
}
