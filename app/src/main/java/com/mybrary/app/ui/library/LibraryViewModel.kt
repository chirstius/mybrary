package com.mybrary.app.ui.library

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mybrary.app.data.prefs.SpreadsheetPreferences
import com.mybrary.app.data.remote.model.SheetColumns
import com.mybrary.app.data.repository.BookRepository
import com.mybrary.app.data.repository.GenreRepository
import com.mybrary.app.data.sync.LibraryManager
import com.mybrary.app.data.sync.SheetsSyncService
import com.mybrary.app.data.sync.SyncResult
import com.mybrary.app.domain.model.Book
import com.mybrary.app.domain.model.ReadingStatus
import com.mybrary.app.domain.model.UserLibrary
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

enum class SortOption { DATE_ADDED, TITLE, AUTHOR }

private data class FilterParams(
    val query: String,
    val status: ReadingStatus?,
    val sort: SortOption,
    val sortAscending: Boolean,
    val genre: String?,
    val libraryId: String,
)

data class LibraryUiState(
    val books: List<Book> = emptyList(),
    val searchQuery: String = "",
    val statusFilter: ReadingStatus? = null,
    val sortOption: SortOption = SortOption.DATE_ADDED,
    val sortAscending: Boolean = true,
    val genreFilter: String? = null,
    val availableGenres: List<String> = emptyList(),
    val isSyncing: Boolean = false,
    val syncMessage: String? = null,
    val activeLibrary: UserLibrary? = null,
    val allLibraries: List<UserLibrary> = emptyList(),
    val showLibrarySwitcher: Boolean = false,
    val isCreatingLibrary: Boolean = false,
    val createLibraryError: String? = null,
    val spreadsheetUrl: String? = null,
    val libraryBookCounts: Map<String, Int> = emptyMap(),
)

private data class SwitcherState(
    val showSwitcher: Boolean,
    val creating: Boolean,
    val error: String?,
    val spreadsheetUrl: String?,
    val libraryCounts: Map<String, Int>,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LibraryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bookRepository: BookRepository,
    private val genreRepository: GenreRepository,
    private val syncService: SheetsSyncService,
    private val libraryManager: LibraryManager,
    private val prefs: SpreadsheetPreferences,
) : ViewModel() {

    private val _exportUri = MutableSharedFlow<Uri>(extraBufferCapacity = 1)
    val exportUri: SharedFlow<Uri> = _exportUri.asSharedFlow()

    init {
        // Silently sync every 5 minutes while the app is in the foreground
        viewModelScope.launch {
            while (true) {
                delay(5 * 60_000L)
                if (!_isSyncing.value) {
                    libraryManager.refreshLibrariesFromDrive()
                    syncService.pushPendingToSheet()
                    syncService.pullFromSheet()
                }
            }
        }
    }

    private val _searchQuery = MutableStateFlow("")
    private val _statusFilter = MutableStateFlow<ReadingStatus?>(null)
    private val _sortOption = MutableStateFlow(SortOption.DATE_ADDED)
    private val _sortAscending = MutableStateFlow(true)
    private val _genreFilter = MutableStateFlow<String?>(null)
    private val _isSyncing = MutableStateFlow(false)
    private val _syncMessage = MutableStateFlow<String?>(null)
    private val _showLibrarySwitcher = MutableStateFlow(false)
    private val _isCreatingLibrary = MutableStateFlow(false)
    private val _createLibraryError = MutableStateFlow<String?>(null)

    init {
        // Restore persisted sort preferences
        viewModelScope.launch {
            val savedSort = runCatching { SortOption.valueOf(prefs.sortOption.first()) }
                .getOrDefault(SortOption.DATE_ADDED)
            _sortOption.value = savedSort
            _sortAscending.value = prefs.sortAscending.first()
        }
    }

    // Filter dimensions + active library ID in one object
    private val filterParams: Flow<FilterParams> = combine(
        _searchQuery, _statusFilter, _sortOption, _sortAscending,
        combine(_genreFilter, libraryManager.activeLibraryId) { g, l -> g to l },
    ) { query, status, sort, asc, (genre, libId) ->
        FilterParams(query, status, sort, asc, genre, libId)
    }

    // Core book + filter state (reacts to library switches automatically)
    private val bookFilterState: Flow<LibraryUiState> = combine(
        filterParams,
        _isSyncing,
        _syncMessage,
        genreRepository.observeAll(),
    ) { filters, syncing, msg, genres ->
        @Suppress("UNCHECKED_CAST")
        arrayOf(filters, syncing, msg, genres)
    }.flatMapLatest { arr ->
        val filters = arr[0] as FilterParams
        val syncing = arr[1] as Boolean
        val msg = arr[2] as String?
        val genres = arr[3] as List<String>
        bookRepository.observeFiltered(
            libraryId = filters.libraryId,
            query = filters.query,
            status = filters.status,
            sortBy = when (filters.sort) {
                SortOption.TITLE -> "title"
                SortOption.AUTHOR -> "author"
                SortOption.DATE_ADDED -> "dateAdded"
            },
            sortAsc = filters.sortAscending,
            genre = filters.genre,
        ).map { books ->
            LibraryUiState(
                books = books,
                searchQuery = filters.query,
                statusFilter = filters.status,
                sortOption = filters.sort,
                sortAscending = filters.sortAscending,
                genreFilter = filters.genre,
                availableGenres = genres,
                isSyncing = syncing,
                syncMessage = msg,
            )
        }
    }

    val uiState: StateFlow<LibraryUiState> = combine(
        bookFilterState,
        libraryManager.activeLibrary,
        libraryManager.libraries,
        combine(
            _showLibrarySwitcher, _isCreatingLibrary, _createLibraryError,
            prefs.spreadsheetId, bookRepository.observeLibraryCounts(),
        ) { s, c, e, id, counts ->
            SwitcherState(s, c, e, id?.let { "https://docs.google.com/spreadsheets/d/$it" }, counts)
        },
    ) { base, activeLib, allLibs, switcher ->
        base.copy(
            activeLibrary = activeLib,
            allLibraries = allLibs,
            showLibrarySwitcher = switcher.showSwitcher,
            isCreatingLibrary = switcher.creating,
            createLibraryError = switcher.error,
            spreadsheetUrl = switcher.spreadsheetUrl,
            libraryBookCounts = switcher.libraryCounts,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    fun setSearchQuery(query: String) { _searchQuery.value = query }
    fun setStatusFilter(status: ReadingStatus?) { _statusFilter.value = status }
    fun setSortOption(sort: SortOption) {
        if (_sortOption.value == sort) {
            _sortAscending.value = !_sortAscending.value
        } else {
            _sortOption.value = sort
            _sortAscending.value = true
        }
        viewModelScope.launch {
            prefs.setSortOption(_sortOption.value.name)
            prefs.setSortAscending(_sortAscending.value)
        }
    }
    fun setGenreFilter(genre: String?) { _genreFilter.value = genre }
    fun clearSyncMessage() { _syncMessage.value = null }

    fun showLibrarySwitcher() { _showLibrarySwitcher.value = true }
    fun dismissLibrarySwitcher() {
        _showLibrarySwitcher.value = false
        _createLibraryError.value = null
    }

    fun switchLibrary(libraryId: String) {
        viewModelScope.launch {
            libraryManager.switchLibrary(libraryId)
            _showLibrarySwitcher.value = false
            _isSyncing.value = true
            syncService.pullFromSheet()
            _isSyncing.value = false
        }
    }

    fun createLibrary(name: String, icon: String = "📚") {
        viewModelScope.launch {
            _isCreatingLibrary.value = true
            _createLibraryError.value = null
            libraryManager.createLibrary(name, icon)
                .onSuccess { _showLibrarySwitcher.value = false }
                .onFailure { _createLibraryError.value = it.message ?: "Failed to create library" }
            _isCreatingLibrary.value = false
        }
    }

    fun setLibraryIcon(libraryId: String, icon: String) {
        viewModelScope.launch { libraryManager.setLibraryIcon(libraryId, icon) }
    }

    fun editLibrary(libraryId: String, name: String, icon: String) {
        viewModelScope.launch {
            libraryManager.renameLibrary(libraryId, name)
            libraryManager.setLibraryIcon(libraryId, icon)
        }
    }

    fun deleteLibrary(libraryId: String) {
        viewModelScope.launch { libraryManager.deleteLibrary(libraryId) }
    }

    fun sync() {
        viewModelScope.launch {
            _isSyncing.value = true
            _syncMessage.value = null
            libraryManager.refreshLibrariesFromDrive()
            val pushResult = syncService.pushPendingToSheet()
            val finalResult = if (pushResult is SyncResult.Success) {
                syncService.pullFromSheet()
            } else pushResult

            _isSyncing.value = false
            _syncMessage.value = when (finalResult) {
                is SyncResult.Success -> "Sync complete"
                is SyncResult.Error -> "Sync error: ${finalResult.message}"
            }
        }
    }

    fun exportLibraryCsv(libraryId: String, libraryName: String) {
        viewModelScope.launch {
            val books = bookRepository.observeAll(libraryId).first()
            val csv = buildString {
                appendLine(SheetColumns.HEADER_ROW.joinToString(",") { it.csvEscape() })
                books.forEach { book ->
                    appendLine(listOf(
                        book.id, book.isbn, book.isbn13 ?: "", book.title,
                        book.authors.joinToString(";"), book.publisher ?: "",
                        book.publishedYear?.toString() ?: "", book.pages?.toString() ?: "",
                        book.description ?: "", book.coverUrl ?: "",
                        book.status.name, book.readingProgress.toString(),
                        book.notes, book.location, book.tags.joinToString(";"),
                        book.loanedTo ?: "", book.loanDueDate?.toString() ?: "",
                        book.dateAdded.toString(), book.dateModified.toString(),
                        book.genre ?: "",
                    ).joinToString(",") { it.csvEscape() })
                }
            }
            val safeName = libraryName.replace(Regex("[^\\w\\s-]"), "").trim().replace(" ", "_")
            val file = File(context.cacheDir, "exports/${safeName}_export.csv")
            file.parentFile?.mkdirs()
            file.writeText(csv)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            _exportUri.emit(uri)
        }
    }

    fun deleteBook(book: Book) {
        viewModelScope.launch {
            syncService.deleteBookFromSheet(book)
            bookRepository.delete(book)
        }
    }
}

private fun String.csvEscape(): String =
    if (contains(',') || contains('"') || contains('\n')) "\"${replace("\"", "\"\"")}\"" else this
