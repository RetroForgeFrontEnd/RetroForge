package com.retroforge.app.ui.library

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.retroforge.core.library.CoverRepository
import com.retroforge.core.library.GameEntity
import com.retroforge.core.library.PlaylistSummary
import java.io.File
import com.retroforge.core.library.LibraryRepository
import com.retroforge.core.library.PlayEntity
import com.retroforge.core.library.ProblemEntity
import com.retroforge.retro.Core
import com.retroforge.retro.LaunchPlan
import com.retroforge.retro.LaunchProblem
import com.retroforge.retro.RetroArchLauncher
import com.retroforge.retro.StandaloneLauncher
import com.retroforge.retro.RetroArchHotkeys
import com.retroforge.retro.HotkeyCombo
import com.retroforge.retro.StoragePaths
import com.retroforge.library.LaunchCommand
import com.retroforge.library.LaunchVars
import com.retroforge.library.StandaloneEmulator
import com.retroforge.library.StandaloneEmulators
import com.retroforge.library.StandaloneOption
import com.retroforge.library.UnsupportedCommandException
import com.retroforge.core.settings.SettingsRepository
import android.content.Context
import android.content.Intent
import com.retroforge.app.player.PlayerActivity
import com.retroforge.player.CoreManager
import com.retroforge.player.CoreState
import com.retroforge.player.PlayerCores
import android.provider.DocumentsContract
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn

data class SystemSummary(val id: String, val name: String, val games: Int, val hacks: Int)

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LibraryViewModel(
    private val repository: LibraryRepository,
    private val retroArch: RetroArchLauncher,
    val covers: CoverRepository,
    private val standalone: StandaloneLauncher,
    private val settings: SettingsRepository,
    private val hotkeys: RetroArchHotkeys,
    private val cores: CoreManager,
    private val coreChoices: com.retroforge.player.CoreChoices,
    private val systemArtStore: com.retroforge.core.library.SystemArtStore,
    private val resolver: android.content.ContentResolver,
    private val viewPrefs: android.content.SharedPreferences,
    private val canvasArt: com.retroforge.core.library.CanvasArtDownloader,
) : ViewModel() {

    private val _systemArt = MutableStateFlow(systemArtStore.cached())
    /** The user's own theme folder's artwork, when they chose one. */
    val ownArt: StateFlow<com.retroforge.core.library.SystemArtIndex?> = _systemArt

    /** The Retro tab's artwork: the user's own theme folder if chosen, else canvas-es (downloaded for their systems). */
    val systemArt: StateFlow<com.retroforge.core.library.SystemArtIndex?> =
        kotlinx.coroutines.flow.combine(_systemArt, canvasArt.index) { own, canvas -> own ?: canvas }
            .stateIn(viewModelScope, SharingStarted.Eagerly, _systemArt.value ?: canvasArt.index.value)
    val indexingArt = MutableStateFlow(false)

    /** Retro tab tiles: console pictures, game art or plain name plates. */
    val tileStyle = MutableStateFlow(TileStyle.of(viewPrefs.getString("tile_style", null)))
    fun nextTileStyle() {
        val next = tileStyle.value.next()
        tileStyle.value = next
        viewPrefs.edit().putString("tile_style", next.name).apply()
        // Game art is larger, so it's only downloaded once someone chooses it.
        if (next == TileStyle.GAME_ART && _systemArt.value == null) {
            viewModelScope.launch { canvasArt.ensure(repository.systemCounts.first().map { it.system }, withGameArt = true) }
        }
    }

    init {
        // Indexes from before console pictures were found are rebuilt once, from the same folder.
        _systemArt.value?.takeIf { it.version < com.retroforge.core.library.SystemArtStore.VERSION }?.let { old ->
            useArtFolder(Uri.parse(old.treeUri))
        }
    }

    /** Reads (never changes) a theme folder's per-system artwork. */
    fun useArtFolder(uri: Uri) {
        runCatching { resolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION) }
        viewModelScope.launch {
            indexingArt.value = true
            val name = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':').orEmpty()
            _systemArt.value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { runCatching { systemArtStore.index(uri, name) }.getOrNull() }
            indexingArt.value = false
        }
    }

    fun clearArtFolder() {
        systemArtStore.clear()
        _systemArt.value = null
    }


    private val emulators = StandaloneEmulators.bundled()

    /** Chosen emulator per system ("RETROARCH" or a standalone id); absent means the built-in player, or RetroArch where it has no core. */
    val emulatorChoices: StateFlow<Map<String, String>> = settings.settings.map { it.emulatorChoices }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    fun standaloneOptions(system: String): List<Pair<StandaloneOption, StandaloneEmulator>> = emulators.optionsFor(system)

    fun installedComponent(emulator: StandaloneEmulator) = standalone.installed(emulator)

    fun hasObtainium() = standalone.hasObtainium()

    fun setEmulatorChoice(system: String, emulator: String?) {
        viewModelScope.launch { settings.setEmulatorChoice(system, emulator) }
    }

    fun getEmulator(emulator: StandaloneEmulator) = standalone.openDownload(emulator)

    /** Starts [game] in a standalone emulator; history is recorded only when it opened. */
    fun playStandalone(game: GameEntity, root: RootEntity?, option: StandaloneOption, emulator: StandaloneEmulator): LaunchProblem? {
        if (root == null) return LaunchProblem.LocationNotSupported
        val component = standalone.installed(emulator) ?: return LaunchProblem.StartFailed("${emulator.name} isn't installed.")
        val romPath = StoragePaths.filePath(root.treeUri, game.documentId)
        val vars = LaunchVars(
            romUri = repository.documentUri(root, game.documentId).toString(),
            romPath = romPath,
            gameDirPath = romPath?.substringBeforeLast('/'),
            romsPath = StoragePaths.filePath(root.treeUri, DocumentsContract.getTreeDocumentId(Uri.parse(root.treeUri))),
        )
        val spec = try {
            LaunchCommand.parse(option.command, vars)
        } catch (e: UnsupportedCommandException) {
            return LaunchProblem.StartFailed("${emulator.name} can't open this game: ${e.message}.")
        }
        return standalone.launch(component, spec).also { if (it == null) repository.recordPlay(game, emulator.id) }
    }

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
    val favourites: StateFlow<List<GameEntity>> = repository.favourites.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val playlists: StateFlow<List<PlaylistSummary>> = repository.playlists.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun isFavourite(game: GameEntity) = repository.isFavourite(game)
    fun setFavourite(game: GameEntity, favourite: Boolean) = repository.setFavourite(game, favourite)
    fun playlist(id: Long) = repository.playlist(id)
    fun playlistGames(id: Long) = repository.playlistGames(id)
    fun playlistsContaining(game: GameEntity) = repository.playlistsContaining(game)
    suspend fun createPlaylist(name: String): Long = repository.createPlaylist(name)
    fun setInPlaylist(id: Long, game: GameEntity, inPlaylist: Boolean) = repository.setInPlaylist(id, game, inPlaylist)
    fun renamePlaylist(id: Long, name: String) = repository.renamePlaylist(id, name)
    fun deletePlaylist(id: Long) = repository.deletePlaylist(id)
    fun movePlaylistGame(id: Long, game: GameEntity, by: Int) = repository.movePlaylistGame(id, game, by)
    fun playFor(gameId: Long): Flow<PlayEntity?> = repository.playFor(gameId)
    fun editFor(game: GameEntity) = repository.editFor(game)
    suspend fun saveDetails(game: GameEntity, details: com.retroforge.core.library.GameDetails) = repository.saveDetails(game, details)
    suspend fun resetDetails(game: GameEntity) = repository.resetDetails(game)

    fun launchPlan(game: GameEntity, root: RootEntity?): LaunchPlan =
        if (root == null) LaunchPlan.NotPossible(LaunchProblem.LocationNotSupported)
        else retroArch.plan(game.system, root.treeUri, game.documentId)

    private val appSettings = settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Starts the game; history is recorded only when RetroArch actually opened. */
    fun play(game: GameEntity, plan: LaunchPlan.Ready, core: Core): LaunchProblem? {
        val s = appSettings.value
        val config = if (s?.retroArchHotkeys != false) {
            hotkeys.prepare(
                plan.install, HotkeyCombo.of(s?.menuCombo ?: 9), HotkeyCombo.of(s?.quitCombo ?: 4),
                fastForwardOnR2 = s?.fastForwardOnR2 != false && game.system in RetroArchHotkeys.NO_R2_SYSTEMS,
                resume = s?.resumeGames != false,
            )
        } else null
        return retroArch.launch(plan.install, plan.romPath, core, config).also { if (it == null) repository.recordPlay(game, core.id) }
    }

    fun openRetroArch(plan: LaunchPlan.Ready) = retroArch.openRetroArch(plan.install)

    /** The built-in player's core for this game, or null when the game needs RetroArch or a standalone app. */
    fun builtInCore(game: GameEntity) = PlayerCores.forGame(game.system, game.fileName, coreChoices.get(game.system))

    val coreStates: StateFlow<Map<String, CoreState>> = cores.states

    fun coreState(id: String) = cores.state(id)

    /** Downloads the core if it isn't here yet (normally it already is), then returns the player's intent. */
    suspend fun builtInIntent(context: Context, game: GameEntity, root: RootEntity, core: PlayerCores.PlayerCore): Intent? {
        if (!cores.ensure(core.id)) return null
        val s = appSettings.value
        repository.recordPlay(game, "retroforge:${core.id}")
        return PlayerActivity.intent(
            context,
            corePath = cores.file(core.id).path,
            uri = repository.documentUri(root, game.documentId),
            fileName = game.fileName,
            system = game.system,
            title = game.title,
            resume = s?.resumeGames != false,
            fastForwardOnR2 = s?.fastForwardOnR2 != false && game.system in RetroArchHotkeys.NO_R2_SYSTEMS,
            menuCombo = HotkeyCombo.of(s?.menuCombo ?: HotkeyCombo.DOWN_SELECT.value),
            quitCombo = HotkeyCombo.of(s?.quitCombo ?: HotkeyCombo.START_SELECT.value),
        )
    }

    fun games(system: String): Flow<List<GameEntity>> = repository.gamesFor(system)
    fun game(id: Long): Flow<GameEntity?> = repository.game(id)
    fun problems(rootId: Long): Flow<List<ProblemEntity>> = repository.problems(rootId)
    suspend fun duplicatesOf(game: GameEntity) = repository.duplicatesOf(game)
    fun systemName(id: String) = repository.systemName(id)
    fun hasAccess(root: RootEntity) = repository.hasAccess(root)

    fun addFolder(uri: Uri) = repository.addFolder(uri)
    fun rescan(root: RootEntity) = repository.scan(root.id)
    fun forget(root: RootEntity) = repository.removeFolder(root)
    fun renameFolder(root: RootEntity, name: String) = repository.renameFolder(root, name)

    /** content:// address of a file in a granted folder, for artwork. */
    fun documentUri(rootsById: Map<Long, RootEntity>, rootId: Long, documentId: String?): Uri? =
        documentId?.let { id -> rootsById[rootId]?.let { repository.documentUri(it, id) } }

    companion object {
        fun factory(
            repository: LibraryRepository,
            retroArch: RetroArchLauncher,
            covers: CoverRepository,
            standalone: StandaloneLauncher,
            settings: SettingsRepository,
            hotkeys: RetroArchHotkeys,
            cores: CoreManager,
            coreChoices: com.retroforge.player.CoreChoices,
            systemArt: com.retroforge.core.library.SystemArtStore,
            resolver: android.content.ContentResolver,
            viewPrefs: android.content.SharedPreferences,
            canvasArt: com.retroforge.core.library.CanvasArtDownloader,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer { LibraryViewModel(repository, retroArch, covers, standalone, settings, hotkeys, cores, coreChoices, systemArt, resolver, viewPrefs, canvasArt) }
        }
    }
}

/** How the Retro tab's system tiles look. */
enum class TileStyle(val label: String) {
    CONSOLES("consoles"), GAME_ART("game art"), PLAIN("plain");

    fun next() = entries[(ordinal + 1) % entries.size]

    companion object {
        fun of(name: String?) = entries.firstOrNull { it.name == name } ?: CONSOLES
    }
}
