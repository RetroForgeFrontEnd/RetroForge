package com.retroforge.app.ui.pc

import com.retroforge.app.R
import com.retroforge.core.ui.str
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
        FreeGame(title, "dos", str(R.string.free_games_shareware), about, "DOS/$title ($year).zip", "$title ($year).zip")
    private fun cart(title: String, folder: String, system: String, file: String, about: String, kind: String = str(R.string.free_games_homebrew)) =
        FreeGame(title, system, kind, about, "$folder/$file", file)

    private const val NES = "Nintendo - Nintendo Entertainment System"
    private const val SNES = "Nintendo - Super Nintendo Entertainment System"
    private const val MD = "Sega - Mega Drive - Genesis"
    private const val GB = "Nintendo - GameBoy"
    private const val GBA = "Nintendo - GameBoy Advance"

    val all get() = listOf(
        FreeGame("Doom (shareware)", "doom", str(R.string.free_games_shareware), str(R.string.free_games_knee_deep_in_the_dead_the),
            "DOOM/Doom (Shareware).zip", "Doom (Shareware).wad", extract = "doom1.wad"),
        dos("Wolfenstein 3D", 1991, str(R.string.free_games_escape_from_castle_wolfenstein_the_shareware)),
        dos("Commander Keen 1 - Marooned on Mars", 1990, str(R.string.free_games_billy_blaze_s_first_adventure_the)),
        dos("Commander Keen 4 - Secret of the Oracle", 1991, str(R.string.free_games_keen_s_best_loved_episode_given)),
        dos("Raptor - Call of the Shadows", 1999, str(R.string.free_games_the_vertical_shooter_s_shareware_episode)),
        cart("Nova the Squirrel", NES, "nes", "Nova the Squirrel.nes", str(R.string.free_games_a_modern_platformer_for_the_nes)),
        cart("Twin Dragons", NES, "nes", "Twin Dragons.nes", str(R.string.free_games_two_player_platformer_made_for_the)),
        cart("Super Tilt Bro", NES, "nes", "Super Tilt Bro (Europe).nes", str(R.string.free_games_a_platform_fighter_on_the_nes)),
        cart("Alter Ego", NES, "nes", "Alter Ego.nes", str(R.string.free_games_puzzle_platformer_swap_places_with_your)),
        cart("Spacegulls", NES, "nes", "Spacegulls (v1.1).nes", str(R.string.free_games_co_op_space_platformer)),
        cart("N-Warp Daisakusen", SNES, "snes", "N-Warp Daisakusen (Europe).zip", str(R.string.free_games_a_shoot_em_up_made_for)),
        cart("Super Boss Gaiden", SNES, "snes", "Super Boss Gaiden (Japan).zip", str(R.string.free_games_beat_em_up_homebrew_for_the)),
        cart("Cave Story (Mega Drive)", MD, "megadrive", "Cave Story (v0.7.0).zip", str(R.string.free_games_cave_story_ported_to_the_mega)),
        cart("Break An Egg", MD, "megadrive", "Break An Egg.md", str(R.string.free_games_mega_drive_puzzle_homebrew)),
        cart("Ultimate Tetris", MD, "megadrive", "Ultimate Tetris.md", str(R.string.free_games_falling_blocks_on_the_mega_drive)),
        cart("Deadeus", GB, "gb", "Deadeus.gb", str(R.string.free_games_a_horror_adventure_for_the_game)),
        cart("Mona and the Witch Hat", GB, "gb", "Mona And The Witch Hat.zip", str(R.string.free_games_game_boy_platformer)),
        cart("Gunman Clive (demo)", GB, "gb", "Gunman Clive (USA) (Demo).zip", str(R.string.free_games_the_demo_of_the_hand_drawn), kind = str(R.string.free_games_demo)),
        cart("Celeste Classic", GBA, "gba", "Celeste Classic (v1.0).zip", str(R.string.free_games_the_original_pico_8_celeste_on)),
        FreeGame("8 Bit Panda", "tic80", str(R.string.free_games_freeware), str(R.string.free_games_cute_tic_80_platformer), "TIC-80/8 Bit Panda.zip", "8 Bit Panda.tic", extract = ".tic"),
        FreeGame("Shadow Over the Twelve Lands", "tic80", str(R.string.free_games_freeware), str(R.string.free_games_a_tic_80_adventure), "TIC-80/Shadow Over the Twelve Lands.zip",
            "Shadow Over the Twelve Lands.tic", extract = ".tic"),
    )

    /**
     * Downloads [game] into [folder] (a game folder the user picked, with write access): [system]/[fileName], as the
     * library expects. Throws with a reason when it can't.
     */
    fun install(game: FreeGame, resolver: ContentResolver, folder: Uri, prefix: String = "", onProgress: (Float?) -> Unit = {}) {
        val url = BASE + game.path.split('/').joinToString("/") { Uri.encode(it) }
        val bytes = com.retroforge.app.Downloads.bytes(url, onProgress = onProgress)
        val content = game.extract?.let { want -> unzip(bytes, want) ?: throw IOException(str(R.string.free_games_the_download_didn_t_have_the)) } ?: bytes
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
