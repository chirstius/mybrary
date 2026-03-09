package com.mybrary.app.ui.auth

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope

private const val SHEETS_SCOPE = "https://www.googleapis.com/auth/spreadsheets"
private const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
private const val DRIVE_APPDATA_SCOPE = "https://www.googleapis.com/auth/drive.appdata"

@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    onSignedIn: () -> Unit,
) {
    val context = LocalContext.current
    val authState by viewModel.authState.collectAsState()

    LaunchedEffect(authState) {
        if (authState is AuthState.SignedIn) onSignedIn()
    }

    val signInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        runCatching {
            viewModel.onSignInResult(context, task.result)
        }.onFailure {
            viewModel.onSignInResult(context, null)
        }
    }

    // Consent launcher: after the user approves the new scope, retry sign-in
    val consentLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        val account = GoogleSignIn.getLastSignedInAccount(context)
        viewModel.onSignInResult(context, account)
    }

    LaunchedEffect(authState) {
        if (authState is AuthState.NeedsConsent) {
            consentLauncher.launch((authState as AuthState.NeedsConsent).intent)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.checkExistingSignIn(context)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Icon(
                imageVector = Icons.Default.MenuBook,
                contentDescription = null,
                modifier = Modifier.size(96.dp),
                tint = MaterialTheme.colorScheme.primary,
            )

            Text(
                text = "mybrary",
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )

            Text(
                text = "Your personal library,\nalways at your fingertips.",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
            )

            Spacer(modifier = Modifier.height(16.dp))

            when (authState) {
                is AuthState.Loading, is AuthState.SigningIn -> {
                    CircularProgressIndicator()
                    if (authState is AuthState.SigningIn) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Setting up your library…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        )
                    }
                }
                is AuthState.NeedsConsent -> {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Requesting permissions…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                    )
                }
                is AuthState.SignedOut, is AuthState.Error -> {
                    if (authState is AuthState.Error) {
                        Text(
                            text = (authState as AuthState.Error).message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                        )
                    }
                    Button(
                        onClick = {
                            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                                .requestEmail()
                                .requestScopes(Scope(SHEETS_SCOPE), Scope(DRIVE_FILE_SCOPE), Scope(DRIVE_APPDATA_SCOPE))
                                .build()
                            val client = GoogleSignIn.getClient(context, gso)
                            signInLauncher.launch(client.signInIntent)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                    ) {
                        Text("Sign in with Google", fontSize = 16.sp)
                    }
                }
                is AuthState.SignedIn -> {
                    CircularProgressIndicator()
                }
            }
        }
    }
}
