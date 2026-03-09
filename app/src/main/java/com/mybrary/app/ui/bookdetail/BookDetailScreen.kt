package com.mybrary.app.ui.bookdetail

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.mybrary.app.domain.model.Book
import com.mybrary.app.domain.model.ReadingStatus
import com.mybrary.app.ui.components.GenreDropdown
import com.mybrary.app.ui.components.StatusChip
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
fun BookDetailScreen(
    onBack: () -> Unit,
    viewModel: BookDetailViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val genres by viewModel.genres.collectAsState()
    val context = LocalContext.current
    val contactsPermission = rememberPermissionState(Manifest.permission.READ_CONTACTS)
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showLoanDialog by remember { mutableStateOf(false) }
    var showFullCover by remember { mutableStateOf(false) }
    var showCoverPickerRow by remember { mutableStateOf(false) }
    var showCoverUrlField by remember { mutableStateOf(false) }
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }
    // Track raw tags text independently so commas aren't swallowed on each recompose
    var tagsText by remember(uiState.book?.id) {
        mutableStateOf(uiState.book?.tags?.joinToString(", ") ?: "")
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            viewModel.updateCoverUrl(uri.toString())
            showCoverPickerRow = false
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            cameraImageUri?.let { viewModel.updateCoverUrl(it.toString()) }
            showCoverPickerRow = false
        }
    }

    fun launchCamera() {
        val file = File(context.cacheDir, "cover_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        cameraImageUri = uri
        cameraLauncher.launch(uri)
    }

    // Full-screen cover viewer
    if (showFullCover && !uiState.book?.coverUrl.isNullOrBlank()) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { showFullCover = false },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.9f))
                    .clickable { showFullCover = false },
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = uiState.book?.coverUrl,
                    contentDescription = "Book cover full size",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                )
                IconButton(
                    onClick = { showFullCover = false },
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                ) {
                    Icon(Icons.Default.Close, "Close", tint = Color.White)
                }
            }
        }
    }

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
            contactsGranted = contactsPermission.status.isGranted,
            onRequestContactsPermission = { contactsPermission.launchPermissionRequest() },
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
                    uiState.book?.let { b ->
                        IconButton(onClick = {
                            val isbnForLink = b.isbn13?.takeIf { it.isNotBlank() } ?: b.isbn
                            val meta = listOfNotNull(
                                b.publishedYear?.toString(),
                                b.pages?.let { "$it pages" },
                            ).joinToString(" · ")
                            val desc = b.description
                            val snippet = if (!desc.isNullOrBlank()) {
                                if (desc.length > 200) "\"${desc.take(200)}…\"" else "\"$desc\""
                            } else null
                            val shareText = buildString {
                                appendLine("📖 ${b.title}")
                                if (b.authors.isNotEmpty()) appendLine("✍️ ${b.authors.joinToString(", ")}")
                                if (meta.isNotEmpty()) appendLine(meta)
                                if (snippet != null) { appendLine(); appendLine(snippet) }
                                if (isbnForLink.isNotBlank()) {
                                    appendLine()
                                    appendLine("🔗 https://openlibrary.org/isbn/$isbnForLink")
                                    appendLine()
                                    append("mybrary://book?isbn=$isbnForLink")
                                }
                            }
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, b.title)
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share Book"))
                        }) {
                            Icon(Icons.Default.Share, "Share")
                        }
                    }
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
            BookHeader(
                book = book,
                onCoverTap = { showFullCover = true },
                onEditCoverTap = { showCoverPickerRow = !showCoverPickerRow },
            )

            // Cover picker row
            if (showCoverPickerRow) {
                Column(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text("Cover:", style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                        OutlinedButton(
                            onClick = { galleryLauncher.launch(PickVisualMediaRequest(ImageOnly)) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Icon(Icons.Default.PhotoLibrary, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Gallery", style = MaterialTheme.typography.labelMedium)
                        }
                        OutlinedButton(
                            onClick = { launchCamera() },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Icon(Icons.Default.CameraAlt, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Camera", style = MaterialTheme.typography.labelMedium)
                        }
                        OutlinedButton(
                            onClick = { showCoverUrlField = !showCoverUrlField },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        ) {
                            Icon(Icons.Default.Link, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("URL", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    if (showCoverUrlField) {
                        OutlinedTextField(
                            value = book.coverUrl ?: "",
                            onValueChange = { viewModel.updateCoverUrl(it) },
                            label = { Text("Cover image URL") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            leadingIcon = { Icon(Icons.Default.Image, null) },
                        )
                    }
                }
            }

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

                // Tags — use local state so commas aren't eaten on every recompose
                OutlinedTextField(
                    value = tagsText,
                    onValueChange = { raw ->
                        tagsText = raw
                        viewModel.updateTags(
                            raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        )
                    },
                    label = { Text("Tags (comma-separated)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Label, null) },
                )

                // Genre
                GenreDropdown(
                    value = book.genre ?: "",
                    onValueChange = viewModel::updateGenre,
                    genres = genres,
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
private fun BookHeader(book: Book, onCoverTap: () -> Unit, onEditCoverTap: () -> Unit) {
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
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable { onCoverTap() },
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
            // Edit cover button overlay
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .size(24.dp)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
                    .clickable { onEditCoverTap() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Edit, "Edit cover",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.primary)
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalPermissionsApi::class)
@Composable
private fun LoanDialog(
    currentLoanedTo: String,
    currentDueDate: LocalDate?,
    contactsGranted: Boolean,
    onRequestContactsPermission: () -> Unit,
    onConfirm: (String, LocalDate?) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(currentLoanedTo) }
    var selectedDate by remember { mutableStateOf(currentDueDate) }
    var showDatePicker by remember { mutableStateOf(false) }
    var contactSuggestions by remember { mutableStateOf(emptyList<String>()) }

    // Query contacts matching typed name (debounced, runs on IO thread)
    LaunchedEffect(name) {
        val query = name  // capture on main thread before any suspension
        if (query.length >= 2 && contactsGranted) {
            contactSuggestions = emptyList()  // clear stale results immediately
            kotlinx.coroutines.delay(200)
            val results = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                val list = mutableListOf<String>()
                val cursor = context.contentResolver.query(
                    ContactsContract.Contacts.CONTENT_URI,
                    arrayOf(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY),
                    "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} LIKE ? AND ${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} != ''",
                    arrayOf("%$query%"),
                    "${ContactsContract.Contacts.DISPLAY_NAME_PRIMARY} ASC",
                )
                cursor?.use {
                    val nameCol = it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
                    while (it.moveToNext()) {
                        val contactName = it.getString(nameCol) ?: continue
                        if (contactName.isNotBlank()) list.add(contactName)
                        if (list.size >= 8) break
                    }
                }
                list
            }
            contactSuggestions = results.filter { it != query }
        } else {
            contactSuggestions = emptyList()
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = selectedDate
                ?.let { java.util.concurrent.TimeUnit.DAYS.toMillis(it.toEpochDay()) }
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    val millis = datePickerState.selectedDateMillis
                    selectedDate = millis?.let {
                        LocalDate.ofEpochDay(java.util.concurrent.TimeUnit.MILLISECONDS.toDays(it))
                    }
                    showDatePicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Loan Book") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Contact name with autocomplete suggestions
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Loaned to") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Person, null) },
                )
                if (!contactsGranted && name.length >= 2) {
                    TextButton(onClick = onRequestContactsPermission) {
                        Icon(Icons.Default.Contacts, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Allow contacts to autocomplete", style = MaterialTheme.typography.labelSmall)
                    }
                }
                if (contactSuggestions.isNotEmpty()) {
                    Card(elevation = CardDefaults.cardElevation(4.dp)) {
                        Column {
                            contactSuggestions.forEach { suggestion ->
                                TextButton(
                                    onClick = {
                                        name = suggestion
                                        contactSuggestions = emptyList()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text(suggestion, modifier = Modifier.fillMaxWidth())
                                }
                            }
                        }
                    }
                }
                // Due date with picker
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Default.CalendarMonth, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(selectedDate?.format(DateTimeFormatter.ofPattern("MMM d, yyyy")) ?: "Set due date")
                }
                if (selectedDate != null) {
                    TextButton(
                        onClick = { selectedDate = null },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Clear due date") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, selectedDate) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun ReadingStatus.displayName() = when (this) {
    ReadingStatus.READ -> "Read"
    ReadingStatus.READING -> "Reading"
    ReadingStatus.TO_READ -> "To Read"
    ReadingStatus.UNREAD -> "Unread"
    ReadingStatus.DNF -> "DNF"
}
