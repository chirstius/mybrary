package com.mybrary.app.ui.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mybrary.app.domain.model.Book
import com.mybrary.app.domain.model.ReadingStatus
import com.mybrary.app.ui.components.BookCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onBookClick: (String) -> Unit,
    onScanClick: () -> Unit,
    onAddClick: () -> Unit,
    onSignOut: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var showSortMenu by remember { mutableStateOf(false) }

    // Snackbar for sync messages
    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(uiState.syncMessage) {
        uiState.syncMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearSyncMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("mybrary") },
                actions = {
                    IconButton(onClick = { viewModel.sync() }, enabled = !uiState.isSyncing) {
                        if (uiState.isSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.Sync, contentDescription = "Sync")
                        }
                    }
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(Icons.Default.Sort, contentDescription = "Sort")
                        }
                        DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
                            SortOption.entries.forEach { sort ->
                                DropdownMenuItem(
                                    text = { Text(sort.label()) },
                                    onClick = {
                                        viewModel.setSortOption(sort)
                                        showSortMenu = false
                                    },
                                    leadingIcon = {
                                        if (uiState.sortOption == sort) {
                                            Icon(Icons.Default.Check, contentDescription = null)
                                        }
                                    },
                                )
                            }
                        }
                    }
                    IconButton(onClick = onSignOut) {
                        Icon(Icons.Default.AccountCircle, contentDescription = "Account")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                SmallFloatingActionButton(onClick = onAddClick) {
                    Icon(Icons.Default.Add, contentDescription = "Add manually")
                }
                FloatingActionButton(onClick = onScanClick) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = "Scan barcode")
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding)) {
            // Search bar
            SearchBar(
                query = uiState.searchQuery,
                onQueryChange = viewModel::setSearchQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            // Status filter chips
            StatusFilterRow(
                selected = uiState.statusFilter,
                onSelect = viewModel::setStatusFilter,
                modifier = Modifier.padding(horizontal = 12.dp),
            )

            // Book count
            Text(
                text = "${uiState.books.size} book${if (uiState.books.size != 1) "s" else ""}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            if (uiState.books.isEmpty()) {
                EmptyState(modifier = Modifier.weight(1f))
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(uiState.books, key = { it.id }) { book ->
                        BookCard(
                            book = book,
                            onClick = { onBookClick(book.id) },
                        )
                    }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }
}

@Composable
private fun SearchBar(query: String, onQueryChange: (String) -> Unit, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier,
        placeholder = { Text("Search title, author, tag, location…") },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
            AnimatedVisibility(query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                }
            }
        },
        singleLine = true,
        shape = MaterialTheme.shapes.medium,
    )
}

@Composable
private fun StatusFilterRow(
    selected: ReadingStatus?,
    onSelect: (ReadingStatus?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
    ) {
        item {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text("All") },
            )
        }
        items(ReadingStatus.entries) { status ->
            FilterChip(
                selected = selected == status,
                onClick = { onSelect(if (selected == status) null else status) },
                label = { Text(status.label()) },
            )
        }
    }
}

@Composable
private fun EmptyState(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                imageVector = Icons.Default.LibraryBooks,
                contentDescription = null,
                modifier = Modifier.size(72.dp),
                tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Your library is empty",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            )
            Text(
                text = "Scan a barcode or add a book manually",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f),
            )
        }
    }
}

private fun ReadingStatus.label() = when (this) {
    ReadingStatus.READ -> "Read"
    ReadingStatus.READING -> "Reading"
    ReadingStatus.TO_READ -> "To Read"
    ReadingStatus.UNREAD -> "Unread"
}

private fun SortOption.label() = when (this) {
    SortOption.DATE_ADDED -> "Date Added"
    SortOption.TITLE -> "Title"
    SortOption.AUTHOR -> "Author"
}
