package com.mybrary.app.ui.setup

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SetupScreen(
    onDone: () -> Unit,
    viewModel: SetupViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.isDone) {
        if (uiState.isDone) onDone()
    }

    if (uiState.isLoading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sheet Settings") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            if (uiState.currentSpreadsheetId.isNotBlank()) {
                val context = LocalContext.current
                val sheetUrl = "https://docs.google.com/spreadsheets/d/${uiState.currentSpreadsheetId}"
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Current Sheet", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
                            Text(
                                uiState.currentSpreadsheetId,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            )
                        }
                        IconButton(onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(sheetUrl)))
                        }) {
                            Icon(
                                Icons.AutoMirrored.Filled.OpenInNew,
                                contentDescription = "Open sheet in browser",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }

            HorizontalDivider()

            Text("Switch to a different sheet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)

            OutlinedTextField(
                value = uiState.newSpreadsheetId,
                onValueChange = viewModel::updateSpreadsheetId,
                label = { Text("Spreadsheet ID") },
                placeholder = { Text("Paste ID from the sheet URL") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = uiState.isError,
                leadingIcon = { Icon(Icons.Default.TableChart, null) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )

            uiState.message?.let { msg ->
                Text(
                    msg,
                    color = if (uiState.isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (uiState.isSaving) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                Button(
                    onClick = viewModel::connect,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = uiState.newSpreadsheetId.isNotBlank(),
                ) {
                    Text("Connect to this Sheet")
                }

                HorizontalDivider()

                Text("Or create a brand-new sheet", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text(
                    "mybrary will create a new Google Sheet in your Drive and switch to it. " +
                        "Your existing local data will be synced to the new sheet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                    textAlign = TextAlign.Start,
                )
                OutlinedButton(
                    onClick = viewModel::createNewSheet,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.AddCircleOutline, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Create New Sheet")
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}
