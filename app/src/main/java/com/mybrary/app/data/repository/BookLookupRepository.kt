package com.mybrary.app.data.repository

import com.google.gson.Gson
import com.mybrary.app.data.remote.GoogleBooksService
import com.mybrary.app.data.remote.OpenLibraryService
import com.mybrary.app.data.remote.buildBookFromOpenLibrary
import com.mybrary.app.data.remote.buildBookFromSearchDoc
import com.mybrary.app.data.remote.extractGenre
import com.mybrary.app.domain.model.Book
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookLookupRepository @Inject constructor(
    private val openLibraryService: OpenLibraryService,
    private val googleBooksService: GoogleBooksService,
    private val gson: Gson,
) {
    /** Look up a book by ISBN using Open Library for metadata/tags, Google Books for genre. */
    suspend fun lookupByIsbn(isbn: String): Result<Book?> = runCatching {
        // Try the detailed bib data endpoint first
        val bibResp = openLibraryService.getBookByIsbn("ISBN:$isbn")
        var book: Book? = null
        if (bibResp.isSuccessful) {
            val body = bibResp.body()
            if (body != null && body.size() > 0) {
                book = buildBookFromOpenLibrary(isbn, body, gson)
            }
        }

        // Fall back to search
        if (book == null) {
            val searchResp = openLibraryService.searchByIsbn(isbn)
            if (searchResp.isSuccessful) {
                val doc = searchResp.body()?.docs?.firstOrNull()
                if (doc != null) book = buildBookFromSearchDoc(isbn, doc)
            }
        }

        // Enrich with genre from Google Books (categories are cleaner BISAC headings)
        if (book != null) {
            val genre = runCatching {
                val resp = googleBooksService.searchByIsbn("isbn:$isbn")
                resp.body().extractGenre()
            }.getOrNull()
            if (genre != null) book = book.copy(genre = genre)
        }

        book
    }
}
