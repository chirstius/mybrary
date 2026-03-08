package com.mybrary.app.ui.bookdetail

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.mybrary.app.domain.model.Book
import com.mybrary.app.domain.model.ReadingStatus
import com.mybrary.app.ui.components.StatusChip
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookDetailScreen(
    onBack: () -> Unit,
    viewModel: BookDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showLoanDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) onBack()
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete book?") },
            text = { Text("This will remove the book from your local library. The sync will remove it from the sheet on next push.") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.delete { onBack() } },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") } },
        )
    }

    if (showLoanDialog) {
        LoanDialog(
            currentLoanedTo = uiState.book?.loanedTo ?: "",
            currentDueDate = uiState.book?.loanDueDate,
            onConfirm = { name, date ->
                viewModel.setLoaned(name, date)
                showLoanDialog = false
            },
            onDismiss = { showLoanDialog = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.book?.title ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(Icons.Default.Delete, "Delete")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("Save") },
                icon = { Icon(Icons.Default.Save, null) },
                onClick = { viewModel.save() },
            )
        },
    ) { innerPadding ->
        val book = uiState.book
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        if (book == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Book not found.")
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            // Header: cover + basic info
            BookHeader(book)

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            Column(
                modifier = Modifier.padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Status
                StatusSection(
                    currentStatus = book.status,
                    onStatusChange = viewModel::updateStatus,
                )

                // Reading progress
                if (book.status == ReadingStatus.READING) {
                    ProgressSection(
                        progress = book.readingProgress,
                        onProgressChange = viewModel::updateProgress,
                    )
                }

                // Notes
                OutlinedTextField(
                    value = book.notes,
                    onValueChange = viewModel::updateNotes,
                    label = { Text("Notes") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 8,
                    leadingIcon = { Icon(Icons.Default.Notes, null) },
                )

                // Location
                OutlinedTextField(
                    value = book.location,
                    onValueChange = viewModel::updateLocation,
                    label = { Text("Location (shelf, room, etc.)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Place, null) },
                )

                // Tags
                OutlinedTextField(
                    value = book.tags.joinToString(", "),
                    onValueChange = { raw ->
                        viewModel.updateTags(
                            raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        )
                    },
                    label = { Text("Tags (comma-separated)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Label, null) },
                )

                // Loan section
                LoanSection(
                    loanedTo = book.loanedTo,
                    dueDate = book.loanDueDate,
                    onLoanClick = { showLoanDialog = true },
                    onReturnClick = { viewModel.clearLoan() },
                )

                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

@Composable
private fun BookHeader(book: Book) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.06f))
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .width(90.dp)
                .height(130.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            if (!book.coverUrl.isNullOrBlank()) {
                AsyncImage(
                    model = book.coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(Icons.Default.MenuBook, null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(book.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            if (book.authors.isNotEmpty()) {
                Text(book.authors.joinToString(", "), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            }
            book.publisher?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                book.publishedYear?.let { Text("$it", style = MaterialTheme.typography.bodySmall) }
                book.pages?.let { Text("• $it pages", style = MaterialTheme.typography.bodySmall) }
            }
            Text("ISBN: ${book.isbn}", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f))
        }
    }
    book.description?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun StatusSection(currentStatus: ReadingStatus, onStatusChange: (ReadingStatus) -> Unit) {
    Column {
        Text("Status", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReadingStatus.entries.forEach { status ->
                FilterChip(
                    selected = status == currentStatus,
                    onClick = { onStatusChange(status) },
                    label = { Text(status.displayName()) },
                )
            }
        }
    }
}

@Composable
private fun ProgressSection(progress: Int, onProgressChange: (Int) -> Unit) {
    Column {
        Text(
            "Reading Progress: $progress%",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Slider(
            value = progress.toFloat(),
            onValueChange = { onProgressChange(it.toInt()) },
            valueRange = 0f..100f,
            steps = 19,
        )
    }
}

@Composable
private fun LoanSection(
    loanedTo: String?,
    dueDate: LocalDate?,
    onLoanClick: () -> Unit,
    onReturnClick: () -> Unit,
) {
    Column {
        Text("Loan", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        if (!loanedTo.isNullOrBlank()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Row(
                    modifier = Modifier.padding(12.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text("Loaned to: $loanedTo", fontWeight = FontWeight.Medium)
                        dueDate?.let {
                            Text("Due: ${it.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))}",
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    TextButton(onClick = onReturnClick) { Text("Returned") }
                }
            }
        } else {
            OutlinedButton(onClick = onLoanClick, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.SwapHoriz, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Mark as Loaned")
            }
        }
    }
}

@Composable
private fun LoanDialog(
    currentLoanedTo: String,
    currentDueDate: LocalDate?,
    onConfirm: (String, LocalDate?) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(currentLoanedTo) }
    var dueDateStr by remember { mutableStateOf(currentDueDate?.toString() ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Loan Book") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Loaned to") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = dueDateStr,
                    onValueChange = { dueDateStr = it },
                    label = { Text("Due date (YYYY-MM-DD)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val date = runCatching { LocalDate.parse(dueDateStr) }.getOrNull()
                onConfirm(name, date)
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun ReadingStatus.displayName() = when (this) {
    ReadingStatus.READ -> "Read"
    ReadingStatus.READING -> "Reading"
    ReadingStatus.TO_READ -> "To Read"
    ReadingStatus.UNREAD -> "Unread"
}
