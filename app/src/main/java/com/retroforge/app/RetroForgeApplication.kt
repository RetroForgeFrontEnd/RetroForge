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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import com.retroforge.retro.RetroArchLauncher
import com.retroforge.core.settings.SettingsRepository
import com.retroforge.core.settings.createSettingsRepository
import com.retroforge.pc.DroidDeckBridge
import com.retroforge.pc.PcStatusRepository

/** Manual dependency wiring; small enough that a DI framework would add more than it saves. */
class AppContainer(application: Application) {
    val settings: SettingsRepository = createSettingsRepository(application)
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
    val romManager = RomManagerRepository(library, application.contentResolver, File(application.filesDir, "rom-manager"))

    // Read by SteamGridDB calls on a background thread; kept current from settings.
    @Volatile private var steamGridDbKey: String? = null
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    init {
        appScope.launch { settings.settings.collect { steamGridDbKey = it.steamGridDbKey } }
    }
    val covers = CoverRepository(
        db = library.database,
        coversDir = File(application.filesDir, "covers"),
        cacheDir = application.cacheDir,
        http = JvmHttpGet("RetroForge/${BuildConfig.VERSION_NAME}"),
        steamGridDbKey = { steamGridDbKey },
    )
}

class RetroForgeApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
