package com.retroforge.app.ui.pc

import android.content.ContentResolver
import android.net.Uri
import com.retroforge.app.player.FolderTarget
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.zip.ZipInputStream

/**
 * A game anyone may download and share: shareware episodes, freeware and homebrew, as RetroArch's own content
 * downloader offers them (libretro's build server). [path] is under [FreeGames.BASE]; [system] is the game folder it
 * goes in; [extract] picks one file out of a zip (Doom's WAD, a TIC-80 cartridge) where the player needs the file
 * itself, saved as [fileName].
 */
data class FreeGame(
    val title: String,
    val system: String,
    val kind: String,
    val about: String,
    val path: String,
    val fileName: String,
    val extract: String? = null,
)

object FreeGames {
    const val BASE = "https://buildbot.libretro.com/assets/cores/"

    private fun dos(title: String, year: Int, about: String) =
        FreeGame(title, "dos", "Shareware", about, "DOS/$title ($year).zip", "$title ($year).zip")
    private fun cart(title: String, folder: String, system: String, file: String, about: String, kind: String = "Homebrew") =
        FreeGame(title, system, kind, about, "$folder/$file", file)

    private const val NES = "Nintendo - Nintendo Entertainment System"
    private const val SNES = "Nintendo - Super Nintendo Entertainment System"
    private const val MD = "Sega - Mega Drive - Genesis"
    private const val GB = "Nintendo - GameBoy"
    private const val GBA = "Nintendo - GameBoy Advance"

    val all = listOf(
        FreeGame("Doom (shareware)", "doom", "Shareware", "Knee-Deep in the Dead, the first episode, as id Software gave it away.",
            "DOOM/Doom (Shareware).zip", "Doom (Shareware).wad", extract = "doom1.wad"),
        dos("Wolfenstein 3D", 1991, "Escape from Castle Wolfenstein: the shareware episode."),
        dos("Commander Keen 1 - Marooned on Mars", 1990, "Billy Blaze's first adventure, the shareware episode."),
        dos("Commander Keen 4 - Secret of the Oracle", 1991, "Keen's best-loved episode, given away as shareware."),
        dos("Raptor - Call of the Shadows", 1999, "The vertical shooter's shareware episode."),
        cart("Nova the Squirrel", NES, "nes", "Nova the Squirrel.nes", "A modern platformer for the NES."),
        cart("Twin Dragons", NES, "nes", "Twin Dragons.nes", "Two-player platformer made for the NES."),
        cart("Super Tilt Bro", NES, "nes", "Super Tilt Bro (Europe).nes", "A platform fighter on the NES."),
        cart("Alter Ego", NES, "nes", "Alter Ego.nes", "Puzzle platformer: swap places with your mirror self."),
        cart("Spacegulls", NES, "nes", "Spacegulls (v1.1).nes", "Co-op space platformer."),
        cart("N-Warp Daisakusen", SNES, "snes", "N-Warp Daisakusen (Europe).zip", "A shoot-'em-up made for the SNES."),
        cart("Super Boss Gaiden", SNES, "snes", "Super Boss Gaiden (Japan).zip", "Beat-'em-up homebrew for the Super Famicom."),
        cart("Cave Story (Mega Drive)", MD, "megadrive", "Cave Story (v0.7.0).zip", "Cave Story ported to the Mega Drive by fans."),
        cart("Break An Egg", MD, "megadrive", "Break An Egg.md", "Mega Drive puzzle homebrew."),
        cart("Ultimate Tetris", MD, "megadrive", "Ultimate Tetris.md", "Falling blocks on the Mega Drive."),
        cart("Deadeus", GB, "gb", "Deadeus.gb", "A horror adventure for the Game Boy."),
        cart("Mona and the Witch Hat", GB, "gb", "Mona And The Witch Hat.zip", "Game Boy platformer."),
        cart("Gunman Clive (demo)", GB, "gb", "Gunman Clive (USA) (Demo).zip", "The demo of the hand-drawn platformer.", kind = "Demo"),
        cart("Celeste Classic", GBA, "gba", "Celeste Classic (v1.0).zip", "The original PICO-8 Celeste, on the GBA."),
        FreeGame("8 Bit Panda", "tic80", "Freeware", "Cute TIC-80 platformer.", "TIC-80/8 Bit Panda.zip", "8 Bit Panda.tic", extract = ".tic"),
        FreeGame("Shadow Over the Twelve Lands", "tic80", "Freeware", "A TIC-80 adventure.", "TIC-80/Shadow Over the Twelve Lands.zip",
            "Shadow Over the Twelve Lands.tic", extract = ".tic"),
    )

    /**
     * Downloads [game] into [folder] (a game folder the user picked, with write access): [system]/[fileName], as the
     * library expects. Throws with a reason when it can't.
     */
    fun install(game: FreeGame, resolver: ContentResolver, folder: Uri, prefix: String = "", onProgress: (Float?) -> Unit = {}) {
        val url = BASE + game.path.split('/').joinToString("/") { Uri.encode(it) }
        val bytes = com.retroforge.app.Downloads.bytes(url, onProgress = onProgress)
        val content = game.extract?.let { want -> unzip(bytes, want) ?: throw IOException("the download didn't have the game file in it") } ?: bytes
        FolderTarget(resolver, folder).write("$prefix${game.system}/${game.fileName}", content)
    }

    /** "roms/" when [folder] is a RetroBat or ES-DE folder with its systems in a "roms" folder, else "". */
    fun prefixFor(resolver: ContentResolver, folder: Uri): String {
        val parent = android.provider.DocumentsContract.getTreeDocumentId(folder)
        val children = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(folder, parent)
        val hasRoms = runCatching {
            resolver.query(children, arrayOf(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME, android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)?.use { c ->
                generateSequence { if (c.moveToNext()) c.getString(0) to c.getString(1) else null }
                    .any { (name, mime) -> name.equals("roms", ignoreCase = true) && mime == android.provider.DocumentsContract.Document.MIME_TYPE_DIR }
            } ?: false
        }.getOrDefault(false)
        return if (hasRoms) "roms/" else ""
    }

    /** The first file in a zip whose name ends with [want] (case ignored). */
    private fun unzip(zip: ByteArray, want: String): ByteArray? = ZipInputStream(ByteArrayInputStream(zip)).use { input ->
        generateSequence { input.nextEntry }.firstOrNull { !it.isDirectory && it.name.endsWith(want, ignoreCase = true) }?.let { input.readBytes() }
    }
}
