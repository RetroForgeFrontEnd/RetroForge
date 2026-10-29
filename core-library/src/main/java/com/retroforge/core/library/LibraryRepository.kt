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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
    /**
     * Games whose title has every word of [query], in any order ("dr mario miracle" finds "Dr. Mario - Miracle Cure").
     * The database narrows by the longest word; the rest are checked here.
     */
    fun search(query: String): Flow<List<GameEntity>> {
        val words = query.lowercase().split(Regex("""[\s\-:.,'!]+""")).filter { it.isNotBlank() }
        val key = words.maxByOrNull { it.length } ?: query.trim()
        val phrase = words.joinToString(" ")
        return dao.search(key.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")).map { games ->
            games.filter { g -> val title = g.title.lowercase(); words.all { it in title } }
                .sortedBy { g -> searchRank(g.title, phrase, words) }
        }
    }

    /** 0 for the title itself, 1 when the title starts with the search, 2 when every word starts a word, else 3. */
    private fun searchRank(title: String, phrase: String, words: List<String>): Int {
        val titleWords = title.lowercase().split(Regex("""[\s\-:.,'!()\[\]]+""")).filter { it.isNotBlank() }
        val joined = titleWords.joinToString(" ")
        return when {
            joined == phrase -> 0
            joined.startsWith(phrase) -> 1
            words.all { w -> titleWords.any { it.startsWith(w) } } -> 2
            else -> 3
        }
    }
    suspend fun duplicatesOf(game: GameEntity): List<GameEntity> = game.duplicateKey?.let { dao.duplicatesOf(it, game.id) }.orEmpty()
    suspend fun rootOf(game: GameEntity): RootEntity? = dao.root(game.rootId)
    val recentlyPlayed: Flow<List<GameEntity>> = dao.recentlyPlayed(12)
    fun playFor(gameId: Long): Flow<PlayEntity?> = dao.playFor(gameId)

    val favourites: Flow<List<GameEntity>> = dao.favouriteGames()

    /**
     * Favourites and playlists as JSON for the backup. Games are named by their path in the user's folder, because
     * folder ids differ between devices; [importLists] matches them back by that path (or its last two parts).
     */
    suspend fun exportLists(): String {
        val json = org.json.JSONObject()
        json.put("favourites", org.json.JSONArray(dao.favouriteGames().first().map { it.relativePath }))
        val lists = org.json.JSONArray()
        dao.playlists().first().forEach { p ->
            lists.put(org.json.JSONObject().put("name", p.name).put("games", org.json.JSONArray(dao.playlistGames(p.id).first().map { it.relativePath })))
        }
        json.put("playlists", lists)
        return json.toString(1)
    }

    /** Puts favourites and playlists from [exportLists] back; playlists with the same name are added to. Returns games matched and missed. */
    suspend fun importLists(text: String): Pair<Int, Int> {
        val json = org.json.JSONObject(text)
        val games = dao.roots().first().flatMap { dao.gamesIn(it.id) }
        fun key(path: String) = path.replace('\\', '/').lowercase().split('/').takeLast(2).joinToString("/")
        val exact = games.associateBy { it.relativePath.lowercase() }
        val loose = games.groupBy { key(it.relativePath) }
        fun find(path: String) = exact[path.lowercase()] ?: loose[key(path)]?.firstOrNull()
        var matched = 0
        var missed = 0
        val favourites = json.optJSONArray("favourites") ?: org.json.JSONArray()
        for (i in 0 until favourites.length()) {
            val game = find(favourites.getString(i))
            if (game == null) { missed++; continue }
            dao.addFavourite(FavouriteEntity(game.rootId, game.relativePath, clock()))
            matched++
        }
        val existing = dao.playlists().first().associateBy({ it.name.lowercase() }, { it.id })
        val lists = json.optJSONArray("playlists") ?: org.json.JSONArray()
        for (i in 0 until lists.length()) {
            val list = lists.getJSONObject(i)
            val name = list.getString("name")
            val entries = list.optJSONArray("games") ?: continue
            val found = (0 until entries.length()).mapNotNull { find(entries.getString(it)) }
            missed += entries.length() - found.size
            if (found.isEmpty()) continue
            val id = existing[name.lowercase()] ?: createPlaylist(name)
            found.forEach { dao.addToPlaylist(id, it.rootId, it.relativePath, clock()) }
            matched += found.size
        }
        return matched to missed
    }
    val playlists: Flow<List<PlaylistSummary>> = dao.playlists()
    fun isFavourite(game: GameEntity): Flow<Boolean> = dao.isFavourite(game.rootId, game.relativePath)
    /** Every game in one folder (for matching other frontends' lists). */
    suspend fun gamesIn(rootId: Long): List<GameEntity> = dao.gamesIn(rootId)

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

    /** A system's name for an id or one of its folder names ("segacd" is the Mega CD). */
    fun systemName(id: String): String = (rules.byId(id) ?: rules.forFolder(id))?.name ?: id

    /**
     * Document providers that belong to RetroForge itself (its network drives): their folders need no grant from the
     * folder picker, because an app can always read its own provider.
     */
    var ownAuthorities: Set<String> = emptySet()

    private fun isOwn(uri: String) = Uri.parse(uri).authority in ownAuthorities

    /** Called with the folder the user picked; keeps read access across restarts and scans it. */
    fun addFolder(treeUri: Uri) {
        scope.launch {
            if (!isOwn(treeUri.toString())) resolver.takePersistableUriPermission(treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            val existing = dao.rootByUri(treeUri.toString())
            val rootId = existing?.id ?: dao.insertRoot(RootEntity(treeUri = treeUri.toString(), name = displayName(treeUri), addedAt = clock()))
            scan(rootId)
        }
    }

    /**
     * Adds a folder and waits for its scan, for a confirmation like "Added: 283 games". Returns the folder as scanned,
     * or null if the scan didn't finish within [timeoutMs].
     */
    suspend fun addFolderAndWait(treeUri: Uri, timeoutMs: Long = 15 * 60_000, onProgress: (ScanProgress) -> Unit = {}): RootEntity? {
        val started = clock()
        addFolder(treeUri)
        return kotlinx.coroutines.withTimeoutOrNull(timeoutMs) {
            while (true) {
                val root = dao.rootByUri(treeUri.toString())
                if (root?.lastScanAt != null && root.lastScanAt >= started && _scans.value[root.id] !is ScanState.Running) return@withTimeoutOrNull root
                (root?.let { _scans.value[it.id] } as? ScanState.Running)?.let { onProgress(it.progress) }
                kotlinx.coroutines.delay(500)
            }
            @Suppress("UNREACHABLE_CODE") null
        }
    }

    /** Forgets a folder: drops RetroForge's index and access grant. The files themselves are untouched. */
    /** The name RetroForge shows for a game folder (the folder itself isn't renamed). */
    fun renameFolder(root: RootEntity, name: String) {
        scope.launch { dao.renameRoot(root.id, name.trim().take(60)) }
    }

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
        isOwn(root.treeUri) || resolver.persistedUriPermissions.any { it.uri.toString() == root.treeUri && it.isReadPermission }

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
