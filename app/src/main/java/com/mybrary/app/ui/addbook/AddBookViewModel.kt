package com.mybrary.app.ui.addbook

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mybrary.app.data.repository.BookRepository
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
    val status: ReadingStatus = ReadingStatus.UNREAD,
    val notes: String = "",
    val location: String = "",
    val tags: String = "",
    val isSaving: Boolean = false,
    val savedId: String? = null,
)

@HiltViewModel
class AddBookViewModel @Inject constructor(
    private val bookRepository: BookRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddBookUiState())
    val uiState: StateFlow<AddBookUiState> = _uiState.asStateFlow()

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
                status = book.status,
                notes = book.notes,
                location = book.location,
                tags = book.tags.joinToString(", "),
            )
        }
    }

    fun update(transform: AddBookUiState.() -> AddBookUiState) {
        _uiState.update { it.transform() }
    }

    fun save() {
        viewModelScope.launch {
            val s = _uiState.value
            _uiState.update { it.copy(isSaving = true) }
            val book = Book(
                id = UUID.randomUUID().toString(),
                isbn = s.isbn,
                title = s.title.ifBlank { "Untitled" },
                authors = s.authors.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                publisher = s.publisher.ifBlank { null },
                publishedYear = s.year.toIntOrNull(),
                pages = s.pages.toIntOrNull(),
                description = s.description.ifBlank { null },
                coverUrl = s.coverUrl.ifBlank { null },
                status = s.status,
                notes = s.notes,
                location = s.location,
                tags = s.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                dateAdded = LocalDateTime.now(),
                dateModified = LocalDateTime.now(),
                pendingSync = true,
            )
            bookRepository.save(book)
            _uiState.update { it.copy(isSaving = false, savedId = book.id) }
        }
    }
}
