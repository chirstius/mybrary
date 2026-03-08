package com.mybrary.app.data.remote.model

import com.google.gson.annotations.SerializedName

// /api/books?bibkeys=ISBN:xxx&jscmd=data&format=json
data class OpenLibraryBibResponse(
    val key: String?,
    val title: String?,
    val authors: List<OlAuthor>?,
    val publishers: List<OlPublisher>?,
    @SerializedName("publish_date") val publishDate: String?,
    @SerializedName("number_of_pages") val numberOfPages: Int?,
    val cover: OlCover?,
    val subjects: List<OlSubject>?,
    @SerializedName("identifiers") val identifiers: OlIdentifiers?,
)

data class OlAuthor(val name: String?)
data class OlPublisher(val name: String?)
data class OlCover(
    val small: String?,
    val medium: String?,
    val large: String?,
)
data class OlSubject(val name: String?)
data class OlIdentifiers(
    val isbn_10: List<String>?,
    val isbn_13: List<String>?,
)

// /search.json?isbn=xxx  — lightweight search fallback
data class OpenLibrarySearchResponse(
    @SerializedName("num_found") val numFound: Int,
    val docs: List<OlSearchDoc>?,
)

data class OlSearchDoc(
    val title: String?,
    @SerializedName("author_name") val authorName: List<String>?,
    val publisher: List<String>?,
    @SerializedName("first_publish_year") val firstPublishYear: Int?,
    @SerializedName("number_of_pages_median") val numberOfPagesMedian: Int?,
    @SerializedName("isbn") val isbn: List<String>?,
    @SerializedName("cover_i") val coverId: Long?,
)
