package com.retroforge.app

import android.app.Application
import android.content.ContentResolver
import com.retroforge.app.ui.hacks.CatalogueFetcher
import com.retroforge.core.library.LibraryDatabase
import com.retroforge.core.library.LibraryRepository
import com.retroforge.core.library.RomManagerRepository
import com.retroforge.hacks.CatalogueStore
import java.io.File
import com.retroforge.artwork.JvmHttpGet
import com.retroforge.core.library.CoverRepository
import com.retroforge.artwork.ScreenScraperApp
import com.retroforge.artwork.ScreenScraperUser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.retroforge.retro.RetroArchLauncher
import com.retroforge.retro.StandaloneLauncher
import com.retroforge.retro.RetroArchHotkeys
import com.retroforge.core.settings.SettingsRepository
import com.retroforge.core.settings.ThemeStore
import com.retroforge.core.settings.createSettingsRepository
import com.retroforge.pc.DroidDeckBridge
import com.retroforge.pc.PcStatusRepository
import com.retroforge.player.CoreManager
import com.retroforge.player.PlayerCores
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/** Manual dependency wiring; small enough that a DI framework would add more than it saves. */
class AppContainer(application: Application) {
    val settings: SettingsRepository = createSettingsRepository(application)
    val themes = ThemeStore(File(application.filesDir, "themes"))
    val droidDeck = DroidDeckBridge(application)
    val pcStatus = PcStatusRepository(droidDeck)
    val contentResolver: ContentResolver = application.contentResolver
    val catalogues = CatalogueStore(
        dir = File(application.filesDir, "catalogues"),
        builtIn = { application.assets.open("catalogues/retroforge-starter.json").bufferedReader().use { it.readText() } },
    )
    val catalogueFetcher = CatalogueFetcher("RetroForge/${BuildConfig.VERSION_NAME}")
    val library = LibraryRepository(LibraryDatabase.create(application), application.contentResolver)
    val retroArch = RetroArchLauncher(application)
    val standalone = StandaloneLauncher(application)
    val retroArchHotkeys = RetroArchHotkeys(application)
    val romManager = RomManagerRepository(library, application.contentResolver, File(application.filesDir, "rom-manager"))
    /** System artwork read from a theme folder the user chose (never shipped). */
    val systemArt = com.retroforge.core.library.SystemArtStore(application.contentResolver, File(application.filesDir, "system-art.json"))
    /** Small view choices (Retro tab tile style). */
    val viewPrefs: android.content.SharedPreferences = application.getSharedPreferences("views", android.content.Context.MODE_PRIVATE)
    /** Default Retro tab artwork, canvas-es by Siddy212 (CC0), fetched for the library's systems only. */
    val canvasArt = com.retroforge.core.library.CanvasArtDownloader(File(application.filesDir, "system-art/canvas-es"), "RetroForge/${BuildConfig.VERSION_NAME}")
    val coreChoices = com.retroforge.player.CoreChoices(File(application.filesDir, "player/cores.properties"))
    /** The built-in player's BIOS folder (the cores' "system" directory); PlayerActivity uses the same path. */
    val playerSystemDir = File(application.getExternalFilesDir(null) ?: application.filesDir, "system")
    val cores = CoreManager(File(application.filesDir, "cores"), "RetroForge/${BuildConfig.VERSION_NAME}")

    // Read by SteamGridDB calls on a background thread; kept current from settings.
    @Volatile private var steamGridDbKey: String? = null
    @Volatile private var screenScraperUser: ScreenScraperUser? = null
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    init {
        appScope.launch {
            settings.settings.collect {
                steamGridDbKey = it.steamGridDbKey
                screenScraperUser = it.screenScraperUser?.let { name -> it.screenScraperPassword?.let { p -> ScreenScraperUser(name, p) } }
            }
        }
        // The built-in player's cores, fetched in the background for whatever systems the library has: the user never
        // has to download one. Runs again when a newly added folder brings new systems.
        appScope.launch {
            library.systemCounts
                .map { counts -> counts.mapNotNull { PlayerCores.forSystem(it.system)?.id }.toSortedSet() }
                .distinctUntilChanged()
                .collect { cores.ensureAll(it) }
        }
        // The Retro tab's artwork for every system in the library, also in the background (console pictures, logos,
        // details; game art only when that tile style is chosen).
        appScope.launch {
            library.systemCounts
                .map { counts -> counts.map { it.system }.toSortedSet() }
                .distinctUntilChanged()
                .collect { canvasArt.ensure(it, withGameArt = viewPrefs.getString("tile_style", null) == "GAME_ART") }
        }
    }
    val covers = CoverRepository(
        db = library.database,
        coversDir = File(application.filesDir, "covers"),
        cacheDir = application.cacheDir,
        http = JvmHttpGet("RetroForge/${BuildConfig.VERSION_NAME}"),
        steamGridDbKey = { steamGridDbKey },
        resolver = application.contentResolver,
        screenScraperApp = { screenScraperApp },
        screenScraperUser = { screenScraperUser },
    )

    /** RetroForge's developer credentials, only when this build was given them (local.properties). */
    private val screenScraperApp: ScreenScraperApp? =
        if (BuildConfig.SCREENSCRAPER_DEV_ID.isBlank()) null
        else ScreenScraperApp(BuildConfig.SCREENSCRAPER_DEV_ID, BuildConfig.SCREENSCRAPER_DEV_PASSWORD, "RetroForge ${BuildConfig.VERSION_NAME}")
}

class RetroForgeApplication : Application(), coil3.SingletonImageLoader.Factory {
    // Theme logos are often SVG.
    override fun newImageLoader(context: coil3.PlatformContext): coil3.ImageLoader =
        coil3.ImageLoader.Builder(context).components { add(coil3.svg.SvgDecoder.Factory()) }.build()

    // Created on first use, so the player process (which never touches it) doesn't open the library or settings.
    val container: AppContainer by lazy { AppContainer(this) }
}
