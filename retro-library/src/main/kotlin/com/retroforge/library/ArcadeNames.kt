package com.retroforge.library

import java.util.zip.GZIPInputStream

/**
 * Proper names for arcade games, whose files are named by MAME's short names ("sf2.zip" is Street Fighter II).
 * From libretro-database's FinalBurn Neo and MAME lists (CC BY-SA 4.0), merged into arcade_names.tsv.gz:
 * short name, title, year, maker.
 */
object ArcadeNames {
    /** Systems whose games are MAME-style romsets. */
    val SYSTEMS = setOf("fbneo", "mame", "neogeo", "cps1", "cps2", "cps3", "naomi", "naomi2", "atomiswave", "segastv")

    private val names: Map<String, String> by lazy {
        val stream = ArcadeNames::class.java.getResourceAsStream("/arcade_names.tsv.gz") ?: return@lazy emptyMap()
        GZIPInputStream(stream).bufferedReader().useLines { lines ->
            lines.mapNotNull { line -> line.split('\t').takeIf { it.size >= 2 }?.let { it[0] to it[1] } }.toMap()
        }
    }

    /** The full title for [fileName] ("sf2.zip"), or null when it isn't a known short name. */
    fun title(fileName: String): String? = names[fileName.substringBeforeLast('.').lowercase()]

    /**
     * [name] with the game's proper title when [system] is an arcade system and the file is a bare short name;
     * otherwise [name] as it is (files already named in full keep their own names).
     */
    fun named(system: String, fileName: String, name: RomName): RomName {
        if (system !in SYSTEMS || name.tags.isNotEmpty() || name.title.contains(' ')) return name
        val full = title(fileName) ?: return name
        // The list's own brackets ("(World 910522)") become tags, the same as a No-Intro name's.
        return RomNames.parse("$full.zip").copy(hackName = name.hackName)
    }
}
