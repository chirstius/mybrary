package com.mybrary.app.ui.addbook

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mybrary.app.domain.model.Book
import com.mybrary.app.domain.model.ReadingStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddBookScreen(
    prefillBook: Book? = null,
    onBack: () -> Unit,
    onSaved: (bookId: String) -> Unit,
    viewModel: AddBookViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(prefillBook) {
        prefillBook?.let { viewModel.prefill(it) }
    }

    LaunchedEffect(uiState.savedId) {
        uiState.savedId?.let { onSaved(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (prefillBook != null) "Add Book" else "Add Book Manually") },
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
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("Add to Library") },
                icon = { Icon(Icons.Default.LibraryAdd, null) },
                onClick = { viewModel.save() },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(Modifier.height(8.dp))

            OutlinedTextField(
                value = uiState.isbn,
                onValueChange = { viewModel.update { copy(isbn = it) } },
                label = { Text("ISBN") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.QrCode, null) },
            )
            OutlinedTextField(
                value = uiState.title,
                onValueChange = { viewModel.update { copy(title = it) } },
                label = { Text("Title *") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Title, null) },
            )
            OutlinedTextField(
                value = uiState.authors,
                onValueChange = { viewModel.update { copy(authors = it) } },
                label = { Text("Author(s) (comma-separated)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Person, null) },
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = uiState.year,
                    onValueChange = { viewModel.update { copy(year = it) } },
                    label = { Text("Year") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = uiState.pages,
                    onValueChange = { viewModel.update { copy(pages = it) } },
                    label = { Text("Pages") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
            }
            OutlinedTextField(
                value = uiState.publisher,
                onValueChange = { viewModel.update { copy(publisher = it) } },
                label = { Text("Publisher") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            OutlinedTextField(
                value = uiState.coverUrl,
                onValueChange = { viewModel.update { copy(coverUrl = it) } },
                label = { Text("Cover Image URL") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Image, null) },
            )

            // Status selector
            Text("Status", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReadingStatus.entries.forEach { status ->
                    FilterChip(
                        selected = uiState.status == status,
                        onClick = { viewModel.update { copy(status = status) } },
                        label = { Text(status.name.replace("_", " ").lowercase()
                            .replaceFirstChar { it.uppercaseChar() }) },
                    )
                }
            }

            OutlinedTextField(
                value = uiState.notes,
                onValueChange = { viewModel.update { copy(notes = it) } },
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
            )
            OutlinedTextField(
                value = uiState.location,
                onValueChange = { viewModel.update { copy(location = it) } },
                label = { Text("Location") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Place, null) },
            )
            OutlinedTextField(
                value = uiState.tags,
                onValueChange = { viewModel.update { copy(tags = it) } },
                label = { Text("Tags (comma-separated)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Default.Label, null) },
            )

            Spacer(Modifier.height(80.dp))
        }
    }
}
