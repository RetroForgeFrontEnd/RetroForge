package com.retroforge.core.library

import com.retroforge.core.ui.str
import com.retroforge.artwork.HttpGet
import com.retroforge.artwork.JvmHttpGet
import com.retroforge.artwork.LibretroThumbnails
import com.retroforge.artwork.SgdbArtKind
import com.retroforge.artwork.SgdbGame
import com.retroforge.artwork.SgdbImage
import com.retroforge.artwork.SgdbResult
import com.retroforge.artwork.SteamGridDb
import com.retroforge.artwork.ThumbnailMatch
import com.retroforge.artwork.ThumbnailType
import com.retroforge.artwork.looksLikeImage
import com.retroforge.artwork.ScreenScraper
import com.retroforge.artwork.ScreenScraperApp
import com.retroforge.artwork.ScreenScraperUser
import com.retroforge.artwork.SsMedia
import com.retroforge.artwork.SsResult
import android.content.ContentResolver
import android.net.Uri
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

sealed interface CoverBatch {
    data class Running(val system: String, val done: Int, val total: Int, val found: Int) : CoverBatch
    data class Finished(val system: String, val found: Int, val missing: Int, val failed: Int) : CoverBatch
    data class Failed(val system: String, val reason: String) : CoverBatch
}

/**
 * Finds and saves cover art. libretro thumbnails (free, no key) can be fetched in bulk; SteamGridDB is used only
 * per game, on request, with the user's own key. Covers go to the app's private "covers" folder.
 */
class CoverRepository(
    private val db: LibraryDatabase,
    private val coversDir: File,
    private val cacheDir: File,
    private val http: HttpGet,
    steamGridDbKey: () -> String?,
    private val resolver: ContentResolver,
    screenScraperApp: () -> ScreenScraperApp? = { null },
    screenScraperUser: () -> ScreenScraperUser? = { null },
    private val clock: () -> Long = System::currentTimeMillis,
    /** For ScreenScraper lookups, which can be slow to answer. */
    screenScraperHttp: HttpGet = http,
) {
    val screenScraper = ScreenScraper(screenScraperHttp, screenScraperApp, screenScraperUser)
    private val dao = db.dao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val indexLock = Mutex()
    private val indexes = mutableMapOf<String, List<String>>()
    val steamGridDb = SteamGridDb(http, steamGridDbKey)

    val covers: Flow<List<CoverEntity>> = dao.covers()

    private val _batch = MutableStateFlow<CoverBatch?>(null)
    val batch: StateFlow<CoverBatch?> = _batch.asStateFlow()
    private var batchJob: Job? = null

    fun fileFor(cover: CoverEntity): File = File(coversDir, cover.file)

    fun hasLibretroSource(system: String) = LibretroThumbnails.folderFor(system) != null

    /** Best libretro box-art candidates for a game, best first. */
    suspend fun libretroMatches(game: GameEntity, limit: Int = 6): Result<List<ThumbnailMatch>> = withContext(Dispatchers.IO) {
        val folder = LibretroThumbnails.folderFor(game.system) ?: return@withContext Result.success(emptyList())
        runCatching { LibretroThumbnails.match(index(folder), folder, ThumbnailType.BOXART, game.fileName, limit) }
    }

    suspend fun searchSteamGridDb(term: String): SgdbResult<List<SgdbGame>> = withContext(Dispatchers.IO) { steamGridDb.search(term) }

    suspend fun steamGridDbCovers(gameId: Long): SgdbResult<List<SgdbImage>> = withContext(Dispatchers.IO) { steamGridDb.images(gameId, SgdbArtKind.COVER) }

    /** Downloads small preview images for the picker (never saved). */
    suspend fun preview(url: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching { http.get(url, emptyMap(), JvmHttpGet.MAX_IMAGE_BYTES) }.getOrNull()
            ?.takeIf { it.code == 200 && looksLikeImage(it.body) }?.body
    }

    /** Downloads [url] and makes it this game's cover. [storedUrl] is what's recorded (credentials removed). */
    suspend fun save(game: GameEntity, url: String, source: String, author: String?, storedUrl: String = url): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val response = http.get(url, emptyMap(), JvmHttpGet.MAX_IMAGE_BYTES)
            check(response.code == 200) { str(R.string.cover_the_server_answered_http, response.code) }
            check(looksLikeImage(response.body)) { str(R.string.cover_that_download_isn_t_an_image) }
            store(game, response.body, storedUrl, source, author)
        }
    }

    /** An image the user picked on the device (e.g. a scan saved from The Cover Project) becomes the cover. */
    suspend fun saveFromDevice(game: GameEntity, image: Uri): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = checkNotNull(resolver.openInputStream(image)) { str(R.string.cover_couldn_t_open_the_image) }.use { input ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(64 * 1024)
                while (true) {
                    val r = input.read(buf)
                    if (r < 0) break
                    out.write(buf, 0, r)
                    check(out.size() <= JvmHttpGet.MAX_IMAGE_BYTES) { str(R.string.cover_that_image_is_larger_than_15) }
                }
                out.toByteArray()
            }
            check(looksLikeImage(bytes)) { str(R.string.cover_that_file_isn_t_a_png) }
            store(game, bytes, "", SOURCE_DEVICE, null)
        }
    }

    /**
     * Looks the game up on ScreenScraper, on request only: file name and size, plus MD5 for files up to 64 MB.
     * Returns the cover images, best first.
     */
    suspend fun screenScraperCovers(game: GameEntity): Result<List<SsMedia>> = withContext(Dispatchers.IO) {
        runCatching {
            if (!screenScraper.available) error(str(R.string.cover_screenscraper_isn_t_available_in_this))
            val systemId = ScreenScraper.systemIds[game.system] ?: error(str(R.string.cover_screenscraper_doesn_t_list_this_system))
            val md5 = if (game.sizeBytes in 1..MD5_MAX_BYTES) runCatching { md5Of(game) }.getOrNull() else null
            when (val r = screenScraper.gameInfo(systemId, game.fileName, game.sizeBytes, md5)) {
                is SsResult.Found -> ScreenScraper.covers(r.game, regionCode(game))
                SsResult.NotFound -> emptyList()
                SsResult.NotAvailable -> error(str(R.string.cover_screenscraper_didn_t_accept_retroforge_s))
                SsResult.BadLogin -> error(str(R.string.cover_screenscraper_didn_t_accept_your_username))
                is SsResult.Failed -> error(r.reason)
            }
        }
    }

    private suspend fun md5Of(game: GameEntity): String {
        val root = checkNotNull(dao.root(game.rootId))
        val digest = MessageDigest.getInstance("MD5")
        checkNotNull(resolver.openInputStream(SafScanNode.documentUri(root.treeUri, game.documentId))).use { input ->
            val buf = ByteArray(256 * 1024)
            while (true) {
                val r = input.read(buf)
                if (r < 0) break
                digest.update(buf, 0, r)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** ScreenScraper region code from the file name's first region, for picking that region's box. */
    private fun regionCode(game: GameEntity): String? = when (game.regions.substringBefore(',').trim()) {
        "USA" -> "us"; str(R.string.cover_europe) -> "eu"; str(R.string.cover_japan) -> "jp"; str(R.string.cover_world) -> "wor"; "UK" -> "uk"; str(R.string.cover_france) -> "fr"; str(R.string.cover_germany) -> "de"
        str(R.string.cover_spain) -> "sp"; str(R.string.cover_italy) -> "it"; str(R.string.cover_australia) -> "au"; str(R.string.cover_brazil) -> "br"; str(R.string.cover_korea) -> "kr"; str(R.string.cover_china) -> "cn"
        else -> null
    }

    /** Forget the chosen cover; the game falls back to its gamelist artwork. */
    suspend fun reset(game: GameEntity) = withContext(Dispatchers.IO) {
        dao.cover(game.rootId, game.relativePath)?.let { File(coversDir, it.file).delete() }
        dao.deleteCover(game.rootId, game.relativePath)
    }

    /** Fetches libretro box art for every game in [games] that has no artwork yet. One download at a time. */
    fun downloadMissing(system: String, games: List<GameEntity>) {
        batchJob?.cancel()
        batchJob = scope.launch {
            val folder = LibretroThumbnails.folderFor(system)
            if (folder == null) {
                _batch.value = CoverBatch.Failed(system, str(R.string.cover_no_free_box_art_source_for))
                return@launch
            }
            val existing = dao.covers().first().map { it.rootId to it.relativePath }.toSet()
            val todo = games.filter { it.imageDocumentId == null && (it.rootId to it.relativePath) !in existing }
            val names = try {
                index(folder)
            } catch (e: Exception) {
                _batch.value = CoverBatch.Failed(system, str(R.string.cover_couldn_t_reach_the_box_art, e.message))
                return@launch
            }
            var found = 0
            var failed = 0
            todo.forEachIndexed { i, game ->
                if (!isActive) return@launch
                _batch.value = CoverBatch.Running(system, i, todo.size, found)
                val best = LibretroThumbnails.match(names, folder, ThumbnailType.BOXART, game.fileName, 1).firstOrNull()
                if (best != null) {
                    if (save(game, best.url, SOURCE_LIBRETRO, null).isSuccess) found++ else failed++
                }
            }
            _batch.value = CoverBatch.Finished(system, found, todo.size - found - failed, failed)
        }
    }

    /**
     * Box art for every game in the library without any, all systems at once: libretro's free covers first, then
     * ScreenScraper for the rest when it can be used. Reports through [batch] with [ALL_SYSTEMS] as the system.
     */
    fun downloadAllMissing() {
        batchJob?.cancel()
        batchJob = scope.launch {
            val existing = dao.covers().first().map { it.rootId to it.relativePath }.toSet()
            val todo = dao.allGames().filter { it.imageDocumentId == null && (it.rootId to it.relativePath) !in existing }
            // One index per libretro folder; null when there's no folder or it couldn't be reached.
            val indexes = mutableMapOf<String, List<String>?>()
            var found = 0
            var failed = 0
            // ScreenScraper is given up on after a few failures in a row (a bad login, the server down, no quota).
            var ssFailures = 0
            todo.forEachIndexed { i, game ->
                if (!isActive) return@launch
                _batch.value = CoverBatch.Running(ALL_SYSTEMS, i, todo.size, found)
                val folder = LibretroThumbnails.folderFor(game.system)
                val names = folder?.let { indexes.getOrPut(it) { runCatching { index(it) }.getOrNull() } }
                val libretro = names?.let { LibretroThumbnails.match(it, folder, ThumbnailType.BOXART, game.fileName, 1).firstOrNull() }
                if (libretro != null) {
                    if (save(game, libretro.url, SOURCE_LIBRETRO, null).isSuccess) found++ else failed++
                } else if (screenScraper.available && ssFailures < 3 && game.system in ScreenScraper.systemIds) {
                    screenScraperCovers(game).onSuccess { covers ->
                        ssFailures = 0
                        val best = covers.firstOrNull { it.type != "box-3D" } ?: covers.firstOrNull()
                        if (best != null) {
                            if (save(game, best.url, SOURCE_SCREENSCRAPER, null, ScreenScraper.withoutCredentials(best.url)).isSuccess) found++ else failed++
                        }
                    }.onFailure { ssFailures++; failed++ }
                }
            }
            _batch.value = CoverBatch.Finished(ALL_SYSTEMS, found, todo.size - found - failed, failed)
        }
    }

    fun cancelBatch() {
        batchJob?.cancel()
        _batch.value = null
    }

    /**
     * 3D box art (ScreenScraper's "box-3D" renders), kept beside the covers. The bookshelf view shows it as it is
     * instead of drawing a case round the flat cover. SteamGridDB has no 3D boxes, so ScreenScraper is the source.
     */
    fun box3d(game: GameEntity): File? = box3dFile(game).takeIf { it.isFile }

    private fun box3dFile(game: GameEntity) = File(coversDir, "box3d/" + sha1("${game.rootId}/${game.relativePath}") + ".img")

    /** Goes up by one whenever 3D boxes are added, so views showing them look again. */
    private val _box3dChanges = MutableStateFlow(0)
    val box3dChanges: StateFlow<Int> = _box3dChanges.asStateFlow()

    /**
     * Looks each of [games] up on ScreenScraper and saves its 3D box, skipping games that have one. [onProgress] gets
     * how many are done. Returns how many 3D boxes were added; fails when ScreenScraper can't be used at all.
     */
    suspend fun fetchBox3d(games: List<GameEntity>, onProgress: (Int) -> Unit): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            var added = 0
            games.forEachIndexed { i, game ->
                val file = box3dFile(game)
                if (!file.isFile) {
                    val media = screenScraperCovers(game).getOrThrow().firstOrNull { it.type == "box-3D" }
                    if (media != null) {
                        val response = http.get(media.url, emptyMap(), JvmHttpGet.MAX_IMAGE_BYTES)
                        if (response.code == 200 && looksLikeImage(response.body)) {
                            file.parentFile?.mkdirs()
                            File(file.path + ".tmp").apply { writeBytes(response.body) }.renameTo(file)
                            added++
                            _box3dChanges.value++
                        }
                    }
                }
                onProgress(i + 1)
            }
            added
        }
    }

    private suspend fun store(game: GameEntity, bytes: ByteArray, url: String, source: String, author: String?) {
        coversDir.mkdirs()
        val name = sha1("${game.rootId}/${game.relativePath}") + ".img"
        val temp = File(coversDir, "$name.tmp")
        temp.writeBytes(bytes)
        val target = File(coversDir, name)
        if (!temp.renameTo(target)) {
            target.delete()
            check(temp.renameTo(target)) { str(R.string.cover_couldn_t_save_the_cover) }
        }
        dao.upsertCover(CoverEntity(game.rootId, game.relativePath, name, source, url, author, clock()))
    }

    /** A system's box-art index: memory, then a disk cache up to a week old, then the server. */
    private suspend fun index(folder: String): List<String> = indexLock.withLock {
        indexes[folder]?.let { return it }
        val cache = File(cacheDir, "libretro-index-${sha1(folder)}.txt")
        val fresh = cache.exists() && clock() - cache.lastModified() < INDEX_MAX_AGE_MS
        val names = if (fresh) {
            cache.readLines()
        } else {
            val response = http.get(LibretroThumbnails.indexUrl(folder, ThumbnailType.BOXART), emptyMap(), 20L * 1024 * 1024)
            check(response.code == 200) { "HTTP ${response.code}" }
            LibretroThumbnails.parseIndex(response.body.toString(Charsets.UTF_8)).also {
                cacheDir.mkdirs()
                cache.writeText(it.joinToString("\n"))
            }
        }
        names.also { indexes[folder] = it }
    }

    private fun sha1(s: String) = MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }

    companion object {
        const val SOURCE_LIBRETRO = "libretro"
        const val SOURCE_STEAMGRIDDB = "steamgriddb"
        const val SOURCE_SCREENSCRAPER = "screenscraper"
        /** The [CoverBatch] system for [downloadAllMissing]. */
        const val ALL_SYSTEMS = "*"
        const val SOURCE_DEVICE = "device"
        private const val MD5_MAX_BYTES = 64L * 1024 * 1024
        private const val INDEX_MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
    }
}
