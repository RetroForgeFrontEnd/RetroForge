package com.retroforge.app.player

import com.retroforge.app.R
import com.retroforge.core.ui.str
import android.graphics.Bitmap
import com.retroforge.artwork.LibretroThumbnails
import com.retroforge.artwork.ThumbnailType
import com.retroforge.player.Rect
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Bezels from The Bezel Project (github.com/thebezelproject): RetroArch overlay PNGs, a frame round the game for each
 * system and most games, shown in landscape. Nothing is included in RetroForge: a system's download (Settings →
 * Covers and bezels) saves its list of game bezels and its system bezel in [root]/<repo>, and a game's own bezel is fetched
 * the first time that game starts (a whole system is hundreds of MB; one bezel is about 150 KB).
 */
class Bezels(private val root: File, private val userAgent: String) {

    /** The system's folder, or null when it hasn't been downloaded. */
    private fun folder(system: String): File? = repoFor(system)?.let { File(root, it) }?.takeIf { File(it, SYSTEM).isFile }

    fun downloaded(system: String) = folder(system) != null

    /** Downloads [system]'s list of game bezels and its system bezel. */
    fun install(system: String) {
        val repo = repoFor(system) ?: throw IOException(str(R.string.bezels_the_bezel_project_has_no_bezels))
        val pngs = overlayFiles(repo).filter { it.endsWith(".png", true) }
        // The system bezel sits in the overlay folder itself (Sega-Mega-Drive.png); games are in GameBezels/<system>.
        val system = pngs.filter { '/' !in it.removePrefix(OVERLAY) }.minByOrNull { it.length }
            ?: throw IOException(str(R.string.bezels_this_pack_has_no_system_bezel))
        val dir = File(root, repo).apply { mkdirs() }
        File(dir, GAMES).writeText(pngs.filter { it.startsWith("${OVERLAY}GameBezels/") }.joinToString("\n"))
        save(system, repo, File(dir, SYSTEM))
    }

    /**
     * Every file under the pack's overlay folder. One request lists a whole pack; GitHub cuts the biggest packs' lists
     * short (PlayStation's has over 50,000 files), so those are listed one folder at a time down to the overlays.
     */
    private fun overlayFiles(repo: String): List<String> {
        fun tree(sha: String, recursive: Boolean) = JSONObject(
            get("$API/bezelproject-$repo/git/trees/$sha" + if (recursive) "?recursive=1" else "").decodeToString(),
        )
        fun entries(json: JSONObject) = json.optJSONArray("tree")?.let { t -> (0 until t.length()).map { t.getJSONObject(it) } }
            ?: throw IOException(str(R.string.bezels_couldn_t_read_the_bezel_list))
        val whole = tree("master", recursive = true)
        if (!whole.optBoolean("truncated")) return entries(whole).map { it.optString("path") }.filter { it.startsWith(OVERLAY) }
        // Walk down retroarch/overlay, then list that folder whole (it's well within GitHub's limit).
        var sha = "master"
        for (name in OVERLAY.trimEnd('/').split('/')) {
            sha = entries(tree(sha, recursive = false)).firstOrNull { it.optString("path") == name }?.optString("sha")
                ?: throw IOException(str(R.string.bezels_this_pack_has_no_folder, name))
        }
        return entries(tree(sha, recursive = true)).map { OVERLAY + it.optString("path") }
    }

    fun remove(system: String) {
        repoFor(system)?.let { File(root, it).deleteRecursively() }
    }

    /** The system bezel, already downloaded. */
    fun systemBezel(system: String): File? = folder(system)?.let { File(it, SYSTEM) }

    /** This game's own bezel, downloaded now if it isn't yet; null when the pack has none for it. Not on the main thread. */
    fun gameBezel(system: String, fileName: String): File? {
        val dir = folder(system) ?: return null
        val paths = File(dir, GAMES).readLines().filter { it.isNotBlank() }
        val names = paths.map { it.substringAfterLast('/').removeSuffix(".png") }
        val best = LibretroThumbnails.match(names, "", ThumbnailType.BOXART, fileName, limit = 1).firstOrNull() ?: return null
        val file = File(dir, "games/${best.name.replace('/', '_')}.png")
        if (!file.isFile) save(paths[names.indexOf(best.name)], repoFor(system)!!, file)
        return file
    }

    private fun save(path: String, repo: String, target: File) {
        val url = "$RAW/bezelproject-$repo/master/" + path.split('/').joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }
        target.parentFile?.mkdirs()
        val part = File(target.path + ".part")
        part.writeBytes(get(url))
        target.delete()
        if (!part.renameTo(target)) throw IOException(str(R.string.bezels_couldn_t_save, target.name))
    }

    private fun get(url: String): ByteArray {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            setRequestProperty("User-Agent", userAgent)
        }
        try {
            if (connection.responseCode != 200) throw IOException(str(R.string.bezels_github_answered, connection.responseCode))
            return connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val API = "https://api.github.com/repos/thebezelproject"
        private const val RAW = "https://raw.githubusercontent.com/thebezelproject"
        private const val OVERLAY = "retroarch/overlay/"
        private const val SYSTEM = "system.png"
        private const val GAMES = "games.txt"

        /** Views preference: bezels shown in landscape (when the system's are downloaded). */
        const val PREF = "bezels"

        /** RetroForge's system ids to The Bezel Project's repository names (bezelproject-<name>). */
        private val repos = mapOf(
            "3do" to "3DO", "amiga500" to "Amiga", "amiga1200" to "Amiga", "amiga4000" to "Amiga", "amigacd32" to "CD32",
            "amigacdtv" to "CDTV", "amstradcpc" to "AmstradCPC", "gx4000" to "AmstradGX4000", "atari2600" to "Atari2600",
            "atari5200" to "Atari5200", "atari7800" to "Atari7800", "atari800" to "Atari800", "jaguar" to "AtariJaguar",
            "lynx" to "AtariLynx", "atarist" to "AtariST", "xegs" to "AtariXEGS", "c64" to "C64", "cdi" to "CDiMono1",
            "channelf" to "ChannelF", "colecovision" to "ColecoVision", "dreamcast" to "Dreamcast", "famicom" to "Famicom",
            "fds" to "FDS", "gamegear" to "GameGear", "gb" to "GB", "gba" to "GBA", "gbc" to "GBC", "gc" to "GC",
            "gamecube" to "GC", "vectrex" to "GCEVectrex", "intellivision" to "Intellivision", "mastersystem" to "MasterSystem",
            "megadrive" to "MegaDrive", "genesis" to "MegaDrive", "msx1" to "MSX", "msx2" to "MSX2", "msxturbor" to "MSXTurbo",
            "n64" to "N64", "n64dd" to "N64DD", "nds" to "NDS", "nes" to "NES", "neogeocd" to "NG-CD", "ngp" to "NGP",
            "ngpc" to "NGPC", "pcengine" to "PCEngine", "pcenginecd" to "PCE-CD", "pcfx" to "PCFX", "ps2" to "PS2",
            "psx" to "PSX", "saturn" to "Saturn", "scummvm" to "ScummVM", "sega32x" to "Sega32X", "segacd" to "SegaCD",
            "megacd" to "SegaCD", "sfc" to "SFC", "sg1000" to "SG-1000", "snes" to "SNES", "sufami" to "SufamiTurbo",
            "supergrafx" to "SuperGrafx", "supervision" to "Supervision", "odyssey2" to "Videopac", "virtualboy" to "Virtualboy",
            "wii" to "Wii", "wswan" to "WonderSwan", "wswanc" to "WonderSwanColor", "x68000" to "X68000", "zx81" to "ZX81",
            "zxspectrum" to "ZXSpectrum",
        )

        fun repoFor(system: String): String? = repos[system]

        /**
         * The bezel's see-through window, where the game goes, in the image's pixels: the transparent span through the
         * middle, measured across a few rows and columns (the median, so a reflection drawn on the glass doesn't count).
         * Null when the middle isn't see-through.
         */
        fun window(bitmap: Bitmap): Rect? {
            val w = bitmap.width
            val h = bitmap.height
            if (w < 16 || h < 16) return null
            fun clear(x: Int, y: Int) = (bitmap.getPixel(x, y) ushr 24) < 128
            if (!clear(w / 2, h / 2)) return null
            fun median(values: List<Int>) = values.sorted()[values.size / 2]
            val rows = listOf(h * 2 / 5, h / 2, h * 3 / 5)
            val cols = listOf(w * 2 / 5, w / 2, w * 3 / 5)
            val left = median(rows.map { y -> var x = w / 2; while (x > 0 && clear(x - 1, y)) x--; x })
            val right = median(rows.map { y -> var x = w / 2; while (x < w - 1 && clear(x + 1, y)) x++; x + 1 })
            val top = median(cols.map { x -> var y = h / 2; while (y > 0 && clear(x, y - 1)) y--; y })
            val bottom = median(cols.map { x -> var y = h / 2; while (y < h - 1 && clear(x, y + 1)) y++; y + 1 })
            // A window that's the whole image is no bezel at all.
            if (right - left >= w - 2 && bottom - top >= h - 2) return null
            return Rect(left, top, right - left, bottom - top)
        }
    }
}
