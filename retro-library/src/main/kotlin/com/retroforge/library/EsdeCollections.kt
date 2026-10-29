package com.retroforge.library

/**
 * ES-DE's custom collections: one file per collection, `custom-<Name>.cfg`, listing a game per line as
 * `%ROMPATH%/snes/Game (USA).zip` (or a full path). RetroForge turns each into a playlist.
 */
object EsdeCollections {

    data class Collection(val name: String, val games: List<String>)

    /** The collection's name from its file name; null for files that aren't custom collections. */
    fun nameOf(fileName: String): String? =
        Regex("""^custom-(.+)\.cfg$""", RegexOption.IGNORE_CASE).matchEntire(fileName)?.groupValues?.get(1)?.trim()?.ifEmpty { null }

    /** Each game as "system folder/…/file", the part after the roms folder, in lower case for matching. */
    fun parse(name: String, text: String): Collection = Collection(
        name,
        text.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }.map { line ->
            val path = line.replace('\\', '/')
            // After %ROMPATH%/ or a "roms" folder; otherwise the last two parts (system folder and file).
            val afterRoms = when {
                path.startsWith("%ROMPATH%/", ignoreCase = true) -> path.substring("%ROMPATH%/".length)
                else -> Regex("""(?i)(?:^|.*/)roms/(.+)""").matchEntire(path)?.groupValues?.get(1) ?: path.split('/').takeLast(2).joinToString("/")
            }
            afterRoms.lowercase()
        },
    )

    /** True when a library game at [relativePath] (inside the user's folder) is the collection's [entry]. */
    fun matches(relativePath: String, entry: String): Boolean {
        val path = relativePath.lowercase()
        return path == entry || path.endsWith("/$entry")
    }
}
