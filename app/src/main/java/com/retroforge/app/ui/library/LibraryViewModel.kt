package com.retroforge.app.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.retroforge.core.library.GameEntity
import com.retroforge.core.library.LibraryRepository
import com.retroforge.core.library.ProblemEntity
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
class LibraryViewModel(private val repository: LibraryRepository) : ViewModel() {

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
        fun factory(repository: LibraryRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { LibraryViewModel(repository) }
        }
    }
}
