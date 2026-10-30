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
    SHORTCUT("desktop", "Winlator shortcut", null);

    companion object {
        fun ofExtension(ext: String) = entries.firstOrNull { it.extension.equals(ext, ignoreCase = true) }
    }
}

/** A PC game in RetroForge's PC library: a store's game id (started in GameNative) or a Winlator shortcut file. */
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

    /** The game's box art, downloaded once and kept (Steam's own art; null for other stores or when it can't be had). */
    fun cover(game: PcGame): File? {
        val url = game.coverUrl ?: return null
        val file = File(context.filesDir, "pc_covers/${game.appId}.jpg")
        if (file.isFile) return file
        return runCatching {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply { connectTimeout = 15_000; readTimeout = 20_000 }
            try {
                if (connection.responseCode != 200) return null
                val bytes = connection.inputStream.use { it.readBytes() }
                file.parentFile?.mkdirs()
                File(file.path + ".part").apply { writeBytes(bytes) }.renameTo(file)
                file
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
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
                    val source = PcSource.ofExtension(name.substringAfterLast('.', "")) ?: continue
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
     * Starts [game] in the app that runs it. Returns null when it started, else what's missing for the player
     * (the app isn't installed, say).
     */
    fun launch(game: PcGame): String? {
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
