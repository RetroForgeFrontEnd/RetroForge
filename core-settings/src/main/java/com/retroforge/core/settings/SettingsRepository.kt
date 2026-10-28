package com.retroforge.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, DARK, LIGHT }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val showPcSection: Boolean = true,
    /** Version of the "no games or BIOS included" notice the user has acknowledged; 0 = never. */
    val noticeAcknowledged: Int = 0,
    val setupDone: Boolean = false,
    /** Folder holding the user's own BIOS files (e.g. RetroBat's "bios"). */
    val biosFolderUri: String? = null,
    /** The folder RetroArch reads BIOS from, granted by the user so RetroForge can copy into it. */
    val retroArchSystemUri: String? = null,
)

fun createSettingsRepository(context: Context): SettingsRepository = SettingsRepository(
    PreferenceDataStoreFactory.create { context.applicationContext.preferencesDataStoreFile("settings") },
)

class SettingsRepository(private val store: DataStore<Preferences>) {

    val settings: Flow<AppSettings> = store.data.map { prefs ->
        AppSettings(
            themeMode = prefs[THEME_MODE]?.let { stored -> ThemeMode.entries.firstOrNull { it.name == stored } }
                ?: AppSettings().themeMode,
            showPcSection = prefs[SHOW_PC] ?: AppSettings().showPcSection,
            noticeAcknowledged = prefs[NOTICE] ?: 0,
            setupDone = prefs[SETUP_DONE] ?: false,
            biosFolderUri = prefs[BIOS_FOLDER],
            retroArchSystemUri = prefs[RA_SYSTEM],
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[THEME_MODE] = mode.name }
    }

    suspend fun setShowPcSection(show: Boolean) {
        store.edit { it[SHOW_PC] = show }
    }

    suspend fun acknowledgeNotice(version: Int) {
        store.edit { it[NOTICE] = version }
    }

    suspend fun setSetupDone(done: Boolean) {
        store.edit { it[SETUP_DONE] = done }
    }

    suspend fun setBiosFolder(uri: String) {
        store.edit { it[BIOS_FOLDER] = uri }
    }

    suspend fun setRetroArchSystemFolder(uri: String) {
        store.edit { it[RA_SYSTEM] = uri }
    }

    private companion object {
        val NOTICE = intPreferencesKey("notice_acknowledged")
        val SETUP_DONE = booleanPreferencesKey("setup_done")
        val BIOS_FOLDER = stringPreferencesKey("bios_folder_uri")
        val RA_SYSTEM = stringPreferencesKey("retroarch_system_uri")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val SHOW_PC = booleanPreferencesKey("show_pc_section")
    }
}
