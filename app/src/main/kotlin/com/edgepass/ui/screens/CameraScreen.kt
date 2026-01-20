package com.edgepass.ui.screens

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
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
    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var selectedStandard by remember { mutableIntStateOf(PassportProcessor.STANDARD_SAUDI_EVISA) }
    var selectedSuit by remember { mutableIntStateOf(0) }
    
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
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
            TextButton(onClick = { lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK }) {
                Text(if (lensFacing == CameraSelector.LENS_FACING_BACK) "BACK" else "FRONT")
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
                    }
                },
                modifier = Modifier.fillMaxSize(),
                update = { previewView ->
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
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
                        } catch (e: Exception) {
                            Log.e("CameraScreen", "Camera binding failed", e)
                        }
                    }, ContextCompat.getMainExecutor(context))
                }
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
                                val rotatedBitmap = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                                    val matrix = Matrix()
                                    matrix.postScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f)
                                    Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                                } else {
                                    bitmap
                                }
                                val stream = java.io.ByteArrayOutputStream()
                                rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
                                val bytes = stream.toByteArray()
                                if (rotatedBitmap != bitmap) rotatedBitmap.recycle()
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
