package com.mybrary.app.data.remote.model

import com.google.gson.annotations.SerializedName

// GET spreadsheets/{id}/values/{range}
data class SheetsValuesResponse(
    val range: String?,
    val majorDimension: String?,
    val values: List<List<String>>?,
)

// PUT / POST body for updating values
data class SheetsValueRange(
    val range: String,
    val majorDimension: String = "ROWS",
    val values: List<List<String?>>,
)

// POST spreadsheets/{id}/values:batchUpdate
data class SheetsBatchUpdateRequest(
    val valueInputOption: String = "RAW",
    val data: List<SheetsValueRange>,
)

// Response from append
data class SheetsAppendResponse(
    val spreadsheetId: String?,
    val tableRange: String?,
    val updates: SheetsUpdateValuesResponse?,
)

data class SheetsUpdateValuesResponse(
    val spreadsheetId: String?,
    val updatedRange: String?,
    val updatedRows: Int?,
    val updatedColumns: Int?,
    val updatedCells: Int?,
)

// Column indices in the sheet (0-based)
object SheetColumns {
    const val ID = 0
    const val ISBN = 1
    const val ISBN13 = 2
    const val TITLE = 3
    const val AUTHORS = 4
    const val PUBLISHER = 5
    const val PUBLISHED_YEAR = 6
    const val PAGES = 7
    const val DESCRIPTION = 8
    const val COVER_URL = 9
    const val STATUS = 10
    const val READING_PROGRESS = 11
    const val NOTES = 12
    const val LOCATION = 13
    const val TAGS = 14
    const val LOANED_TO = 15
    const val LOAN_DUE_DATE = 16
    const val DATE_ADDED = 17
    const val DATE_MODIFIED = 18
    const val GENRE = 19
    const val TOTAL = 20

    val HEADER_ROW = listOf(
        "ID", "ISBN", "ISBN13", "Title", "Authors", "Publisher",
        "Published Year", "Pages", "Description", "Cover URL",
        "Status", "Reading Progress", "Notes", "Location", "Tags",
        "Loaned To", "Loan Due Date", "Date Added", "Date Modified", "Genre",
    )
}
