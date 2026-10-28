package com.retroforge.app.ui.setup

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.retroforge.core.library.BiosInstaller
import com.retroforge.core.library.CopyResult
import com.retroforge.core.library.LibraryRepository
import com.retroforge.core.library.SafScanNode
import com.retroforge.core.settings.AppSettings
import com.retroforge.core.settings.SettingsRepository
import com.retroforge.library.BiosChecker
import com.retroforge.library.BiosReport
import com.retroforge.retro.Core
import com.retroforge.retro.RetroArchCores
import com.retroforge.retro.RetroArchInstall
import com.retroforge.retro.RetroArchLauncher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One core to download in RetroArch, and the systems in the user's library that use it. */
data class CoreNeed(val core: Core, val systems: List<String>, val games: Int)

data class LibrarySummary(val folders: Int, val games: Int, val systemIds: List<String>, val cores: List<CoreNeed>, val unsupported: List<String>)

data class BiosState(
    val checking: Boolean = false,
    val reports: List<BiosReport>? = null,
    val copying: Boolean = false,
    val copyResult: CopyResult? = null,
    val error: String? = null,
)

class SetupViewModel(
    private val library: LibraryRepository,
    private val retroArch: RetroArchLauncher,
    private val settings: SettingsRepository,
    private val resolver: ContentResolver,
) : ViewModel() {

    private val checker = BiosChecker.bundled()
    private val installer = BiosInstaller(resolver)

    val appSettings: StateFlow<AppSettings?> = settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val summary: StateFlow<LibrarySummary?> = combine(library.roots, library.systemCounts) { roots, counts ->
        val bySystem = counts.associate { it.system to it.games }
        val cores = bySystem.keys.mapNotNull { system -> RetroArchCores.coresFor(system).firstOrNull()?.let { it to system } }
            .groupBy({ it.first }, { it.second })
            .map { (core, systems) -> CoreNeed(core, systems.map(library::systemName).sorted(), systems.sumOf { bySystem[it] ?: 0 }) }
            .sortedByDescending { it.games }
        LibrarySummary(
            folders = roots.size,
            games = counts.sumOf { it.games },
            systemIds = bySystem.keys.toList(),
            cores = cores,
            unsupported = bySystem.keys.filter { RetroArchCores.coresFor(it).isEmpty() }.map(library::systemName).sorted(),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _retroArch = MutableStateFlow<RetroArchInstall?>(null)
    val retroArchInstall: StateFlow<RetroArchInstall?> = _retroArch.asStateFlow()

    private val _bios = MutableStateFlow(BiosState())
    val bios: StateFlow<BiosState> = _bios.asStateFlow()

    fun refresh() {
        _retroArch.value = retroArch.findInstall()
    }

    fun openRetroArch() {
        retroArch.findInstall()?.let(retroArch::openRetroArch)
    }

    fun addGameFolder(uri: Uri) = library.addFolder(uri)

    fun onBiosFolderPicked(uri: Uri) {
        resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        viewModelScope.launch {
            settings.setBiosFolder(uri.toString())
            checkBios(uri)
        }
    }

    fun recheckBios() {
        appSettings.value?.biosFolderUri?.let { checkBios(it.toUri()) }
    }

    private fun checkBios(uri: Uri) {
        val systems = summary.value?.systemIds.orEmpty()
        viewModelScope.launch {
            _bios.update { it.copy(checking = true, error = null, copyResult = null) }
            val result = withContext(Dispatchers.IO) {
                runCatching { checker.check(SafScanNode.root(resolver, uri, "bios"), systems) }
            }
            _bios.update {
                it.copy(checking = false, reports = result.getOrNull(), error = result.exceptionOrNull()?.let { e -> "Couldn't read that folder: ${e.message}" })
            }
        }
    }

    fun onRetroArchFolderPicked(uri: Uri) {
        resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        viewModelScope.launch {
            settings.setRetroArchSystemFolder(uri.toString())
            copyBios(uri)
        }
    }

    fun copyBios(target: Uri) {
        val source = appSettings.value?.biosFolderUri?.toUri() ?: return
        val reports = _bios.value.reports ?: return
        viewModelScope.launch {
            _bios.update { it.copy(copying = true, copyResult = null, error = null) }
            val result = withContext(Dispatchers.IO) { runCatching { installer.copy(source, installer.toCopy(reports), target) } }
            _bios.update {
                it.copy(copying = false, copyResult = result.getOrNull(), error = result.exceptionOrNull()?.let { e -> "Copy failed: ${e.message}" })
            }
        }
    }

    fun finish() {
        viewModelScope.launch { settings.setSetupDone(true) }
    }

    fun systemName(id: String) = library.systemName(id)

    companion object {
        fun factory(library: LibraryRepository, retroArch: RetroArchLauncher, settings: SettingsRepository, resolver: ContentResolver): ViewModelProvider.Factory =
            viewModelFactory { initializer { SetupViewModel(library, retroArch, settings, resolver) } }
    }
}
