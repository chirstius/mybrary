package com.mybrary.app.data.sync

import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mybrary.app.data.prefs.SpreadsheetPreferences
import com.mybrary.app.data.remote.AddSheetBody
import com.mybrary.app.data.remote.AddSheetRequestItem
import com.mybrary.app.data.remote.AddSheetTabRequest
import com.mybrary.app.data.remote.AuthTokenStore
import com.mybrary.app.data.remote.CreateSpreadsheetRequest
import com.mybrary.app.data.remote.DriveService
import com.mybrary.app.data.remote.GoogleSheetsService
import com.mybrary.app.data.remote.SheetProperties
import com.mybrary.app.data.remote.SpreadsheetProperties
import com.mybrary.app.data.remote.model.SheetColumns
import com.mybrary.app.data.remote.model.SheetsValueRange
import com.mybrary.app.domain.model.UserLibrary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import retrofit2.Response
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryManager @Inject constructor(
    private val prefs: SpreadsheetPreferences,
    private val sheetsService: GoogleSheetsService,
    private val driveService: DriveService,
    private val gson: Gson,
) {
    /** All known libraries, derived from DataStore JSON. */
    val libraries: Flow<List<UserLibrary>> = prefs.librariesJson
        .map { json -> json?.parseLibraries() ?: emptyList() }

    /** The ID of the currently active library. */
    val activeLibraryId: Flow<String> = prefs.activeLibraryId

    /** The currently active library object, or null if the list hasn't loaded yet. */
    val activeLibrary: Flow<UserLibrary?> = combine(libraries, activeLibraryId) { libs, id ->
        libs.firstOrNull { it.id == id }
    }

    /**
     * Called at sign-in. Initialises the library list from:
     * 1. Local DataStore (fastest — already set up)
     * 2. Drive AppData (cross-device discovery, old or new format)
     * 3. Legacy local spreadsheet_id preference (upgrade path from single-library)
     * 4. Creates a new library if nothing is found.
     */
    suspend fun initializeLibraries(defaultName: String = "My Library"): SyncResult {
        // 1. Already have libraries in DataStore
        val existingJson = prefs.getLibrariesJson()
        if (!existingJson.isNullOrBlank()) {
            val libs = existingJson.parseLibraries()
            if (libs.isNotEmpty()) {
                val activeId = prefs.getActiveLibraryId()
                val active = libs.firstOrNull { it.id == activeId } ?: libs.first()
                prefs.setSpreadsheetId(active.spreadsheetId)
                Log.d("LibraryManager", "Restored ${libs.size} libraries from DataStore, active: ${active.name}")
                return SyncResult.Success
            }
        }

        // 2. Check Drive AppData (supports old + new format)
        val driveConfig = runCatching { driveService.loadFullConfig() }.getOrNull()
        if (driveConfig != null && driveConfig.libraries.isNotEmpty()) {
            Log.d("LibraryManager", "Found ${driveConfig.libraries.size} libraries from Drive AppData")
            saveToPrefs(driveConfig.activeLibraryId, driveConfig.libraries)
            val active = driveConfig.libraries.firstOrNull { it.id == driveConfig.activeLibraryId }
                ?: driveConfig.libraries.first()
            prefs.setSpreadsheetId(active.spreadsheetId)
            return SyncResult.Success
        }

        // 3. Migrate legacy single spreadsheet_id
        val legacySheetId = prefs.getSpreadsheetId()
        if (!legacySheetId.isNullOrBlank()) {
            Log.d("LibraryManager", "Migrating legacy spreadsheet ID: $legacySheetId")
            val lib = UserLibrary(id = "default", name = defaultName, spreadsheetId = legacySheetId)
            saveToPrefs("default", listOf(lib))
            syncToDrive("default", listOf(lib))
            return SyncResult.Success
        }

        // 4. Create a fresh library
        Log.d("LibraryManager", "No existing library found, creating '$defaultName'")
        return createLibrary(defaultName).fold(
            onSuccess = { SyncResult.Success },
            onFailure = { SyncResult.Error(it.message ?: "Failed to create library") },
        )
    }

    /**
     * Create a new named library: creates a Google Sheet, writes the header,
     * creates a Drive folder, and stores the result locally and on Drive AppData.
     */
    suspend fun createLibrary(name: String, icon: String = "📚"): Result<UserLibrary> = runCatching {
        // Create spreadsheet (Books + Genres tabs pre-created by CreateSpreadsheetRequest default)
        val createResp = withToken { auth ->
            sheetsService.createSpreadsheet(
                auth = auth,
                body = CreateSpreadsheetRequest(SpreadsheetProperties(name)),
            )
        }
        if (!createResp.isSuccessful) {
            throw Exception("Could not create spreadsheet: HTTP ${createResp.code()}")
        }
        val spreadsheetId = createResp.body()?.spreadsheetId
            ?: throw Exception("No spreadsheet ID in response")

        // Write Books header row
        val headerBody = SheetsValueRange(range = "Books!A1:T1", values = listOf(SheetColumns.HEADER_ROW))
        withToken { auth -> sheetsService.updateValues(spreadsheetId, "Books!A1:T1", auth = auth, body = headerBody) }

        // Write Genres header row
        val genreHeader = SheetsValueRange(range = "Genres!A1", values = listOf(listOf("Genre")))
        val genreResp = withToken { auth ->
            sheetsService.updateValues(spreadsheetId, "Genres!A1", auth = auth, body = genreHeader)
        }
        if (!genreResp.isSuccessful && genreResp.code() == 400) {
            // Genres tab doesn't exist yet — create it then retry
            val addReq = AddSheetTabRequest(listOf(AddSheetRequestItem(AddSheetBody(SheetProperties("Genres")))))
            withToken { auth -> sheetsService.addSheetTab(spreadsheetId, auth, addReq) }
            withToken { auth -> sheetsService.updateValues(spreadsheetId, "Genres!A1", auth = auth, body = genreHeader) }
        }

        // Create "Mybrary" folder and move sheet into it
        val folderId = runCatching { driveService.createMybraryFolder() }.getOrNull()
        if (folderId != null) {
            runCatching { driveService.moveToFolder(spreadsheetId, folderId) }
        }

        val newLib = UserLibrary(
            id = UUID.randomUUID().toString(),
            name = name,
            spreadsheetId = spreadsheetId,
            folderId = folderId,
            icon = icon,
        )

        // Persist locally and update Drive AppData
        val current = prefs.getLibrariesJson()?.parseLibraries() ?: emptyList()
        val updated = current + newLib
        saveToPrefs(newLib.id, updated)
        prefs.setSpreadsheetId(spreadsheetId)
        syncToDrive(newLib.id, updated)

        Log.d("LibraryManager", "Created library '${newLib.name}' (${newLib.id})")
        newLib
    }

    /** Switch the active library and update DataStore + Drive AppData. */
    suspend fun switchLibrary(libraryId: String) {
        val libs = prefs.getLibrariesJson()?.parseLibraries() ?: return
        val lib = libs.firstOrNull { it.id == libraryId } ?: return
        prefs.setActiveLibraryId(libraryId)
        prefs.setSpreadsheetId(lib.spreadsheetId)
        syncToDrive(libraryId, libs)
        Log.d("LibraryManager", "Switched to library '${lib.name}'")
    }

    /** Set the emoji icon for a library. */
    suspend fun setLibraryIcon(libraryId: String, icon: String) {
        val libs = prefs.getLibrariesJson()?.parseLibraries() ?: return
        val updated = libs.map { if (it.id == libraryId) it.copy(icon = icon) else it }
        prefs.setLibrariesJson(gson.toJson(updated))
        syncToDrive(prefs.getActiveLibraryId(), updated)
    }

    /** Rename a library locally and on Drive AppData. */
    suspend fun renameLibrary(libraryId: String, newName: String) {
        val libs = prefs.getLibrariesJson()?.parseLibraries() ?: return
        val updated = libs.map { if (it.id == libraryId) it.copy(name = newName) else it }
        prefs.setLibrariesJson(gson.toJson(updated))
        syncToDrive(prefs.getActiveLibraryId(), updated)
    }

    /**
     * Remove a library from the local list and Drive AppData.
     * If it was the active library, switches to the first remaining one.
     * Does NOT delete the underlying Google Sheet.
     * Returns false if it is the only library (cannot delete last library).
     */
    suspend fun deleteLibrary(libraryId: String): Boolean {
        val libs = prefs.getLibrariesJson()?.parseLibraries() ?: return false
        if (libs.size <= 1) return false
        val updated = libs.filter { it.id != libraryId }
        val activeId = prefs.getActiveLibraryId()
        val newActiveId = if (activeId == libraryId) updated.first().id else activeId
        val newActive = updated.first { it.id == newActiveId }
        saveToPrefs(newActiveId, updated)
        prefs.setSpreadsheetId(newActive.spreadsheetId)
        syncToDrive(newActiveId, updated)
        Log.d("LibraryManager", "Deleted library $libraryId, now active: ${newActive.name}")
        return true
    }

    /**
     * Sync library list from Drive AppData: adds new libraries and updates
     * name/icon for existing ones. Safe to call on every sync.
     */
    suspend fun refreshLibrariesFromDrive() {
        val driveConfig = runCatching { driveService.loadFullConfig() }.getOrNull() ?: return
        val localLibs = prefs.getLibrariesJson()?.parseLibraries() ?: return
        val driveById = driveConfig.libraries.associateBy { it.id }

        // Update name/icon for existing libraries from Drive
        val updated = localLibs.map { local ->
            val remote = driveById[local.id] ?: return@map local
            local.copy(name = remote.name, icon = remote.icon)
        }
        // Add any libraries only known to Drive
        val newLibs = driveConfig.libraries.filter { drive -> localLibs.none { it.id == drive.id } }
        val merged = updated + newLibs

        if (merged == localLibs) return
        prefs.setLibrariesJson(gson.toJson(merged))
        Log.d("LibraryManager", "Refreshed from Drive: ${newLibs.size} new, metadata updated")
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    private suspend fun saveToPrefs(activeId: String, libraries: List<UserLibrary>) {
        prefs.setActiveLibraryId(activeId)
        prefs.setLibrariesJson(gson.toJson(libraries))
    }

    private suspend fun syncToDrive(activeLibraryId: String, libraries: List<UserLibrary>) {
        runCatching { driveService.saveFullConfig(activeLibraryId, libraries) }
    }

    private fun String.parseLibraries(): List<UserLibrary> =
        runCatching {
            gson.fromJson<List<UserLibrary>>(
                this,
                object : TypeToken<List<UserLibrary>>() {}.type,
            )
        }.getOrNull() ?: emptyList()

    private suspend fun <T> withToken(block: suspend (String) -> Response<T>): Response<T> {
        val first = block(AuthTokenStore.bearer())
        if (first.code() != 401) return first
        return if (AuthTokenStore.refresh()) block(AuthTokenStore.bearer()) else first
    }
}
