package com.mybrary.app.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BookDao {

    @Query("SELECT * FROM books ORDER BY dateAdded DESC")
    fun observeAll(): Flow<List<BookEntity>>

    @Query("""
        SELECT * FROM books
        WHERE (:query = '' OR title LIKE '%' || :query || '%'
            OR authors LIKE '%' || :query || '%'
            OR isbn LIKE '%' || :query || '%'
            OR isbn13 LIKE '%' || :query || '%'
            OR notes LIKE '%' || :query || '%'
            OR tags LIKE '%' || :query || '%'
            OR location LIKE '%' || :query || '%')
        AND (:status = '' OR status = :status)
        ORDER BY
            CASE :sortBy
                WHEN 'title' THEN title
                WHEN 'author' THEN authors
                ELSE dateAdded
            END ASC
    """)
    fun observeFiltered(
        query: String,
        status: String,
        sortBy: String,
    ): Flow<List<BookEntity>>

    @Query("SELECT * FROM books WHERE id = :id")
    suspend fun getById(id: String): BookEntity?

    @Query("SELECT * FROM books WHERE isbn = :isbn OR isbn13 = :isbn LIMIT 1")
    suspend fun getByIsbn(isbn: String): BookEntity?

    @Query("SELECT * FROM books WHERE pendingSync = 1")
    suspend fun getPendingSync(): List<BookEntity>

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

    @Query("SELECT COUNT(*) FROM books")
    suspend fun count(): Int

    @Query("SELECT * FROM books WHERE loanedTo IS NOT NULL AND loanedTo != ''")
    fun observeLoaned(): Flow<List<BookEntity>>
}
