package com.retroforge.app.ui.library

import com.retroforge.app.R
import com.retroforge.core.ui.str
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
import com.retroforge.library.IntentExtra
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.retroforge.app.player.GameFiles
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
/** Systems whose emulator app needs the game as a real file: on a network drive it's copied to the phone first. */
private val COPY_FROM_NETWORK = setOf("switch")

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

    /** How a system's games are shown: as the theme has them, or always a list or a grid of covers. */
    val gameLayout = MutableStateFlow(GameLayout.of(viewPrefs.getString("game_layout", null)))
    fun nextGameLayout() = setGameLayout(gameLayout.value.next())
    fun setGameLayout(layout: GameLayout) {
        gameLayout.value = layout
        viewPrefs.edit().putString("game_layout", layout.name).apply()
    }

    /** Retro tab tiles: console pictures, game art, game art with the console on it, or plain name plates. */
    val tileStyle = MutableStateFlow(TileStyle.of(viewPrefs.getString("tile_style", null)))
    fun nextTileStyle() {
        val next = tileStyle.value.next()
        tileStyle.value = next
        viewPrefs.edit().putString("tile_style", next.name).apply()
        // Game art is larger, so it's only downloaded once someone chooses it.
        if (next.usesGameArt && _systemArt.value == null) {
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

    /** A network-drive game being copied to the phone before its emulator app starts: (game id, percent). */
    private val _copying = MutableStateFlow<Pair<Long, Int>?>(null)
    val copying: StateFlow<Pair<Long, Int>?> = _copying

    /**
     * Starts [game] in a standalone emulator; history is recorded only when it opened. Switch games on a network drive
     * are copied to the phone first (with [copying] showing the percentage): Eden maps the game file into memory, which
     * a file streamed from a network drive can't do, so the game froze on a black screen.
     */
    fun playStandalone(
        context: Context, game: GameEntity, root: RootEntity?, option: StandaloneOption, emulator: StandaloneEmulator,
        onProblem: (LaunchProblem?) -> Unit,
        extras: List<IntentExtra> = emptyList(),
    ) {
        if (root == null) return onProblem(LaunchProblem.LocationNotSupported)
        val component = standalone.installed(emulator) ?: return onProblem(LaunchProblem.StartFailed(str(R.string.library_isn_t_installed, emulator.name)))
        val romUri = repository.documentUri(root, game.documentId)
        fun launch(uri: Uri, romPath: String?, inBackground: Boolean = false): LaunchProblem? {
            val vars = LaunchVars(
                romUri = uri.toString(),
                romPath = romPath,
                gameDirPath = romPath?.substringBeforeLast('/'),
                romsPath = StoragePaths.filePath(root.treeUri, DocumentsContract.getTreeDocumentId(Uri.parse(root.treeUri))),
            )
            val spec = try {
                LaunchCommand.parse(option.command, vars).let { it.copy(extras = it.extras + extras) }
            } catch (e: UnsupportedCommandException) {
                return LaunchProblem.StartFailed(str(R.string.library_can_t_open_this_game, emulator.name, e.message))
            }
            if (inBackground) {
                // Android won't let RetroForge open another app from the background: a notification does it on a tap.
                GameCopyService.ready(context, game.title, runCatching { standalone.intent(component, spec) }.getOrElse { return null })
                repository.recordPlay(game, emulator.id)
                return null
            }
            return standalone.launch(component, spec).also { if (it == null) repository.recordPlay(game, emulator.id) }
        }
        if (romUri.authority?.endsWith(".network") != true || game.system !in COPY_FROM_NETWORK) {
            return onProblem(launch(romUri, StoragePaths.filePath(root.treeUri, game.documentId)))
        }
        if (_copying.value != null) return
        onProblem(null)
        _copying.value = game.id to 0
        val app = context.applicationContext
        GameCopyService.start(app, game.title)
        viewModelScope.launch {
            var shown = 0
            val local = withContext(Dispatchers.IO) {
                runCatching {
                    // The built-in player's game cache: copied once, reused next time, the two most recent games kept.
                    val cache = File(context.externalCacheDir ?: context.cacheDir, "games")
                    val dir = File(cache, Integer.toHexString(romUri.toString().hashCode()))
                    GameFiles.prune(cache, keep = 2, current = dir)
                    File(GameFiles(resolver, dir).expose(romUri, game.fileName) { percent ->
                        _copying.value = game.id to percent
                        if (percent != shown) { shown = percent; GameCopyService.progress(app, game.title, percent) }
                    })
                }
            }
            GameCopyService.stop(app)
            _copying.value = null
            local.fold(
                onSuccess = { file ->
                    val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.ports", file)
                    onProblem(launch(uri, null, inBackground = !GameCopyService.inFront()))
                },
                onFailure = { onProblem(LaunchProblem.StartFailed("Couldn't copy ${game.fileName} from the network drive: ${it.message}")) },
            )
        }
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

    /** Retro tab: only systems with games (default), or every system RetroForge knows (Settings → Retro tab). */
    private val showAllSystems = MutableStateFlow(viewPrefs.getBoolean(SHOW_ALL_SYSTEMS, false))
    private val prefsListener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (key == SHOW_ALL_SYSTEMS) showAllSystems.value = prefs.getBoolean(SHOW_ALL_SYSTEMS, false)
    }.also { viewPrefs.registerOnSharedPreferenceChangeListener(it) }

    override fun onCleared() {
        viewPrefs.unregisterOnSharedPreferenceChangeListener(prefsListener)
    }

    val systems: StateFlow<List<SystemSummary>> = kotlinx.coroutines.flow.combine(repository.systemCounts, showAllSystems) { counts, all ->
        val withGames = counts.map { SystemSummary(it.system, repository.systemName(it.system), it.games, it.hacks) }
        val others = if (all) repository.rules.systems.filter { rule -> counts.none { it.system == rule.id } }.map { SystemSummary(it.id, it.name, 0, 0) } else emptyList()
        (withGames + others).sortedBy { it.name.lowercase() }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

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
    val backlog: StateFlow<List<com.retroforge.core.library.BacklogGame>> = repository.backlog.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val playedGames: StateFlow<List<com.retroforge.core.library.PlayedGame>> = repository.playedGames.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val everyGame: StateFlow<List<GameEntity>> = repository.everyGame.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun backlogStatus(game: GameEntity) = repository.backlogStatus(game)
    suspend fun gameFor(system: String, fileName: String): GameEntity? = repository.gameFor(system, fileName)
    fun setBacklog(game: GameEntity, status: com.retroforge.core.library.BacklogStatus?) = repository.setBacklog(game, status)
    fun setFavourite(game: GameEntity, favourite: Boolean) = repository.setFavourite(game, favourite)
    fun hackMark(game: GameEntity) = repository.hackMark(game)
    fun setHackMark(game: GameEntity, isHack: Boolean?) = repository.setHackMark(game, isHack)
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

    /** The built-in player's core for [system]: the chosen one, else the default. */
    fun systemCore(system: String) = PlayerCores.choices(system).let { list -> list.firstOrNull { it.id == coreChoices.get(system) } ?: list.firstOrNull() }

    val coreStates: StateFlow<Map<String, CoreState>> = cores.states

    fun coreState(id: String) = cores.state(id)

    /** Downloads the core if it isn't here yet (normally it already is), then returns the player's intent. */
    suspend fun builtInIntent(context: Context, game: GameEntity, root: RootEntity, core: PlayerCores.PlayerCore, netplay: String? = null, loadState: java.io.File? = null): Intent? {
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
            copyFirst = viewPrefs.getBoolean(PlayerActivity.COPY_FIRST_PREF, false),
            netplay = netplay,
            loadState = loadState,
        )
    }

    fun games(system: String): Flow<List<GameEntity>> = repository.gamesFor(system)
    suspend fun randomGameId(): Long? = repository.randomGameId()
    /** The library's copy of a game another device is hosting for netplay (Netplay.gameId: system|file|size). */
    suspend fun gameIdForNetplay(gameId: String): Long? = gameId.split('|').takeIf { it.size >= 2 }?.let { repository.gameIdFor(it[0], it[1]) }
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
        /** View preference: show systems without games in the Retro tab. */
        const val SHOW_ALL_SYSTEMS = "show_all_systems"
        const val SCAN_ON_START = "scan_on_start"

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
enum class GameLayout(val label: String) {
    THEME("theme layout"), GRID("grid"), LIST("list"), SHELF("bookshelf");

    fun next() = entries[(ordinal + 1) % entries.size]

    companion object {
        fun of(name: String?) = entries.firstOrNull { it.name == name } ?: THEME
    }
}

enum class TileStyle(val label: String) {
    CONSOLES("consoles"), GAME_ART("game art"), ART_AND_CONSOLE("game art + console"), PLAIN("plain");

    val usesGameArt get() = this == GAME_ART || this == ART_AND_CONSOLE

    fun next() = entries[(ordinal + 1) % entries.size]

    companion object {
        fun of(name: String?) = entries.firstOrNull { it.name == name } ?: CONSOLES
    }
}
