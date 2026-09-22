package com.iyonzkasir.ui

import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.*
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import com.iyonzkasir.BRAND
import com.iyonzkasir.BRAND_LIGHT
import com.iyonzkasir.DANGER
import com.iyonzkasir.SUCCESS
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Executors
import kotlin.coroutines.resume

// ═══════════════════════════════════════════════════════════
// BARCODE SCANNER DIALOG
// Pakai CameraX + ML Kit. Handle permission otomatis.
// ═══════════════════════════════════════════════════════════
@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BarcodeScannerDialog(
    title: String = "Scan Barcode",
    hintText: String = "Arahkan kamera ke barcode",
    onResult: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current

    var result by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var isScanning by remember { mutableStateOf(true) }

    // Pick from gallery
    val galleryPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            // Decode bitmap di background
            try {
                val bitmap = ctx.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it)
                }
                if (bitmap != null) {
                    scanBitmap(ctx, bitmap) { code ->
                        if (code != null) {
                            result = code
                        } else {
                            error = "Barcode nggak terdeteksi di gambar"
                        }
                    }
                } else {
                    error = "Gagal buka gambar"
                }
            } catch (e: Exception) {
                error = "Error: ${e.message}"
            }
        }
    }

    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

    // Auto request saat pertama buka
    LaunchedEffect(Unit) {
        if (!cameraPermission.status.isGranted) {
            cameraPermission.launchPermissionRequest()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.QrCodeScanner, null, tint = BRAND)
                Spacer(Modifier.width(8.dp))
                Text(title)
            }
        },
        text = {
            Column(
                Modifier.fillMaxWidth().heightIn(min = 340.dp, max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (!cameraPermission.status.isGranted) {
                    // Permission belum
                    Card(colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )) {
                        Column(Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.CameraAlt, null,
                                tint = BRAND, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("Izin Kamera Dibutuhkan",
                                fontWeight = FontWeight.Bold)
                            Text("Kasih izin kamera untuk scan barcode",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(12.dp))
                            Button(
                                onClick = { cameraPermission.launchPermissionRequest() },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = BRAND)
                            ) {
                                Icon(Icons.Default.Check, null)
                                Spacer(Modifier.width(6.dp))
                                Text("Kasih Izin")
                            }
                        }
                    }
                } else if (result != null) {
                    // Hasil ditemukan
                    Card(colors = CardDefaults.cardColors(
                        containerColor = BRAND_LIGHT
                    )) {
                        Column(Modifier.padding(16.dp).fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.CheckCircle, null,
                                tint = SUCCESS, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(8.dp))
                            Text("Barcode Terdeteksi",
                                fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Surface(
                                color = Color.White,
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(result!!,
                                    modifier = Modifier.padding(
                                        horizontal = 16.dp, vertical = 8.dp),
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium)
                            }
                            Spacer(Modifier.height(16.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = {
                                    result = null
                                    error = null
                                    isScanning = true
                                }) {
                                    Icon(Icons.Default.Refresh, null,
                                        Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Scan Lagi")
                                }
                                Button(
                                    onClick = { onResult(result!!) },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = BRAND)
                                ) {
                                    Icon(Icons.Default.Check, null,
                                        Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Pakai")
                                }
                            }
                        }
                    }
                } else {
                    // Camera view
                    Box(
                        Modifier.fillMaxWidth().weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black)
                    ) {
                        CameraPreview(
                            onBarcodeDetected = { code ->
                                if (isScanning && code.isNotBlank()) {
                                    isScanning = false
                                    result = code
                                }
                            }
                        )

                        // Overlay frame
                        ScanOverlay()

                        // Hint text bawah
                        Surface(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(16.dp),
                            color = Color.Black.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(hintText,
                                modifier = Modifier.padding(
                                    horizontal = 12.dp, vertical = 6.dp),
                                color = Color.White,
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                error?.let {
                    Card(colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )) {
                        Row(Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Error, null,
                                tint = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer)
                        }
                    }
                }

                // Tombol scan dari galeri
                if (result == null && cameraPermission.status.isGranted) {
                    OutlinedButton(
                        onClick = { galleryPicker.launch(arrayOf("image/*")) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Image, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Scan dari Galeri",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Tutup") }
        }
    )
}

// ═══════════════════════════════════════════════════════════
// CAMERA PREVIEW (CameraX + ML Kit)
// ═══════════════════════════════════════════════════════════
@Composable
private fun CameraPreview(
    onBarcodeDetected: (String) -> Unit
) {
    val ctx = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val previewView = remember { PreviewView(ctx).apply {
        scaleType = PreviewView.ScaleType.FILL_CENTER
    } }

    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }

    // ML Kit scanner — support banyak format
    val scanner = remember {
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E,
                Barcode.FORMAT_CODE_128,
                Barcode.FORMAT_CODE_39,
                Barcode.FORMAT_CODE_93,
                Barcode.FORMAT_ITF,
                Barcode.FORMAT_CODABAR,
                Barcode.FORMAT_QR_CODE,
                Barcode.FORMAT_DATA_MATRIX,
                Barcode.FORMAT_PDF417,
                Barcode.FORMAT_AZTEC
            ).build()
        BarcodeScanning.getClient(options)
    }

    LaunchedEffect(lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
        val cameraProvider = try {
            suspendCancellableCoroutine<ProcessCameraProvider> { cont ->
                cameraProviderFuture.addListener({
                    try { cont.resume(cameraProviderFuture.get()) }
                    catch (e: Exception) { cont.cancel(e) }
                }, ContextCompat.getMainExecutor(ctx))
            }
        } catch (_: Exception) { null } ?: return@LaunchedEffect

        try { cameraProvider.unbindAll() } catch (_: Exception) {}

        val preview = Preview.Builder().build().also {
            it.setSurfaceProvider(previewView.surfaceProvider)
        }

        val analysis = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()
            .also { ia ->
                ia.setAnalyzer(analyzerExecutor) { imageProxy ->
                    processImageProxy(scanner, imageProxy) { code ->
                        if (code != null) onBarcodeDetected(code)
                    }
                }
            }

        try {
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis
            )
        } catch (_: Exception) {}
    }

    // Cleanup saat dispose
    DisposableEffect(Unit) {
        onDispose {
            try { scanner.close() } catch (_: Exception) {}
            try { analyzerExecutor.shutdown() } catch (_: Exception) {}
        }
    }

    AndroidView(
        factory = { previewView },
        modifier = Modifier.fillMaxSize()
    )
}

// ═══════════════════════════════════════════════════════════
// PROCESS IMAGE — submit ke ML Kit
// ═══════════════════════════════════════════════════════════
@androidx.camera.core.ExperimentalGetImage
private fun processImageProxy(
    scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    imageProxy: ImageProxy,
    onResult: (String?) -> Unit
) {
    val mediaImage = imageProxy.image ?: run {
        imageProxy.close()
        return
    }
    val image = InputImage.fromMediaImage(
        mediaImage, imageProxy.imageInfo.rotationDegrees
    )
    scanner.process(image)
        .addOnSuccessListener { barcodes ->
            val code = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
            if (code != null) onResult(code)
        }
        .addOnFailureListener { /* silent */ }
        .addOnCompleteListener { imageProxy.close() }
}

// ═══════════════════════════════════════════════════════════
// SCAN BITMAP (untuk gallery)
// ═══════════════════════════════════════════════════════════
private fun scanBitmap(
    ctx: Context,
    bitmap: Bitmap,
    onResult: (String?) -> Unit
) {
    try {
        val scanner = BarcodeScanning.getClient()
        val image = InputImage.fromBitmap(bitmap, 0)
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                val code = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }?.rawValue
                onResult(code)
                try { scanner.close() } catch (_: Exception) {}
            }
            .addOnFailureListener {
                onResult(null)
                try { scanner.close() } catch (_: Exception) {}
            }
    } catch (_: Exception) {
        onResult(null)
    }
}

// ═══════════════════════════════════════════════════════════
// SCAN OVERLAY — frame di tengah
// ═══════════════════════════════════════════════════════════
@Composable
private fun ScanOverlay() {
    Canvas(Modifier.fillMaxSize()) {
        val boxW = size.width * 0.75f
        val boxH = size.height * 0.45f
        val boxLeft = (size.width - boxW) / 2
        val boxTop = (size.height - boxH) / 2

        // Dim area luar
        val dimColor = Color.Black.copy(alpha = 0.5f)
        // Top
        drawRect(color = dimColor,
            topLeft = Offset(0f, 0f),
            size = Size(size.width, boxTop))
        // Bottom
        drawRect(color = dimColor,
            topLeft = Offset(0f, boxTop + boxH),
            size = Size(size.width, size.height - (boxTop + boxH)))
        // Left
        drawRect(color = dimColor,
            topLeft = Offset(0f, boxTop),
            size = Size(boxLeft, boxH))
        // Right
        drawRect(color = dimColor,
            topLeft = Offset(boxLeft + boxW, boxTop),
            size = Size(size.width - (boxLeft + boxW), boxH))

        // Border putih
        val stroke = 4f
        drawRect(
            color = Color.White,
            topLeft = Offset(boxLeft, boxTop),
            size = Size(boxW, boxH),
            style = Stroke(width = stroke)
        )

        // Corner accent warna brand (4 sudut)
        val cornerLen = 40f
        val cornerStroke = 6f
        val brandColor = Color(0xFFFF6B35)

        // Top-left
        drawLine(brandColor, Offset(boxLeft, boxTop),
            Offset(boxLeft + cornerLen, boxTop), cornerStroke)
        drawLine(brandColor, Offset(boxLeft, boxTop),
            Offset(boxLeft, boxTop + cornerLen), cornerStroke)

        // Top-right
        drawLine(brandColor, Offset(boxLeft + boxW, boxTop),
            Offset(boxLeft + boxW - cornerLen, boxTop), cornerStroke)
        drawLine(brandColor, Offset(boxLeft + boxW, boxTop),
            Offset(boxLeft + boxW, boxTop + cornerLen), cornerStroke)

        // Bottom-left
        drawLine(brandColor, Offset(boxLeft, boxTop + boxH),
            Offset(boxLeft + cornerLen, boxTop + boxH), cornerStroke)
        drawLine(brandColor, Offset(boxLeft, boxTop + boxH),
            Offset(boxLeft, boxTop + boxH - cornerLen), cornerStroke)

        // Bottom-right
        drawLine(brandColor, Offset(boxLeft + boxW, boxTop + boxH),
            Offset(boxLeft + boxW - cornerLen, boxTop + boxH), cornerStroke)
        drawLine(brandColor, Offset(boxLeft + boxW, boxTop + boxH),
            Offset(boxLeft + boxW, boxTop + boxH - cornerLen), cornerStroke)
    }
}
