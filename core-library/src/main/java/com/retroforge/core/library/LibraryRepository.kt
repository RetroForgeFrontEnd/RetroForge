package com.retroforge.core.library

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.retroforge.library.LibraryScanner
import com.retroforge.library.RomNames
import com.retroforge.library.ScanCancelled
import com.retroforge.library.ScanProgress
import com.retroforge.library.ScannedGame
import com.retroforge.library.SystemRules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface ScanState {
    data class Running(val progress: ScanProgress) : ScanState
    data class Finished(val games: Int, val problems: Int) : ScanState
    data class Failed(val reason: String) : ScanState
}

/**
 * The game library: folders the user granted, and the index built by scanning them. Scans run in an
 * application-wide scope, so leaving a screen doesn't stop them; results replace a folder's index atomically.
 */
class LibraryRepository(
    val database: LibraryDatabase,
    private val resolver: ContentResolver,
    val rules: SystemRules = SystemRules.bundled(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val dao = database.dao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val jobs = mutableMapOf<Long, Job>()
    private val _scans = MutableStateFlow<Map<Long, ScanState>>(emptyMap())
    val scans: StateFlow<Map<Long, ScanState>> = _scans.asStateFlow()

    val roots: Flow<List<RootEntity>> = dao.roots()
    val systemCounts: Flow<List<SystemCount>> = dao.systemCounts()
    val hacks: Flow<List<GameEntity>> = dao.hacks()
    fun gamesFor(system: String): Flow<List<GameEntity>> = dao.gamesFor(system)
    fun game(id: Long): Flow<GameEntity?> = dao.game(id)
    fun problems(rootId: Long): Flow<List<ProblemEntity>> = dao.problems(rootId)
    fun search(query: String): Flow<List<GameEntity>> = dao.search(query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_"))
    suspend fun duplicatesOf(game: GameEntity): List<GameEntity> = game.duplicateKey?.let { dao.duplicatesOf(it, game.id) }.orEmpty()
    suspend fun rootOf(game: GameEntity): RootEntity? = dao.root(game.rootId)
    val recentlyPlayed: Flow<List<GameEntity>> = dao.recentlyPlayed(12)
    fun playFor(gameId: Long): Flow<PlayEntity?> = dao.playFor(gameId)

    val favourites: Flow<List<GameEntity>> = dao.favouriteGames()
    val playlists: Flow<List<PlaylistSummary>> = dao.playlists()
    fun isFavourite(game: GameEntity): Flow<Boolean> = dao.isFavourite(game.rootId, game.relativePath)
    fun playlist(id: Long): Flow<PlaylistEntity?> = dao.playlist(id)
    fun playlistGames(id: Long): Flow<List<GameEntity>> = dao.playlistGames(id)
    fun playlistsContaining(game: GameEntity): Flow<List<Long>> = dao.playlistsContaining(game.rootId, game.relativePath)

    fun setFavourite(game: GameEntity, favourite: Boolean) {
        scope.launch {
            if (favourite) dao.addFavourite(FavouriteEntity(game.rootId, game.relativePath, clock()))
            else dao.removeFavourite(game.rootId, game.relativePath)
        }
    }

    suspend fun createPlaylist(name: String): Long = dao.insertPlaylist(PlaylistEntity(name = name.trim(), createdAt = clock()))
    fun renamePlaylist(id: Long, name: String) { scope.launch { dao.renamePlaylist(id, name.trim()) } }
    fun deletePlaylist(id: Long) { scope.launch { dao.deletePlaylist(id) } }

    fun setInPlaylist(id: Long, game: GameEntity, inPlaylist: Boolean) {
        scope.launch {
            if (inPlaylist) dao.addToPlaylist(id, game.rootId, game.relativePath, clock())
            else dao.removePlaylistItem(id, game.rootId, game.relativePath)
        }
    }

    fun movePlaylistGame(id: Long, game: GameEntity, by: Int) { scope.launch { dao.movePlaylistItem(id, game.rootId, game.relativePath, by) } }

    fun editFor(game: GameEntity): Flow<GameEditEntity?> = dao.edit(game.rootId, game.relativePath)

    suspend fun saveDetails(game: GameEntity, details: GameDetails) = dao.saveEdit(game, details, clock())

    suspend fun resetDetails(game: GameEntity) = dao.resetEdit(game)

    fun recordPlay(game: GameEntity, core: String) {
        scope.launch { dao.recordPlay(game.rootId, game.relativePath, core, clock()) }
    }

    fun systemName(id: String): String = rules.byId(id)?.name ?: id

    /** Called with the folder the user picked; keeps read access across restarts and scans it. */
    fun addFolder(treeUri: Uri) {
        scope.launch {
            resolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val existing = dao.rootByUri(treeUri.toString())
            val rootId = existing?.id ?: dao.insertRoot(RootEntity(treeUri = treeUri.toString(), name = displayName(treeUri), addedAt = clock()))
            scan(rootId)
        }
    }

    /** Forgets a folder: drops RetroForge's index and access grant. The files themselves are untouched. */
    fun removeFolder(root: RootEntity) {
        jobs.remove(root.id)?.cancel()
        scope.launch {
            dao.deleteRoot(root.id)
            val uri = Uri.parse(root.treeUri)
            val write = resolver.persistedUriPermissions.any { it.uri == uri && it.isWritePermission }
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or (if (write) Intent.FLAG_GRANT_WRITE_URI_PERMISSION else 0)
            runCatching { resolver.releasePersistableUriPermission(uri, flags) }
            _scans.update { it - root.id }
        }
    }

    fun hasAccess(root: RootEntity): Boolean =
        resolver.persistedUriPermissions.any { it.uri.toString() == root.treeUri && it.isReadPermission }

    fun scan(rootId: Long) {
        jobs[rootId]?.cancel()
        jobs[rootId] = scope.launch {
            val root = dao.root(rootId) ?: return@launch
            if (!hasAccess(root)) {
                setState(rootId, ScanState.Failed("RetroForge no longer has access to this folder. Remove it and add it again."))
                return@launch
            }
            setState(rootId, ScanState.Running(ScanProgress(0, 0, null, 0)))
            val node = SafScanNode.root(resolver, Uri.parse(root.treeUri), root.name)
            val result = try {
                LibraryScanner(rules).scan(node, onProgress = { setState(rootId, ScanState.Running(it)) }, isCancelled = { !isActive })
            } catch (_: ScanCancelled) {
                return@launch
            } catch (e: Exception) {
                setState(rootId, ScanState.Failed("Scan failed: ${e.message ?: e.javaClass.simpleName}"))
                return@launch
            }
            dao.replaceIndex(
                root,
                games = result.games.map { it.toEntity(rootId) },
                problems = result.problems.map { ProblemEntity(rootId = rootId, path = it.path, message = it.message) },
                layout = result.layout.name,
                unrecognised = result.unrecognisedFolders.joinToString("\n"),
                at = clock(),
            )
            setState(rootId, ScanState.Finished(result.games.size, result.problems.size))
        }
    }

    fun documentUri(root: RootEntity, documentId: String): Uri = SafScanNode.documentUri(root.treeUri, documentId)

    private fun setState(rootId: Long, state: ScanState) = _scans.update { it + (rootId to state) }

    private fun displayName(treeUri: Uri): String {
        val docId = DocumentsContract.getTreeDocumentId(treeUri)
        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
        return runCatching {
            resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: docId.substringAfterLast(':').substringAfterLast('/').ifEmpty { "Folder" }
    }

    private fun ScannedGame.toEntity(rootId: Long) = GameEntity(
        rootId = rootId,
        system = system,
        relativePath = relativePath,
        fileName = fileName,
        documentId = nodeId,
        title = displayTitle,
        sortKey = RomNames.sortKey(displayTitle),
        tags = name.tags.joinToString("\n"),
        regions = name.regions.joinToString(", "),
        isHack = isHack,
        hackFolder = hackFolder?.ifEmpty { null },
        sizeBytes = size,
        modifiedAt = lastModified,
        duplicateKey = duplicateKey,
        discs = discs,
        description = metadata?.description,
        releaseDate = metadata?.releaseDate,
        developer = metadata?.developer,
        publisher = metadata?.publisher,
        genre = metadata?.genre,
        players = metadata?.players,
        rating = metadata?.rating,
        imageDocumentId = imageNodeId,
    )
}
