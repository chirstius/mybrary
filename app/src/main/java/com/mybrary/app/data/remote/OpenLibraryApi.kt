package com.mybrary.app.data.remote

import com.mybrary.app.data.remote.model.OpenLibrarySearchResponse
import com.mybrary.app.domain.model.Book
import com.mybrary.app.domain.model.ReadingStatus
import com.google.gson.Gson
import com.google.gson.JsonObject
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query
import java.time.LocalDateTime
import java.util.UUID

interface OpenLibraryService {
    @GET("api/books")
    suspend fun getBookByIsbn(
        @Query("bibkeys") bibkeys: String,   // e.g. "ISBN:9780140328721"
        @Query("jscmd") jscmd: String = "data",
        @Query("format") format: String = "json",
    ): Response<JsonObject>

    @GET("search.json")
    suspend fun searchByIsbn(
        @Query("isbn") isbn: String,
        @Query("fields") fields: String = "title,author_name,publisher,first_publish_year,number_of_pages_median,isbn,cover_i,subject",
        @Query("limit") limit: Int = 1,
    ): Response<OpenLibrarySearchResponse>
}

/** Map Open Library API response to a domain [Book]. */
fun buildBookFromOpenLibrary(isbn: String, json: JsonObject, gson: Gson): Book? {
    val key = "ISBN:$isbn"
    val obj = json.getAsJsonObject(key) ?: return null

    val title = obj.get("title")?.asString ?: return null
    val authors = obj.getAsJsonArray("authors")
        ?.mapNotNull { it.asJsonObject.get("name")?.asString }
        ?: emptyList()
    val publishers = obj.getAsJsonArray("publishers")
        ?.mapNotNull { it.asJsonObject.get("name")?.asString }
    val publishDate = obj.get("publish_date")?.asString
    val pages = obj.get("number_of_pages")?.asInt
    val coverObj = obj.getAsJsonObject("cover")
    val coverUrl = coverObj?.get("large")?.asString
        ?: coverObj?.get("medium")?.asString
        ?: "https://covers.openlibrary.org/b/isbn/$isbn-L.jpg"
    val isbn13 = obj.getAsJsonObject("identifiers")
        ?.getAsJsonArray("isbn_13")?.firstOrNull()?.asString

    val year = publishDate?.let { Regex("""\d{4}""").find(it)?.value?.toIntOrNull() }

    val subjectTags = obj.getAsJsonArray("subjects")
        ?.mapNotNull { runCatching { it.asJsonObject?.get("name")?.asString }.getOrNull() }
        ?.take(5)
        ?: emptyList()

    return Book(
        id = UUID.randomUUID().toString(),
        isbn = isbn,
        isbn13 = isbn13,
        title = title,
        authors = authors,
        publisher = publishers?.firstOrNull(),
        publishedYear = year,
        pages = pages,
        coverUrl = coverUrl,
        tags = subjectTags,
        status = ReadingStatus.UNREAD,
        dateAdded = LocalDateTime.now(),
        dateModified = LocalDateTime.now(),
        pendingSync = true,
    )
}

fun buildBookFromSearchDoc(
    isbn: String,
    doc: com.mybrary.app.data.remote.model.OlSearchDoc,
): Book {
    val coverId = doc.coverId
    val coverUrl = if (coverId != null)
        "https://covers.openlibrary.org/b/id/$coverId-L.jpg"
    else
        "https://covers.openlibrary.org/b/isbn/$isbn-L.jpg"

    return Book(
        id = UUID.randomUUID().toString(),
        isbn = isbn,
        isbn13 = doc.isbn?.firstOrNull { it.length == 13 },
        title = doc.title ?: "Unknown Title",
        authors = doc.authorName ?: emptyList(),
        publisher = doc.publisher?.firstOrNull(),
        publishedYear = doc.firstPublishYear,
        pages = doc.numberOfPagesMedian,
        coverUrl = coverUrl,
        tags = doc.subject?.take(5) ?: emptyList(),
        status = ReadingStatus.UNREAD,
        dateAdded = LocalDateTime.now(),
        dateModified = LocalDateTime.now(),
        pendingSync = true,
    )
}
