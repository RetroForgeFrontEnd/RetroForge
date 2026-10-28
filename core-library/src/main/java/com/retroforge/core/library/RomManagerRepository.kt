package com.retroforge.core.library

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

    suspend fun findCompressible(root: RootEntity): CompressReport = withContext(Dispatchers.IO) {
        val games = dao.gamesIn(root.id).map { CompressCandidate(it.system, it.relativePath, systemFolder(root, it), it.title, it.sizeBytes) }
        val (jobs, skipped) = CompressPlanner.plan(games, SafCollectionFiles(resolver, Uri.parse(root.treeUri)), library.rules)
        CompressReport(jobs.sortedWith(compareBy({ it.system }, { it.title.lowercase() })), skipped)
    }

    suspend fun findMultiDisc(root: RootEntity): PlaylistReport = withContext(Dispatchers.IO) {
        val games = dao.gamesIn(root.id).map { CompressCandidate(it.system, it.relativePath, systemFolder(root, it), it.title, it.sizeBytes) }
        val (jobs, skipped) = MultiDiscPlanner.plan(games, SafCollectionFiles(resolver, Uri.parse(root.treeUri)), library.rules)
        PlaylistReport(jobs, skipped)
    }

    fun makePlaylists(root: RootEntity, jobs: List<PlaylistJob>) = start(root, "playlists", "Made ${jobs.size} playlists") { files, journal ->
        _run.value = ManagerRun.Running("Making playlists", "${jobs.size} multi-disc games", null)
        val problems = PlaylistMaker(files).run(jobs, journal) { !isActive }
        val made = journal.log.steps.size
        val summary = "Made ${if (made == 1) "1 playlist" else "$made playlists"} for multi-disc games"
        journal.finish(summary)
        ManagerRun.Done("Playlists made", "$summary. Each shows as one game; change discs from the emulator's menu.", problems)
    }

    fun moveDuplicates(root: RootEntity, selections: List<Pair<DuplicateSet, DuplicateCandidate>>) = start(root, "duplicates", "Moving ${selections.sumOf { it.first.files.size - 1 }} copies") { files, journal ->
        _run.value = ManagerRun.Running("Tidying duplicates", "Moving copies to _duplicates …", null)
        val r = DuplicateMover(files).run(selections, journal) { !isActive }
        val summary = "Moved ${r.moved} ${if (r.moved == 1) "copy" else "copies"} to _duplicates" +
            if (r.gamelistsUpdated > 0) " and updated ${r.gamelistsUpdated} gamelist.xml" else ""
        journal.finish(summary)
        ManagerRun.Done("Duplicates tidied", "$summary.", r.problems)
    }

    fun compress(root: RootEntity, jobs: List<CompressJob>) = start(root, "compress", "Compressing ${jobs.size} games") { files, journal ->
        val r = Compressor(files).run(jobs, journal, onProgress = { p ->
            _run.value = ManagerRun.Running(
                "Compressing ${p.job + 1} of ${p.jobs}",
                "${p.title}: ${p.stage.lowercase()} ${p.done * 100 / maxOf(p.total, 1)}%",
                (p.job + p.done.toFloat() / maxOf(p.total, 1)) / p.jobs,
            )
        }) { !isActive }
        val saved = r.bytesBefore - r.bytesAfter
        val summary = "Compressed ${r.converted} of ${jobs.size} games, saving ${formatBytes(saved)}; originals are in _originals"
        journal.finish(summary)
        ManagerRun.Done(if (r.converted == jobs.size) "Compression finished" else "Compression stopped", "$summary.", r.problems)
    }

    /** The files deleting [game] would move (an .m3u's discs, a .cue's tracks …). Reads only. */
    suspend fun gameParts(root: RootEntity, game: GameEntity): List<String> = withContext(Dispatchers.IO) {
        GameFiles.of(SafCollectionFiles(resolver, Uri.parse(root.treeUri)), game.relativePath)
    }

    fun deleteGame(root: RootEntity, game: GameEntity) = start(root, "delete", "Deleted ${game.title}") { files, journal ->
        _run.value = ManagerRun.Running("Deleting", game.title, null)
        val problems = GameDeleter(files).moveToDeleted(game.relativePath, systemFolder(root, game), journal)
        journal.finish("Deleted ${game.title}")
        ManagerRun.Done("Deleted", "${game.title} is in the $DELETED_FOLDER folder. Undo it from History, or use Free up space to delete it for good.", problems)
    }

    suspend fun holdingSummaries(root: RootEntity): List<HoldingSummary> = withContext(Dispatchers.IO) {
        if (!library.hasAccess(root)) emptyList() else runCatching { Holding.summaries(SafCollectionFiles(resolver, Uri.parse(root.treeUri))) }.getOrDefault(emptyList())
    }

    /** Permanently deletes a holding folder. History entries that needed those files can no longer be undone. */
    fun purge(root: RootEntity, folder: String) = startRaw {
        if (!hasWriteAccess(root)) return@startRaw ManagerRun.Failed("RetroForge needs permission to change this folder.")
        _run.value = ManagerRun.Running("Deleting permanently", folder, null)
        SafCollectionFiles(resolver, Uri.parse(root.treeUri)).deleteHoldingFolder(folder)
        _logs.value.filter { it.rootId == root.id && it.undoneAt == null && Holding.dependsOn(it, folder) }
            .forEach { save(it.copy(purgedAt = clock())) }
        ManagerRun.Done("Deleted permanently", "Emptied $folder.", emptyList())
    }

    fun undo(root: RootEntity, log: OperationLog) {
        startRaw {
            _run.value = ManagerRun.Running("Undoing", log.summary, null)
            val problems = Undo.run(SafCollectionFiles(resolver, Uri.parse(root.treeUri)), log)
            save(log.copy(undoneAt = clock()))
            library.scan(root.id)
            ManagerRun.Done("Undone", "Put back: ${log.summary.replaceFirstChar { it.lowercase() }}.", problems)
        }
    }

    fun cancel() { job?.cancel() }

    fun dismiss() { if (_run.value !is ManagerRun.Running) _run.value = null }

    private fun start(root: RootEntity, kind: String, summary: String, block: suspend CoroutineScope.(SafCollectionFiles, Journal) -> ManagerRun) = startRaw {
        if (!hasWriteAccess(root)) return@startRaw ManagerRun.Failed("RetroForge needs permission to change this folder.")
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
                ManagerRun.Done("Stopped", "Stopped. Anything already done can be undone from History.", emptyList())
            } catch (e: Exception) {
                ManagerRun.Failed(e.message ?: e.javaClass.simpleName)
            }
            if (!isActive) _run.value = ManagerRun.Done("Stopped", "Stopped. Anything already done can be undone from History.", emptyList())
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
