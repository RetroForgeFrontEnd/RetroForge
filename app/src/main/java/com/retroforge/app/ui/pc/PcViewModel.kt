package com.retroforge.app.ui.pc

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.retroforge.pc.PcStatus
import com.retroforge.pc.PcStatusRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PcViewModel(private val repository: PcStatusRepository) : ViewModel() {

    private val _status = MutableStateFlow<PcStatus?>(null)
    val status: StateFlow<PcStatus?> = _status.asStateFlow()

    /** Re-checks the GPU and DroidDeck, e.g. after the user returns from installing DroidDeck. */
    fun refresh() {
        viewModelScope.launch { _status.value = repository.load() }
    }

    companion object {
        fun factory(repository: PcStatusRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { PcViewModel(repository) }
        }
    }
}
