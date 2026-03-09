package com.mybrary.app.data.repository

import android.util.Log
import com.google.gson.Gson
import com.mybrary.app.BuildConfig
import com.mybrary.app.data.remote.GoogleBooksService
import com.mybrary.app.data.remote.GoogleBooksVolumeInfo
import com.mybrary.app.data.remote.OpenLibraryService
import com.mybrary.app.data.remote.buildBookFromOpenLibrary
import com.mybrary.app.data.remote.buildBookFromSearchDoc
import com.mybrary.app.domain.model.Book
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "BookLookup"

/**
 * Ordered list of genre/theme keywords to match against a book description.
 * Longer/more-specific phrases are listed first so they match before their
 * shorter sub-strings (e.g. "legal thriller" before "thriller").
 *
 * Note: proper-noun extraction (character names, places) would require an NLP
 * model or LLM API call — a natural next step if richer tag quality is needed.
 */
private val THEME_KEYWORDS = listOf(
    // Specific genre combos first
    "legal thriller", "medical thriller", "psychological thriller",
    "historical fiction", "historical mystery", "historical romance",
    "true crime", "science fiction", "literary fiction",
    "secret society", "forbidden love",
    // Core genres
    "thriller", "mystery", "suspense", "horror", "romance", "fantasy",
    "biography", "memoir", "adventure", "espionage", "spy",
    "detective", "crime", "military", "western", "dystopian",
    "paranormal", "supernatural", "satire",
    // Themes / motifs
    "mythology", "folklore", "religion", "philosophy", "psychology",
    "political", "conspiracy", "ancient", "prophecy", "secret",
    "magic", "quest", "survival", "heist", "betrayal", "revenge",
    "redemption", "coming-of-age",
)

/** Scan [description] for known genre/theme keywords and return matches (up to 8). */
private fun extractTagsFromDescription(description: String): List<String> {
    val lower = description.lowercase()
    return THEME_KEYWORDS.filter { keyword -> lower.contains(keyword) }.take(8)
}

@Singleton
class BookLookupRepository @Inject constructor(
    private val openLibraryService: OpenLibraryService,
    private val googleBooksService: GoogleBooksService,
    private val gson: Gson,
) {
    /** Look up a book by ISBN, blending best-of-breed data from Open Library and Google Books. */
    suspend fun lookupByIsbn(isbn: String): Result<Book?> = runCatching {
        Log.d(TAG, "lookupByIsbn: $isbn")

        // --- Open Library: primary metadata source ---
        val bibResp = openLibraryService.getBookByIsbn("ISBN:$isbn")
        var book: Book? = null
        if (bibResp.isSuccessful) {
            val body = bibResp.body()
            if (body != null && body.size() > 0) {
                book = buildBookFromOpenLibrary(isbn, body, gson)
                Log.d(TAG, "OL bib: '${book?.title}', tags=${book?.tags?.size}, cover=${book?.coverUrl != null}")
            } else {
                Log.d(TAG, "OL bib: empty (${bibResp.code()})")
            }
        } else {
            Log.w(TAG, "OL bib: HTTP ${bibResp.code()}")
        }

        if (book == null) {
            val searchResp = openLibraryService.searchByIsbn(isbn)
            if (searchResp.isSuccessful) {
                val doc = searchResp.body()?.docs?.firstOrNull()
                if (doc != null) {
                    book = buildBookFromSearchDoc(isbn, doc)
                    Log.d(TAG, "OL search: '${book?.title}', tags=${book?.tags?.size}")
                } else {
                    Log.d(TAG, "OL search: no docs")
                }
            } else {
                Log.w(TAG, "OL search: HTTP ${searchResp.code()}")
            }
        }

        // --- Google Books: enrich / fill gaps ---
        if (book != null) {
            val gbResult = runCatching {
                val resp = googleBooksService.searchByIsbn("isbn:$isbn", apiKey = BuildConfig.GOOGLE_BOOKS_API_KEY)
                Log.d(TAG, "GoogleBooks HTTP ${resp.code()}, items=${resp.body()?.items?.size}")
                if (resp.code() == 429) {
                    Log.w(TAG, "GoogleBooks rate-limited")
                    null
                } else {
                    resp.body()?.items?.firstOrNull()?.volumeInfo
                }
            }
            val gb: GoogleBooksVolumeInfo? = gbResult.getOrNull()
            if (gbResult.isFailure) Log.e(TAG, "GoogleBooks error", gbResult.exceptionOrNull())
            Log.d(TAG, "GoogleBooks: categories=${gb?.categories}, pages=${gb?.pageCount}, hasDesc=${gb?.description != null}, hasCover=${gb?.imageLinks != null}")

            // Genre: GB BISAC categories (cleaner headings) → OL first subject fallback
            val genre = gb?.categories?.firstOrNull()
                ?.split("/")?.firstOrNull()?.trim()?.takeIf { it.isNotBlank() }
                ?: book.tags.firstOrNull()?.takeIf { it.isNotBlank() }
            Log.d(TAG, "Genre resolved: $genre")

            // Tags: OL subjects (richest) → GB categories → extract from description
            val gbCategories = gb?.categories.orEmpty()
            val resolvedDescription = book.description?.takeIf { it.isNotBlank() }
                ?: gb?.description?.takeIf { it.isNotBlank() }
            val tags: List<String> = when {
                book.tags.isNotEmpty() -> book.tags
                gbCategories.isNotEmpty() -> gbCategories
                resolvedDescription != null -> {
                    val extracted = extractTagsFromDescription(resolvedDescription)
                    Log.d(TAG, "Tags from description keywords: $extracted")
                    extracted
                }
                else -> emptyList()
            }

            // Blend best-of-breed fields: prefer OL, fill gaps from Google Books
            val gbCoverUrl = gb?.imageLinks?.thumbnail
                ?.replace("http://", "https://") // GB serves http, enforce https
            book = book.copy(
                description   = resolvedDescription,
                coverUrl      = book.coverUrl?.takeIf { it.isNotBlank() } ?: gbCoverUrl,
                publisher     = book.publisher?.takeIf { it.isNotBlank() } ?: gb?.publisher,
                publishedYear = book.publishedYear ?: gb?.publishedDate?.take(4)?.toIntOrNull(),
                pages         = book.pages?.takeIf { it > 0 } ?: gb?.pageCount?.takeIf { it > 0 },
                genre         = genre,
                tags          = tags,
            )
            Log.d(TAG, "Final: genre=$genre, tags=$tags, pages=${book.pages}, cover=${book.coverUrl != null}")
        }

        book
    }
}
