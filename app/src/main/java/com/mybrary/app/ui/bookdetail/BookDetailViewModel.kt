package com.mybrary.app.ui.bookdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mybrary.app.data.repository.BookRepository
import com.mybrary.app.domain.model.Book
import com.mybrary.app.domain.model.ReadingStatus
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val bookRepository: BookRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val bookId: String = checkNotNull(savedStateHandle["bookId"])

    private val _uiState = MutableStateFlow(BookDetailUiState())
    val uiState: StateFlow<BookDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val book = bookRepository.getById(bookId)
            _uiState.update { it.copy(book = book, isLoading = false) }
        }
    }

    fun updateStatus(status: ReadingStatus) = updateBook { copy(status = status) }
    fun updateProgress(progress: Int) = updateBook { copy(readingProgress = progress) }
    fun updateNotes(notes: String) = updateBook { copy(notes = notes) }
    fun updateLocation(location: String) = updateBook { copy(location = location) }
    fun updateTags(tags: List<String>) = updateBook { copy(tags = tags) }

    fun setLoaned(loanedTo: String, dueDate: LocalDate?) = updateBook {
        copy(loanedTo = loanedTo.ifBlank { null }, loanDueDate = dueDate)
    }

    fun clearLoan() = updateBook { copy(loanedTo = null, loanDueDate = null) }

    fun save() {
        viewModelScope.launch {
            val book = _uiState.value.book ?: return@launch
            bookRepository.save(book)
            _uiState.update { it.copy(isSaved = true) }
        }
    }

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            bookRepository.deleteById(bookId)
            onDeleted()
        }
    }

    private fun updateBook(transform: Book.() -> Book) {
        _uiState.update { state ->
            state.copy(book = state.book?.transform())
        }
    }
}
