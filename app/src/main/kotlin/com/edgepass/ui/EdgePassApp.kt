package com.edgepass.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RectF
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.edgepass.lib.MlKitFaceDetector
import com.edgepass.lib.PassportProcessor
import com.edgepass.ui.screens.CameraScreen

data class DetectedFace(
    val boundingBox: RectF,
    val confidence: Float
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EdgePassApp() {
    val context = LocalContext.current
    val processor = remember { PassportProcessor.getInstance(context) }
    val faceDetector = remember { MlKitFaceDetector(context) }

    var currentScreen by remember { mutableStateOf("camera") }
    var capturedImageBytes by remember { mutableStateOf<ByteArray?>(null) }
    var processedImageBytes by remember { mutableStateOf<ByteArray?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var selectedStandard by remember { mutableIntStateOf(PassportProcessor.STANDARD_SAUDI_EVISA) }
    var detectedFaces by remember { mutableStateOf<List<DetectedFace>>(emptyList()) }
    var removeBackground by remember { mutableStateOf(false) }

    LaunchedEffect(capturedImageBytes) {
        capturedImageBytes?.let { bytes ->
            faceDetector.detectFromBytes(bytes) { faces ->
                detectedFaces = faces.map { DetectedFace(it.boundingBox, it.confidence) }
                Log.d("EdgePassApp", "Detected ${faces.size} faces")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("EdgePass - ${processor.version}") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (currentScreen) {
                "camera" -> {
                    CameraScreen(
                        processor = processor,
                        onImageCaptured = { bytes ->
                            capturedImageBytes = bytes
                            currentScreen = "preview"
                        }
                    )
                }
                "preview" -> {
                    PreviewScreen(
                        originalImage = capturedImageBytes?.let { decodeBitmap(it) },
                        processedImage = processedImageBytes?.let { decodeBitmap(it) },
                        detectedFaces = detectedFaces,
                        removeBackground = removeBackground,
                        onRemoveBackgroundChanged = { removeBackground = it },
                        isProcessing = isProcessing,
                        onProcess = {
                            if (capturedImageBytes != null) {
                                isProcessing = true
                                Log.d("EdgePassApp", "Starting image processing with ${capturedImageBytes!!.size} bytes")
                                
                                val faceCenterX = if (detectedFaces.isNotEmpty()) {
                                    val face = detectedFaces.first()
                                    (face.boundingBox.left + face.boundingBox.width() / 2)
                                } else null
                                
                                val faceCenterY = if (detectedFaces.isNotEmpty()) {
                                    val face = detectedFaces.first()
                                    (face.boundingBox.top + face.boundingBox.height() / 2)
                                } else null
                                
                                Log.d("EdgePassApp", "Face center: $faceCenterX, $faceCenterY")
                                
                                val result = processor.generate(
                                    capturedImageBytes!!,
                                    selectedStandard,
                                    null,
                                    faceCenterX,
                                    faceCenterY,
                                    removeBackground
                                )
                                Log.d("EdgePassApp", "Processing result: ${result?.size ?: "null"} bytes")
                                processedImageBytes = result
                                isProcessing = false
                            }
                        },
                        onRetake = {
                            capturedImageBytes = null
                            processedImageBytes = null
                            currentScreen = "camera"
                        },
                        onSave = {
                            processedImageBytes?.let { bytes ->
                                val saved = saveImageToGallery(context, bytes)
                                if (saved) {
                                    Toast.makeText(context, "Image saved to Gallery!", Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(context, "Failed to save image", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewScreen(
    originalImage: Bitmap?,
    processedImage: Bitmap?,
    detectedFaces: List<DetectedFace>,
    removeBackground: Boolean,
    onRemoveBackgroundChanged: (Boolean) -> Unit,
    isProcessing: Boolean,
    onProcess: () -> Unit,
    onRetake: () -> Unit,
    onSave: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Preview", style = MaterialTheme.typography.headlineMedium)
        
        if (detectedFaces.isNotEmpty()) {
            Text(
                "✓ ${detectedFaces.size} face(s) detected",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Green
            )
        } else {
            Text(
                "No faces detected",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Red
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(
                checked = removeBackground,
                onCheckedChange = onRemoveBackgroundChanged
            )
            Text("Remove background")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Original")
                Box(
                    modifier = Modifier
                        .size(150.dp)
                        .background(Color.Black)
                ) {
                    originalImage?.let { img ->
                        Image(
                            bitmap = img.asImageBitmap(),
                            contentDescription = "Original",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )

                        detectedFaces.forEach { face ->
                            androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                                val canvasWidth = size.width
                                val canvasHeight = size.height
                                val scaleX = canvasWidth / img.width.toFloat()
                                val scaleY = canvasHeight / img.height.toFloat()
                                val scale = minOf(scaleX, scaleY)
                                val drawnWidth = img.width * scale
                                val drawnHeight = img.height * scale
                                val offsetX = (canvasWidth - drawnWidth) / 2
                                val offsetY = (canvasHeight - drawnHeight) / 2
                                val boxLeft = offsetX + face.boundingBox.left * scale
                                val boxTop = offsetY + face.boundingBox.top * scale
                                val boxRight = offsetX + face.boundingBox.right * scale
                                val boxBottom = offsetY + face.boundingBox.bottom * scale
                                drawRect(
                                    color = Color.Green,
                                    topLeft = Offset(boxLeft, boxTop),
                                    size = androidx.compose.ui.geometry.Size(boxRight - boxLeft, boxBottom - boxTop),
                                    style = Stroke(width = 2f)
                                )
                            }
                        }
                    }
                }
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Processed")
                processedImage?.let { img ->
                    Image(
                        bitmap = img.asImageBitmap(),
                        contentDescription = "Processed",
                        modifier = Modifier.size(150.dp),
                        contentScale = ContentScale.Crop
                    )
                } ?: Box(
                    modifier = Modifier
                        .size(150.dp)
                        .background(Color.Gray),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Not processed")
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        if (isProcessing) {
            CircularProgressIndicator()
            Text("Processing with Rust engine...")
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(onClick = onProcess) {
                    Text("Generate")
                }
                Button(onClick = onRetake) {
                    Text("Retake")
                }
                if (processedImage != null) {
                    Button(onClick = onSave) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

private fun decodeBitmap(bytes: ByteArray): Bitmap? {
    return try {
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    } catch (e: Exception) {
        null
    }
}

private fun saveImageToGallery(context: android.content.Context, imageBytes: ByteArray): Boolean {
    return try {
        val filename = "EdgePass_${System.currentTimeMillis()}.jpg"
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, filename)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/EdgePass")
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues) ?: return false
        resolver.openOutputStream(uri)?.use { outputStream ->
            outputStream.write(imageBytes)
        } ?: return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            contentValues.clear()
            contentValues.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
        }
        Log.d("EdgePassApp", "Image saved: $filename")
        true
    } catch (e: Exception) {
        Log.e("EdgePassApp", "Failed to save image: ${e.message}")
        false
    }
}
