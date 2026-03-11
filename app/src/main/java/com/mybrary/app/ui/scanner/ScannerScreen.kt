package com.mybrary.app.ui.scanner

import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.google.accompanist.permissions.*
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.mybrary.app.domain.model.ReadingStatus
import com.mybrary.app.ui.components.GenreDropdown
import java.io.File
import java.util.concurrent.Executors

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    onBack: () -> Unit,
    onBookAdded: (bookId: String) -> Unit,
    onAddManually: (isbn: String) -> Unit,
    viewModel: ScannerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val autoAdd by viewModel.autoAddEnabled.collectAsState()
    val genres by viewModel.genres.collectAsState()
    val context = LocalContext.current
    val cameraPermission = rememberPermissionState(android.Manifest.permission.CAMERA)

    // Cover editing state — hoisted so launchers can update it
    var editedCoverUrl by remember { mutableStateOf("") }
    var showCoverPickerRow by remember { mutableStateOf(false) }
    var showCoverUrlField by remember { mutableStateOf(false) }
    var showFullCover by remember { mutableStateOf(false) }
    var cameraImageUri by remember { mutableStateOf<Uri?>(null) }

    // Sync cover URL whenever the scanned book changes
    LaunchedEffect(uiState) {
        if (uiState is ScanUiState.BookFound) {
            editedCoverUrl = (uiState as ScanUiState.BookFound).book.coverUrl ?: ""
            showCoverPickerRow = false
            showCoverUrlField = false
        }
    }

    // Gallery picker
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            // Persist read access so Coil can load it after app restarts
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            editedCoverUrl = uri.toString()
            showCoverPickerRow = false
        }
    }

    // Camera — takes a full-res photo via FileProvider
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            editedCoverUrl = cameraImageUri?.toString() ?: editedCoverUrl
            showCoverPickerRow = false
        }
    }

    fun launchCamera() {
        val file = File(context.cacheDir, "cover_${System.currentTimeMillis()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        cameraImageUri = uri
        cameraLauncher.launch(uri)
    }

    // Show confirmation dialog when book is added
    if (uiState is ScanUiState.Added) {
        val addedBookId = (uiState as ScanUiState.Added).bookId
        val addedBookTitle = (uiState as ScanUiState.Added).bookTitle
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Added to Library") },
            text = { Text("\"$addedBookTitle\" has been added to your library.") },
            confirmButton = {
                TextButton(onClick = { onBookAdded(addedBookId) }) {
                    Text("View Details")
                }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { viewModel.reset() }) {
                        Text("Scan Another")
                    }
                    TextButton(onClick = { onBack() }) {
                        Text("Library")
                    }
                }
            },
        )
    }

    LaunchedEffect(Unit) {
        if (!cameraPermission.status.isGranted) cameraPermission.launchPermissionRequest()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan Book") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.reset(); onBack() }) {
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
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            if (cameraPermission.status.isGranted) {
                CameraPreview(
                    onBarcodeDetected = viewModel::onBarcodeDetected,
                    active = uiState is ScanUiState.Scanning,
                )
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Camera permission required to scan barcodes.")
                }
            }

            when (val state = uiState) {
                is ScanUiState.Scanning -> ScanOverlay()
                is ScanUiState.Loading, is ScanUiState.Added -> {
                    Box(
                        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator(color = Color.White) }
                }
                is ScanUiState.BookFound -> {
                    var editedStatus by remember { mutableStateOf(state.book.status) }
                    var editedNotes by remember { mutableStateOf(state.book.notes) }
                    var editedLocation by remember { mutableStateOf(state.book.location) }
                    var editedGenre by remember { mutableStateOf(state.book.genre ?: "") }
                    var editedTags by remember { mutableStateOf(state.book.tags.joinToString(", ")) }

                    // Full-screen cover viewer
                    if (showFullCover && editedCoverUrl.isNotBlank()) {
                        androidx.compose.ui.window.Dialog(
                            onDismissRequest = { showFullCover = false },
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.9f))
                                    .clickable { showFullCover = false },
                                contentAlignment = Alignment.Center,
                            ) {
                                AsyncImage(
                                    model = editedCoverUrl,
                                    contentDescription = "Book cover full size",
                                    contentScale = ContentScale.Fit,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                )
                                IconButton(
                                    onClick = { showFullCover = false },
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Close",
                                        tint = Color.White,
                                    )
                                }
                            }
                        }
                    }

                    ModalBottomSheet(
                        onDismissRequest = { viewModel.reset() },
                        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp)
                                .padding(bottom = 32.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                if (state.alreadyInLibrary) "Already in your library" else "Book found!",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )

                            // Book header: cover + metadata
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                // Cover with edit overlay
                                Box(
                                    modifier = Modifier
                                        .width(72.dp)
                                        .height(108.dp),
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (editedCoverUrl.isNotBlank()) {
                                            AsyncImage(
                                                model = editedCoverUrl,
                                                contentDescription = "Book cover",
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .fillMaxSize()
                                                    .clickable { showFullCover = true },
                                            )
                                        } else {
                                            Icon(
                                                Icons.Default.MenuBook, null,
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            )
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(4.dp)
                                            .size(24.dp)
                                            .background(
                                                MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                                RoundedCornerShape(12.dp),
                                            )
                                            .clickable {
                                                showCoverPickerRow = !showCoverPickerRow
                                                if (!showCoverPickerRow) showCoverUrlField = false
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            Icons.Default.Edit,
                                            contentDescription = "Edit cover",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(14.dp),
                                        )
                                    }
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        state.book.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    if (state.book.authors.isNotEmpty()) {
                                        Text(
                                            state.book.authors.joinToString(", "),
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                                        )
                                    }
                                    if (state.book.publisher != null) {
                                        Text(
                                            state.book.publisher,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                        )
                                    }
                                    val meta = listOfNotNull(
                                        state.book.publishedYear?.toString(),
                                        state.book.pages?.let { "$it pages" },
                                    ).joinToString(" · ")
                                    if (meta.isNotBlank()) {
                                        Text(
                                            meta,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                        )
                                    }
                                }
                            }

                            // Cover image picker row
                            if (showCoverPickerRow) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        "Cover:",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                                    )
                                    OutlinedButton(
                                        onClick = {
                                            galleryLauncher.launch(PickVisualMediaRequest(ImageOnly))
                                        },
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
                                        value = editedCoverUrl,
                                        onValueChange = { editedCoverUrl = it },
                                        label = { Text("Cover image URL") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true,
                                        leadingIcon = { Icon(Icons.Default.Image, null) },
                                    )
                                }
                            }

                            if (!state.alreadyInLibrary) {
                                HorizontalDivider()

                                Text("Reading Status", style = MaterialTheme.typography.labelLarge)
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    ReadingStatus.entries.forEach { s ->
                                        FilterChip(
                                            selected = editedStatus == s,
                                            onClick = { editedStatus = s },
                                            label = {
                                                Text(
                                                    s.name.replace("_", " ")
                                                        .lowercase()
                                                        .replaceFirstChar { it.uppercaseChar() }
                                                )
                                            },
                                        )
                                    }
                                }

                                OutlinedTextField(
                                    value = editedNotes,
                                    onValueChange = { editedNotes = it },
                                    label = { Text("Notes") },
                                    modifier = Modifier.fillMaxWidth(),
                                    minLines = 2,
                                )

                                OutlinedTextField(
                                    value = editedLocation,
                                    onValueChange = { editedLocation = it },
                                    label = { Text("Location (shelf, room...)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    leadingIcon = { Icon(Icons.Default.Place, null) },
                                )

                                GenreDropdown(
                                    value = editedGenre,
                                    onValueChange = { editedGenre = it },
                                    genres = genres,
                                )

                                OutlinedTextField(
                                    value = editedTags,
                                    onValueChange = { editedTags = it },
                                    label = { Text("Tags (comma-separated)") },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    leadingIcon = { Icon(Icons.Default.Label, null) },
                                )

                                HorizontalDivider()

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "Auto-add scanned books",
                                            style = MaterialTheme.typography.bodyMedium,
                                        )
                                        Text(
                                            "Skip this screen for future scans",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                                        )
                                    }
                                    Switch(
                                        checked = autoAdd,
                                        onCheckedChange = { viewModel.setAutoAdd(it) },
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.reset() },
                                    modifier = Modifier.weight(1f),
                                ) { Text("Scan Again") }

                                Button(
                                    onClick = {
                                        if (state.alreadyInLibrary) {
                                            onBookAdded(state.book.id)
                                        } else {
                                            viewModel.addToLibrary(
                                                state.book.copy(
                                                    status = editedStatus,
                                                    notes = editedNotes,
                                                    location = editedLocation,
                                                    coverUrl = editedCoverUrl.ifBlank { null },
                                                    genre = editedGenre.ifBlank { null },
                                                    tags = editedTags.split(",").map { it.trim() }.filter { it.isNotEmpty() },
                                                )
                                            )
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(if (state.alreadyInLibrary) "View Book" else "Add to Library")
                                }
                            }
                        }
                    }
                }
                is ScanUiState.BookNotFound -> {
                    BookNotFoundSheet(
                        isbn = state.isbn,
                        onAddManually = { onAddManually(state.isbn) },
                        onRescan = { viewModel.reset() },
                    )
                }
                is ScanUiState.Error -> {
                    Box(
                        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Card(modifier = Modifier.padding(32.dp)) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text("Error: ${state.message}", color = MaterialTheme.colorScheme.error)
                                TextButton(onClick = { viewModel.reset() }) { Text("Try Again") }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraPreview(onBarcodeDetected: (String) -> Unit, active: Boolean) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    val barcodeScanner = remember { BarcodeScanning.getClient() }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }
                val imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                            if (!active) { imageProxy.close(); return@setAnalyzer }
                            val mediaImage = imageProxy.image
                            if (mediaImage != null) {
                                val image = InputImage.fromMediaImage(
                                    mediaImage, imageProxy.imageInfo.rotationDegrees
                                )
                                barcodeScanner.process(image)
                                    .addOnSuccessListener { barcodes ->
                                        barcodes.firstOrNull { barcode ->
                                            barcode.valueType == Barcode.TYPE_ISBN ||
                                            (barcode.format == Barcode.FORMAT_EAN_13 &&
                                                barcode.rawValue?.let {
                                                    it.startsWith("978") || it.startsWith("979")
                                                } == true)
                                        }?.rawValue?.let(onBarcodeDetected)
                                    }
                                    .addOnCompleteListener { imageProxy.close() }
                            } else {
                                imageProxy.close()
                            }
                        }
                    }
                runCatching {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalyzer
                    )
                }.onFailure { Log.e("Scanner", "Camera bind failed", it) }
            }, ContextCompat.getMainExecutor(ctx))
            previewView
        },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun ScanOverlay() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(modifier = Modifier.weight(1f))
            Box(modifier = Modifier.size(260.dp, 120.dp).background(Color.Transparent)) {
                listOf(Alignment.TopStart, Alignment.TopEnd, Alignment.BottomStart, Alignment.BottomEnd)
                    .forEach { align ->
                        Box(
                            modifier = Modifier
                                .align(align)
                                .size(24.dp, 4.dp)
                                .background(Color.White, RoundedCornerShape(2.dp))
                        )
                    }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text("Point at a book barcode", color = Color.White, fontWeight = FontWeight.Medium)
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun BookNotFoundSheet(isbn: String, onAddManually: () -> Unit, onRescan: () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surface,
                    RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                )
                .padding(16.dp),
        ) {
            Text(
                "Book not found",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "ISBN: $isbn",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRescan, modifier = Modifier.weight(1f)) {
                    Text("Scan Again")
                }
                Button(onClick = onAddManually, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add Manually")
                }
            }
        }
    }
}
