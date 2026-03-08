package com.mybrary.app.data.repository

import com.google.gson.Gson
import com.mybrary.app.data.remote.OpenLibraryService
import com.mybrary.app.data.remote.buildBookFromOpenLibrary
import com.mybrary.app.data.remote.buildBookFromSearchDoc
import com.mybrary.app.domain.model.Book
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BookLookupRepository @Inject constructor(
    private val openLibraryService: OpenLibraryService,
    private val gson: Gson,
) {
    /** Look up a book by ISBN using Open Library. Returns null if not found. */
    suspend fun lookupByIsbn(isbn: String): Result<Book?> = runCatching {
        // Try the detailed bib data endpoint first
        val bibResp = openLibraryService.getBookByIsbn("ISBN:$isbn")
        if (bibResp.isSuccessful) {
            val body = bibResp.body()
            if (body != null && body.size() > 0) {
                return@runCatching buildBookFromOpenLibrary(isbn, body, gson)
            }
        }

        // Fall back to search
        val searchResp = openLibraryService.searchByIsbn(isbn)
        if (searchResp.isSuccessful) {
            val doc = searchResp.body()?.docs?.firstOrNull()
            if (doc != null) return@runCatching buildBookFromSearchDoc(isbn, doc)
        }

        null
    }
}
