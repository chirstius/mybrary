package com.mybrary.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mybrary.app.data.prefs.SpreadsheetPreferences
import com.mybrary.app.data.repository.GenreRepository
import com.mybrary.app.data.sync.SheetsSyncService
import com.mybrary.app.data.sync.SyncResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val spreadsheetId: String? = null,
    val autoAddOnScan: Boolean = false,
    val genres: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val prefs: SpreadsheetPreferences,
    private val genreRepository: GenreRepository,
    private val syncService: SheetsSyncService,
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = combine(
        prefs.spreadsheetId,
        prefs.autoAddOnScan,
        genreRepository.observeAll(),
    ) { id, autoAdd, genres ->
        SettingsUiState(
            spreadsheetId = id,
            autoAddOnScan = autoAdd,
            genres = genres,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setAutoAddOnScan(enabled: Boolean) {
        viewModelScope.launch { prefs.setAutoAddOnScan(enabled) }
    }

    fun addGenre(name: String) {
        viewModelScope.launch {
            val added = genreRepository.add(name)
            if (added) {
                launch { syncService.pushGenreToSheet(name) }
            }
        }
    }

    fun deleteGenre(name: String) {
        viewModelScope.launch { genreRepository.delete(name) }
    }

    fun connectToSheet(id: String) {
        viewModelScope.launch {
            prefs.setSpreadsheetId(id.trim())
            val result = syncService.ensureHeaderRow()
            val msg = when (result) {
                is SyncResult.Success -> "Connected to sheet"
                is SyncResult.Error -> "Error: ${result.message}"
            }
            // Emit message via a side-channel since uiState is derived
            _message.value = msg
        }
    }

    fun createNewSheet() {
        viewModelScope.launch {
            _isLoading.value = true
            prefs.setSpreadsheetId("")
            val result = syncService.createAndInitSheetIfNeeded()
            _isLoading.value = false
            _message.value = when (result) {
                is SyncResult.Success -> "New sheet created"
                is SyncResult.Error -> "Error: ${result.message}"
            }
        }
    }

    fun clearMessage() { _message.value = null }

    private val _message = MutableStateFlow<String?>(null)
    private val _isLoading = MutableStateFlow(false)

    val message: StateFlow<String?> = _message.asStateFlow()
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
}
