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

class RetroForgeApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
