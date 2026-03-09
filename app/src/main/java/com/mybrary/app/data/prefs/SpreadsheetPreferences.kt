package com.mybrary.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "mybrary_prefs")

@Singleton
class SpreadsheetPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val SPREADSHEET_ID_KEY = stringPreferencesKey("spreadsheet_id")
    private val AUTO_ADD_KEY = booleanPreferencesKey("auto_add_on_scan")
    private val ACTIVE_LIBRARY_ID_KEY = stringPreferencesKey("active_library_id")
    private val LIBRARIES_JSON_KEY = stringPreferencesKey("libraries_json")

    val spreadsheetId: Flow<String?> = context.dataStore.data
        .map { prefs -> prefs[SPREADSHEET_ID_KEY]?.takeIf { it.isNotBlank() } }

    val autoAddOnScan: Flow<Boolean> = context.dataStore.data
        .map { prefs -> prefs[AUTO_ADD_KEY] ?: false }

    val activeLibraryId: Flow<String> = context.dataStore.data
        .map { prefs -> prefs[ACTIVE_LIBRARY_ID_KEY]?.ifBlank { null } ?: "default" }

    val librariesJson: Flow<String?> = context.dataStore.data
        .map { prefs -> prefs[LIBRARIES_JSON_KEY]?.ifBlank { null } }

    suspend fun getSpreadsheetId(): String? = spreadsheetId.first()

    suspend fun setSpreadsheetId(id: String) {
        context.dataStore.edit { prefs -> prefs[SPREADSHEET_ID_KEY] = id.trim() }
    }

    suspend fun setAutoAddOnScan(enabled: Boolean) {
        context.dataStore.edit { prefs -> prefs[AUTO_ADD_KEY] = enabled }
    }

    suspend fun getActiveLibraryId(): String = activeLibraryId.first()

    suspend fun setActiveLibraryId(id: String) {
        context.dataStore.edit { prefs -> prefs[ACTIVE_LIBRARY_ID_KEY] = id }
    }

    suspend fun getLibrariesJson(): String? = librariesJson.first()

    suspend fun setLibrariesJson(json: String) {
        context.dataStore.edit { prefs -> prefs[LIBRARIES_JSON_KEY] = json }
    }
}
