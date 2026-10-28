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
    /** The user's own SteamGridDB API key. Stored only in app-private storage, never logged. */
    val steamGridDbKey: String? = null,
    /** Emulator chosen per system (system id -> "RETROARCH" or a standalone emulator id); absent = RetroArch. */
    val emulatorChoices: Map<String, String> = emptyMap(),
    /** Give games started from RetroForge these RetroArch button combos (RetroArch INPUT_COMBO values). */
    val retroArchHotkeys: Boolean = true,
    val menuCombo: Int = 9,
    val quitCombo: Int = 4,
    /** Hold R2 to fast-forward on systems whose controllers had no R2 (GBA, SNES …). */
    val fastForwardOnR2: Boolean = true,
    /** Save a state when a game closes and load it next time, so it carries on where it was left. */
    val resumeGames: Boolean = true,
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
            steamGridDbKey = prefs[SGDB_KEY],
            retroArchHotkeys = prefs[HOTKEYS] ?: true,
            menuCombo = prefs[MENU_COMBO] ?: 9,
            quitCombo = prefs[QUIT_COMBO] ?: 4,
            fastForwardOnR2 = prefs[FAST_FORWARD_R2] ?: true,
            resumeGames = prefs[RESUME] ?: true,
            emulatorChoices = prefs[EMULATORS].orEmpty().lines().filter { '=' in it }.associate { it.substringBefore('=') to it.substringAfter('=') },
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

    suspend fun setSteamGridDbKey(key: String?) {
        store.edit { if (key.isNullOrBlank()) it.remove(SGDB_KEY) else it[SGDB_KEY] = key.trim() }
    }

    suspend fun setEmulatorChoice(system: String, emulator: String?) {
        store.edit { prefs ->
            val choices = prefs[EMULATORS].orEmpty().lines().filter { '=' in it && it.substringBefore('=') != system } +
                listOfNotNull(emulator?.let { "$system=$it" })
            if (choices.isEmpty()) prefs.remove(EMULATORS) else prefs[EMULATORS] = choices.joinToString("\n")
        }
    }

    suspend fun setRetroArchHotkeys(enabled: Boolean, menu: Int, quit: Int) {
        store.edit {
            it[HOTKEYS] = enabled
            it[MENU_COMBO] = menu
            it[QUIT_COMBO] = quit
        }
    }

    suspend fun setResumeGames(enabled: Boolean) {
        store.edit { it[RESUME] = enabled }
    }

    suspend fun setFastForwardOnR2(enabled: Boolean) {
        store.edit { it[FAST_FORWARD_R2] = enabled }
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
        val SGDB_KEY = stringPreferencesKey("steamgriddb_key")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val SHOW_PC = booleanPreferencesKey("show_pc_section")
        val EMULATORS = stringPreferencesKey("emulator_choices")
        val HOTKEYS = booleanPreferencesKey("retroarch_hotkeys")
        val MENU_COMBO = intPreferencesKey("retroarch_menu_combo")
        val QUIT_COMBO = intPreferencesKey("retroarch_quit_combo")
        val FAST_FORWARD_R2 = booleanPreferencesKey("fast_forward_r2")
        val RESUME = booleanPreferencesKey("resume_games")
    }
}
