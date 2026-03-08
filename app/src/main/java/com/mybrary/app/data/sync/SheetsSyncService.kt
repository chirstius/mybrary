package com.mybrary.app.data.sync

import android.util.Log
import com.mybrary.app.BuildConfig
import com.mybrary.app.data.remote.AuthTokenStore
import com.mybrary.app.data.remote.GoogleSheetsService
import com.mybrary.app.data.remote.SheetColumns
import com.mybrary.app.data.remote.model.SheetsValueRange
import com.mybrary.app.data.remote.toBook
import com.mybrary.app.data.remote.toSheetRow
import com.mybrary.app.data.repository.BookRepository
import com.mybrary.app.domain.model.Book
import javax.inject.Inject
import javax.inject.Singleton

sealed class SyncResult {
    object Success : SyncResult()
    data class Error(val message: String) : SyncResult()
}

@Singleton
class SheetsSyncService @Inject constructor(
    private val sheetsService: GoogleSheetsService,
    private val bookRepository: BookRepository,
) {
    private val spreadsheetId = BuildConfig.SHEETS_SPREADSHEET_ID
    private val sheetRange = "Books"

    /**
     * Full pull: fetch all rows from the sheet and upsert into local DB.
     * Sheet is the master record — local edits (pendingSync=true) are preserved
     * only if the local dateModified is newer than the sheet's.
     */
    suspend fun pullFromSheet(): SyncResult {
        val auth = AuthTokenStore.bearer()
        val response = runCatching {
            sheetsService.getValues(spreadsheetId, sheetRange, auth)
        }.getOrElse { return SyncResult.Error(it.message ?: "Network error") }

        if (!response.isSuccessful) {
            return SyncResult.Error("HTTP ${response.code()}: ${response.message()}")
        }

        val rows = response.body()?.values ?: return SyncResult.Success
        // Row 0 is the header; data starts at row index 1
        val books = rows.drop(1).mapIndexedNotNull { index, row ->
            row.toBook(rowIndex = index + 2) // +2 because sheet rows are 1-based + header
        }

        bookRepository.replaceAllFromSheet(books)
        Log.d("Sync", "Pulled ${books.size} books from sheet")
        return SyncResult.Success
    }

    /**
     * Push all local books marked pendingSync=true up to the sheet.
     * Uses append for new books (no sheetRowIndex) and update for existing ones.
     */
    suspend fun pushPendingToSheet(): SyncResult {
        val auth = AuthTokenStore.bearer()
        val pending = bookRepository.getPendingSync()
        if (pending.isEmpty()) return SyncResult.Success

        for (book in pending) {
            val result = if (book.sheetRowIndex == null) {
                appendBook(book, auth)
            } else {
                updateBook(book, auth)
            }
            if (result is SyncResult.Error) return result
        }
        return SyncResult.Success
    }

    private suspend fun appendBook(book: Book, auth: String): SyncResult {
        val body = SheetsValueRange(
            range = sheetRange,
            values = listOf(book.toSheetRow()),
        )
        val response = runCatching {
            sheetsService.appendValues(spreadsheetId, sheetRange, auth = auth, body = body)
        }.getOrElse { return SyncResult.Error(it.message ?: "Network error") }

        if (!response.isSuccessful) {
            return SyncResult.Error("Append failed: HTTP ${response.code()}")
        }

        // Parse the updated range to extract the row number (e.g. "Books!A42:S42" → 42)
        val updatedRange = response.body()?.updates?.updatedRange ?: ""
        val rowIndex = Regex("""(\d+)$""").find(updatedRange)?.value?.toIntOrNull()
        if (rowIndex != null) {
            bookRepository.markSynced(book.id, rowIndex)
        }
        return SyncResult.Success
    }

    private suspend fun updateBook(book: Book, auth: String): SyncResult {
        val row = book.sheetRowIndex ?: return SyncResult.Error("No row index")
        val range = "Books!A$row:S$row"
        val body = SheetsValueRange(range = range, values = listOf(book.toSheetRow()))

        val response = runCatching {
            sheetsService.updateValues(spreadsheetId, range, auth = auth, body = body)
        }.getOrElse { return SyncResult.Error(it.message ?: "Network error") }

        if (!response.isSuccessful) {
            return SyncResult.Error("Update failed: HTTP ${response.code()}")
        }
        bookRepository.markSynced(book.id, row)
        return SyncResult.Success
    }

    /**
     * Write the header row if the sheet appears empty.
     * Call once after creating a new spreadsheet.
     */
    suspend fun ensureHeaderRow(): SyncResult {
        val auth = AuthTokenStore.bearer()
        val body = SheetsValueRange(
            range = "Books!A1:S1",
            values = listOf(SheetColumns.HEADER_ROW),
        )
        val response = runCatching {
            sheetsService.updateValues(spreadsheetId, "Books!A1:S1", auth = auth, body = body)
        }.getOrElse { return SyncResult.Error(it.message ?: "Network error") }
        return if (response.isSuccessful) SyncResult.Success
        else SyncResult.Error("Header write failed: HTTP ${response.code()}")
    }
}
