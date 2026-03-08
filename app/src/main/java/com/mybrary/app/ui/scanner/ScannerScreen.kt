package com.mybrary.app.ui.scanner

import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.google.accompanist.permissions.*
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.mybrary.app.ui.components.BookCard
import java.util.concurrent.Executors

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun ScannerScreen(
    onBack: () -> Unit,
    onBookAdded: (bookId: String) -> Unit,
    onAddManually: (isbn: String) -> Unit,
    viewModel: ScannerViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val cameraPermission = rememberPermissionState(android.Manifest.permission.CAMERA)

    LaunchedEffect(Unit) {
        if (!cameraPermission.status.isGranted) {
            cameraPermission.launchPermissionRequest()
        }
    }

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("Scan Book") },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.reset()
                        onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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

            // Overlay UI based on state
            when (val state = uiState) {
                is ScanUiState.Scanning -> {
                    ScanOverlay()
                }
                is ScanUiState.Loading -> {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color.White)
                    }
                }
                is ScanUiState.BookFound -> {
                    BookFoundBottomSheet(
                        book = state.book,
                        alreadyInLibrary = state.alreadyInLibrary,
                        onAdd = {
                            viewModel.addToLibrary(state.book) { id ->
                                onBookAdded(id)
                            }
                        },
                        onViewExisting = { onBookAdded(state.book.id) },
                        onRescan = { viewModel.reset() },
                    )
                }
                is ScanUiState.BookNotFound -> {
                    BookNotFoundSheet(
                        isbn = state.isbn,
                        onAddManually = { onAddManually(state.isbn) },
                        onRescan = { viewModel.reset() },
                    )
                }
                is ScanUiState.Error -> {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center) {
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
                                        barcodes.firstOrNull { it.valueType == Barcode.TYPE_ISBN }
                                            ?.rawValue
                                            ?.let(onBarcodeDetected)
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
            // Viewfinder box
            Box(
                modifier = Modifier
                    .size(260.dp, 120.dp)
                    .background(Color.Transparent)
            ) {
                // Corner indicators
                listOf(
                    Alignment.TopStart, Alignment.TopEnd,
                    Alignment.BottomStart, Alignment.BottomEnd,
                ).forEach { align ->
                    Box(
                        modifier = Modifier
                            .align(align)
                            .size(24.dp, 4.dp)
                            .background(Color.White, RoundedCornerShape(2.dp))
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Text(
                "Point at a book barcode",
                color = Color.White,
                fontWeight = FontWeight.Medium,
            )
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun BookFoundBottomSheet(
    book: com.mybrary.app.domain.model.Book,
    alreadyInLibrary: Boolean,
    onAdd: () -> Unit,
    onViewExisting: () -> Unit,
    onRescan: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                if (alreadyInLibrary) "Already in your library" else "Book found!",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            BookCard(book = book, onClick = {})
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRescan, modifier = Modifier.weight(1f)) {
                    Text("Scan Again")
                }
                Button(
                    onClick = if (alreadyInLibrary) onViewExisting else onAdd,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (alreadyInLibrary) "View Book" else "Add to Library")
                }
            }
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
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                .padding(16.dp),
        ) {
            Text("Book not found", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text("ISBN: $isbn", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onRescan, modifier = Modifier.weight(1f)) { Text("Scan Again") }
                Button(onClick = onAddManually, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Add, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add Manually")
                }
            }
        }
    }
}
