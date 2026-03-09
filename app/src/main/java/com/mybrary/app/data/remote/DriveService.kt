package com.mybrary.app.data.remote

import com.google.gson.Gson
import com.mybrary.app.domain.model.UserLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

data class MybraryDriveConfig(
    val spreadsheetId: String,
    val folderId: String? = null,
)

/** Public multi-library config returned from Drive AppData. */
data class DriveFullConfig(
    val activeLibraryId: String,
    val libraries: List<UserLibrary>,
)

private data class DriveFileList(val files: List<DriveFileItem>?)
private data class DriveFileItem(val id: String?)
private data class DriveFileCreated(val id: String?)

// Internal serialization types — supports both old and new Drive AppData format.
private data class DriveStoredLibrary(
    val id: String,
    val name: String,
    val spreadsheetId: String,
    val folderId: String? = null,
    val icon: String = "📚",
)
private data class DriveStoredConfig(
    val spreadsheetId: String? = null,   // legacy v1
    val folderId: String? = null,        // legacy v1
    val activeLibraryId: String? = null, // v2
    val libraries: List<DriveStoredLibrary>? = null, // v2
)

/**
 * Handles Google Drive API operations:
 *  - AppData folder: persists the spreadsheet ID so it can be discovered on any device.
 *  - Folder management: creates a "Mybrary" folder in Drive and moves the spreadsheet into it.
 *
 * Uses the @Named("sheets") OkHttpClient which auto-injects the Bearer token.
 */
@Singleton
class DriveService @Inject constructor(
    @Named("sheets") private val okHttpClient: OkHttpClient,
    private val gson: Gson,
) {
    private val apiBase = "https://www.googleapis.com"
    private val uploadBase = "https://www.googleapis.com/upload"

    /** Load the stored spreadsheet config from Drive AppData. Returns null if not found. */
    suspend fun loadConfig(): MybraryDriveConfig? = withContext(Dispatchers.IO) {
        val fileId = findAppDataFileId() ?: return@withContext null
        downloadFileContent(fileId)?.let { json ->
            runCatching { gson.fromJson(json, MybraryDriveConfig::class.java) }.getOrNull()
        }
    }

    /** Save the spreadsheet config to Drive AppData (creates or overwrites). */
    suspend fun saveConfig(config: MybraryDriveConfig): Boolean = withContext(Dispatchers.IO) {
        val json = gson.toJson(config)
        val existingId = findAppDataFileId()
        if (existingId != null) updateAppDataFile(existingId, json)
        else createAppDataFile(json)
    }

    /** Create a "Mybrary" folder in Drive root. Returns the folder ID, or null on failure. */
    suspend fun createMybraryFolder(): String? = withContext(Dispatchers.IO) {
        val body = """{"name":"Mybrary","mimeType":"application/vnd.google-apps.folder"}"""
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$apiBase/drive/v3/files")
            .post(body)
            .build()
        runCatching {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) null
                else gson.fromJson(response.body?.string(), DriveFileCreated::class.java)?.id
            }
        }.getOrNull()
    }

    /** Move a Drive file into a folder (removes it from Drive root). */
    suspend fun moveToFolder(fileId: String, folderId: String): Boolean = withContext(Dispatchers.IO) {
        val url = "$apiBase/drive/v3/files/$fileId?addParents=$folderId&removeParents=root&fields=id"
        val body = "{}".toRequestBody("application/json".toMediaType())
        val request = Request.Builder().url(url).patch(body).build()
        runCatching {
            okHttpClient.newCall(request).execute().use { it.isSuccessful }
        }.getOrElse { false }
    }

    // ── Private helpers ──────────────────────────────────────────────────────

    private fun findAppDataFileId(): String? {
        val url = "$apiBase/drive/v3/files?spaces=appDataFolder" +
                "&q=name%3D'mybrary_config.json'&fields=files(id)"
        val request = Request.Builder().url(url).get().build()
        return runCatching {
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@runCatching null
                val body = response.body?.string() ?: return@runCatching null
                gson.fromJson(body, DriveFileList::class.java)?.files?.firstOrNull()?.id
            }
        }.getOrNull()
    }

    private fun downloadFileContent(fileId: String): String? {
        val request = Request.Builder()
            .url("$apiBase/drive/v3/files/$fileId?alt=media")
            .get()
            .build()
        return runCatching {
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) response.body?.string() else null
            }
        }.getOrNull()
    }

    private fun createAppDataFile(json: String): Boolean {
        val boundary = "mybrary_boundary"
        val metadata = """{"name":"mybrary_config.json","parents":["appDataFolder"]}"""
        val multipart = "--$boundary\r\n" +
                "Content-Type: application/json\r\n\r\n$metadata\r\n" +
                "--$boundary\r\n" +
                "Content-Type: application/json\r\n\r\n$json\r\n" +
                "--$boundary--"
        val body = multipart.toRequestBody("multipart/related; boundary=$boundary".toMediaType())
        val request = Request.Builder()
            .url("$uploadBase/drive/v3/files?uploadType=multipart")
            .post(body)
            .build()
        return runCatching {
            okHttpClient.newCall(request).execute().use { it.isSuccessful }
        }.getOrElse { false }
    }

    private fun updateAppDataFile(fileId: String, json: String): Boolean {
        val body = json.toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("$uploadBase/drive/v3/files/$fileId?uploadType=media")
            .patch(body)
            .build()
        return runCatching {
            okHttpClient.newCall(request).execute().use { it.isSuccessful }
        }.getOrElse { false }
    }

    /**
     * Load the full multi-library config from Drive AppData.
     * Handles both the legacy single-library format and the new multi-library format.
     */
    suspend fun loadFullConfig(): DriveFullConfig? = withContext(Dispatchers.IO) {
        val fileId = findAppDataFileId() ?: return@withContext null
        val json = downloadFileContent(fileId) ?: return@withContext null
        runCatching {
            val stored = gson.fromJson(json, DriveStoredConfig::class.java)
                ?: return@withContext null
            when {
                !stored.libraries.isNullOrEmpty() && stored.activeLibraryId != null -> {
                    DriveFullConfig(
                        activeLibraryId = stored.activeLibraryId,
                        libraries = stored.libraries.map {
                            UserLibrary(it.id, it.name, it.spreadsheetId, it.folderId, it.icon)
                        },
                    )
                }
                stored.spreadsheetId != null -> {
                    // Migrate legacy single-library format
                    val lib = UserLibrary("default", "My Library", stored.spreadsheetId, stored.folderId)
                    DriveFullConfig("default", listOf(lib))
                }
                else -> null
            }
        }.getOrNull()
    }

    /** Save the multi-library config to Drive AppData (creates or overwrites). */
    suspend fun saveFullConfig(activeLibraryId: String, libraries: List<UserLibrary>): Boolean =
        withContext(Dispatchers.IO) {
            val stored = DriveStoredConfig(
                activeLibraryId = activeLibraryId,
                libraries = libraries.map {
                    DriveStoredLibrary(it.id, it.name, it.spreadsheetId, it.folderId, it.icon)
                },
            )
            val json = gson.toJson(stored)
            val existingId = findAppDataFileId()
            if (existingId != null) updateAppDataFile(existingId, json)
            else createAppDataFile(json)
        }

    /**
     * Upload a local image URI to Google Drive.
     * Returns the Drive file ID on success, or null on failure.
     */
    suspend fun uploadImage(context: android.content.Context, uri: android.net.Uri): String? =
        withContext(Dispatchers.IO) {
            val bytes = runCatching {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }.getOrNull() ?: return@withContext null

            val mimeType = context.contentResolver.getType(uri) ?: "image/jpeg"
            val fileName = "cover_${System.currentTimeMillis()}.jpg"
            val boundary = "mybrary_img_boundary"
            val metadata = """{"name":"$fileName"}"""

            val metaPart = "--$boundary\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n$metadata\r\n"
            val dataPart = "--$boundary\r\nContent-Type: $mimeType\r\n\r\n"
            val ending = "\r\n--$boundary--"

            val body = (metaPart.toByteArray() + dataPart.toByteArray() + bytes + ending.toByteArray())
                .toRequestBody("multipart/related; boundary=$boundary".toMediaType())

            val request = Request.Builder()
                .url("$uploadBase/drive/v3/files?uploadType=multipart&fields=id")
                .post(body)
                .build()

            runCatching {
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) null
                    else gson.fromJson(response.body?.string(), DriveFileCreated::class.java)?.id
                }
            }.getOrNull()
        }
}
