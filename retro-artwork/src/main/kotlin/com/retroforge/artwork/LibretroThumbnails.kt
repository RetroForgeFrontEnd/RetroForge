package com.retroforge.artwork

import com.retroforge.library.RomFlag
import com.retroforge.library.RomNames
import java.net.URLDecoder
import java.net.URLEncoder

enum class ThumbnailType(val folder: String) { BOXART("Named_Boxarts"), SNAP("Named_Snaps"), TITLE("Named_Titles") }

data class ThumbnailMatch(val name: String, val url: String, val score: Int, val exact: Boolean)

/**
 * libretro's free thumbnail server (the one RetroArch itself uses): https://thumbnails.libretro.com/<system>/<type>/<name>.png.
 * Each system/type folder publishes an index page, so matching can be done locally against the real file names.
 */
object LibretroThumbnails {

    private const val BASE = "https://thumbnails.libretro.com"
    private val reRelease = Regex("Collection|Virtual Console|Switch Online|Classic|Mini|Arcade|Anniversary|Compilation|Evercade|Steam|GOG", RegexOption.IGNORE_CASE)

    /** RetroForge system id → the server's folder name (checked against the server's index, 2026-10-28). */
    val systems: Map<String, String> = mapOf(
        "nes" to "Nintendo - Nintendo Entertainment System",
        "fds" to "Nintendo - Family Computer Disk System",
        "snes" to "Nintendo - Super Nintendo Entertainment System",
        "sfc" to "Nintendo - Super Nintendo Entertainment System",
        "snes-msu1" to "Nintendo - Super Nintendo Entertainment System",
        "satellaview" to "Nintendo - Satellaview",
        "sufami" to "Nintendo - Sufami Turbo",
        "gb" to "Nintendo - Game Boy",
        "gbc" to "Nintendo - Game Boy Color",
        "gba" to "Nintendo - Game Boy Advance",
        "n64" to "Nintendo - Nintendo 64",
        "n64dd" to "Nintendo - Nintendo 64DD",
        "nds" to "Nintendo - Nintendo DS",
        "3ds" to "Nintendo - Nintendo 3DS",
        "virtualboy" to "Nintendo - Virtual Boy",
        "pokemini" to "Nintendo - Pokemon Mini",
        "gamecube" to "Nintendo - GameCube",
        "wii" to "Nintendo - Wii",
        "wiiu" to "Nintendo - Wii U",
        "mastersystem" to "Sega - Master System - Mark III",
        "megadrive" to "Sega - Mega Drive - Genesis",
        "genesis" to "Sega - Mega Drive - Genesis",
        "megadrive-msu" to "Sega - Mega Drive - Genesis",
        "gamegear" to "Sega - Game Gear",
        "sg1000" to "Sega - SG-1000",
        "megacd" to "Sega - Mega-CD - Sega CD",
        "segacd" to "Sega - Mega-CD - Sega CD",
        "sega32x" to "Sega - 32X",
        "saturn" to "Sega - Saturn",
        "dreamcast" to "Sega - Dreamcast",
        "naomi" to "Sega - Naomi",
        "naomi2" to "Sega - Naomi 2",
        "atomiswave" to "Atomiswave",
        "psx" to "Sony - PlayStation",
        "ps2" to "Sony - PlayStation 2",
        "ps3" to "Sony - PlayStation 3",
        "psp" to "Sony - PlayStation Portable",
        "psvita" to "Sony - PlayStation Vita",
        "pcengine" to "NEC - PC Engine - TurboGrafx 16",
        "pcenginecd" to "NEC - PC Engine CD - TurboGrafx-CD",
        "supergrafx" to "NEC - PC Engine SuperGrafx",
        "pcfx" to "NEC - PC-FX",
        "pc98" to "NEC - PC-98",
        "atari2600" to "Atari - 2600",
        "atari5200" to "Atari - 5200",
        "atari7800" to "Atari - 7800",
        "atari800" to "Atari - 8-bit",
        "jaguar" to "Atari - Jaguar",
        "lynx" to "Atari - Lynx",
        "atarist" to "Atari - ST",
        "ngp" to "SNK - Neo Geo Pocket",
        "ngpc" to "SNK - Neo Geo Pocket Color",
        "neogeo" to "SNK - Neo Geo",
        "neogeocd" to "SNK - Neo Geo CD",
        "wswan" to "Bandai - WonderSwan",
        "wswanc" to "Bandai - WonderSwan Color",
        "colecovision" to "Coleco - ColecoVision",
        "intellivision" to "Mattel - Intellivision",
        "vectrex" to "GCE - Vectrex",
        "odyssey2" to "Magnavox - Odyssey2",
        "channelf" to "Fairchild - Channel F",
        "supervision" to "Watara - Supervision",
        "3do" to "The 3DO Company - 3DO",
        "cdi" to "Philips - CD-i",
        "mame" to "MAME",
        "fbneo" to "FBNeo - Arcade Games",
        "msx1" to "Microsoft - MSX",
        "msx2" to "Microsoft - MSX2",
        "xbox" to "Microsoft - Xbox",
        "c64" to "Commodore - 64",
        "c20" to "Commodore - VIC-20",
        "amiga500" to "Commodore - Amiga",
        "amiga1200" to "Commodore - Amiga",
        "amigacd32" to "Commodore - CD32",
        "amigacdtv" to "Commodore - CDTV",
        "zxspectrum" to "Sinclair - ZX Spectrum",
        "zx81" to "Sinclair - ZX 81",
        "amstradcpc" to "Amstrad - CPC",
        "gx4000" to "Amstrad - GX4000",
        "x68000" to "Sharp - X68000",
        "dos" to "DOS",
        "scummvm" to "ScummVM",
        "tic80" to "TIC-80",
        "wasm4" to "WASM-4",
    )

    fun folderFor(system: String): String? = systems[system]

    fun indexUrl(folder: String, type: ThumbnailType) = "$BASE/${encode(folder)}/${type.folder}/"

    fun imageUrl(folder: String, type: ThumbnailType, name: String) = "$BASE/${encode(folder)}/${type.folder}/${encode("$name.png")}"

    /** File names (without ".png") listed on a folder's index page. */
    fun parseIndex(html: String): List<String> =
        Regex("""href="([^"/?]+)\.png"""").findAll(html)
            .map { URLDecoder.decode(it.groupValues[1].replace("+", "%2B"), "UTF-8") }
            .distinct()
            .toList()

    /** The server stores names with these characters replaced by "_" (libretro thumbnail naming rules). */
    fun sanitise(name: String): String = name.replace(Regex("""[&*/:`<>?\\|"]"""), "_")

    /**
     * Ranks candidate box art for a game file. Exact name matches win; otherwise the title must match, and
     * region, revision and "plain release" (no Beta/Demo/collection tags) decide the order.
     */
    fun match(candidates: List<String>, folder: String, type: ThumbnailType, fileName: String, limit: Int = 6): List<ThumbnailMatch> {
        val base = sanitise(fileName.substringBeforeLast('.', fileName))
        val wanted = RomNames.parse(fileName)
        val wantedRegions = wanted.regions.toSet()
        return candidates.mapNotNull { candidate ->
            if (candidate == base) return@mapNotNull ThumbnailMatch(candidate, imageUrl(folder, type, candidate), 10_000, exact = true)
            val parsed = RomNames.parse("$candidate.png")
            if (parsed.sortKey != wanted.sortKey || parsed.sortKey.isEmpty()) return@mapNotNull null
            var score = 1_000
            score += 100 * parsed.regions.count { it in wantedRegions }
            // A "World" cart was sold everywhere, so the USA, Europe or Japan box is just as right.
            if ("World" in wantedRegions) score += 60 * parsed.regions.count { it in setOf("USA", "Europe", "Japan") }.coerceAtMost(1)
            if (wantedRegions.isEmpty() && parsed.regions.any { it in setOf("USA", "World", "Europe") }) score += 50
            if (parsed.revision != null && parsed.revision == wanted.revision) score += 30
            val unwanted = setOf(RomFlag.BETA, RomFlag.PROTO, RomFlag.DEMO, RomFlag.SAMPLE)
            score -= 200 * parsed.flags.count { it in unwanted && it !in wanted.flags }
            val extraTags = parsed.tags.filter { tag -> tag.split(",").none { it.trim() in parsed.regions } && tag != parsed.revision }
            score -= 20 * extraTags.size
            // Re-releases have their own box art; prefer the original unless the file itself is that edition.
            score -= 150 * extraTags.count { tag -> reRelease.containsMatchIn(tag) && tag !in wanted.tags }
            ThumbnailMatch(candidate, imageUrl(folder, type, candidate), score, exact = false)
        }.sortedWith(compareByDescending<ThumbnailMatch> { it.score }.thenBy { it.name }).take(limit)
    }

    private fun encode(segment: String): String = URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
}
