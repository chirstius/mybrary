package com.mybrary.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val message by viewModel.message.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var advancedExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }

        // LazyColumn outer — genres use a bounded inner verticalScroll Box
        androidx.compose.foundation.lazy.LazyColumn(
            contentPadding = PaddingValues(16.dp),
            modifier = Modifier.padding(innerPadding),
        ) {
            // ── Scanning ────────────────────────────────────────────────
            item {
                SectionHeader("Scanning")
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-add scanned books", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Skip confirmation when a book is found",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                        )
                    }
                    Switch(
                        checked = uiState.autoAddOnScan,
                        onCheckedChange = { viewModel.setAutoAddOnScan(it) },
                    )
                }
                Spacer(Modifier.height(20.dp))
                HorizontalDivider()
            }

            // ── Genres ───────────────────────────────────────────────────
            item {
                Spacer(Modifier.height(20.dp))
                SectionHeader("Genres")
                Spacer(Modifier.height(8.dp))
                GenreAddRow(onAdd = { viewModel.addGenre(it) })
                if (uiState.genres.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    // Bounded scrollable box — shows ~4 genres at a time
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 152.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        Column {
                            uiState.genres.forEach { genre ->
                                GenreRow(name = genre, onDelete = { viewModel.deleteGenre(genre) })
                            }
                        }
                    }
                } else {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "No genres yet. Add one above.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    )
                }
                Spacer(Modifier.height(20.dp))
                HorizontalDivider()
            }

            // ── Advanced ─────────────────────────────────────────────────
            item {
                Spacer(Modifier.height(16.dp))
                Surface(
                    onClick = { advancedExpanded = !advancedExpanded },
                    color = MaterialTheme.colorScheme.surface,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        SectionHeader("Advanced")
                        Icon(
                            imageVector = if (advancedExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                AnimatedVisibility(visible = advancedExpanded) {
                    Column(modifier = Modifier.padding(top = 12.dp)) {
                        SheetSection(
                            spreadsheetId = uiState.spreadsheetId,
                            onConnect = { viewModel.connectToSheet(it) },
                            onCreateNew = { viewModel.createNewSheet() },
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
    )
}

@Composable
private fun SheetSection(
    spreadsheetId: String?,
    onConnect: (String) -> Unit,
    onCreateNew: () -> Unit,
) {
    var sheetIdInput by remember { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (!spreadsheetId.isNullOrBlank()) {
            Text(
                "Current: …${spreadsheetId.takeLast(12)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
            )
        }
        OutlinedTextField(
            value = sheetIdInput,
            onValueChange = { sheetIdInput = it },
            label = { Text("Switch to existing Sheet ID") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.TableChart, null) },
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { if (sheetIdInput.isNotBlank()) onConnect(sheetIdInput) },
                modifier = Modifier.weight(1f),
            ) { Text("Connect") }
            OutlinedButton(
                onClick = onCreateNew,
                modifier = Modifier.weight(1f),
            ) { Text("Create New Sheet") }
        }
    }
}

@Composable
private fun GenreAddRow(onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("New genre") },
            modifier = Modifier.weight(1f),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {
                if (text.isNotBlank()) { onAdd(text.trim()); text = "" }
            }),
        )
        IconButton(onClick = { if (text.isNotBlank()) { onAdd(text.trim()); text = "" } }) {
            Icon(Icons.Default.Add, "Add genre", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun GenreRow(name: String, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, top = 2.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
            Icon(
                Icons.Default.Delete,
                contentDescription = "Delete $name",
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
