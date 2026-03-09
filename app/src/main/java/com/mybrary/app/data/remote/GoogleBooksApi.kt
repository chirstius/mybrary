package com.mybrary.app.data.remote

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface GoogleBooksService {
    @GET("volumes")
    suspend fun searchByIsbn(
        @Query("q") query: String,
        @Query("key") apiKey: String,
        @Query("fields") fields: String = "items/volumeInfo(categories,description,imageLinks,pageCount,publisher,publishedDate)",
        @Query("maxResults") maxResults: Int = 1,
    ): Response<GoogleBooksResponse>
}

data class GoogleBooksResponse(
    val items: List<GoogleBooksItem>?,
)

data class GoogleBooksItem(
    val volumeInfo: GoogleBooksVolumeInfo?,
)

data class GoogleBooksVolumeInfo(
    val categories: List<String>?,
    val description: String?,
    val imageLinks: GoogleBooksImageLinks?,
    val pageCount: Int?,
    val publisher: String?,
    val publishedDate: String?,
)

data class GoogleBooksImageLinks(
    val thumbnail: String?,
    val smallThumbnail: String?,
)
