package com.mybrary.app.data.remote

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the current Google OAuth2 access token so Retrofit interceptors / callers
 * can retrieve it without touching the UI layer.
 */
object AuthTokenStore {
    private val _token = MutableStateFlow<String?>(null)
    val token: StateFlow<String?> = _token.asStateFlow()

    fun set(token: String?) { _token.value = token }
    fun get(): String? = _token.value
    fun bearer(): String = "Bearer ${_token.value ?: ""}"
    fun clear() { _token.value = null }
}
