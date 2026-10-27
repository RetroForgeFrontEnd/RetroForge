package com.retroforge.app

import android.app.Application
import android.content.ContentResolver
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
}

class RetroForgeApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
