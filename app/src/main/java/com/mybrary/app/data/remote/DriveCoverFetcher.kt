package com.mybrary.app.data.remote

import android.net.Uri
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

/**
 * Coil [Fetcher] that handles `drive://FILE_ID` URIs by downloading the image
 * from the Google Drive API with the current OAuth2 bearer token.
 */
class DriveCoverFetcher(
    private val fileId: String,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val url = "https://www.googleapis.com/drive/v3/files/$fileId?alt=media"

        fun buildRequest(token: String) = Request.Builder()
            .url(url)
            .header("Authorization", token)
            .build()

        var response = httpClient.newCall(buildRequest(AuthTokenStore.bearer())).execute()
        if (response.code == 401) {
            response.close()
            AuthTokenStore.refresh()
            response = httpClient.newCall(buildRequest(AuthTokenStore.bearer())).execute()
        }

        if (!response.isSuccessful) {
            response.close()
            throw IOException("Drive image fetch failed: HTTP ${response.code}")
        }

        val body = response.body ?: throw IOException("Empty Drive response body")
        return SourceResult(
            source = ImageSource(source = body.source(), context = options.context),
            mimeType = body.contentType()?.toString(),
            dataSource = DataSource.NETWORK,
        )
    }

    class Factory : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (data.scheme != "drive") return null
            val fileId = data.host ?: return null
            return DriveCoverFetcher(fileId, options)
        }
    }

    companion object {
        private val httpClient = OkHttpClient()
    }
}
