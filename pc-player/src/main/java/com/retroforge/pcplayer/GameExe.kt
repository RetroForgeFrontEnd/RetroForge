package com.retroforge.pcplayer

import org.json.JSONObject
import java.io.File

/**
 * Which .exe in a Windows game's folder starts the game. In order:
 *  1. GOG's own answer: the primary play task in the folder's goggame-<id>.info.
 *  2. An .exe named like the folder (Snooker 19 Challenge Pack → Snooker19.exe counts).
 *  3. The biggest .exe in the folder itself, then in its subfolders.
 * Installers, uninstallers, crash reporters, redistributables and the like are never picked. (Unreal Engine's
 * starter .exes are fine to pick: the player runs the real game behind them, see [PcSession.unrealGame].)
 */
object GameExe {

    private val NOT_THE_GAME = Regex(
        "(?i)unins|setup|install|crash|redist|vcredist|dxsetup|directx|dotnet|report|launcherhelper|prereq|" +
            "easyanticheat|eac_|battleye|cefprocess|helper|updater|update\\.exe|config|settings|benchmark|7z|unitycrash",
    )

    /** The .exe to start, or null when the folder has none. */
    fun best(folder: File): File? = candidates(folder).firstOrNull()

    /** Every .exe that might start the game, likeliest first (for the user to choose from). */
    fun candidates(folder: File): List<File> {
        val exes = folder.walkTopDown().maxDepth(5)
            .filter { it.isFile && it.extension.equals("exe", true) && !NOT_THE_GAME.containsMatchIn(it.name) }
            .toList()
        val gog = gogPrimary(folder)
        val name = simple(folder.name)
        return exes.sortedWith(
            compareByDescending<File> { it == gog }
                .thenByDescending { matchesName(simple(it.nameWithoutExtension), name) }
                .thenBy { it.relativeTo(folder).path.count { c -> c == '/' || c == '\\' } }
                .thenByDescending { it.length() },
        )
    }

    /** GOG games say which program is the game in goggame-<id>.info: the play task marked primary. */
    private fun gogPrimary(folder: File): File? {
        val info = folder.listFiles { f -> f.name.startsWith("goggame-") && f.name.endsWith(".info") }?.firstOrNull() ?: return null
        return runCatching {
            val tasks = JSONObject(info.readText()).optJSONArray("playTasks") ?: return null
            (0 until tasks.length()).map { tasks.getJSONObject(it) }
                .firstOrNull { it.optBoolean("isPrimary") && it.optString("category", "game") == "game" && it.has("path") }
                ?.let { File(folder, it.getString("path").replace('\\', '/')) }
                ?.takeIf { it.isFile }
        }.getOrNull()
    }

    private fun simple(s: String) = s.lowercase().filter { it.isLetterOrDigit() }

    /** "snooker19" matches "snooker19challengepack"; very short names ("game") don't count. */
    private fun matchesName(exe: String, folder: String) =
        exe.length >= 4 && (exe == folder || folder.startsWith(exe) || exe.startsWith(folder))
}
