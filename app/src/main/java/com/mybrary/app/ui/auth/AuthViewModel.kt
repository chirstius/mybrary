package com.mybrary.app.ui.auth

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.mybrary.app.data.remote.AuthTokenStore
import com.mybrary.app.data.sync.LibraryManager
import com.mybrary.app.data.sync.SheetsSyncService
import com.mybrary.app.data.sync.SyncResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed class AuthState {
    object Loading : AuthState()
    object SignedOut : AuthState()
    object SigningIn : AuthState()
    data class SignedIn(val displayName: String?, val email: String?) : AuthState()
    data class Error(val message: String) : AuthState()
    /** Google requires additional consent — launch [intent] to show the consent screen. */
    data class NeedsConsent(val intent: android.content.Intent) : AuthState()
}

// All scopes in a single token request — space-separated after "oauth2:"
private const val AUTH_SCOPE =
    "oauth2:https://www.googleapis.com/auth/spreadsheets " +
    "https://www.googleapis.com/auth/drive.file " +
    "https://www.googleapis.com/auth/drive.appdata"

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val syncService: SheetsSyncService,
    private val libraryManager: LibraryManager,
) : ViewModel() {

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    /** Call on startup to restore sign-in from the last session. */
    fun checkExistingSignIn(context: Context) {
        viewModelScope.launch {
            val account = GoogleSignIn.getLastSignedInAccount(context)
            if (account != null) {
                fetchTokenAndSignIn(context, account)
            } else {
                _authState.value = AuthState.SignedOut
            }
        }
    }

    /** Called after the Google Sign-In intent returns a result. */
    fun onSignInResult(context: Context, account: GoogleSignInAccount?) {
        if (account == null) {
            _authState.value = AuthState.Error("Sign-in cancelled or failed")
            return
        }
        viewModelScope.launch {
            fetchTokenAndSignIn(context, account)
        }
    }

    private suspend fun fetchTokenAndSignIn(context: Context, account: GoogleSignInAccount) {
        _authState.value = AuthState.SigningIn
        val gmsAccount = account.account ?: run {
            _authState.value = AuthState.Error("Could not retrieve account")
            return
        }
        runCatching {
            withContext(Dispatchers.IO) {
                GoogleAuthUtil.getToken(context, gmsAccount, AUTH_SCOPE)
            }
        }.onSuccess { token ->
            AuthTokenStore.set(token)
            // Register a refresher so the sync service can recover from 401 errors
            AuthTokenStore.setRefresher {
                withContext(Dispatchers.IO) {
                    val current = AuthTokenStore.get()
                    if (current != null) GoogleAuthUtil.invalidateToken(context, current)
                    GoogleAuthUtil.getToken(context, gmsAccount, AUTH_SCOPE)
                }
            }
            // Initialise library list (create if needed, or restore from Drive AppData)
            val initResult = libraryManager.initializeLibraries(
                defaultName = account.displayName?.let { "$it's Library" } ?: "My Library",
            )
            if (initResult is SyncResult.Error) {
                _authState.value = AuthState.Error("Could not set up library: ${initResult.message}")
                return
            }
            // Pull existing data from the active library's sheet
            syncService.pullFromSheet()
            _authState.value = AuthState.SignedIn(
                displayName = account.displayName,
                email = account.email,
            )
        }.onFailure { e ->
            when (e) {
                is com.google.android.gms.auth.UserRecoverableAuthException ->
                    _authState.value = e.intent
                        ?.let { AuthState.NeedsConsent(it) }
                        ?: AuthState.Error("Consent required but no recovery intent available")
                else ->
                    _authState.value = AuthState.Error(e.message ?: "Token fetch failed")
            }
        }
    }

    fun signOut(context: Context) {
        viewModelScope.launch {
            GoogleSignIn.getClient(
                context,
                com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN,
            ).signOut()
            AuthTokenStore.clear()
            _authState.value = AuthState.SignedOut
        }
    }
}
