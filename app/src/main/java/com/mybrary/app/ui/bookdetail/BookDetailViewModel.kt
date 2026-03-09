package com.mybrary.app.ui.bookdetail

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mybrary.app.data.remote.DriveService
import com.mybrary.app.data.repository.BookRepository
import com.mybrary.app.data.repository.GenreRepository
import com.mybrary.app.data.sync.SheetsSyncService
import com.mybrary.app.domain.model.Book
import com.mybrary.app.domain.model.ReadingStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class BookDetailUiState(
    val book: Book? = null,
    val isLoading: Boolean = true,
    val isSaved: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class BookDetailViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookRepository: BookRepository,
    private val genreRepository: GenreRepository,
    private val syncService: SheetsSyncService,
    private val driveService: DriveService,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val bookId: String = checkNotNull(savedStateHandle["bookId"])

    private val _uiState = MutableStateFlow(BookDetailUiState())
    val uiState: StateFlow<BookDetailUiState> = _uiState.asStateFlow()

    val genres: StateFlow<List<String>> = genreRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val book = bookRepository.getById(bookId)
            _uiState.update { it.copy(book = book, isLoading = false) }
        }
    }

    fun updateGenre(genre: String) = updateBook { copy(genre = genre.ifBlank { null }) }
    fun updateStatus(status: ReadingStatus) = updateBook { copy(status = status) }
    fun updateProgress(progress: Int) = updateBook { copy(readingProgress = progress) }
    fun updateNotes(notes: String) = updateBook { copy(notes = notes) }
    fun updateLocation(location: String) = updateBook { copy(location = location) }
    fun updateTags(tags: List<String>) = updateBook { copy(tags = tags) }
    fun updateCoverUrl(url: String) = updateBook { copy(coverUrl = url.ifBlank { null }) }

    fun setLoaned(loanedTo: String, dueDate: LocalDate?) = updateBook {
        copy(loanedTo = loanedTo.ifBlank { null }, loanDueDate = dueDate)
    }

    fun clearLoan() = updateBook { copy(loanedTo = null, loanDueDate = null) }

    fun save() {
        viewModelScope.launch {
            var book = _uiState.value.book ?: return@launch

            // Upload local cover to Drive and replace with drive:// URL
            val coverUrl = book.coverUrl
            if (coverUrl != null && coverUrl.isLocalUri()) {
                val fileId = driveService.uploadImage(context, Uri.parse(coverUrl))
                if (fileId != null) {
                    book = book.copy(coverUrl = "drive://$fileId")
                    _uiState.update { it.copy(book = book) }
                }
            }

            bookRepository.save(book)
            book.genre?.let { genre ->
                val added = genreRepository.add(genre)
                if (added) launch { syncService.pushGenreToSheet(genre) }
            }
            launch { syncService.pushPendingToSheet() }
            _uiState.update { it.copy(isSaved = true) }
        }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            val book = _uiState.value.book
            // Remove from sheet first so a subsequent pull doesn't restore it
            if (book != null) syncService.deleteBookFromSheet(book)
            bookRepository.deleteById(bookId)
            onDeleted()
        }
    }

    private fun updateBook(transform: Book.() -> Book) {
        _uiState.update { state ->
            state.copy(book = state.book?.transform())
        }
    }

    private fun String.isLocalUri() = startsWith("content://") || startsWith("file://")
}
