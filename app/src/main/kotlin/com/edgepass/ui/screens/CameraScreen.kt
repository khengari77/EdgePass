package com.edgepass.ui.screens

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.edgepass.lib.MlKitFaceDetector
import com.edgepass.lib.PassportProcessor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

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
    val detectionExecutor = remember { Executors.newSingleThreadExecutor() }
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }

    val faceDetector = remember { MlKitFaceDetector(context) }
    var detectionResult by remember { mutableStateOf<MlKitFaceDetector.DetectionResult?>(null) }
    var isDetecting by remember { mutableStateOf(false) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var detectionTrigger by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
            detectionExecutor.shutdown()
            faceDetector.release()
        }
    }

    LaunchedEffect(detectionTrigger) {
        capturedBitmap?.let { bitmap ->
            if (!isDetecting) {
                isDetecting = true
                faceDetector.detectRealtime(bitmap) { result ->
                    detectionResult = result
                    isDetecting = false
                }
            }
        }
    }

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
                .border(2.dp, Color.Gray, RoundedCornerShape(8.dp))
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

            detectionResult?.let { result ->
                if (result.faces.isNotEmpty()) {
                    val face = result.faces.first()

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val canvasWidth = size.width
                        val canvasHeight = size.height

                        val scaleX = canvasWidth / face.expandedBox.width()
                        val scaleY = canvasHeight / face.expandedBox.height()

                        val expandedLeft = face.expandedBox.left * scaleX
                        val expandedTop = face.expandedBox.top * scaleY
                        val expandedRight = face.expandedBox.right * scaleX
                        val expandedBottom = face.expandedBox.bottom * scaleY
                        val expandedWidth = expandedRight - expandedLeft
                        val expandedHeight = expandedBottom - expandedTop

                        val ovalWidth = expandedWidth * 0.8f
                        val ovalHeight = expandedHeight * 1.2f
                        val ovalLeft = (canvasWidth - ovalWidth) / 2
                        val ovalTop = (canvasHeight - ovalHeight) / 2

                        drawOval(
                            color = Color.White.copy(alpha = 0.3f),
                            topLeft = Offset(ovalLeft, ovalTop),
                            size = Size(ovalWidth, ovalHeight),
                            style = Stroke(width = 4f)
                        )

                        val faceCenterX = face.boundingBox.centerX() * scaleX
                        val faceCenterY = face.boundingBox.centerY() * scaleY

                        drawCircle(
                            color = Color.Yellow,
                            radius = 8f,
                            center = Offset(faceCenterX, faceCenterY)
                        )
                    }
                }
            }

            detectionResult?.let { result ->
                val statusText = when (result.status) {
                    MlKitFaceDetector.FacePositionStatus.GOOD -> "✓ Good position"
                    MlKitFaceDetector.FacePositionStatus.TOO_FAR -> "Move closer"
                    MlKitFaceDetector.FacePositionStatus.TOO_CLOSE -> "Move back"
                    MlKitFaceDetector.FacePositionStatus.NOT_CENTERED -> "Center your face"
                    MlKitFaceDetector.FacePositionStatus.NO_FACE -> "Face not detected"
                }

                val statusColor = when (result.status) {
                    MlKitFaceDetector.FacePositionStatus.GOOD -> Color.Green
                    MlKitFaceDetector.FacePositionStatus.TOO_FAR -> Color.Yellow
                    MlKitFaceDetector.FacePositionStatus.TOO_CLOSE -> Color.Yellow
                    MlKitFaceDetector.FacePositionStatus.NOT_CENTERED -> Color.Yellow
                    MlKitFaceDetector.FacePositionStatus.NO_FACE -> Color.Red
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = statusText,
                        color = statusColor,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
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
                                var bitmap = image.toBitmap()
                                val rotationDegrees = image.imageInfo.rotationDegrees

                                Log.d("CameraScreen", "Original image - rotation: $rotationDegrees, format: ${image.format}")

                                val correctedBitmap = when {
                                    lensFacing == CameraSelector.LENS_FACING_FRONT && rotationDegrees == 270 -> {
                                        val matrix = Matrix()
                                        matrix.postScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f)
                                        matrix.postRotate(90f)
                                        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                                    }
                                    lensFacing == CameraSelector.LENS_FACING_FRONT -> {
                                        val matrix = Matrix()
                                        matrix.postScale(-1f, 1f, bitmap.width / 2f, bitmap.height / 2f)
                                        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                                    }
                                    rotationDegrees > 0 -> {
                                        val matrix = Matrix()
                                        matrix.postRotate(-rotationDegrees.toFloat())
                                        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
                                    }
                                    else -> bitmap
                                }

                                capturedBitmap = correctedBitmap
                                detectionTrigger++

                                val stream = java.io.ByteArrayOutputStream()
                                correctedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)
                                val bytes = stream.toByteArray()

                                if (correctedBitmap != bitmap) {
                                    bitmap.recycle()
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
