package com.edgepass.ui.screens

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.edgepass.lib.PassportProcessor
import java.util.concurrent.Executors

@Composable
fun CameraScreen(
    processor: PassportProcessor,
    onImageCaptured: (ByteArray) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_FRONT) }
    var selectedStandard by remember { mutableIntStateOf(PassportProcessor.STANDARD_SAUDI_EVISA) }
    var selectedSuit by remember { mutableIntStateOf(0) }
    
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    // Rebind camera when lensFacing changes
    LaunchedEffect(lensFacing, previewView) {
        previewView?.let { pv ->
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()
                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(pv.surfaceProvider)
                }
                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()
                val cameraSelector = CameraSelector.Builder()
                    .requireLensFacing(lensFacing)
                    .build()
                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageCapture
                    )
                    Log.d("CameraScreen", "Camera rebound with lensFacing: $lensFacing")
                } catch (e: Exception) {
                    Log.e("CameraScreen", "Camera binding failed", e)
                }
            }, ContextCompat.getMainExecutor(context))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            StandardButton("Saudi", selectedStandard == PassportProcessor.STANDARD_SAUDI_EVISA) { selectedStandard = PassportProcessor.STANDARD_SAUDI_EVISA }
            StandardButton("US", selectedStandard == PassportProcessor.STANDARD_US) { selectedStandard = PassportProcessor.STANDARD_US }
            StandardButton("Schengen", selectedStandard == PassportProcessor.STANDARD_SCHENGEN) { selectedStandard = PassportProcessor.STANDARD_SCHENGEN }
            StandardButton("ID", selectedStandard == PassportProcessor.STANDARD_GENERAL_ID) { selectedStandard = PassportProcessor.STANDARD_GENERAL_ID }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = { 
                lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK 
                Log.d("CameraScreen", "Toggle clicked, lensFacing now: $lensFacing")
            }) {
                Text(if (lensFacing == CameraSelector.LENS_FACING_BACK) "FRONT" else "BACK")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
        ) {
            AndroidView(
                factory = { ctx ->
                    PreviewView(ctx).apply {
                        scaleType = PreviewView.ScaleType.FILL_CENTER
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    }.also { previewView = it }
                },
                modifier = Modifier.fillMaxSize(),
                update = { }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            SuitButton("None", selectedSuit == 0) { selectedSuit = 0 }
            SuitButton("Suit 1", selectedSuit == 1) { selectedSuit = 1 }
            SuitButton("Suit 2", selectedSuit == 2) { selectedSuit = 2 }
            SuitButton("Suit 3", selectedSuit == 3) { selectedSuit = 3 }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                imageCapture?.let { capture ->
                    capture.takePicture(
                        cameraExecutor,
                        object : ImageCapture.OnImageCapturedCallback() {
                            override fun onCaptureSuccess(image: ImageProxy) {
                                val bitmap = image.toBitmap()
                                val rotationDegrees = image.imageInfo.rotationDegrees
                                
                                Log.d("CameraScreen", "Original image - rotation: $rotationDegrees, format: ${image.format}")
                                
                                val correctedBitmap = when {
                                    lensFacing == CameraSelector.LENS_FACING_FRONT && rotationDegrees == 270 -> {
                                        // Front camera needs mirroring and rotation correction
                                        val matrix = Matrix()
                                        matrix.postScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f)
                                        matrix.postRotate(90f)
                                        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                                    }
                                    lensFacing == CameraSelector.LENS_FACING_FRONT -> {
                                        // Front camera - mirror only
                                        val matrix = Matrix()
                                        matrix.postScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f)
                                        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                                    }
                                    rotationDegrees > 0 -> {
                                        // Back camera with rotation - rotate back to normal
                                        val matrix = Matrix()
                                        matrix.postRotate(-rotationDegrees.toFloat())
                                        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                                    }
                                    else -> bitmap
                                }
                                
                                val stream = java.io.ByteArrayOutputStream()
                                correctedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
                                val bytes = stream.toByteArray()
                                
                                if (correctedBitmap != bitmap) {
                                    correctedBitmap.recycle()
                                }
                                bitmap.recycle()
                                image.close()
                                onImageCaptured(bytes)
                            }
                            override fun onError(exception: ImageCaptureException) {
                                Log.e("CameraScreen", "Photo capture failed", exception)
                            }
                        }
                    )
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
        ) {
            Text("Capture")
        }
    }
}

@Composable
private fun StandardButton(text: String, selected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.height(48.dp)
    ) {
        Text(text, color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SuitButton(text: String, selected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier.height(48.dp)
    ) {
        Text(text, color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private fun ImageProxy.toBitmap(): Bitmap {
    val buffer = planes[0].buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)
    return android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        ?: throw IllegalStateException("Failed to decode image")
}
