package com.mybrary.app.data.repository

import com.mybrary.app.data.local.BookDao
import com.mybrary.app.data.local.toDomain
import com.mybrary.app.data.local.toEntity
import com.mybrary.app.domain.model.Book
import com.mybrary.app.domain.model.ReadingStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookRepository @Inject constructor(
    private val bookDao: BookDao,
) {
    fun observeAll(libraryId: String): Flow<List<Book>> =
        bookDao.observeAll(libraryId).map { list -> list.map { it.toDomain() } }

    fun observeFiltered(
        libraryId: String,
        query: String = "",
        status: ReadingStatus? = null,
        sortBy: String = "dateAdded",
        genre: String? = null,
    ): Flow<List<Book>> =
        bookDao.observeFiltered(
            libraryId = libraryId,
            query = query,
            status = status?.name ?: "",
            sortBy = sortBy,
            genre = genre ?: "",
        ).map { list -> list.map { it.toDomain() } }

    fun observeLoaned(libraryId: String): Flow<List<Book>> =
        bookDao.observeLoaned(libraryId).map { list -> list.map { it.toDomain() } }

    suspend fun getById(id: String): Book? =
        bookDao.getById(id)?.toDomain()

    suspend fun getByIsbn(isbn: String, libraryId: String): Book? =
        bookDao.getByIsbn(isbn, libraryId)?.toDomain()

    suspend fun save(book: Book) {
        // Preserve sheetRowIndex from DB in case it was set by a concurrent sync
        val existing = bookDao.getById(book.id)
        val rowIndex = existing?.sheetRowIndex ?: book.sheetRowIndex
        val updated = book.copy(
            dateModified = LocalDateTime.now(),
            pendingSync = true,
            sheetRowIndex = rowIndex,
        )
        bookDao.upsert(updated.toEntity())
    }

    suspend fun delete(book: Book) {
        bookDao.delete(book.toEntity())
    }

    suspend fun deleteById(id: String) {
        bookDao.deleteById(id)
    }

    /** Upsert books from the sheet and remove any local books not present in the sheet (authoritative pull). */
    suspend fun replaceAllFromSheet(books: List<Book>, libraryId: String) {
        val localPendingIds = bookDao.getPendingSync(libraryId).map { it.id }.toSet()
        val toUpsert = books.filter { it.id !in localPendingIds }
        bookDao.upsertAll(toUpsert.map { it.toEntity() })
        // Remove books no longer in the sheet (e.g. deleted on another device or locally)
        val sheetIds = books.map { it.id }
        if (sheetIds.isEmpty()) {
            bookDao.deleteAllNonPending(libraryId)
        } else {
            bookDao.deleteExceptIds(libraryId, sheetIds)
        }
    }

    suspend fun getPendingSync(libraryId: String): List<Book> =
        bookDao.getPendingSync(libraryId).map { it.toDomain() }

    suspend fun markSynced(id: String, sheetRowIndex: Int) {
        val entity = bookDao.getById(id) ?: return
        bookDao.upsert(entity.copy(pendingSync = false, sheetRowIndex = sheetRowIndex))
    }

    suspend fun count(libraryId: String): Int = bookDao.count(libraryId)

    fun observeLibraryCounts(): Flow<Map<String, Int>> =
        bookDao.observeLibraryCounts().map { list -> list.associate { it.libraryId to it.cnt } }
}
