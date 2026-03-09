package com.mybrary.app.ui.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mybrary.app.data.prefs.SpreadsheetPreferences
import com.mybrary.app.data.sync.SheetsSyncService
import com.mybrary.app.data.sync.SyncResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SetupUiState(
    val currentSpreadsheetId: String = "",
    val newSpreadsheetId: String = "",
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isDone: Boolean = false,
    val message: String? = null,
    val isError: Boolean = false,
)

@HiltViewModel
class SetupViewModel @Inject constructor(
    private val spreadsheetPreferences: SpreadsheetPreferences,
    private val syncService: SheetsSyncService,
) : ViewModel() {

    private val _uiState = MutableStateFlow(SetupUiState())
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val existingId = spreadsheetPreferences.getSpreadsheetId() ?: ""
            _uiState.update { it.copy(currentSpreadsheetId = existingId, newSpreadsheetId = existingId, isLoading = false) }
        }
    }

    fun updateSpreadsheetId(id: String) {
        _uiState.update { it.copy(newSpreadsheetId = id, message = null, isError = false) }
    }

    /** Switch to a different existing spreadsheet (or update the current ID). */
    fun connect() {
        viewModelScope.launch {
            val id = _uiState.value.newSpreadsheetId.trim()
            if (id.isBlank()) {
                _uiState.update { it.copy(message = "Please enter a spreadsheet ID.", isError = true) }
                return@launch
            }
            _uiState.update { it.copy(isSaving = true, message = null, isError = false) }
            spreadsheetPreferences.setSpreadsheetId(id)

            // Write header if the sheet is empty, then pull
            syncService.ensureHeaderRow()
            val pullResult = syncService.pullFromSheet()
            when (pullResult) {
                is SyncResult.Success -> _uiState.update { it.copy(isSaving = false, isDone = true) }
                is SyncResult.Error -> _uiState.update {
                    it.copy(isSaving = false, message = "Sync failed: ${pullResult.message}", isError = true)
                }
            }
        }
    }

    /** Let the app auto-create a new sheet via the Drive API and switch to it. */
    fun createNewSheet() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, message = null, isError = false) }
            // Clear the stored ID so createAndInitSheetIfNeeded will create a fresh one
            spreadsheetPreferences.setSpreadsheetId("")
            val result = syncService.createAndInitSheetIfNeeded()
            when (result) {
                is SyncResult.Success -> {
                    val newId = spreadsheetPreferences.getSpreadsheetId() ?: ""
                    _uiState.update { it.copy(isSaving = false, isDone = true, currentSpreadsheetId = newId) }
                }
                is SyncResult.Error -> _uiState.update {
                    it.copy(isSaving = false, message = "Could not create sheet: ${result.message}", isError = true)
                }
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(message = null, isError = false) }
    }
}
