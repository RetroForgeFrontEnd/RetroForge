package com.retroforge.app.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.retroforge.core.library.CoverRepository
import com.retroforge.core.library.GameEntity
import java.io.File
import com.retroforge.core.library.LibraryRepository
import com.retroforge.core.library.PlayEntity
import com.retroforge.core.library.ProblemEntity
import com.retroforge.retro.Core
import com.retroforge.retro.LaunchPlan
import com.retroforge.retro.LaunchProblem
import com.retroforge.retro.RetroArchLauncher
import com.retroforge.core.library.RootEntity
import com.retroforge.core.library.ScanState
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class SystemSummary(val id: String, val name: String, val games: Int, val hacks: Int)

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LibraryViewModel(
    private val repository: LibraryRepository,
    private val retroArch: RetroArchLauncher,
    val covers: CoverRepository,
) : ViewModel() {

    /** Chosen covers by (folder, path); these take priority over gamelist artwork. */
    val chosenCovers: StateFlow<Map<Pair<Long, String>, File>> = covers.covers
        .map { list -> list.associate { (it.rootId to it.relativePath) to covers.fileFor(it) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** What to show for a game: the chosen cover, else its gamelist artwork, else null (title tile). */
    fun art(rootsById: Map<Long, RootEntity>, covers: Map<Pair<Long, String>, File>, game: GameEntity): Any? =
        covers[game.rootId to game.relativePath] ?: documentUri(rootsById, game.rootId, game.imageDocumentId)


    val roots: StateFlow<List<RootEntity>?> = repository.roots.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val scans: StateFlow<Map<Long, ScanState>> = repository.scans

    val systems: StateFlow<List<SystemSummary>> = repository.systemCounts
        .map { counts -> counts.map { SystemSummary(it.system, repository.systemName(it.system), it.games, it.hacks) }.sortedBy { it.name.lowercase() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val hacks: StateFlow<List<GameEntity>> = repository.hacks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val query = MutableStateFlow("")
    val searchResults: StateFlow<List<GameEntity>> = query
        .debounce(250)
        .flatMapLatest { q -> if (q.trim().length < 2) flowOf(emptyList()) else repository.search(q.trim()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentlyPlayed: StateFlow<List<GameEntity>> = repository.recentlyPlayed.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun playFor(gameId: Long): Flow<PlayEntity?> = repository.playFor(gameId)

    fun launchPlan(game: GameEntity, root: RootEntity?): LaunchPlan =
        if (root == null) LaunchPlan.NotPossible(LaunchProblem.LocationNotSupported)
        else retroArch.plan(game.system, root.treeUri, game.documentId)

    /** Starts the game; history is recorded only when RetroArch actually opened. */
    fun play(game: GameEntity, plan: LaunchPlan.Ready, core: Core): LaunchProblem? =
        retroArch.launch(plan.install, plan.romPath, core).also { if (it == null) repository.recordPlay(game, core.id) }

    fun openRetroArch(plan: LaunchPlan.Ready) = retroArch.openRetroArch(plan.install)

    fun games(system: String): Flow<List<GameEntity>> = repository.gamesFor(system)
    fun game(id: Long): Flow<GameEntity?> = repository.game(id)
    fun problems(rootId: Long): Flow<List<ProblemEntity>> = repository.problems(rootId)
    suspend fun duplicatesOf(game: GameEntity) = repository.duplicatesOf(game)
    fun systemName(id: String) = repository.systemName(id)
    fun hasAccess(root: RootEntity) = repository.hasAccess(root)

    fun addFolder(uri: Uri) = repository.addFolder(uri)
    fun rescan(root: RootEntity) = repository.scan(root.id)
    fun forget(root: RootEntity) = repository.removeFolder(root)

    /** content:// address of a file in a granted folder, for artwork. */
    fun documentUri(rootsById: Map<Long, RootEntity>, rootId: Long, documentId: String?): Uri? =
        documentId?.let { id -> rootsById[rootId]?.let { repository.documentUri(it, id) } }

    companion object {
        fun factory(repository: LibraryRepository, retroArch: RetroArchLauncher, covers: CoverRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { LibraryViewModel(repository, retroArch, covers) }
        }
    }
}
