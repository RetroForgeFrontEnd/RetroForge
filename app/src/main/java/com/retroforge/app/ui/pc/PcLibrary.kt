package com.retroforge.app.ui.pc

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Where a PC game lives, which says which app starts it. The file extensions are ES-DE's, so a "windows" or "steam"
 * folder set up for ES-DE (or the files GameNative and Winlator export for frontends) works as it is.
 */
enum class PcSource(val extension: String, val label: String, val gameNative: String?) {
    STEAM("steam", "Steam", "STEAM"),
    EPIC("epic", "Epic", "EPIC"),
    GOG("gog", "GOG", "GOG"),
    AMAZON("amazon", "Amazon", "AMAZON"),
    CUSTOM("pcgame", "GameNative", "CUSTOM_GAME"),
    SHORTCUT("desktop", "Winlator shortcut", null),

    /** A Windows game folder copied into RetroForge, played in RetroForge's own PC player ([shortcutPath] is its .exe). */
    WINDOWS("exe", "RetroForge PC player", null);

    companion object {
        fun ofExtension(ext: String) = entries.firstOrNull { it.extension.equals(ext, ignoreCase = true) }
    }
}

/**
 * A PC game in RetroForge's PC library: a store's game id (started in GameNative), a Winlator shortcut file, or a
 * Windows game's .exe (started in RetroForge's own PC player).
 */
data class PcGame(val title: String, val source: PcSource, val appId: String? = null, val shortcutPath: String? = null) {
    val key get() = "${source.name}:${appId ?: shortcutPath}"

    /** Steam's own box art (no account or key needed); other stores show the title instead. */
    val coverUrl get() = if (source == PcSource.STEAM && appId != null) "https://cdn.cloudflare.steamstatic.com/steam/apps/$appId/library_600x900.jpg" else null
}

/** A game found by Steam's store search. */
data class SteamHit(val appId: String, val name: String)

/**
 * RetroForge's PC library: the list (kept in pc_games.json), adding games from a folder or Steam's store search, and
 * starting them in the app that runs them (the same launch requests ES-DE uses).
 */
class PcLibrary(private val context: Context) {
    private val file = File(context.filesDir, "pc_games.json")

    fun all(): List<PcGame> = runCatching {
        val array = JSONArray(file.readText())
        (0 until array.length()).map { array.getJSONObject(it) }.mapNotNull { o ->
            val source = runCatching { PcSource.valueOf(o.getString("source")) }.getOrNull() ?: return@mapNotNull null
            PcGame(o.getString("title"), source, o.optString("appId").ifBlank { null }, o.optString("shortcutPath").ifBlank { null })
        }
    }.getOrDefault(emptyList()).sortedBy { it.title.lowercase() }

    private fun save(games: List<PcGame>) {
        file.writeText(JSONArray(games.distinctBy { it.key }.map { g ->
            JSONObject().put("title", g.title).put("source", g.source.name).put("appId", g.appId ?: "").put("shortcutPath", g.shortcutPath ?: "")
        }).toString())
    }

    fun add(games: List<PcGame>): Int {
        val before = all()
        val known = before.map { it.key }.toSet()
        val added = games.filter { it.key !in known }.distinctBy { it.key }
        save(before + added)
        return added.size
    }

    fun remove(game: PcGame) = save(all().filter { it.key != game.key })

    /**
     * The game's box art, downloaded once and kept: Steam's own art for Steam games, and for Windows games from
     * elsewhere the art of the same game on Steam's store, found by name. Steam's tall library art where the game
     * has it, else its wide store banner (older games). Null when there's none (other stores, no match).
     */
    fun cover(game: PcGame): File? {
        val appId = when {
            game.source == PcSource.STEAM -> game.appId
            game.source == PcSource.WINDOWS -> game.appId?.takeIf { it.toIntOrNull() != null } ?: storeMatch(game.title)
            else -> null
        } ?: return null
        val dir = File(context.filesDir, "pc_covers")
        File(dir, "$appId.jpg").takeIf { it.isFile }?.let { return it }
        if (File(dir, "$appId.none").isFile) return null
        for (image in listOf("library_600x900.jpg", "header.jpg")) {
            val bytes = runCatching { download("https://cdn.cloudflare.steamstatic.com/steam/apps/$appId/$image") }.getOrNull() ?: continue
            dir.mkdirs()
            val file = File(dir, "$appId.jpg")
            File(file.path + ".part").apply { writeBytes(bytes) }.renameTo(file)
            return file
        }
        // Steam has no art for it (a developer tool, say): don't ask again every time the tab opens.
        dir.mkdirs()
        File(dir, "$appId.none").createNewFile()
        return null
    }

    /** The Steam store's app id for a game by this name (its box art), found once and remembered; null if none. */
    private fun storeMatch(title: String): String? {
        fun simple(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
        val name = title.replace(Regex("""\s*\((RetroForge|GOG|Steam)\)\s*$"""), "").trim()
        val memo = File(context.filesDir, "pc_covers/match-${simple(name)}.txt")
        if (memo.isFile) return memo.readText().trim().takeIf { it.isNotEmpty() }
        val hits = runCatching { searchSteam(name) }.getOrNull() ?: return null
        val wanted = simple(name)
        val hit = hits.firstOrNull { simple(it.name) == wanted }
            ?: hits.firstOrNull { simple(it.name).startsWith(wanted) || wanted.startsWith(simple(it.name)) && simple(it.name).length >= 4 }
        memo.parentFile?.mkdirs()
        memo.writeText(hit?.appId.orEmpty())
        return hit?.appId
    }

    private fun download(url: String): ByteArray? {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply { connectTimeout = 15_000; readTimeout = 20_000 }
        try {
            if (connection.responseCode != 200) return null
            return connection.inputStream.use { it.readBytes() }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * Every PC game file in a folder the user picked (and its subfolders): .steam/.epic/.gog/.amazon/.pcgame files
     * holding a store id, and .desktop shortcuts from Winlator Cmod or WinNative. Returns the games and how many
     * shortcuts couldn't be used (Winlator needs a real file path, so they must be on the phone's storage or SD card).
     */
    fun scan(tree: Uri, startId: String = DocumentsContract.getTreeDocumentId(tree)): Pair<List<PcGame>, Int> {
        val resolver = context.contentResolver
        val found = mutableListOf<PcGame>()
        var unusable = 0
        fun walk(parentId: String) {
            val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
            val columns = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE)
            resolver.query(uri, columns, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getString(0)
                    val name = c.getString(1)
                    if (c.getString(2) == Document.MIME_TYPE_DIR) { walk(id); continue }
                    val source = PcSource.ofExtension(name.substringAfterLast('.', ""))?.takeIf { it != PcSource.WINDOWS } ?: continue
                    if (c.getLong(3) > 64 * 1024) continue
                    val text = runCatching {
                        resolver.openInputStream(DocumentsContract.buildDocumentUriUsingTree(tree, id))?.use { it.readBytes().decodeToString() }
                    }.getOrNull().orEmpty()
                    val title = name.substringBeforeLast('.')
                    if (source == PcSource.SHORTCUT) {
                        val path = realPath(id)
                        if (path == null) { unusable++; continue }
                        val shown = Regex("""(?m)^Name=(.+)$""").find(text)?.groupValues?.get(1)?.trim() ?: title
                        found += PcGame(shown, source, shortcutPath = path)
                    } else {
                        val appId = text.trim().lineSequence().firstOrNull()?.trim()?.takeIf { it.isNotEmpty() } ?: continue
                        found += PcGame(title, source, appId = appId)
                    }
                }
            }
        }
        walk(startId)
        return found to unusable
    }

    /**
     * The PC folders in the user's game folders, as ES-DE lays them out: "windows", "steam" … beside snes, psx and the
     * rest (in the folder itself, or in its "roms" folder). Scanned like [scan]; folders that can't be read are skipped.
     */
    fun scanGameFolders(roots: List<Uri>): Pair<List<PcGame>, Int> {
        val resolver = context.contentResolver
        fun folders(tree: Uri, parentId: String): List<Pair<String, String>> = runCatching {
            val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
            resolver.query(uri, arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE), null, null, null)?.use { c ->
                buildList { while (c.moveToNext()) if (c.getString(2) == Document.MIME_TYPE_DIR) add(c.getString(0) to c.getString(1)) }
            }.orEmpty()
        }.getOrDefault(emptyList())
        val games = mutableListOf<PcGame>()
        var unusable = 0
        for (tree in roots) {
            val top = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull() ?: continue
            val here = folders(tree, top)
            val inRoms = here.filter { it.second.equals("roms", ignoreCase = true) }.flatMap { folders(tree, it.first) }
            for ((id, name) in here + inRoms) {
                if (name.lowercase() !in PC_FOLDERS) continue
                runCatching { scan(tree, id) }.onSuccess { (found, skipped) -> games += found; unusable += skipped }
            }
        }
        return games to unusable
    }

    /** A storage document's file path ("primary:Download/x" → /storage/emulated/0/Download/x), or null if it has none. */
    private fun realPath(documentId: String): String? {
        val volume = documentId.substringBefore(':', "")
        val rest = documentId.substringAfter(':', "")
        return when {
            volume == "primary" -> "/storage/emulated/0/$rest"
            Regex("[0-9A-F]{4}-[0-9A-F]{4}").matches(volume) -> "/storage/$volume/$rest"
            else -> null
        }
    }

    /**
     * Where Windows games for RetroForge's PC player live on the device itself: RetroForge's private storage. Not the
     * shared Android/data folder: there every file the game touches goes through Android's storage service, which
     * some devices' Android 16 aborts under Wine's file traffic (a Galaxy Tab A9+), closing the game.
     */
    fun windowsGamesDir(): File = File(context.filesDir, "PC games")

    /** The shared folder games went to before (still played from there). */
    private fun sharedGamesDir(): File? = context.getExternalFilesDir(null)?.let { File(it, "PC games") }

    /** Each SD card's folder for PC games (Android gives the app one on each, with a real path the player can use). */
    private fun sdCardGamesDirs(): List<File> = context.getExternalFilesDirs(null).drop(1).filterNotNull()
        .filter { android.os.Environment.getExternalStorageState(it) == android.os.Environment.MEDIA_MOUNTED }
        .map { File(it, "PC games") }

    /** Every place Windows games can live: the device, each SD card in, and the shared folder from before. */
    fun windowsGamesDirs(): List<File> = listOf(windowsGamesDir()) + sdCardGamesDirs() + listOfNotNull(sharedGamesDir())

    /** Where a new game goes: the device or an SD card, whichever has the most free space. */
    fun roomiestGamesDir(): File = (listOf(windowsGamesDir()) + sdCardGamesDirs()).maxBy { it.apply { mkdirs() }.usableSpace }

    /** Whether [dir] is on an SD card rather than the device's own storage. */
    fun onSdCard(dir: File): Boolean = sdCardGamesDirs().any { dir.canonicalPath.startsWith(it.canonicalFile.parentFile!!.canonicalPath) }

    /**
     * Copies a Windows game folder the user picked into [roomiestGamesDir] (RetroForge's PC player needs the files
     * where it can open them directly) and adds it. [onProgress] gets 0–100. Returns the game, or null when the
     * folder has no .exe to start.
     */
    fun addWindowsGame(tree: Uri, onProgress: (Int) -> Unit): PcGame? {
        val resolver = context.contentResolver
        val top = DocumentsContract.getTreeDocumentId(tree)
        class Item(val id: String, val path: String, val size: Long)
        val files = mutableListOf<Item>()
        fun walk(parentId: String, path: String) {
            val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
            val columns = arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE)
            resolver.query(uri, columns, null, null, null)?.use { c ->
                while (c.moveToNext()) {
                    val child = if (path.isEmpty()) c.getString(1) else "$path/${c.getString(1)}"
                    if (c.getString(2) == Document.MIME_TYPE_DIR) walk(c.getString(0), child)
                    else files += Item(c.getString(0), child, c.getLong(3))
                }
            }
        }
        walk(top, "")
        val name = resolver.query(DocumentsContract.buildDocumentUriUsingTree(tree, top), arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)
            ?.use { if (it.moveToFirst()) it.getString(0) else null } ?: top.substringAfterLast('/')
        if (files.none { it.path.endsWith(".exe", ignoreCase = true) }) return null
        val folder = File(roomiestGamesDir(), name.replace(Regex("""[\/:*?"<>|]"""), "_"))
        val total = files.sumOf { it.size }.coerceAtLeast(1)
        var copied = 0L
        for (item in files) {
            val out = File(folder, item.path)
            if (out.isFile && out.length() == item.size) { copied += item.size; continue }
            out.parentFile?.mkdirs()
            resolver.openInputStream(DocumentsContract.buildDocumentUriUsingTree(tree, item.id))?.use { input ->
                out.outputStream().use { output ->
                    val buffer = ByteArray(1 shl 16)
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        copied += n
                        onProgress((copied * 100 / total).toInt())
                    }
                }
            }
        }
        val exe = com.retroforge.pcplayer.GameExe.best(folder) ?: return null
        val game = withSteamAppId(PcGame(name, PcSource.WINDOWS, shortcutPath = exe.path))
        add(listOf(game))
        return game
    }

    /** The .exe files in a Windows game's folder that might start it, likeliest first. */
    fun exeChoices(game: PcGame): List<File> {
        val folder = windowsGameFolder(game) ?: return emptyList()
        return com.retroforge.pcplayer.GameExe.candidates(folder)
    }

    /** The game's own folder in one of [windowsGamesDirs] (its .exe can be in a subfolder of it). */
    fun windowsGameFolder(game: PcGame): File? {
        val exe = File(game.shortcutPath ?: return null)
        val roots = windowsGamesDirs().map { it.canonicalFile }.toSet()
        var dir = exe.parentFile?.canonicalFile ?: return null
        while (dir.parentFile != null && dir.parentFile !in roots) dir = dir.parentFile!!
        return dir.takeIf { it.parentFile in roots }
    }

    /** Starts [game] with [exe] from now on (the user's choice). Returns the updated game. */
    fun setExe(game: PcGame, exe: File): PcGame {
        val updated = game.copy(shortcutPath = exe.path)
        save(all().map { if (it.key == game.key) updated else it })
        return updated
    }

    /**
     * A Windows game that uses Steam (its folder has steam_api DLLs) needs its Steam app id for RetroForge's PC
     * player to get its ticket: from a steam_appid.txt in the folder, else Steam's store search by the game's name.
     * Saves what it finds. Call off the main thread.
     */
    fun withSteamAppId(game: PcGame): PcGame {
        if (game.source != PcSource.WINDOWS || !game.appId.isNullOrBlank()) return game
        val folder = File(game.shortcutPath ?: return game).parentFile ?: return game
        val files = folder.walkTopDown().maxDepth(6).filter { it.isFile }.toList()
        if (files.none { it.name.equals("steam_api.dll", true) || it.name.equals("steam_api64.dll", true) }) return game
        fun simple(s: String) = s.lowercase().filter { it.isLetterOrDigit() }
        val title = game.title.removeSuffix(" (RetroForge)")
        val appId = files.firstOrNull { it.name.equals("steam_appid.txt", true) }?.readText()?.trim()?.takeIf { it.toIntOrNull() != null }
            ?: runCatching { searchSteam(title) }.getOrDefault(emptyList()).firstOrNull { simple(it.name) == simple(title) }?.appId
            ?: return game
        val updated = game.copy(appId = appId)
        save(all().map { if (it.key == game.key) updated else it })
        return updated
    }

    /**
     * Starts [game] in the app that runs it. Returns null when it started, else what's missing for the player
     * (the app isn't installed, say).
     */
    fun launch(game: PcGame): String? {
        if (game.source == PcSource.WINDOWS) {
            if (!com.retroforge.pcplayer.PcRuntime.deviceSupported) return "RetroForge's PC player needs a 64-bit ARM device (most phones and handhelds); this one is ${android.os.Build.SUPPORTED_ABIS.firstOrNull()}."
            val exe = File(game.shortcutPath ?: return "This game has no .exe set.")
            if (!exe.isFile) return "${exe.name} isn't in RetroForge's PC games folder any more. Add the game's folder again."
            context.startActivity(
                com.retroforge.pcplayer.PcPlayerActivity.intent(context, exe, game.title, game.appId?.toIntOrNull())
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return null
        }
        val tries: List<Intent> = when (game.source) {
            PcSource.SHORTCUT -> WINLATORS.map { (pkg, activity) ->
                Intent().setComponent(ComponentName(pkg, activity)).putExtra("shortcut_path", game.shortcutPath)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            else -> buildList {
                add(
                    Intent("app.gamenative.LAUNCH_GAME").setComponent(ComponentName("app.gamenative", "app.gamenative.MainActivity"))
                        .putExtra("game_source", game.source.gameNative).apply {
                            game.appId?.toIntOrNull()?.let { putExtra("app_id", it) } ?: putExtra("app_id", game.appId)
                        },
                )
                // Steam games: GameHub Lite too, for devices that use it instead.
                if (game.source == PcSource.STEAM) GAMEHUB_LITE.forEach { pkg ->
                    add(
                        Intent("gamehub.lite.LAUNCH_GAME").setComponent(ComponentName(pkg, "com.xj.landscape.launcher.ui.gamedetail.GameDetailActivity"))
                            .putExtra("autoStartGame", true).putExtra("steamAppId", game.appId),
                    )
                }
            }
        }
        for (intent in tries) {
            val ok = runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                .onFailure { if (it !is ActivityNotFoundException && it !is SecurityException) throw it }.isSuccess
            if (ok) return null
        }
        return when (game.source) {
            PcSource.SHORTCUT -> "Winlator Cmod (or WinNative) isn't installed. Get it from the PC tab, then try again."
            else -> "GameNative isn't installed. Get it from the PC tab, sign in to ${game.source.label} there and install the game, then try again."
        }
    }

    companion object {
        /** Apps that start a Winlator shortcut file, as ES-DE knows them: Winlator Cmod (and its Glibc and PRoot builds), WinNative. */
        private val WINLATORS = listOf(
            "com.winlator.cmod" to "com.winlator.cmod.XServerDisplayActivity",
            "com.winlator.vanilla" to "com.winlator.cmod.XServerDisplayActivity",
            "com.winlator" to "com.winlator.XServerDisplayActivity",
            "com.cmodded.winlator" to "com.winlator.XServerDisplayActivity",
            "com.winnative.cmod" to "com.winlator.cmod.runtime.display.XServerDisplayActivity",
        )
        private val GAMEHUB_LITE = listOf("gamehub.lite", "emuready.gamehub.lite")

        /** Game folder names that hold PC games (ES-DE's and RetroBat's). */
        private val PC_FOLDERS = setOf("windows", "steam", "epic", "gog", "pc", "pcwindows", "windows9x", "win")

        /** Steam's public store search (no account or key): the user's game, to add by name. */
        fun searchSteam(term: String): List<SteamHit> {
            val url = "https://store.steampowered.com/api/storesearch/?l=english&cc=GB&term=" + URLEncoder.encode(term, "UTF-8")
            val connection = (URL(url).openConnection() as HttpURLConnection).apply { connectTimeout = 15_000; readTimeout = 20_000 }
            try {
                if (connection.responseCode != 200) error("Steam answered ${connection.responseCode}")
                val items = JSONObject(connection.inputStream.use { it.readBytes().decodeToString() }).optJSONArray("items") ?: return emptyList()
                return (0 until items.length()).map { items.getJSONObject(it) }.map { SteamHit(it.get("id").toString(), it.getString("name")) }
            } finally {
                connection.disconnect()
            }
        }
    }
}
