package com.retroforge.pcplayer.steam

import com.retroforge.pcplayer.R
import com.retroforge.core.ui.str
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Gets a Steam game ready to start without the Steam client, the way GameNative does it: the game's steam_api DLLs
 * are swapped for the Goldberg Steam emulator's (gbe_fork, LGPL-3.0; the build GameNative ships), and its
 * steam_settings folder gets the signed-in account and, where the game uses one, its encrypted app ticket from Steam.
 * It only goes ahead when Steam confirms the account owns the game. The original DLLs are kept beside them as
 * `<name>.orig`.
 *
 * DLC isn't unlocked: only what Steam's ticket says the account has.
 */
class SteamGameSetup(private val runtimeRoot: File, private val account: SteamAccount) {

    /** The game folder's steam_api / steam_api64 DLLs, or empty when the game doesn't use Steam. */
    fun steamDlls(gameDir: File): List<File> = gameDir.walkTopDown().maxDepth(6)
        .filter { it.isFile && (it.name.equals("steam_api.dll", true) || it.name.equals("steam_api64.dll", true)) }
        .toList()

    /** The game's Steam app id, from a steam_appid.txt in its folder, or [known] (from the PC library). */
    fun appId(gameDir: File, known: Int?): Int? {
        known?.takeIf { it > 0 }?.let { return it }
        return gameDir.walkTopDown().maxDepth(6)
            .firstOrNull { it.isFile && it.name.equals("steam_appid.txt", true) && it.parentFile?.name != "steam_settings" }
            ?.readText()?.trim()?.toIntOrNull()
    }

    /**
     * Sets the game up for [appId], with Steam lobbies between players on the same (or RetroForge's virtual) network
     * when [lobbies] is set ("Play together" in the player's menu). Throws with a message for the user when it can't (not signed in, Steam gave no
     * ticket for this account, the emulator DLLs couldn't be downloaded).
     */
    suspend fun prepare(gameDir: File, appId: Int, lobbies: Boolean = false) {
        val dlls = steamDlls(gameDir)
        if (dlls.isEmpty()) return
        if (!account.signedIn) throw IOException(str(R.string.steam_game_setup_this_is_a_steam_game_sign))
        val access = account.gameAccess(appId) ?: throw IOException(str(R.string.steam_game_setup_sign_in_to_steam_on_retroforge))
        if (!access.owned) throw IOException(str(R.string.steam_game_setup_steam_says_this_account_doesn_t, appId))
        val ticket = access.encryptedTicket
        for (dll in dlls) {
            val is64 = dll.name.equals("steam_api64.dll", true)
            val ours = emulatorDll(is64)
            val original = File(dll.parentFile, dll.name + ".orig")
            if (!original.isFile && sha256(dll) != sha256(ours)) {
                writeInterfaces(dll)
                dll.copyTo(original)
            }
            ours.copyTo(dll, overwrite = true)
            writeSettings(dll.parentFile!!, appId, ticket, lobbies)
        }
    }

    /** Puts the game's own DLLs back (for the Steam client or another app). */
    fun restore(gameDir: File) {
        gameDir.walkTopDown().maxDepth(6).filter { it.isFile && it.name.endsWith(".dll.orig", true) }.forEach { original ->
            original.copyTo(File(original.parentFile, original.name.removeSuffix(".orig")), overwrite = true)
            original.delete()
        }
    }

    private fun writeSettings(dir: File, appId: Int, ticket: String?, lobbies: Boolean) {
        File(dir, "steam_appid.txt").takeIf { !it.exists() }?.writeText(appId.toString())
        val settings = File(dir, "steam_settings").apply { mkdirs() }
        File(settings, "steam_appid.txt").writeText(appId.toString())
        // gbe's networking: Steam lobbies and the game's Steam networking between players on the same network (or
        // RetroForge's virtual LAN). On only for "Play together"; single-player doesn't need it. (It used to hang
        // SteamAPI_Init: Windows' adapter list was broken under Wine on Android, which libpcexec.so now fixes.)
        // disable_lan_only=1: gbe leaves Windows' own networking calls alone.
        File(settings, "configs.main.ini").writeText(
            "[main::connectivity]\ndisable_lan_only=1\ndisable_networking=${if (lobbies) 0 else 1}\n",
        )
        val steamId = account.steamId64
        val accountId = steamId and 0xFFFFFFFFL
        File(settings, "configs.user.ini").writeText(
            buildString {
                appendLine("[user::general]")
                appendLine("account_name=${account.accountName.orEmpty()}")
                appendLine("account_steamid=$steamId")
                appendLine("language=english")
                if (ticket != null) appendLine("ticket=$ticket")
                appendLine()
                appendLine("[user::saves]")
                appendLine("local_save_path=C:\\Program Files (x86)\\Steam\\userdata\\$accountId")
            },
        )
    }

    /** Lists the Steam interface versions the game's own DLL was built with, so the emulator offers the same ones. */
    private fun writeInterfaces(dll: File) {
        val out = File(dll.parentFile, "steam_interfaces.txt")
        if (out.exists()) return
        val found = sortedSetOf<String>()
        val text = StringBuilder()
        fun flush() {
            if (text.length >= 10 && INTERFACE.matches(text)) found += text.toString()
            text.setLength(0)
        }
        for (b in dll.readBytes()) {
            val c = b.toInt() and 0xFF
            if (c in 0x20..0x7E) text.append(c.toChar()) else flush()
        }
        flush()
        if (found.isNotEmpty()) out.writeText(found.joinToString("\n"))
    }

    /** The emulator's DLL, downloaded once into the runtime folder. */
    private fun emulatorDll(is64: Boolean): File {
        val name = if (is64) "steam_api64.dll" else "steam_api.dll"
        val file = File(runtimeRoot, "steampipe/$name")
        if (file.isFile && sha256(file) == (if (is64) SHA_64 else SHA_32)) return file
        file.parentFile?.mkdirs()
        val connection = (URL("$DLL_SOURCE/$name").openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
        }
        try {
            if (connection.responseCode != 200) throw IOException(str(R.string.steam_game_setup_couldn_t_download_the_steam_emulator, connection.responseCode))
            val part = File(file.path + ".part")
            connection.inputStream.use { input -> part.outputStream().use { input.copyTo(it) } }
            if (sha256(part) != (if (is64) SHA_64 else SHA_32)) {
                part.delete()
                throw IOException(str(R.string.steam_game_setup_the_steam_emulator_download_didn_t))
            }
            if (!part.renameTo(file)) throw IOException(str(R.string.steam_game_setup_couldn_t_save_the_steam_emulator))
            return file
        } finally {
            connection.disconnect()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        private val INTERFACE = Regex("^Steam[A-Za-z]+[0-9]{3}$", RegexOption.IGNORE_CASE)

        /** GameNative's gbe_fork build, pinned to the commit that last changed it ("Updated goldberg version …"). */
        private const val DLL_SOURCE =
            "https://raw.githubusercontent.com/utkarshdalal/GameNative/08d2db098917ae029e4a4acccb73f37f8f699434/app/src/main/assets/steampipe"
        private const val SHA_64 = "513430ac8b869b0eed258d9e5feba5176a35446b3b61a63d01ab1d87683cdd1b"
        private const val SHA_32 = "4cc9dfcab8b4df0db507fa91d0acec6e58693fd4edd78dc80d39bda8072d08ed"
    }
}
