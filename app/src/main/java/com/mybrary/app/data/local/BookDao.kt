package com.mybrary.app.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

data class LibraryCount(val libraryId: String, @ColumnInfo(name = "cnt") val cnt: Int)

@Dao
interface BookDao {

    @Query("SELECT * FROM books WHERE libraryId = :libraryId ORDER BY dateAdded DESC")
    fun observeAll(libraryId: String): Flow<List<BookEntity>>

    @Query("""
        SELECT * FROM books
        WHERE libraryId = :libraryId
        AND (:query = '' OR title LIKE '%' || :query || '%'
            OR authors LIKE '%' || :query || '%'
            OR isbn LIKE '%' || :query || '%'
            OR isbn13 LIKE '%' || :query || '%'
            OR notes LIKE '%' || :query || '%'
            OR tags LIKE '%' || :query || '%'
            OR location LIKE '%' || :query || '%')
        AND (:status = '' OR status = :status)
        AND (:genre = '' OR genre = :genre)
        ORDER BY
            CASE :sortBy
                WHEN 'title' THEN title
                WHEN 'author' THEN authors
                ELSE dateAdded
            END ASC
    """)
    fun observeFiltered(
        libraryId: String,
        query: String,
        status: String,
        sortBy: String,
        genre: String,
    ): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getById(id: String): BookEntity?

    @Query("SELECT * FROM books WHERE (isbn = :isbn OR isbn13 = :isbn) AND libraryId = :libraryId LIMIT 1")
    suspend fun getByIsbn(isbn: String, libraryId: String): BookEntity?

    @Query("SELECT * FROM books WHERE pendingSync = 1 AND libraryId = :libraryId")
    suspend fun getPendingSync(libraryId: String): List<BookEntity>

    @Upsert
    suspend fun upsert(book: BookEntity)

    @Upsert
    suspend fun upsertAll(books: List<BookEntity>)

    @Delete
    suspend fun delete(book: BookEntity)

    @Query("DELETE FROM books WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE books SET pendingSync = 0 WHERE id = :id")
    suspend fun markSynced(id: String)

    @Query("DELETE FROM books WHERE libraryId = :libraryId AND pendingSync = 0 AND id NOT IN (:ids)")
    suspend fun deleteExceptIds(libraryId: String, ids: List<String>)

    @Query("DELETE FROM books WHERE libraryId = :libraryId AND pendingSync = 0")
    suspend fun deleteAllNonPending(libraryId: String)

    @Query("SELECT COUNT(*) FROM books WHERE libraryId = :libraryId")
    suspend fun count(libraryId: String): Int

    @Query("SELECT libraryId, COUNT(*) as cnt FROM books GROUP BY libraryId")
    fun observeLibraryCounts(): Flow<List<LibraryCount>>

    @Query("SELECT * FROM books WHERE loanedTo IS NOT NULL AND loanedTo != '' AND libraryId = :libraryId")
    fun observeLoaned(libraryId: String): Flow<List<BookEntity>>
}
