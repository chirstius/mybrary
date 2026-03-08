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
    fun observeAll(): Flow<List<Book>> =
        bookDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun observeFiltered(
        query: String = "",
        status: ReadingStatus? = null,
        sortBy: String = "dateAdded",
    ): Flow<List<Book>> =
        bookDao.observeFiltered(
            query = query,
            status = status?.name ?: "",
            sortBy = sortBy,
        ).map { list -> list.map { it.toDomain() } }

    fun observeLoaned(): Flow<List<Book>> =
        bookDao.observeLoaned().map { list -> list.map { it.toDomain() } }

    suspend fun getById(id: String): Book? =
        bookDao.getById(id)?.toDomain()

    suspend fun getByIsbn(isbn: String): Book? =
        bookDao.getByIsbn(isbn)?.toDomain()

    suspend fun save(book: Book) {
        val updated = book.copy(dateModified = LocalDateTime.now(), pendingSync = true)
        bookDao.upsert(updated.toEntity())
    }

    suspend fun delete(book: Book) {
        bookDao.delete(book.toEntity())
    }

    suspend fun deleteById(id: String) {
        bookDao.deleteById(id)
    }

    /** Replace all local books with what came from the sheet (full sync). */
    suspend fun replaceAllFromSheet(books: List<Book>) {
        bookDao.upsertAll(books.map { it.toEntity() })
    }

    suspend fun getPendingSync(): List<Book> =
        bookDao.getPendingSync().map { it.toDomain() }

    suspend fun markSynced(id: String, sheetRowIndex: Int) {
        val entity = bookDao.getById(id) ?: return
        bookDao.upsert(entity.copy(pendingSync = false, sheetRowIndex = sheetRowIndex))
    }

    suspend fun count(): Int = bookDao.count()
}
