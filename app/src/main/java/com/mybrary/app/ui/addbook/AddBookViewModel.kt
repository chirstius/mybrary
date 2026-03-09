package com.mybrary.app.ui.addbook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mybrary.app.data.repository.BookLookupRepository
import com.mybrary.app.data.repository.BookRepository
import com.mybrary.app.data.repository.GenreRepository
import com.mybrary.app.data.sync.LibraryManager
import com.mybrary.app.data.sync.SheetsSyncService
import com.mybrary.app.domain.model.Book
import com.mybrary.app.domain.model.ReadingStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject

data class AddBookUiState(
    val isbn: String = "",
    val title: String = "",
    val authors: String = "",
    val publisher: String = "",
    val year: String = "",
    val pages: String = "",
    val description: String = "",
    val coverUrl: String = "",
    val genre: String = "",
    val status: ReadingStatus = ReadingStatus.UNREAD,
    val notes: String = "",
    val location: String = "",
    val tags: String = "",
    val readingProgress: Int = 0,
    val isSaving: Boolean = false,
    val isLookingUp: Boolean = false,
    val lookupError: String? = null,
    val savedId: String? = null,
)

@HiltViewModel
class AddBookViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val lookupRepository: BookLookupRepository,
    private val genreRepository: GenreRepository,
    private val syncService: SheetsSyncService,
    private val libraryManager: LibraryManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddBookUiState())
    val uiState: StateFlow<AddBookUiState> = _uiState.asStateFlow()

    val genres: StateFlow<List<String>> = genreRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private suspend fun activeLibraryId() = libraryManager.activeLibraryId.first()

    fun prefillIsbn(isbn: String) {
        if (isbn.isNotBlank() && _uiState.value.isbn.isBlank()) {
            _uiState.update { it.copy(isbn = isbn) }
        }
    }

    fun prefill(book: Book) {
        _uiState.update {
            it.copy(
                isbn = book.isbn,
                title = book.title,
                authors = book.authors.joinToString(", "),
                publisher = book.publisher ?: "",
                year = book.publishedYear?.toString() ?: "",
                pages = book.pages?.toString() ?: "",
                description = book.description ?: "",
                coverUrl = book.coverUrl ?: "",
                genre = book.genre ?: "",
                status = book.status,
                notes = book.notes,
                location = book.location,
                tags = book.tags.joinToString(", "),
            )
        }
    }

    /** Look up the current ISBN and populate all fields, as if it were scanned. */
    fun lookupIsbn() {
        val isbn = _uiState.value.isbn.trim()
        if (isbn.isBlank()) return
        viewModelScope.launch {
            _uiState.update { it.copy(isLookingUp = true, lookupError = null) }
            lookupRepository.lookupByIsbn(isbn).fold(
                onSuccess = { book ->
                    if (book != null) {
                        prefill(book)
                        _uiState.update { it.copy(isLookingUp = false) }
                    } else {
                        _uiState.update { it.copy(isLookingUp = false, lookupError = "Book not found for ISBN $isbn") }
                    }
                },
                onFailure = { e ->
                    _uiState.update { it.copy(isLookingUp = false, lookupError = e.message ?: "Lookup failed") }
                },
            )
        }
    }

    fun update(transform: AddBookUiState.() -> AddBookUiState) {
        _uiState.update { it.transform() }
    }

    fun clearLookupError() {
        _uiState.update { it.copy(lookupError = null) }
    }

    fun save() {
        viewModelScope.launch {
            val s = _uiState.value
            _uiState.update { it.copy(isSaving = true) }
            val book = Book(
                id = UUID.randomUUID().toString(),
                libraryId = activeLibraryId(),
                isbn = s.isbn,
                title = s.title.ifBlank { "Untitled" },
                authors = s.authors.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                publisher = s.publisher.ifBlank { null },
                publishedYear = s.year.toIntOrNull(),
                pages = s.pages.toIntOrNull(),
                description = s.description.ifBlank { null },
                coverUrl = s.coverUrl.ifBlank { null },
                genre = s.genre.ifBlank { null },
                status = s.status,
                readingProgress = s.readingProgress,
                notes = s.notes,
                location = s.location,
                tags = s.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                dateAdded = LocalDateTime.now(),
                dateModified = LocalDateTime.now(),
                pendingSync = true,
            )
            bookRepository.save(book)
            book.genre?.let { genre ->
                val added = genreRepository.add(genre)
                if (added) launch { syncService.pushGenreToSheet(genre) }
            }
            _uiState.update { it.copy(isSaving = false, savedId = book.id) }
        }
    }
}
