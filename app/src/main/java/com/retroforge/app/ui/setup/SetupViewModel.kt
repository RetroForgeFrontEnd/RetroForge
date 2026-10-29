package com.retroforge.app.ui.setup

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
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
import kotlinx.coroutines.flow.first
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
    /** 0..1 while checking, across every BIOS folder being read. */
    val progress: Float = 0f,
    val reports: List<BiosReport>? = null,
    val copying: Boolean = false,
    val copyResult: CopyResult? = null,
    /** Copied into the built-in player's own BIOS folder automatically after each check. */
    val playerCopy: CopyResult? = null,
    val error: String? = null,
)

class SetupViewModel(
    private val library: LibraryRepository,
    private val retroArch: RetroArchLauncher,
    private val settings: SettingsRepository,
    private val resolver: ContentResolver,
    /** The built-in player's BIOS ("system") folder. */
    private val playerSystemDir: java.io.File,
    private val cores: com.retroforge.player.CoreManager,
) : ViewModel() {

    /** Download state of the built-in player's emulators (fetched in the background for the library's systems). */
    val coreStates: StateFlow<Map<String, com.retroforge.player.CoreState>> = cores.states

    fun coreState(id: String) = cores.state(id)


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
            // Not playable: no built-in player, no RetroArch core and no standalone emulator for it.
            unsupported = bySystem.keys.filter {
                RetroArchCores.coresFor(it).isEmpty() && com.retroforge.player.PlayerCores.forSystem(it) == null &&
                    standalone.optionsFor(it).isEmpty()
            }.map(library::systemName).sorted(),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _retroArch = MutableStateFlow<RetroArchInstall?>(null)
    val retroArchInstall: StateFlow<RetroArchInstall?> = _retroArch.asStateFlow()

    private val _bios = MutableStateFlow(BiosState())
    val bios: StateFlow<BiosState> = _bios.asStateFlow()

    private val standalone by lazy { com.retroforge.library.StandaloneEmulators.bundled() }

    /** Games outside a folder for their system (loose in the roms folder): setup offers to sort them. */
    suspend fun looseGames(): Int = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        library.roots.first().filter { it.layout != "SINGLE_SYSTEM" }.sumOf { root ->
            library.gamesIn(root.id).count { g -> g.relativePath.split('/').dropLast(1).none { library.rules.forFolder(it)?.id == g.system } }
        }
    }

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

    /**
     * A BIOS folder found without asking: a "bios" folder inside one of the game folders, or beside it on one of
     * RetroForge's own network drives (RetroBat and ES-DE keep bios next to roms). Used and checked when found;
     * returns false when there's none, so the screen can ask for the folder instead.
     */
    suspend fun useFoundBiosFolder(): Boolean {
        val found = withContext(Dispatchers.IO) { foundBiosFolders().firstOrNull() } ?: return false
        settings.setBiosFolder(found.toString())
        checkBios(found)
        return true
    }

    /** BIOS folders beside the game folders that open and aren't empty. */
    private suspend fun foundBiosFolders(): List<Uri> =
        library.roots.first().asSequence().flatMap { root -> biosCandidates(Uri.parse(root.treeUri)) }.distinct()
            .filter { candidate -> runCatching { SafScanNode.root(resolver, candidate, "bios").children().isNotEmpty() }.getOrDefault(false) }
            .toList()

    private fun biosCandidates(tree: Uri): Sequence<Uri> = sequence {
        val authority = tree.authority ?: return@sequence
        val docId = runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull() ?: return@sequence
        val own = authority == "${com.retroforge.app.BuildConfig.APPLICATION_ID}.network"
        // Only RetroForge's own drives can be opened at any folder without a new permission.
        if (!own) return@sequence
        runCatching { SafScanNode.root(resolver, tree, "").children() }.getOrDefault(emptyList())
            .firstOrNull { it.isDirectory && it.name.equals("bios", ignoreCase = true) }
            ?.let { yield(DocumentsContract.buildTreeDocumentUri(authority, it.id)) }
        val share = docId.substringBefore('/')
        val parent = docId.substringAfter('/', "").trimEnd('/').substringBeforeLast('/', "")
        yield(DocumentsContract.buildTreeDocumentUri(authority, "$share/" + listOf(parent, "bios").filter { it.isNotEmpty() }.joinToString("/")))
    }

    fun recheckBios() {
        appSettings.value?.biosFolderUri?.let { checkBios(it.toUri()) }
    }

    private fun checkBios(uri: Uri) {
        val systems = summary.value?.systemIds.orEmpty()
        viewModelScope.launch {
            _bios.update { it.copy(checking = true, progress = 0f, error = null, copyResult = null) }
            // Other BIOS folders RetroForge can read (a RetroBat "bios" beside the games on a network drive) fill in
            // what the chosen one lacks: each system takes the best result from any of them. Progress covers them all.
            val otherFolders = withContext(Dispatchers.IO) { foundBiosFolders().filter { it != uri } }
            val folderCount = 1 + otherFolders.size
            fun progressOf(index: Int): (Float) -> Unit = { p -> _bios.update { it.copy(progress = (index + p) / folderCount) } }
            val result = withContext(Dispatchers.IO) {
                runCatching { checker.check(SafScanNode.root(resolver, uri, "bios"), systems, progressOf(0)) }
            }
            val others = withContext(Dispatchers.IO) {
                otherFolders.mapIndexedNotNull { i, f ->
                    runCatching { f to checker.check(SafScanNode.root(resolver, f, "bios"), systems, progressOf(i + 1)) }.getOrNull()
                }
            }
            val sources = result.getOrNull()?.let { primary -> primary.map { report ->
                (listOf(uri to report) + others.mapNotNull { (f, reports) -> reports.firstOrNull { it.rule == report.rule }?.let { f to it } })
                    .minBy { it.second.status.ordinal }
            } }
            _bios.update {
                it.copy(checking = false, reports = sources?.map { s -> s.second }, error = result.exceptionOrNull()?.let { e -> "Couldn't read that folder: ${e.message}" })
            }
            // The built-in player needs no folder picking: its BIOS folder is RetroForge's own. Each file is copied
            // from the folder it was found in.
            sources?.let { list ->
                val copy = withContext(Dispatchers.IO) {
                    runCatching {
                        list.groupBy({ it.first }, { it.second }).map { (folder, reports) ->
                            installer.copyToFolder(folder, installer.toCopy(reports), playerSystemDir)
                        }.reduce { a, b -> CopyResult(a.copied + b.copied, a.alreadyThere + b.alreadyThere, a.failed + b.failed) }
                    }
                }
                _bios.update { it.copy(playerCopy = copy.getOrNull()) }
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
        fun factory(
            library: LibraryRepository,
            retroArch: RetroArchLauncher,
            settings: SettingsRepository,
            resolver: ContentResolver,
            playerSystemDir: java.io.File,
            cores: com.retroforge.player.CoreManager,
        ): ViewModelProvider.Factory =
            viewModelFactory { initializer { SetupViewModel(library, retroArch, settings, resolver, playerSystemDir, cores) } }
    }
}
