package com.retroforge.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.retroforge.core.settings.AppSettings
import com.retroforge.core.settings.SettingsRepository
import com.retroforge.core.settings.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {

    val settings: StateFlow<AppSettings> =
        repository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())

    val noticeAcknowledged: StateFlow<Int?> =
        repository.settings.map<AppSettings, Int?> { it.noticeAcknowledged }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** null until settings have loaded, so the first-run wizard isn't opened for returning users. */
    val setupDone: StateFlow<Boolean?> =
        repository.settings.map<AppSettings, Boolean?> { it.setupDone }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun markSetupOffered() {
        viewModelScope.launch { repository.setSetupDone(true) }
    }

    fun acknowledgeNotice(version: Int) {
        viewModelScope.launch { repository.acknowledgeNotice(version) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { repository.setThemeMode(mode) }
    }

    fun setShowPcSection(show: Boolean) {
        viewModelScope.launch { repository.setShowPcSection(show) }
    }

    companion object {
        fun factory(repository: SettingsRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(repository) }
        }
    }
}
