package com.mybrary.app.data.sync

import android.util.Log
import com.mybrary.app.data.prefs.SpreadsheetPreferences
import com.mybrary.app.data.remote.AuthTokenStore
import com.mybrary.app.data.remote.AddSheetBody
import com.mybrary.app.data.remote.AddSheetRequestItem
import com.mybrary.app.data.remote.AddSheetTabRequest
import com.mybrary.app.data.remote.CreateSpreadsheetRequest
import com.mybrary.app.data.remote.DriveService
import com.mybrary.app.data.remote.GoogleSheetsService
import com.mybrary.app.data.remote.MybraryDriveConfig
import com.mybrary.app.data.remote.SheetProperties
import com.mybrary.app.data.remote.SpreadsheetProperties
import com.mybrary.app.data.remote.model.SheetColumns
import com.mybrary.app.data.remote.model.SheetsValueRange
import com.mybrary.app.data.remote.toBook
import com.mybrary.app.data.remote.toSheetRow
import com.mybrary.app.data.repository.BookRepository
import com.mybrary.app.data.repository.GenreRepository
import com.mybrary.app.domain.model.Book
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val spreadsheetPreferences: SpreadsheetPreferences,
    private val driveService: DriveService,
    private val genreRepository: GenreRepository,
) {
    private val sheetRange = "Books"
    private val genreRange = "Genres"
    private val pushMutex = Mutex()

    private suspend fun getSpreadsheetId(): String? = spreadsheetPreferences.getSpreadsheetId()
    private suspend fun getActiveLibraryId(): String = spreadsheetPreferences.getActiveLibraryId()

    /**
     * Runs [block] with the current auth token. On 401, refreshes the token and retries once.
     */
    private suspend fun <T> withToken(block: suspend (String) -> retrofit2.Response<T>): retrofit2.Response<T> {
        val first = block(AuthTokenStore.bearer())
        if (first.code() != 401) return first
        return if (AuthTokenStore.refresh()) block(AuthTokenStore.bearer()) else first
    }

    /**
     * Full pull: fetch all rows from the sheet and upsert into local DB.
     * Sheet is the master record — local edits (pendingSync=true) are preserved
     * only if the local dateModified is newer than the sheet's.
     */
    suspend fun pullFromSheet(): SyncResult {
        val spreadsheetId = getSpreadsheetId()
            ?: return SyncResult.Error("No Google Sheet configured. Please set up your spreadsheet in the app settings.")
        val response = runCatching {
            withToken { auth -> sheetsService.getValues(spreadsheetId, sheetRange, auth) }
        }.getOrElse { return SyncResult.Error(it.message ?: "Network error") }

        if (!response.isSuccessful) {
            return SyncResult.Error("HTTP ${response.code()}: ${response.message()}")
        }

        val rows = response.body()?.values ?: return SyncResult.Success
        val libraryId = getActiveLibraryId()
        // Row 0 is the header; data starts at row index 1
        val books = rows.drop(1).mapIndexedNotNull { index, row ->
            row.toBook(rowIndex = index + 2)?.copy(libraryId = libraryId)
        }

        bookRepository.replaceAllFromSheet(books, libraryId)
        Log.d("Sync", "Pulled ${books.size} books from sheet")

        // Also pull genres
        pullGenresFromSheet()
        return SyncResult.Success
    }

    /** Pull all genres from the "Genres" sheet tab and upsert into the local DB. */
    suspend fun pullGenresFromSheet(): SyncResult {
        val spreadsheetId = getSpreadsheetId() ?: return SyncResult.Success
        var response = runCatching {
            withToken { auth -> sheetsService.getValues(spreadsheetId, "$genreRange!A:A", auth) }
        }.getOrElse { return SyncResult.Error(it.message ?: "Network error") }
        if (!response.isSuccessful) {
            // Tab likely doesn't exist yet — create it and retry once
            ensureGenresSheet()
            response = runCatching {
                withToken { auth -> sheetsService.getValues(spreadsheetId, "$genreRange!A:A", auth) }
            }.getOrElse { return SyncResult.Success }
        }
        if (!response.isSuccessful) return SyncResult.Success
        val rows = response.body()?.values ?: return SyncResult.Success
        val names = rows.drop(1).mapNotNull { it.firstOrNull()?.trim()?.ifBlank { null } }
        genreRepository.addAll(names)
        Log.d("Sync", "Pulled ${names.size} genres from sheet")
        return SyncResult.Success
    }

    /** Append a single genre name to the "Genres" sheet tab. */
    suspend fun pushGenreToSheet(name: String): SyncResult {
        val spreadsheetId = getSpreadsheetId() ?: return SyncResult.Success
        val body = SheetsValueRange(range = genreRange, values = listOf(listOf(name)))
        var response = runCatching {
            withToken { auth -> sheetsService.appendValues(spreadsheetId, genreRange, auth = auth, body = body) }
        }.getOrElse { return SyncResult.Error(it.message ?: "Network error") }
        if (!response.isSuccessful) {
            // Tab likely missing — create it then retry once
            ensureGenresSheet()
            response = runCatching {
                withToken { auth -> sheetsService.appendValues(spreadsheetId, genreRange, auth = auth, body = body) }
            }.getOrElse { return SyncResult.Error(it.message ?: "Network error") }
        }
        return if (response.isSuccessful) SyncResult.Success
        else SyncResult.Error("Genre push failed: HTTP ${response.code()}")
    }

    /**
     * Ensure the "Genres" sheet tab exists with a header row.
     * Creates the tab via batchUpdate if it doesn't exist yet.
     */
    suspend fun ensureGenresSheet(): SyncResult {
        val spreadsheetId = getSpreadsheetId() ?: return SyncResult.Success
        val headerBody = SheetsValueRange(range = "$genreRange!A1", values = listOf(listOf("Genre")))
        val response = runCatching {
            withToken { auth -> sheetsService.updateValues(spreadsheetId, "$genreRange!A1", auth = auth, body = headerBody) }
        }.getOrElse { return SyncResult.Error(it.message ?: "Network error") }

        if (response.isSuccessful) return SyncResult.Success

        // HTTP 400 typically means the sheet tab doesn't exist — create it then retry
        if (response.code() == 400) {
            val addReq = AddSheetTabRequest(listOf(AddSheetRequestItem(AddSheetBody(SheetProperties("Genres")))))
            runCatching {
                withToken { auth -> sheetsService.addSheetTab(spreadsheetId, auth, addReq) }
            }.getOrElse { return SyncResult.Error(it.message ?: "Failed to create Genres tab") }
            val retry = runCatching {
                withToken { auth -> sheetsService.updateValues(spreadsheetId, "$genreRange!A1", auth = auth, body = headerBody) }
            }.getOrElse { return SyncResult.Error(it.message ?: "Network error") }
            return if (retry.isSuccessful) SyncResult.Success
            else SyncResult.Error("Genres header write failed: HTTP ${retry.code()}")
        }
        Log.w("Sync", "Could not write Genres header: HTTP ${response.code()} — continuing")
        return SyncResult.Success // non-fatal
    }

    /**
     * Clear a book's row in the sheet so it is removed on the next pull.
     * No-op if the book has no sheetRowIndex (was never synced).
     */
    suspend fun deleteBookFromSheet(book: Book): SyncResult {
        val spreadsheetId = getSpreadsheetId() ?: return SyncResult.Success
        val row = book.sheetRowIndex ?: return SyncResult.Success
        val range = "Books!A$row:T$row"
        val emptyRow = List(SheetColumns.TOTAL) { "" }
        val body = SheetsValueRange(range = range, values = listOf(emptyRow))
        val response = runCatching {
            withToken { auth -> sheetsService.updateValues(spreadsheetId, range, auth = auth, body = body) }
        }.getOrElse { return SyncResult.Error(it.message ?: "Network error") }
        return if (response.isSuccessful) SyncResult.Success
        else SyncResult.Error("Sheet row clear failed: HTTP ${response.code()}")
    }

    /**
     * Push all local books marked pendingSync=true up to the sheet.
     * Uses append for new books (no sheetRowIndex) and update for existing ones.
     */
    suspend fun pushPendingToSheet(): SyncResult = pushMutex.withLock {
        val spreadsheetId = getSpreadsheetId()
            ?: return@withLock SyncResult.Error("No Google Sheet configured.")
        val libraryId = getActiveLibraryId()
        val pending = bookRepository.getPendingSync(libraryId)
        if (pending.isEmpty()) return@withLock SyncResult.Success

        for (book in pending) {
            val result = if (book.sheetRowIndex == null) {
                appendBook(book, spreadsheetId)
            } else {
                updateBook(book, spreadsheetId)
            }
            if (result is SyncResult.Error) return@withLock result
        }
        SyncResult.Success
    }

    private suspend fun appendBook(book: Book, spreadsheetId: String): SyncResult {
        val appendRange = "Books!A:T"
        val body = SheetsValueRange(
            range = appendRange,
            values = listOf(book.toSheetRow()),
        )
        val response = runCatching {
            withToken { auth -> sheetsService.appendValues(spreadsheetId, appendRange, auth = auth, body = body) }
        }.getOrElse { return SyncResult.Error(it.message ?: "Network error") }

        if (!response.isSuccessful) {
            return SyncResult.Error("Append failed: HTTP ${response.code()}")
        }

        // Parse the updated range to extract the row number (e.g. "Books!A42:T42" → 42)
        val updatedRange = response.body()?.updates?.updatedRange ?: ""
        Log.d("Sync", "Append updatedRange='$updatedRange' for book '${book.title}'")
        val rowIndex = Regex("""(\d+)$""").find(updatedRange)?.value?.toIntOrNull()
        if (rowIndex != null) {
            bookRepository.markSynced(book.id, rowIndex)
        } else {
            Log.w("Sync", "Could not parse row index from updatedRange='$updatedRange'")
        }
        return SyncResult.Success
    }

    private suspend fun updateBook(book: Book, spreadsheetId: String): SyncResult {
        val row = book.sheetRowIndex ?: return SyncResult.Error("No row index")
        val range = "Books!A$row:T$row"
        val body = SheetsValueRange(range = range, values = listOf(book.toSheetRow()))

        val response = runCatching {
            withToken { auth -> sheetsService.updateValues(spreadsheetId, range, auth = auth, body = body) }
        }.getOrElse { return SyncResult.Error(it.message ?: "Network error") }

        if (!response.isSuccessful) {
            return SyncResult.Error("Update failed: HTTP ${response.code()}")
        }
        bookRepository.markSynced(book.id, row)
        return SyncResult.Success
    }

    /**
     * Sets up the spreadsheet for this device:
     * 1. If a local spreadsheet ID is stored, verify it and return.
     * 2. If no local ID, check Drive AppData for a config saved from another device.
     * 3. If nothing found, create a new sheet, move it into a "Mybrary" Drive folder,
     *    and save the config to Drive AppData for future device sign-ins.
     */
    suspend fun createAndInitSheetIfNeeded(): SyncResult {
        // 1. We already have a local ID — verify it still works
        if (getSpreadsheetId() != null) {
            val headerResult = ensureHeaderRow()
            if (headerResult is SyncResult.Success) return SyncResult.Success
            Log.w("Sync", "Stored sheet unusable (${(headerResult as SyncResult.Error).message}), checking Drive")
            spreadsheetPreferences.setSpreadsheetId("")
        }

        // 2. Check Drive AppData for an existing spreadsheet ID (another device may have created it)
        val driveConfig = driveService.loadConfig()
        if (driveConfig != null) {
            Log.d("Sync", "Found spreadsheet from Drive AppData: ${driveConfig.spreadsheetId}")
            spreadsheetPreferences.setSpreadsheetId(driveConfig.spreadsheetId)
            val headerResult = ensureHeaderRow()
            if (headerResult is SyncResult.Success) return SyncResult.Success
            Log.w("Sync", "Drive AppData sheet unusable, recreating")
            spreadsheetPreferences.setSpreadsheetId("")
        }

        // 3. Create a fresh spreadsheet
        val response = runCatching {
            withToken { auth ->
                sheetsService.createSpreadsheet(
                    auth = auth,
                    body = CreateSpreadsheetRequest(SpreadsheetProperties("mybrary")),
                )
            }
        }.getOrElse { return SyncResult.Error(it.message ?: "Failed to create spreadsheet") }

        if (!response.isSuccessful) {
            return SyncResult.Error("Could not create spreadsheet: HTTP ${response.code()}")
        }
        val spreadsheetId = response.body()?.spreadsheetId
            ?: return SyncResult.Error("No spreadsheet ID in response")

        spreadsheetPreferences.setSpreadsheetId(spreadsheetId)
        Log.d("Sync", "Created spreadsheet: $spreadsheetId")

        ensureGenresSheet()

        // Organise into a "Mybrary" Drive folder and persist the config
        val folderId = driveService.createMybraryFolder()
        if (folderId != null) {
            driveService.moveToFolder(spreadsheetId, folderId)
            driveService.saveConfig(MybraryDriveConfig(spreadsheetId, folderId))
            Log.d("Sync", "Moved sheet into Mybrary folder: $folderId")
        } else {
            // Folder creation failed (non-fatal) — still save the spreadsheet ID
            driveService.saveConfig(MybraryDriveConfig(spreadsheetId))
            Log.w("Sync", "Could not create Mybrary folder, sheet stays in Drive root")
        }

        return ensureHeaderRow()
    }

    /**
     * Write the header row to the configured spreadsheet.
     * Call once after connecting to a new spreadsheet.
     */
    suspend fun ensureHeaderRow(): SyncResult {
        val spreadsheetId = getSpreadsheetId()
            ?: return SyncResult.Error("No Google Sheet configured.")
        val body = SheetsValueRange(
            range = "Books!A1:T1",
            values = listOf(SheetColumns.HEADER_ROW),
        )
        val response = runCatching {
            withToken { auth -> sheetsService.updateValues(spreadsheetId, "Books!A1:T1", auth = auth, body = body) }
        }.getOrElse { return SyncResult.Error(it.message ?: "Network error") }
        return if (response.isSuccessful) SyncResult.Success
        else SyncResult.Error("Header write failed: HTTP ${response.code()}")
    }
}
