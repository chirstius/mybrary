package com.mybrary.app.ui.auth

import android.accounts.Account
import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.mybrary.app.data.remote.AuthTokenStore
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
}

private const val SHEETS_SCOPE = "oauth2:https://www.googleapis.com/auth/spreadsheets"

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val syncService: SheetsSyncService,
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
                GoogleAuthUtil.getToken(context, gmsAccount, SHEETS_SCOPE)
            }
        }.onSuccess { token ->
            AuthTokenStore.set(token)
            _authState.value = AuthState.SignedIn(account.displayName, account.email)
            // Initial pull from sheet
            syncService.pullFromSheet()
        }.onFailure { e ->
            _authState.value = AuthState.Error(e.message ?: "Token fetch failed")
        }
    }

    fun signOut(context: Context) {
        viewModelScope.launch {
            GoogleSignIn.getClient(context, com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN)
                .signOut()
            AuthTokenStore.clear()
            _authState.value = AuthState.SignedOut
        }
    }
}
