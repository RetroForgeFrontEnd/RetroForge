package com.retroforge.app.ui.settings

import android.content.Context
import com.retroforge.app.ui.pc.PortDownloads
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import java.io.File
import java.io.IOException

/**
 * N64 texture packs RetroForge can download directly: GhostlyDark's free "Reloaded" packs, from their official GitHub
 * releases. Each is a single GLideN64 .hts file in a 7z, unpacked into system/Mupen64plus/cache where the built-in
 * player's N64 emulator finds it by the game's internal name. Made for the USA versions of the games.
 */
object TexturePackDownloads {
    data class Pack(val game: String, val repo: String)

    val packs = listOf(
        Pack("Super Mario 64", "GhostlyDark/SM64-Reloaded"),
        Pack("Mario Kart 64", "GhostlyDark/MK64-Reloaded"),
        Pack("Super Smash Bros.", "GhostlyDark/SSB-Reloaded"),
        Pack("Majora's Mask", "GhostlyDark/MM-Reloaded"),
        Pack("Ocarina of Time", "GhostlyDark/OoT-Reloaded"),
    )

    /** The pack's HD .hts file from its latest release (not the multi-part 4K one). */
    fun file(pack: Pack) = PortDownloads.latest(pack.repo)
        .filter { it.name.contains("gliden64-hts", true) && it.name.endsWith(".7z", true) }
        .sortedBy { if (it.name.contains("-hd", true)) 0 else if (it.name.contains("4k", true)) 2 else 1 }
        .firstOrNull()

    /** Downloads and unpacks the pack; returns the .hts files' names. */
    fun install(context: Context, pack: Pack, cacheDir: File, onProgress: (String) -> Unit): List<String> {
        val release = file(pack) ?: throw IOException("its release has no GLideN64 pack")
        val archive = File(context.cacheDir, "textures/${release.name.replace(Regex("""[^\w.-]"""), "_")}")
        try {
            PortDownloads.download(release, archive) { onProgress("Downloading ${pack.game}… $it% of ${release.size / 1_000_000} MB") }
            onProgress("Unpacking ${pack.game}…")
            cacheDir.mkdirs()
            val names = mutableListOf<String>()
            SevenZFile.builder().setFile(archive).get().use { seven ->
                for (entry in seven.entries) {
                    if (entry.isDirectory || !entry.name.endsWith(".hts", true)) continue
                    val name = entry.name.replace('\\', '/').substringAfterLast('/')
                    val part = File(cacheDir, "$name.part")
                    seven.getInputStream(entry).use { input -> part.outputStream().use { input.copyTo(it, 1 shl 20) } }
                    File(cacheDir, name).delete()
                    if (!part.renameTo(File(cacheDir, name))) throw IOException("couldn't save $name")
                    names += name
                }
            }
            if (names.isEmpty()) throw IOException("the download had no .hts file in it")
            return names
        } finally {
            archive.delete()
        }
    }
}
