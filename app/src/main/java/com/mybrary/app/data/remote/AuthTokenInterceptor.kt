package com.mybrary.app.data.remote

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the current Google OAuth2 access token. Supports refreshing the token
 * via a refresher lambda registered at sign-in time.
 */
object AuthTokenStore {
    private val _token = MutableStateFlow<String?>(null)
    val token: StateFlow<String?> = _token.asStateFlow()

    /** Registered at sign-in; invalidates the old token and fetches a fresh one. */
    private var refresher: (suspend () -> String?)? = null

    fun set(token: String?) { _token.value = token }
    fun get(): String? = _token.value
    fun bearer(): String = "Bearer ${_token.value ?: ""}"

    fun setRefresher(block: suspend () -> String?) { refresher = block }

    /** Re-fetch the token. Returns true if a new token was obtained. */
    suspend fun refresh(): Boolean {
        val newToken = runCatching { refresher?.invoke() }.getOrNull() ?: return false
        _token.value = newToken
        return true
    }

    fun clear() {
        _token.value = null
        refresher = null
    }
}
