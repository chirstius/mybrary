package com.mybrary.app.ui.scanner

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mybrary.app.data.prefs.SpreadsheetPreferences
import com.mybrary.app.data.remote.DriveService
import com.mybrary.app.data.repository.BookLookupRepository
import com.mybrary.app.data.repository.BookRepository
import com.mybrary.app.data.repository.GenreRepository
import com.mybrary.app.data.sync.LibraryManager
import com.mybrary.app.data.sync.SheetsSyncService
import com.mybrary.app.domain.model.Book
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed class ScanUiState {
    object Scanning : ScanUiState()
    object Loading : ScanUiState()
    data class BookFound(val book: Book, val alreadyInLibrary: Boolean) : ScanUiState()
    data class BookNotFound(val isbn: String) : ScanUiState()
    data class Error(val message: String) : ScanUiState()
    /** Book was added to library; show confirmation dialog. */
    data class Added(val bookId: String, val bookTitle: String) : ScanUiState()
}

@HiltViewModel
class ScannerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val lookupRepository: BookLookupRepository,
    private val bookRepository: BookRepository,
    private val genreRepository: GenreRepository,
    private val syncService: SheetsSyncService,
    private val driveService: DriveService,
    private val prefs: SpreadsheetPreferences,
    private val libraryManager: LibraryManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow<ScanUiState>(ScanUiState.Scanning)
    val uiState: StateFlow<ScanUiState> = _uiState.asStateFlow()

    val autoAddEnabled: StateFlow<Boolean> = prefs.autoAddOnScan
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private suspend fun activeLibraryId() = libraryManager.activeLibraryId.first()

    val genres: StateFlow<List<String>> = genreRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private var lastScannedIsbn: String? = null

    fun onBarcodeDetected(isbn: String) {
        if (isbn == lastScannedIsbn) return
        if (_uiState.value is ScanUiState.Loading) return
        lastScannedIsbn = isbn

        viewModelScope.launch {
            _uiState.value = ScanUiState.Loading

            val existing = bookRepository.getByIsbn(isbn, activeLibraryId())
            if (existing != null) {
                _uiState.value = ScanUiState.BookFound(existing, alreadyInLibrary = true)
                return@launch
            }

            lookupRepository.lookupByIsbn(isbn).fold(
                onSuccess = { book ->
                    if (book != null) {
                        if (autoAddEnabled.value) {
                            saveBookWithGenre(book)
                            _uiState.value = ScanUiState.Added(book.id, book.title)
                        } else {
                            _uiState.value = ScanUiState.BookFound(book, alreadyInLibrary = false)
                        }
                    } else {
                        _uiState.value = ScanUiState.BookNotFound(isbn)
                    }
                },
                onFailure = { e ->
                    _uiState.value = ScanUiState.Error(e.message ?: "Lookup failed")
                },
            )
        }
    }

    fun addToLibrary(book: Book) {
        viewModelScope.launch {
            saveBookWithGenre(book)
            _uiState.value = ScanUiState.Added(book.id, book.title)
        }
    }

    private suspend fun saveBookWithGenre(book: Book) {
        val libBook = book.copy(libraryId = activeLibraryId())
        // Upload local cover to Drive and replace with drive:// URL
        val resolvedBook = libBook.coverUrl?.takeIf { it.isLocalUri() }?.let { localUri ->
            val fileId = driveService.uploadImage(context, Uri.parse(localUri))
            if (fileId != null) libBook.copy(coverUrl = "drive://$fileId") else libBook
        } ?: libBook

        bookRepository.save(resolvedBook)
        resolvedBook.genre?.let { genre ->
            val added = genreRepository.add(genre)
            if (added) viewModelScope.launch { syncService.pushGenreToSheet(genre) }
        }
        viewModelScope.launch { syncService.pushPendingToSheet() }
    }

    private fun String.isLocalUri() = startsWith("content://") || startsWith("file://")

    fun setAutoAdd(enabled: Boolean) {
        viewModelScope.launch { prefs.setAutoAddOnScan(enabled) }
    }

    fun reset() {
        lastScannedIsbn = null
        _uiState.value = ScanUiState.Scanning
    }
}
