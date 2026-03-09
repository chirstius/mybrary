package com.mybrary.app.ui.library

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.mybrary.app.domain.model.Book
import com.mybrary.app.domain.model.ReadingStatus
import com.mybrary.app.domain.model.UserLibrary
import com.mybrary.app.ui.components.BookCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onBookClick: (String) -> Unit,
    onScanClick: () -> Unit,
    onAddClick: () -> Unit,
    onSignOut: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: LibraryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showSortMenu by remember { mutableStateOf(false) }
    var showAccountMenu by remember { mutableStateOf(false) }
    var showSignOutDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.exportUri.collect { uri ->
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Export Library"))
        }
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text("Sign out?") },
            text = { Text("Your local library data will remain. Sign in again to sync with your Google Sheet.") },
            confirmButton = {
                TextButton(onClick = { showSignOutDialog = false; onSignOut() }) { Text("Sign Out") }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (uiState.showLibrarySwitcher) {
        LibrarySwitcherSheet(
            libraries = uiState.allLibraries,
            activeLibraryId = uiState.activeLibrary?.id,
            libraryCounts = uiState.libraryBookCounts,
            isCreating = uiState.isCreatingLibrary,
            createError = uiState.createLibraryError,
            onSwitch = viewModel::switchLibrary,
            onCreate = viewModel::createLibrary,
            onSetIcon = viewModel::setLibraryIcon,
            onEdit = viewModel::editLibrary,
            onDelete = viewModel::deleteLibrary,
            onExport = { id, name -> viewModel.exportLibraryCsv(id, name) },
            onDismiss = viewModel::dismissLibrarySwitcher,
        )
    }

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
                title = {
                    val active = uiState.activeLibrary
                    Column(modifier = Modifier.clickable { viewModel.showLibrarySwitcher() }) {
                        Text(
                            text = if (active != null) "${active.icon}  ${active.name}" else "mybrary",
                            style = MaterialTheme.typography.titleLarge,
                        )
                        if (active != null) {
                            val count = uiState.libraryBookCounts[active.id] ?: 0
                            Text(
                                text = "$count item${if (count != 1) "s" else ""}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.65f),
                            )
                        }
                    }
                },
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
                                    onClick = { viewModel.setSortOption(sort); showSortMenu = false },
                                    leadingIcon = {
                                        if (uiState.sortOption == sort) Icon(Icons.Default.Check, null)
                                    },
                                )
                            }
                        }
                    }
                    Box {
                        IconButton(onClick = { showAccountMenu = true }) {
                            Icon(Icons.Default.AccountCircle, contentDescription = "Account")
                        }
                        DropdownMenu(expanded = showAccountMenu, onDismissRequest = { showAccountMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                onClick = { showAccountMenu = false; onOpenSettings() },
                                leadingIcon = { Icon(Icons.Default.Settings, null) },
                            )
                            if (uiState.spreadsheetUrl != null) {
                                DropdownMenuItem(
                                    text = { Text("Open in Google Sheets") },
                                    onClick = {
                                        showAccountMenu = false
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uiState.spreadsheetUrl)))
                                    },
                                    leadingIcon = { Icon(Icons.Default.OpenInBrowser, null) },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Sign Out") },
                                onClick = { showAccountMenu = false; showSignOutDialog = true },
                                leadingIcon = { Icon(Icons.Default.Logout, null) },
                            )
                        }
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
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
            var searchText by remember { mutableStateOf(uiState.searchQuery) }
            SearchBar(
                query = searchText,
                onQueryChange = { searchText = it; viewModel.setSearchQuery(it) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
            StatusFilterRow(
                selected = uiState.statusFilter,
                onSelect = viewModel::setStatusFilter,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            if (uiState.availableGenres.isNotEmpty()) {
                GenreFilterRow(
                    genres = uiState.availableGenres,
                    selected = uiState.genreFilter,
                    onSelect = viewModel::setGenreFilter,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
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
                        BookCard(book = book, onClick = { onBookClick(book.id) })
                    }
                    item { Spacer(modifier = Modifier.height(80.dp)) }
                }
            }
        }
    }
}

// ── Library Switcher Bottom Sheet ───────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LibrarySwitcherSheet(
    libraries: List<UserLibrary>,
    activeLibraryId: String?,
    libraryCounts: Map<String, Int>,
    isCreating: Boolean,
    createError: String?,
    onSwitch: (String) -> Unit,
    onCreate: (String, String) -> Unit,
    onSetIcon: (String, String) -> Unit,
    onEdit: (String, String, String) -> Unit,
    onDelete: (String) -> Unit,
    onExport: (id: String, name: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var editingLibrary by remember { mutableStateOf<UserLibrary?>(null) }

    if (showCreateDialog) {
        NewLibraryDialog(
            isCreating = isCreating,
            error = createError,
            onCreate = { name, icon -> onCreate(name, icon) },
            onDismiss = { showCreateDialog = false },
        )
    }

    editingLibrary?.let { lib ->
        EditLibraryDialog(
            library = lib,
            canDelete = libraries.size > 1,
            onEdit = { name, icon -> onEdit(lib.id, name, icon); editingLibrary = null },
            onDelete = { onDelete(lib.id); editingLibrary = null },
            onExport = { onExport(lib.id, lib.name); editingLibrary = null },
            onDismiss = { editingLibrary = null },
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            Text(
                text = "Libraries",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            HorizontalDivider()
            libraries.forEach { library ->
                val isActive = library.id == activeLibraryId
                Surface(
                    onClick = { if (!isActive) onSwitch(library.id) },
                    color = if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    else Color.Transparent,
                ) {
                    ListItem(
                        headlineContent = { Text(library.name) },
                        supportingContent = {
                            val count = libraryCounts[library.id] ?: 0
                            Text(
                                "$count item${if (count != 1) "s" else ""}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                            )
                        },
                        leadingContent = {
                            Text(library.icon, style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(horizontal = 4.dp))
                        },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = { editingLibrary = library },
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Edit, null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                    )
                                }
                                if (isActive) {
                                    Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            TextButton(
                onClick = { showCreateDialog = true },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("New Library")
            }
        }
    }
}

@Composable
private fun EditLibraryDialog(
    library: UserLibrary,
    canDelete: Boolean,
    onEdit: (name: String, icon: String) -> Unit,
    onDelete: () -> Unit,
    onExport: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(library.name) }
    var icon by remember { mutableStateOf(library.icon) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val suggestions = listOf("📚", "🏠", "🏫", "🧪", "📖", "🎭", "🌍", "💼", "🎨", "🔬", "📝", "🎵", "🏋️", "🍳", "✈️", "🧒")

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Library?") },
            text = { Text("\"${library.name}\" will be removed from Mybrary. The Google Sheet will not be deleted.") },
            confirmButton = {
                TextButton(
                    onClick = onDelete,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Library") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = icon,
                        onValueChange = { if (it.length <= 4) icon = it },
                        label = { Text("Icon") },
                        singleLine = true,
                        modifier = Modifier.width(76.dp),
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(suggestions) { emoji ->
                        Surface(
                            onClick = { icon = emoji },
                            shape = MaterialTheme.shapes.small,
                            color = if (icon == emoji) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Text(emoji, modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
                TextButton(
                    onClick = onExport,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Export as CSV")
                }
                if (canDelete) {
                    TextButton(
                        onClick = { showDeleteConfirm = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Delete Library")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onEdit(name.trim(), icon.ifBlank { "📚" }) },
                enabled = name.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

@Composable
private fun NewLibraryDialog(
    isCreating: Boolean,
    error: String?,
    onCreate: (String, String) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var icon by remember { mutableStateOf("📚") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Library") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = icon,
                        onValueChange = { if (it.length <= 4) icon = it },
                        label = { Text("Icon") },
                        singleLine = true,
                        modifier = Modifier.width(76.dp),
                        enabled = !isCreating,
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        enabled = !isCreating,
                    )
                }
                if (error != null) {
                    Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                if (isCreating) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (name.isNotBlank()) onCreate(name.trim(), icon.ifBlank { "📚" }) },
                enabled = name.isNotBlank() && !isCreating,
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isCreating) { Text("Cancel") }
        },
    )
}

@Composable
private fun IconPickerDialog(
    current: String,
    onPick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var icon by remember { mutableStateOf(current) }
    val suggestions = listOf("📚", "🏠", "🏫", "🧪", "📖", "🎭", "🌍", "💼", "🎨", "🔬", "📝", "🎵", "🏋️", "🍳", "✈️", "🧒")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Library Icon") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Type any emoji or pick one below.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = icon,
                    onValueChange = { if (it.length <= 4) icon = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.width(100.dp),
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(suggestions) { emoji ->
                        Surface(
                            onClick = { icon = emoji },
                            shape = MaterialTheme.shapes.small,
                            color = if (icon == emoji) MaterialTheme.colorScheme.primaryContainer
                            else MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Text(emoji, modifier = Modifier.padding(8.dp), style = MaterialTheme.typography.titleLarge)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(icon.ifBlank { "📚" }) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

// ── Supporting composables ──────────────────────────────────────────────────

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
        item { FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("All") }) }
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
private fun GenreFilterRow(
    genres: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp),
    ) {
        item { FilterChip(selected = selected == null, onClick = { onSelect(null) }, label = { Text("All Genres") }) }
        items(genres) { genre ->
            FilterChip(
                selected = selected == genre,
                onClick = { onSelect(if (selected == genre) null else genre) },
                label = { Text(genre) },
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
    ReadingStatus.DNF -> "DNF"
}

private fun SortOption.label() = when (this) {
    SortOption.DATE_ADDED -> "Date Added"
    SortOption.TITLE -> "Title"
    SortOption.AUTHOR -> "Author"
}
