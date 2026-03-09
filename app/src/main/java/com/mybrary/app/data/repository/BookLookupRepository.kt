package com.mybrary.app.data.repository

import android.util.Log
import com.google.gson.Gson
import com.mybrary.app.BuildConfig
import com.mybrary.app.data.remote.GoogleBooksService
import com.mybrary.app.data.remote.OpenLibraryService
import com.mybrary.app.data.remote.buildBookFromOpenLibrary
import com.mybrary.app.data.remote.buildBookFromSearchDoc
import com.mybrary.app.data.remote.extractGenre
import com.mybrary.app.domain.model.Book
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "BookLookup"

@Singleton
class BookLookupRepository @Inject constructor(
    private val openLibraryService: OpenLibraryService,
    private val googleBooksService: GoogleBooksService,
    private val gson: Gson,
) {
    /** Look up a book by ISBN using Open Library for metadata/tags, Google Books for genre. */
    suspend fun lookupByIsbn(isbn: String): Result<Book?> = runCatching {
        Log.d(TAG, "lookupByIsbn: $isbn")

        // Try the detailed bib data endpoint first
        val bibResp = openLibraryService.getBookByIsbn("ISBN:$isbn")
        var book: Book? = null
        if (bibResp.isSuccessful) {
            val body = bibResp.body()
            if (body != null && body.size() > 0) {
                book = buildBookFromOpenLibrary(isbn, body, gson)
                Log.d(TAG, "OpenLibrary bib: found '${book?.title}'")
            } else {
                Log.d(TAG, "OpenLibrary bib: empty body (code ${bibResp.code()})")
            }
        } else {
            Log.w(TAG, "OpenLibrary bib: HTTP ${bibResp.code()}")
        }

        // Fall back to search
        if (book == null) {
            val searchResp = openLibraryService.searchByIsbn(isbn)
            if (searchResp.isSuccessful) {
                val doc = searchResp.body()?.docs?.firstOrNull()
                if (doc != null) {
                    book = buildBookFromSearchDoc(isbn, doc)
                    Log.d(TAG, "OpenLibrary search: found '${book?.title}'")
                } else {
                    Log.d(TAG, "OpenLibrary search: no docs")
                }
            } else {
                Log.w(TAG, "OpenLibrary search: HTTP ${searchResp.code()}")
            }
        }

        // Enrich with genre from Google Books (categories are cleaner BISAC headings)
        if (book != null) {
            val genreResult = runCatching {
                val resp = googleBooksService.searchByIsbn("isbn:$isbn", apiKey = BuildConfig.GOOGLE_BOOKS_API_KEY)
                Log.d(TAG, "GoogleBooks HTTP ${resp.code()}, items=${resp.body()?.items?.size}")
                val raw = resp.body()?.items?.firstOrNull()?.volumeInfo?.categories
                Log.d(TAG, "GoogleBooks raw categories: $raw")
                if (resp.code() == 429) null else resp.body().extractGenre()
            }
            val genre = genreResult.getOrNull()
            if (genreResult.isFailure) Log.e(TAG, "GoogleBooks error", genreResult.exceptionOrNull())

            // Fall back to first Open Library subject when Google Books gives nothing
            val resolvedGenre = genre ?: book.tags.firstOrNull()?.takeIf { it.isNotBlank() }
            Log.d(TAG, "Genre resolved: $resolvedGenre (googleBooks=$genre)")
            if (resolvedGenre != null) book = book.copy(genre = resolvedGenre)
        }

        book
    }
}
