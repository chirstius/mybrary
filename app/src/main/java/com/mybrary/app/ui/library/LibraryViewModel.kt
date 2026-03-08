package com.mybrary.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mybrary.app.data.repository.BookRepository
import com.mybrary.app.data.sync.SheetsSyncService
import com.mybrary.app.data.sync.SyncResult
import com.mybrary.app.domain.model.Book
import com.mybrary.app.domain.model.ReadingStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SortOption { DATE_ADDED, TITLE, AUTHOR }

data class LibraryUiState(
    val books: List<Book> = emptyList(),
    val searchQuery: String = "",
    val statusFilter: ReadingStatus? = null,
    val sortOption: SortOption = SortOption.DATE_ADDED,
    val isSyncing: Boolean = false,
    val syncMessage: String? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val bookRepository: BookRepository,
    private val syncService: SheetsSyncService,
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _statusFilter = MutableStateFlow<ReadingStatus?>(null)
    private val _sortOption = MutableStateFlow(SortOption.DATE_ADDED)
    private val _isSyncing = MutableStateFlow(false)
    private val _syncMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<LibraryUiState> = combine(
        _searchQuery,
        _statusFilter,
        _sortOption,
        _isSyncing,
        _syncMessage,
    ) { query, status, sort, syncing, msg ->
        Triple(Triple(query, status, sort), syncing, msg)
    }.flatMapLatest { (filters, syncing, msg) ->
        val (query, status, sort) = filters
        bookRepository.observeFiltered(
            query = query,
            status = status,
            sortBy = when (sort) {
                SortOption.TITLE -> "title"
                SortOption.AUTHOR -> "author"
                SortOption.DATE_ADDED -> "dateAdded"
            },
        ).map { books ->
            LibraryUiState(
                books = books,
                searchQuery = query,
                statusFilter = status,
                sortOption = sort,
                isSyncing = syncing,
                syncMessage = msg,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun setSearchQuery(query: String) { _searchQuery.value = query }
    fun setStatusFilter(status: ReadingStatus?) { _statusFilter.value = status }
    fun setSortOption(sort: SortOption) { _sortOption.value = sort }
    fun clearSyncMessage() { _syncMessage.value = null }

    fun sync() {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncMessage.value = null
            val pullResult = syncService.pullFromSheet()
            val pushResult = if (pullResult is SyncResult.Success) {
                syncService.pushPendingToSheet()
            } else pullResult

            _isSyncing.value = false
            _syncMessage.value = when (pushResult) {
                is SyncResult.Success -> "Sync complete"
                is SyncResult.Error -> "Sync error: ${pushResult.message}"
            }
        }
    }

    fun deleteBook(book: Book) {
        viewModelScope.launch {
            bookRepository.delete(book)
        }
    }
}
