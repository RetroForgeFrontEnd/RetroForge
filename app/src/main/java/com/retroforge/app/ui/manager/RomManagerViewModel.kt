package com.retroforge.app.ui.manager

import com.retroforge.app.R
import com.retroforge.core.ui.str
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.retroforge.core.library.CompressReport
import com.retroforge.core.library.DuplicateReport
import com.retroforge.core.library.GameEntity
import com.retroforge.core.library.HeaderReport
import com.retroforge.core.library.IdentifyReport
import com.retroforge.library.manage.IdentifyOutcome
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import com.retroforge.library.manage.HoldingSummary
import kotlinx.coroutines.flow.first
import com.retroforge.core.library.LibraryRepository
import com.retroforge.core.library.ManagerRun
import com.retroforge.core.library.PlaylistReport
import com.retroforge.core.library.RomManagerRepository
import com.retroforge.core.library.RootEntity
import com.retroforge.library.manage.DuplicateSet
import com.retroforge.library.manage.KeepRule
import com.retroforge.library.manage.OperationLog
import com.retroforge.library.manage.Sameness
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ManagerTab(private val labelId: Int) {
    DUPLICATES(R.string.rom_manager_duplicates), IDENTIFY(R.string.rom_manager_identify), HEADERS(R.string.rom_manager_headers), COMPRESS(R.string.rom_manager_compress_3), MULTI_DISC(R.string.rom_manager_multi_disc), SORT(R.string.rom_manager_sort), HISTORY(R.string.rom_manager_history);

    val label: String get() = str(labelId)
}

sealed interface Loadable<out T> {
    data object Idle : Loadable<Nothing>
    data class Loading(val done: Int, val total: Int) : Loadable<Nothing> {
        /** How far it has got, 0 to 1, or null before it knows how much there is. */
        val share: Float? get() = if (total > 0) done.toFloat() / total else null
    }
    data class Ready<T>(val value: T) : Loadable<T>
    data class Failed(val reason: String) : Loadable<Nothing>
}

/** What the user is about to confirm. */
sealed interface Pending {
    data class MoveDuplicates(val count: Int) : Pending
    data class Compress(val count: Int, val bytes: Long) : Pending
    data class Undo(val log: OperationLog) : Pending
    data class DeleteGame(val game: GameEntity, val parts: List<String>) : Pending
    data class Purge(val summary: HoldingSummary) : Pending
    data class MakePlaylists(val count: Int) : Pending
    data class SortLoose(val count: Int) : Pending
    data class Rename(val count: Int) : Pending
    data class FixHeaders(val count: Int) : Pending
}

class RomManagerViewModel(private val library: LibraryRepository, private val manager: RomManagerRepository) : ViewModel() {
    /** The system's short name ("PS2", "N64") for an id like "ps2". */
    fun systemName(id: String): String = library.systemName(id)


    val roots: StateFlow<List<RootEntity>?> = library.roots.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val run: StateFlow<ManagerRun?> = manager.run
    val logs: StateFlow<List<OperationLog>> = manager.logs

    val rootId = MutableStateFlow<Long?>(null)
    val tab = MutableStateFlow(ManagerTab.DUPLICATES)
    val pending = MutableStateFlow<Pending?>(null)
    val writeAccessRefused = MutableStateFlow(false)
    /** Bumped after a permission grant, so screens re-check access. */
    val accessChecks = MutableStateFlow(0)

    val duplicates = MutableStateFlow<Loadable<DuplicateReport>>(Loadable.Idle)
    val keepRule = MutableStateFlow(KeepRule.MOST_COMPATIBLE)
    /** Per set (by index): the path to keep, when the user changed it. */
    val keepChoice = MutableStateFlow<Map<Int, String>>(emptyMap())
    val selectedSets = MutableStateFlow<Set<Int>>(emptySet())

    val compressible = MutableStateFlow<Loadable<CompressReport>>(Loadable.Idle)
    val selectedSystems = MutableStateFlow<Set<String>>(emptySet())

    val holding = MutableStateFlow<List<HoldingSummary>>(emptyList())

    val multiDisc = MutableStateFlow<Loadable<PlaylistReport>>(Loadable.Idle)

    fun findMultiDisc() {
        val root = root() ?: return
        viewModelScope.launch {
            multiDisc.value = Loadable.Loading(0, 0)
            multiDisc.value = try {
                Loadable.Ready(manager.findMultiDisc(root) { done, total -> multiDisc.value = Loadable.Loading(done, total) })
            } catch (e: Exception) {
                Loadable.Failed(e.message ?: str(R.string.rom_manager_couldn_t_look_for_multi_disc))
            }
        }
    }

    val identified = MutableStateFlow<Loadable<IdentifyReport>>(Loadable.Idle)
    /** Systems whose games will be renamed. */
    val renameSystems = MutableStateFlow<Set<String>>(emptySet())
    private var identifyJob: Job? = null

    fun identify() {
        val root = root() ?: return
        identifyJob?.cancel()
        identifyJob = viewModelScope.launch {
            identified.value = Loadable.Loading(0, 0)
            identified.value = try {
                val report = manager.identify(root) { done, total -> identified.value = Loadable.Loading(done, total) }
                renameSystems.value = report.of(IdentifyOutcome.RENAME).map { it.candidate.system }.toSet()
                Loadable.Ready(report)
            } catch (e: Exception) {
                if (!isActive) Loadable.Idle else Loadable.Failed(e.message ?: str(R.string.rom_manager_couldn_t_look))
            }
        }
    }

    fun stopIdentify() {
        identifyJob?.cancel()
        identified.value = Loadable.Idle
    }

    fun toggleRenameSystem(system: String) { renameSystems.value = renameSystems.value.let { if (system in it) it - system else it + system } }

    fun renameJobs() = (identified.value as? Loadable.Ready)?.value?.of(IdentifyOutcome.RENAME).orEmpty().filter { it.candidate.system in renameSystems.value }

    fun askRename() { pending.value = Pending.Rename(renameJobs().size) }

    val headers = MutableStateFlow<Loadable<HeaderReport>>(Loadable.Idle)
    /** Systems whose games' headers will be fixed. */
    val headerSystems = MutableStateFlow<Set<String>>(emptySet())
    private var headersJob: Job? = null

    fun findHeaderProblems() {
        val root = root() ?: return
        headersJob?.cancel()
        headersJob = viewModelScope.launch {
            headers.value = Loadable.Loading(0, 0)
            headers.value = try {
                val report = manager.findHeaderProblems(root) { done, total -> headers.value = Loadable.Loading(done, total) }
                headerSystems.value = report.jobs.map { it.candidate.system }.toSet()
                Loadable.Ready(report)
            } catch (e: Exception) {
                if (!isActive) Loadable.Idle else Loadable.Failed(e.message ?: str(R.string.rom_manager_couldn_t_look))
            }
        }
    }

    fun stopHeaders() {
        headersJob?.cancel()
        headers.value = Loadable.Idle
    }

    fun toggleHeaderSystem(system: String) { headerSystems.value = headerSystems.value.let { if (system in it) it - system else it + system } }

    fun headerJobs() = (headers.value as? Loadable.Ready)?.value?.jobs.orEmpty().filter { it.candidate.system in headerSystems.value }

    fun askFixHeaders() { pending.value = Pending.FixHeaders(headerJobs().size) }

    val loose = MutableStateFlow<Loadable<List<com.retroforge.core.library.SortJob>>>(Loadable.Idle)

    fun findLoose() {
        val root = root() ?: return
        viewModelScope.launch {
            loose.value = Loadable.Loading(0, 0)
            loose.value = try { Loadable.Ready(manager.findLoose(root)) } catch (e: Exception) { Loadable.Failed(e.message ?: str(R.string.rom_manager_couldn_t_look)) }
        }
    }

    fun askSortLoose() {
        pending.value = Pending.SortLoose((loose.value as? Loadable.Ready)?.value.orEmpty().size)
    }

    fun askMakePlaylists() {
        val jobs = (multiDisc.value as? Loadable.Ready)?.value?.jobs.orEmpty()
        pending.value = Pending.MakePlaylists(jobs.size)
    }

    fun refreshHolding() {
        val root = root() ?: return
        viewModelScope.launch { holding.value = manager.holdingSummaries(root) }
    }

    /** Opened from a game's page: select its folder and ask to confirm deleting it. */
    fun askDelete(gameId: Long) {
        viewModelScope.launch {
            val game = library.game(gameId).first() ?: return@launch
            val root = library.rootOf(game) ?: return@launch
            selectRoot(root)
            pending.value = Pending.DeleteGame(game, runCatching { manager.gameParts(root, game) }.getOrDefault(listOf(game.relativePath)))
        }
    }

    fun askPurge(summary: HoldingSummary) { pending.value = Pending.Purge(summary) }

    fun root(): RootEntity? = roots.value?.firstOrNull { it.id == rootId.value }

    fun selectRoot(root: RootEntity) {
        if (rootId.value == root.id) return
        rootId.value = root.id
        duplicates.value = Loadable.Idle
        compressible.value = Loadable.Idle
        multiDisc.value = Loadable.Idle
        stopIdentify()
        stopHeaders()
        pending.value = null
    }

    fun hasWriteAccess(root: RootEntity) = manager.hasWriteAccess(root)

    fun grantWriteAccess(picked: Uri) {
        val root = root() ?: return
        writeAccessRefused.value = !manager.grantWriteAccess(root, picked)
        accessChecks.value++
    }

    fun findDuplicates() {
        val root = root() ?: return
        viewModelScope.launch {
            duplicates.value = Loadable.Loading(0, 0)
            duplicates.value = try {
                val report = manager.findDuplicates(root) { done, total -> duplicates.value = Loadable.Loading(done, total) }
                keepChoice.value = emptyMap()
                // Only sets whose copies are proven identical start ticked.
                selectedSets.value = report.sets.indices.filter { report.sets[it].sameness == Sameness.SAME_DATA }.toSet()
                Loadable.Ready(report)
            } catch (e: Exception) {
                Loadable.Failed(e.message ?: str(R.string.rom_manager_couldn_t_check_for_duplicates))
            }
        }
    }

    fun keepFor(index: Int, set: DuplicateSet) = set.files.firstOrNull { it.relativePath == keepChoice.value[index] } ?: set.keep(keepRule.value)

    fun toggleSet(index: Int) { selectedSets.value = selectedSets.value.let { if (index in it) it - index else it + index } }

    fun chooseKeep(index: Int, path: String) { keepChoice.value = keepChoice.value + (index to path) }

    fun findCompressible() {
        val root = root() ?: return
        viewModelScope.launch {
            compressible.value = Loadable.Loading(0, 0)
            compressible.value = try {
                val report = manager.findCompressible(root) { done, total -> compressible.value = Loadable.Loading(done, total) }
                selectedSystems.value = emptySet()
                Loadable.Ready(report)
            } catch (e: Exception) {
                Loadable.Failed(e.message ?: str(R.string.rom_manager_couldn_t_look_for_games_to))
            }
        }
    }

    fun toggleSystem(system: String) { selectedSystems.value = selectedSystems.value.let { if (system in it) it - system else it + system } }

    fun askMoveDuplicates() {
        val report = (duplicates.value as? Loadable.Ready)?.value ?: return
        pending.value = Pending.MoveDuplicates(selectedSets.value.sumOf { report.sets[it].files.size - 1 })
    }

    fun askCompress() {
        val jobs = selectedJobs()
        pending.value = Pending.Compress(jobs.size, jobs.sumOf { it.inputBytes })
    }

    fun askUndo(log: OperationLog) { pending.value = Pending.Undo(log) }

    fun selectedJobs() = (compressible.value as? Loadable.Ready)?.value?.jobs.orEmpty().filter { it.system in selectedSystems.value }

    fun confirm() {
        val root = root() ?: return
        when (val p = pending.value) {
            is Pending.MoveDuplicates -> {
                val report = (duplicates.value as? Loadable.Ready)?.value ?: return
                manager.moveDuplicates(root, selectedSets.value.sorted().map { report.sets[it] to keepFor(it, report.sets[it]) })
                duplicates.value = Loadable.Idle
            }
            is Pending.Compress -> {
                manager.compress(root, selectedJobs())
                compressible.value = Loadable.Idle
            }
            is Pending.Undo -> manager.undo(root, p.log)
            is Pending.DeleteGame -> manager.deleteGame(root, p.game)
            is Pending.Purge -> manager.purge(root, p.summary.folder)
            is Pending.MakePlaylists -> {
                manager.makePlaylists(root, (multiDisc.value as? Loadable.Ready)?.value?.jobs.orEmpty())
                multiDisc.value = Loadable.Idle
            }
            is Pending.SortLoose -> {
                manager.sortLoose(root, (loose.value as? Loadable.Ready)?.value.orEmpty())
                loose.value = Loadable.Idle
            }
            is Pending.Rename -> {
                manager.rename(root, renameJobs())
                identified.value = Loadable.Idle
            }
            is Pending.FixHeaders -> {
                manager.fixHeaders(root, headerJobs())
                headers.value = Loadable.Idle
            }
            null -> Unit
        }
        pending.value = null
    }

    fun cancelPending() { pending.value = null }
    fun stop() = manager.cancel()
    fun dismissRun() {
        manager.dismiss()
        refreshHolding()
    }

    companion object {
        fun factory(library: LibraryRepository, manager: RomManagerRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { RomManagerViewModel(library, manager) }
        }
    }
}
