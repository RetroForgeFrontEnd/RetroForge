package com.retroforge.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, DARK, LIGHT }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.DARK,
    val showPcSection: Boolean = true,
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
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        store.edit { it[THEME_MODE] = mode.name }
    }

    suspend fun setShowPcSection(show: Boolean) {
        store.edit { it[SHOW_PC] = show }
    }

    private companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val SHOW_PC = booleanPreferencesKey("show_pc_section")
    }
}
