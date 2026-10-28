package com.retroforge.core.library

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
    private val clock: () -> Long = System::currentTimeMillis,
) {
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

    /** Downloads [url] and makes it this game's cover. */
    suspend fun save(game: GameEntity, url: String, source: String, author: String?): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val response = http.get(url, emptyMap(), JvmHttpGet.MAX_IMAGE_BYTES)
            check(response.code == 200) { "the server answered HTTP ${response.code}" }
            check(looksLikeImage(response.body)) { "that download isn't an image" }
            store(game, response.body, url, source, author)
        }
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
                _batch.value = CoverBatch.Failed(system, "No free box-art source for this system yet.")
                return@launch
            }
            val existing = dao.covers().first().map { it.rootId to it.relativePath }.toSet()
            val todo = games.filter { it.imageDocumentId == null && (it.rootId to it.relativePath) !in existing }
            val names = try {
                index(folder)
            } catch (e: Exception) {
                _batch.value = CoverBatch.Failed(system, "Couldn't reach the box-art server: ${e.message}")
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

    fun cancelBatch() {
        batchJob?.cancel()
        _batch.value = null
    }

    private suspend fun store(game: GameEntity, bytes: ByteArray, url: String, source: String, author: String?) {
        coversDir.mkdirs()
        val name = sha1("${game.rootId}/${game.relativePath}") + ".img"
        val temp = File(coversDir, "$name.tmp")
        temp.writeBytes(bytes)
        val target = File(coversDir, name)
        if (!temp.renameTo(target)) {
            target.delete()
            check(temp.renameTo(target)) { "couldn't save the cover" }
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
        private const val INDEX_MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
    }
}
