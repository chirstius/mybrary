package com.mybrary.app.data.remote

import com.google.gson.annotations.SerializedName
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface GoogleBooksService {
    @GET("volumes")
    suspend fun searchByIsbn(
        @Query("q") query: String,
        @Query("fields") fields: String = "items/volumeInfo/categories",
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
)

/** Extract the primary genre from a Google Books response. */
fun GoogleBooksResponse?.extractGenre(): String? =
    this?.items?.firstOrNull()?.volumeInfo?.categories?.firstOrNull()
        ?.split("/")?.firstOrNull()?.trim()
        ?.takeIf { it.isNotBlank() }
