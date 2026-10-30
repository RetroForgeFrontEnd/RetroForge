package com.retroforge.core.library

import com.retroforge.core.ui.pluralStr
import com.retroforge.core.ui.str
import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import com.retroforge.library.manage.CancelledException
import com.retroforge.library.manage.CompressCandidate
import com.retroforge.library.manage.CompressJob
import com.retroforge.library.manage.CompressPlanner
import com.retroforge.library.manage.CompressSkip
import com.retroforge.library.manage.Compressor
import com.retroforge.library.manage.ContentFingerprint
import com.retroforge.library.manage.DuplicateCandidate
import com.retroforge.library.manage.DuplicateMover
import com.retroforge.library.manage.DuplicatePlanner
import com.retroforge.library.manage.DuplicateSet
import com.retroforge.library.manage.DELETED_FOLDER
import com.retroforge.library.manage.GameDeleter
import com.retroforge.library.manage.GameFiles
import com.retroforge.library.manage.Holding
import com.retroforge.library.manage.HoldingSummary
import com.retroforge.library.manage.Journal
import com.retroforge.library.manage.MultiDiscPlanner
import com.retroforge.library.manage.PlaylistJob
import com.retroforge.library.manage.PlaylistMaker
import com.retroforge.library.manage.PlaylistSkip
import com.retroforge.library.manage.OperationLog
import com.retroforge.library.manage.SkippedSet
import com.retroforge.library.manage.Undo
import com.retroforge.library.manage.Step
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class DuplicateReport(val sets: List<DuplicateSet>, val skipped: List<SkippedSet>)

data class CompressReport(val jobs: List<CompressJob>, val skipped: List<CompressSkip>)

data class PlaylistReport(val jobs: List<PlaylistJob>, val skipped: List<PlaylistSkip>)

/** A game found outside its system's folder, and the folder it would move to. */
data class SortJob(val path: String, val system: String, val title: String, val folder: String)

sealed interface ManagerRun {
    data class Running(val title: String, val detail: String, val fraction: Float?) : ManagerRun
    data class Done(val title: String, val summary: String, val problems: List<String>) : ManagerRun
    data class Failed(val reason: String) : ManagerRun
}

/**
 * The ROM manager: the only part of RetroForge that changes files in a game folder, and only after the user
 * confirms a preview. Every run writes an undo log to [logDir] as it goes.
 */
class RomManagerRepository(
    private val library: LibraryRepository,
    private val resolver: ContentResolver,
    private val logDir: File,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val dao = library.database.dao()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private val _run = MutableStateFlow<ManagerRun?>(null)
    val run: StateFlow<ManagerRun?> = _run.asStateFlow()
    private val _logs = MutableStateFlow<List<OperationLog>>(emptyList())
    val logs: StateFlow<List<OperationLog>> = _logs.asStateFlow()

    init { scope.launch { reloadLogs() } }

    fun hasWriteAccess(root: RootEntity): Boolean =
        resolver.persistedUriPermissions.any { it.uri.toString() == root.treeUri && it.isWritePermission }

    /** The user picked [picked] to allow changes; it must be the same folder. */
    fun grantWriteAccess(root: RootEntity, picked: Uri): Boolean {
        val rootUri = Uri.parse(root.treeUri)
        if (DocumentsContract.getTreeDocumentId(picked) != DocumentsContract.getTreeDocumentId(rootUri) || picked.authority != rootUri.authority) return false
        resolver.takePersistableUriPermission(picked, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        return hasWriteAccess(root)
    }

    private fun systemFolder(root: RootEntity, game: GameEntity): String {
        if (root.layout == "SINGLE_SYSTEM") return ""
        val parts = game.relativePath.split('/')
        val i = parts.dropLast(1).indexOfFirst { library.rules.forFolder(it)?.id == game.system }
        return if (i < 0) parts.first() else parts.take(i + 1).joinToString("/")
    }

    private fun GameEntity.hasMetadata() = description != null || releaseDate != null || developer != null || genre != null

    /** Finds duplicate sets and checks whether each set's copies hold the same data. Reads only. */
    suspend fun findDuplicates(root: RootEntity, onProgress: (Int, Int) -> Unit): DuplicateReport = withContext(Dispatchers.IO) {
        val games = dao.gamesIn(root.id).filter { it.duplicateKey != null }
        val (sets, skipped) = DuplicatePlanner.plan(games.map {
            DuplicateCandidate(it.system, it.relativePath, systemFolder(root, it), it.title, it.sizeBytes, it.duplicateKey!!, it.hasMetadata(), it.imageDocumentId != null)
        })
        val files = SafCollectionFiles(resolver, Uri.parse(root.treeUri))
        val checked = sets.mapIndexed { i, set ->
            onProgress(i, sets.size)
            set.copy(sameness = ContentFingerprint.sameness(set.files.map { f -> ContentFingerprint.of(f.fileName, f.size) { files.open(f.relativePath) } }))
        }
        DuplicateReport(checked, skipped)
    }

    /** Checked one system folder at a time, so [onProgress] (done, total) can show how far it has got. */
    suspend fun findCompressible(root: RootEntity, onProgress: (Int, Int) -> Unit = { _, _ -> }): CompressReport = withContext(Dispatchers.IO) {
        val bySystem = dao.gamesIn(root.id).map { CompressCandidate(it.system, it.relativePath, systemFolder(root, it), it.title, it.sizeBytes) }
            .groupBy { it.systemFolder }.values.toList()
        val files = SafCollectionFiles(resolver, Uri.parse(root.treeUri))
        val jobs = mutableListOf<CompressJob>()
        val skipped = mutableListOf<CompressSkip>()
        bySystem.forEachIndexed { i, games ->
            onProgress(i, bySystem.size)
            CompressPlanner.plan(games, files, library.rules).let { (j, s) -> jobs += j; skipped += s }
        }
        CompressReport(jobs.sortedWith(compareBy({ it.system }, { it.title.lowercase() })), skipped)
    }

    suspend fun findMultiDisc(root: RootEntity, onProgress: (Int, Int) -> Unit = { _, _ -> }): PlaylistReport = withContext(Dispatchers.IO) {
        val bySystem = dao.gamesIn(root.id).map { CompressCandidate(it.system, it.relativePath, systemFolder(root, it), it.title, it.sizeBytes) }
            .groupBy { it.systemFolder }.values.toList()
        val files = SafCollectionFiles(resolver, Uri.parse(root.treeUri))
        val jobs = mutableListOf<PlaylistJob>()
        val skipped = mutableListOf<PlaylistSkip>()
        bySystem.forEachIndexed { i, games ->
            onProgress(i, bySystem.size)
            MultiDiscPlanner.plan(games, files, library.rules).let { (j, s) -> jobs += j; skipped += s }
        }
        PlaylistReport(jobs, skipped)
    }

    /**
     * Games outside a folder for their system (found by file type), each with the folder it belongs in: the one this
     * collection already uses for that system (so "GBA" stays "GBA"), else a new one named like RetroBat/ES-DE ("gba").
     */
    suspend fun findLoose(root: RootEntity): List<SortJob> = withContext(Dispatchers.IO) {
        if (root.layout == "SINGLE_SYSTEM") return@withContext emptyList()
        val games = dao.gamesIn(root.id)
        fun loose(g: GameEntity) = g.relativePath.split('/').dropLast(1).none { library.rules.forFolder(it)?.id == g.system }
        val (outside, inside) = games.partition(::loose)
        val existing = inside.groupBy { it.system }.mapValues { (_, list) -> systemFolder(root, list.first()) }
        // Where system folders live: next to each other in "roms/" or at the top.
        val base = existing.values.firstOrNull()?.substringBeforeLast('/', "")?.let { if (it.isEmpty()) "" else "$it/" } ?: ""
        outside.map { g -> SortJob(g.relativePath, g.system, g.title, existing[g.system] ?: (base + g.system)) }
            .sortedWith(compareBy({ it.system }, { it.title.lowercase() }))
    }

    fun sortLoose(root: RootEntity, jobs: List<SortJob>) = start(root, "sort", str(R.string.rom_manager_sorted_games_into_system_folders, jobs.size)) { files, journal ->
        _run.value = ManagerRun.Running(str(R.string.rom_manager_sorting_games), str(R.string.rom_manager_moving_games_into_their_system_folders, jobs.size), null)
        val problems = mutableListOf<String>()
        val moves = mutableListOf<Pair<String, String>>()
        for (job in jobs) {
            if (!isActive) break
            val name = job.path.substringAfterLast('/')
            val to = "${job.folder}/$name"
            try {
                if (files.exists(to)) { problems += str(R.string.rom_manager_is_already_in, name, job.folder); continue }
                files.makeFolders(job.folder)
                files.move(job.path, job.folder)
                journal.record(Step.Moved(job.path, to))
                moves += job.path to to
            } catch (e: Exception) {
                problems += "$name: ${e.message}"
            }
        }
        relink(root, journal, moves, reverseOnUndo = true)
        val summary = pluralStr(R.plurals.rom_manager_sorted_games, moves.size, moves.size)
        journal.finish(summary)
        ManagerRun.Done(str(R.string.rom_manager_games_sorted), str(R.string.rom_manager_undo_from_history_puts_them_back, summary), problems)
    }

    fun makePlaylists(root: RootEntity, jobs: List<PlaylistJob>) = start(root, "playlists", str(R.string.rom_manager_made_playlists, jobs.size)) { files, journal ->
        _run.value = ManagerRun.Running(str(R.string.rom_manager_making_playlists), str(R.string.rom_manager_multi_disc_games, jobs.size), null)
        val problems = PlaylistMaker(files).run(jobs, journal) { !isActive }
        val made = journal.log.steps.size
        val summary = pluralStr(R.plurals.rom_manager_made_playlists, made, made)
        journal.finish(summary)
        ManagerRun.Done(str(R.string.rom_manager_playlists_made), str(R.string.rom_manager_each_shows_as_one_game_change, summary), problems)
    }

    fun moveDuplicates(root: RootEntity, selections: List<Pair<DuplicateSet, DuplicateCandidate>>) = start(root, "duplicates", str(R.string.rom_manager_moving_copies, selections.sumOf { it.first.files.size - 1 })) { files, journal ->
        _run.value = ManagerRun.Running(str(R.string.rom_manager_tidying_duplicates), str(R.string.rom_manager_moving_copies_to_duplicates), null)
        val r = DuplicateMover(files).run(selections, journal) { !isActive }
        relink(root, journal, r.replacedBy, reverseOnUndo = false)
        val summary = pluralStr(R.plurals.rom_manager_moved_copies, r.moved, r.moved) +
            if (r.gamelistsUpdated > 0) " and updated ${r.gamelistsUpdated} gamelist.xml" else ""
        journal.finish(summary)
        ManagerRun.Done(str(R.string.rom_manager_duplicates_tidied), "$summary.", r.problems)
    }

    fun compress(root: RootEntity, jobs: List<CompressJob>) = start(root, "compress", str(R.string.rom_manager_compressing_games, jobs.size)) { files, journal ->
        val r = Compressor(files).run(jobs, journal, onProgress = { p ->
            _run.value = ManagerRun.Running(
                str(R.string.rom_manager_compressing_of, p.job + 1, p.jobs),
                "${p.title}: ${p.stage.lowercase()} ${p.done * 100 / maxOf(p.total, 1)}%",
                (p.job + p.done.toFloat() / maxOf(p.total, 1)) / p.jobs,
            )
        }) { !isActive }
        relink(root, journal, r.renamed, reverseOnUndo = true)
        val saved = r.bytesBefore - r.bytesAfter
        val summary = str(R.string.rom_manager_compressed_of_games_saving_originals_are, r.converted, jobs.size, formatBytes(saved))
        journal.finish(summary)
        ManagerRun.Done(if (r.converted == jobs.size) str(R.string.rom_manager_compression_finished) else str(R.string.rom_manager_compression_stopped), "$summary.", r.problems)
    }

    /** The files deleting [game] would move (an .m3u's discs, a .cue's tracks …). Reads only. */
    suspend fun gameParts(root: RootEntity, game: GameEntity): List<String> = withContext(Dispatchers.IO) {
        GameFiles.of(SafCollectionFiles(resolver, Uri.parse(root.treeUri)), game.relativePath)
    }

    fun deleteGame(root: RootEntity, game: GameEntity) = start(root, "delete", str(R.string.rom_manager_deleted, game.title)) { files, journal ->
        _run.value = ManagerRun.Running(str(R.string.rom_manager_deleting), game.title, null)
        val problems = GameDeleter(files).moveToDeleted(game.relativePath, systemFolder(root, game), journal)
        journal.finish(str(R.string.rom_manager_deleted, game.title))
        ManagerRun.Done(str(R.string.rom_manager_deleted_2), str(R.string.rom_manager_is_in_the_folder_undo_it, game.title, DELETED_FOLDER), problems)
    }

    suspend fun holdingSummaries(root: RootEntity): List<HoldingSummary> = withContext(Dispatchers.IO) {
        if (!library.hasAccess(root)) emptyList() else runCatching { Holding.summaries(SafCollectionFiles(resolver, Uri.parse(root.treeUri))) }.getOrDefault(emptyList())
    }

    /** Permanently deletes a holding folder. History entries that needed those files can no longer be undone. */
    fun purge(root: RootEntity, folder: String) = startRaw {
        if (!hasWriteAccess(root)) return@startRaw ManagerRun.Failed(str(R.string.rom_manager_retroforge_needs_permission_to_change_this))
        _run.value = ManagerRun.Running(str(R.string.rom_manager_deleting_permanently), folder, null)
        SafCollectionFiles(resolver, Uri.parse(root.treeUri)).deleteHoldingFolder(folder)
        _logs.value.filter { it.rootId == root.id && it.undoneAt == null && Holding.dependsOn(it, folder) }
            .forEach { save(it.copy(purgedAt = clock())) }
        ManagerRun.Done(str(R.string.rom_manager_deleted_permanently), str(R.string.rom_manager_emptied, folder), emptyList())
    }

    fun undo(root: RootEntity, log: OperationLog) {
        startRaw {
            _run.value = ManagerRun.Running(str(R.string.rom_manager_undoing), log.summary, null)
            val problems = Undo.run(SafCollectionFiles(resolver, Uri.parse(root.treeUri)), log)
            // Point favourites, playlists, history and covers back at the restored files.
            log.steps.filterIsInstance<Step.Relinked>().filter { it.reverseOnUndo }.asReversed().forEach { dao.relink(root.id, it.to, it.from) }
            save(log.copy(undoneAt = clock()))
            library.scan(root.id)
            ManagerRun.Done(str(R.string.rom_manager_undone), str(R.string.rom_manager_put_back, log.summary.replaceFirstChar { it.lowercase() }), problems)
        }
    }

    /** Keeps favourites, playlists, play history and chosen covers with the game when its path changed. */
    private suspend fun relink(root: RootEntity, journal: Journal, moves: List<Pair<String, String>>, reverseOnUndo: Boolean) {
        for ((from, to) in moves) {
            dao.relink(root.id, from, to)
            journal.record(Step.Relinked(from, to, reverseOnUndo))
        }
    }

    fun cancel() { job?.cancel() }

    fun dismiss() { if (_run.value !is ManagerRun.Running) _run.value = null }

    private fun start(root: RootEntity, kind: String, summary: String, block: suspend CoroutineScope.(SafCollectionFiles, Journal) -> ManagerRun) = startRaw {
        if (!hasWriteAccess(root)) return@startRaw ManagerRun.Failed(str(R.string.rom_manager_retroforge_needs_permission_to_change_this))
        val journal = Journal(OperationLog(UUID.randomUUID().toString(), kind, root.id, clock(), summary), ::save)
        try {
            block(SafCollectionFiles(resolver, Uri.parse(root.treeUri)), journal)
        } finally {
            library.scan(root.id)
        }
    }

    private fun startRaw(block: suspend CoroutineScope.() -> ManagerRun) {
        if (job?.isActive == true) return
        job = scope.launch {
            _run.value = try {
                block()
            } catch (_: CancelledException) {
                ManagerRun.Done(str(R.string.rom_manager_stopped), str(R.string.rom_manager_stopped_anything_already_done_can_be), emptyList())
            } catch (e: Exception) {
                ManagerRun.Failed(e.message ?: e.javaClass.simpleName)
            }
            if (!isActive) _run.value = ManagerRun.Done(str(R.string.rom_manager_stopped), str(R.string.rom_manager_stopped_anything_already_done_can_be), emptyList())
        }
    }

    @Synchronized
    private fun save(log: OperationLog) {
        logDir.mkdirs()
        val tmp = File(logDir, "${log.id}.json.tmp")
        tmp.writeText(log.encode())
        tmp.renameTo(File(logDir, "${log.id}.json"))
        reloadLogs()
    }

    private fun reloadLogs() {
        _logs.value = logDir.listFiles { f -> f.name.endsWith(".json") }.orEmpty()
            .mapNotNull { runCatching { OperationLog.decode(it.readText()) }.getOrNull() }
            .filter { it.steps.isNotEmpty() }
            .sortedByDescending { it.startedAt }
    }

    companion object {
        fun formatBytes(bytes: Long): String = when {
            bytes >= 1L shl 30 -> "%.1f GB".format(bytes / (1L shl 30).toDouble())
            bytes >= 1L shl 20 -> "%.1f MB".format(bytes / (1L shl 20).toDouble())
            else -> "${bytes / 1024} KB"
        }
    }
}
