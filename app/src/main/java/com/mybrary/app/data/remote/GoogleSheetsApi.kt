package com.mybrary.app.data.remote

import com.mybrary.app.data.remote.model.*
import com.mybrary.app.domain.model.Book
import com.mybrary.app.domain.model.ReadingStatus
import com.google.gson.Gson
import com.google.gson.JsonObject
import retrofit2.Response
import retrofit2.http.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

interface GoogleSheetsService {

    @GET("v4/spreadsheets/{spreadsheetId}/values/{range}")
    suspend fun getValues(
        @Path("spreadsheetId") spreadsheetId: String,
        @Path("range") range: String,
        @Header("Authorization") auth: String,
    ): Response<SheetsValuesResponse>

    @POST("v4/spreadsheets/{spreadsheetId}/values/{range}:append")
    suspend fun appendValues(
        @Path("spreadsheetId") spreadsheetId: String,
        @Path("range") range: String,
        @Query("valueInputOption") valueInputOption: String = "RAW",
        @Header("Authorization") auth: String,
        @Body body: SheetsValueRange,
    ): Response<SheetsAppendResponse>

    @PUT("v4/spreadsheets/{spreadsheetId}/values/{range}")
    suspend fun updateValues(
        @Path("spreadsheetId") spreadsheetId: String,
        @Path("range") range: String,
        @Query("valueInputOption") valueInputOption: String = "RAW",
        @Header("Authorization") auth: String,
        @Body body: SheetsValueRange,
    ): Response<SheetsUpdateValuesResponse>

    @POST("v4/spreadsheets/{spreadsheetId}/values:batchUpdate")
    suspend fun batchUpdate(
        @Path("spreadsheetId") spreadsheetId: String,
        @Header("Authorization") auth: String,
        @Body body: SheetsBatchUpdateRequest,
    ): Response<Unit>

    /** Add a new sheet tab to an existing spreadsheet. */
    @POST("v4/spreadsheets/{spreadsheetId}:batchUpdate")
    suspend fun addSheetTab(
        @Path("spreadsheetId") spreadsheetId: String,
        @Header("Authorization") auth: String,
        @Body body: AddSheetTabRequest,
    ): Response<Unit>

    @POST("v4/spreadsheets")
    suspend fun createSpreadsheet(
        @Header("Authorization") auth: String,
        @Body body: CreateSpreadsheetRequest,
    ): Response<CreateSpreadsheetResponse>
}

data class CreateSpreadsheetRequest(
    val properties: SpreadsheetProperties,
    val sheets: List<SheetConfig> = listOf(
        SheetConfig(SheetProperties("Books")),
        SheetConfig(SheetProperties("Genres")),
    ),
)
data class SpreadsheetProperties(val title: String)
data class SheetProperties(val title: String)
data class SheetConfig(val properties: SheetProperties)
data class CreateSpreadsheetResponse(val spreadsheetId: String?, val spreadsheetUrl: String?)

data class AddSheetTabRequest(val requests: List<AddSheetRequestItem>)
data class AddSheetRequestItem(val addSheet: AddSheetBody)
data class AddSheetBody(val properties: SheetProperties)

// Convert a sheet row to a domain Book
fun List<String>.toBook(rowIndex: Int): Book? {
    // Allow rows with or without the Genre column (backward compat)
    if (size < SheetColumns.TOTAL - 1) return null
    val id = getOrElse(SheetColumns.ID) { "" }.ifBlank { return null }
    return try {
        Book(
            id = id,
            isbn = getOrElse(SheetColumns.ISBN) { "" },
            isbn13 = getOrElse(SheetColumns.ISBN13) { "" }.ifBlank { null },
            title = getOrElse(SheetColumns.TITLE) { "Unknown" },
            authors = getOrElse(SheetColumns.AUTHORS) { "" }.splitTags(),
            publisher = getOrElse(SheetColumns.PUBLISHER) { "" }.ifBlank { null },
            publishedYear = getOrElse(SheetColumns.PUBLISHED_YEAR) { "" }.toIntOrNull(),
            pages = getOrElse(SheetColumns.PAGES) { "" }.toIntOrNull(),
            description = getOrElse(SheetColumns.DESCRIPTION) { "" }.ifBlank { null },
            coverUrl = getOrElse(SheetColumns.COVER_URL) { "" }.ifBlank { null },
            status = getOrElse(SheetColumns.STATUS) { "UNREAD" }.let {
                runCatching { ReadingStatus.valueOf(it) }.getOrDefault(ReadingStatus.UNREAD)
            },
            readingProgress = getOrElse(SheetColumns.READING_PROGRESS) { "0" }.toIntOrNull() ?: 0,
            notes = getOrElse(SheetColumns.NOTES) { "" },
            location = getOrElse(SheetColumns.LOCATION) { "" },
            tags = getOrElse(SheetColumns.TAGS) { "" }.splitTags(),
            loanedTo = getOrElse(SheetColumns.LOANED_TO) { "" }.ifBlank { null },
            loanDueDate = getOrElse(SheetColumns.LOAN_DUE_DATE) { "" }.let {
                if (it.isBlank()) null else runCatching { LocalDate.parse(it) }.getOrNull()
            },
            dateAdded = getOrElse(SheetColumns.DATE_ADDED) { "" }.let {
                runCatching { LocalDateTime.parse(it) }.getOrDefault(LocalDateTime.now())
            },
            dateModified = getOrElse(SheetColumns.DATE_MODIFIED) { "" }.let {
                runCatching { LocalDateTime.parse(it) }.getOrDefault(LocalDateTime.now())
            },
            genre = getOrElse(SheetColumns.GENRE) { "" }.ifBlank { null },
            sheetRowIndex = rowIndex,
            pendingSync = false,
        )
    } catch (e: Exception) {
        null
    }
}

fun Book.toSheetRow(): List<String?> = listOf(
    id,
    isbn,
    isbn13 ?: "",
    title,
    authors.joinToString("|"),
    publisher ?: "",
    publishedYear?.toString() ?: "",
    pages?.toString() ?: "",
    description ?: "",
    coverUrl ?: "",
    status.name,
    readingProgress.toString(),
    notes,
    location,
    tags.joinToString("|"),
    loanedTo ?: "",
    loanDueDate?.toString() ?: "",
    dateAdded.toString(),
    dateModified.toString(),
    genre ?: "",
)

private fun String.splitTags() =
    if (isBlank()) emptyList() else split("|").map { it.trim() }.filter { it.isNotEmpty() }
