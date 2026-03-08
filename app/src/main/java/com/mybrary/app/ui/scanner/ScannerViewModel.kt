package com.mybrary.app.ui.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mybrary.app.data.repository.BookLookupRepository
import com.mybrary.app.data.repository.BookRepository
import com.mybrary.app.domain.model.Book
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ScanUiState {
    object Scanning : ScanUiState()
    object Loading : ScanUiState()
    data class BookFound(val book: Book, val alreadyInLibrary: Boolean) : ScanUiState()
    data class BookNotFound(val isbn: String) : ScanUiState()
    data class Error(val message: String) : ScanUiState()
}

@HiltViewModel
class ScannerViewModel @Inject constructor(
    private val lookupRepository: BookLookupRepository,
    private val bookRepository: BookRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ScanUiState>(ScanUiState.Scanning)
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    private var lastScannedIsbn: String? = null

    fun onBarcodeDetected(isbn: String) {
        if (isbn == lastScannedIsbn) return
        if (_uiState.value is ScanUiState.Loading) return
        lastScannedIsbn = isbn

        viewModelScope.launch {
            _uiState.value = ScanUiState.Loading

            // Check if book already in library
            val existing = bookRepository.getByIsbn(isbn)
            if (existing != null) {
                _uiState.value = ScanUiState.BookFound(existing, alreadyInLibrary = true)
                return@launch
            }

            lookupRepository.lookupByIsbn(isbn).fold(
                onSuccess = { book ->
                    _uiState.value = if (book != null) {
                        ScanUiState.BookFound(book, alreadyInLibrary = false)
                    } else {
                        ScanUiState.BookNotFound(isbn)
                    }
                },
                onFailure = { e ->
                    _uiState.value = ScanUiState.Error(e.message ?: "Lookup failed")
                },
            )
        }
    }

    fun addToLibrary(book: Book, onAdded: (bookId: String) -> Unit) {
        viewModelScope.launch {
            bookRepository.save(book)
            onAdded(book.id)
        }
    }

    fun reset() {
        lastScannedIsbn = null
        _uiState.value = ScanUiState.Scanning
    }
}
