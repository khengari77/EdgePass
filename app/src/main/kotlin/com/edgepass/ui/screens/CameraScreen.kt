package com.edgepass.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.edgepass.lib.MlKitFaceDetector
import com.edgepass.lib.PassportProcessor
import java.nio.ByteBuffer
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

    val faceDetector = remember { MlKitFaceDetector(context) }
    var detectionResult by remember { mutableStateOf<MlKitFaceDetector.DetectionResult?>(null) }

    var imageWidth by remember { mutableIntStateOf(1) }
    var imageHeight by remember { mutableIntStateOf(1) }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
            faceDetector.release()
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

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
                    .build()

                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                    val rotation = imageProxy.imageInfo.rotationDegrees
                    if (rotation == 90 || rotation == 270) {
                        imageWidth = imageProxy.height
                        imageHeight = imageProxy.width
                    } else {
                        imageWidth = imageProxy.width
                        imageHeight = imageProxy.height
                    }

                    val mediaImage = imageProxy.image
                    if (mediaImage != null) {
                        val inputImage = com.google.mlkit.vision.common.InputImage.fromMediaImage(
                            mediaImage,
                            imageProxy.imageInfo.rotationDegrees
                        )

                        faceDetector.detectFromInputImage(inputImage) { faces ->
                            val status = evaluateFacePosition(faces, imageWidth, imageHeight)
                            detectionResult = MlKitFaceDetector.DetectionResult(faces, status, false)
                            imageProxy.close()
                        }
                    } else {
                        imageProxy.close()
                    }
                }

                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
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
                        imageAnalysis,
                        imageCapture
                    )
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
            StandardButton("Schengen", selectedStandard == PassportProcessor.STANDARD_SCHENGEN) { selectedStandard = PassportProcessor.STANDARD_GENERAL_ID }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = {
                lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK
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
                        scaleType = PreviewView.ScaleType.FIT_CENTER
                        implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    }.also { previewView = it }
                },
                modifier = Modifier.fillMaxSize()
            )

            detectionResult?.let { result ->
                if (result.faces.isNotEmpty()) {
                    val face = result.faces.first()

                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val scaleX = size.width / imageWidth
                        val scaleY = size.height / imageHeight
                        val scale = minOf(scaleX, scaleY)

                        val offsetX = (size.width - (imageWidth * scale)) / 2f
                        val offsetY = (size.height - (imageHeight * scale)) / 2f

                        val left = offsetX + face.expandedBox.left * scale
                        val top = offsetY + face.expandedBox.top * scale
                        val width = face.expandedBox.width() * scale
                        val height = face.expandedBox.height() * scale

                        drawOval(
                            color = Color.Green,
                            topLeft = Offset(left, top),
                            size = Size(width, height),
                            style = Stroke(width = 5f)
                        )
                    }
                }
            }

            detectionResult?.let { result ->
                val statusText = when (result.status) {
                    MlKitFaceDetector.FacePositionStatus.GOOD -> "Good position"
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
                val capture = imageCapture ?: return@Button
                capture.takePicture(
                    cameraExecutor,
                    object : ImageCapture.OnImageCapturedCallback() {
                        override fun onCaptureSuccess(image: ImageProxy) {
                            // Use CameraX's built-in toBitmap method which handles all formats correctly
                            val bitmap = image.toBitmap()
                            val rotationDegrees = image.imageInfo.rotationDegrees

                            // Calculate final dimensions after rotation
                            val finalWidth: Int
                            val finalHeight: Int
                            if (rotationDegrees == 90 || rotationDegrees == 270) {
                                finalWidth = bitmap.height
                                finalHeight = bitmap.width
                            } else {
                                finalWidth = bitmap.width
                                finalHeight = bitmap.height
                            }

                            val matrix = Matrix()

                            // Apply rotation
                            matrix.postRotate(rotationDegrees.toFloat())

                            // Handle front camera mirroring
                            if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
                                // Mirror around vertical axis after rotation
                                matrix.postScale(-1f, 1f, finalWidth / 2f, finalHeight / 2f)
                            }

                            val rotatedBitmap = Bitmap.createBitmap(
                                bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true
                            )

                            val stream = java.io.ByteArrayOutputStream()
                            rotatedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)

                            image.close()
                            bitmap.recycle()

                            onImageCaptured(stream.toByteArray())
                            if (rotatedBitmap != bitmap) rotatedBitmap.recycle()
                        }

                        override fun onError(exception: ImageCaptureException) {
                            Log.e("CameraScreen", "Capture failed", exception)
                        }
                    }
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
        ) {
            Text("Capture")
        }
    }
}

private fun evaluateFacePosition(faces: List<MlKitFaceDetector.FaceInfo>, imageWidth: Int, imageHeight: Int): MlKitFaceDetector.FacePositionStatus {
    if (faces.isEmpty()) {
        return MlKitFaceDetector.FacePositionStatus.NO_FACE
    }

    val face = faces.first()
    val faceCenterX = face.boundingBox.centerX()
    val faceCenterY = face.boundingBox.centerY()

    val imageCenterX = imageWidth / 2f
    val imageCenterY = imageHeight / 2f

    val horizontalOffset = kotlin.math.abs(faceCenterX - imageCenterX) / imageWidth
    val verticalOffset = kotlin.math.abs(faceCenterY - imageCenterY) / imageHeight

    if (horizontalOffset > 0.15f || verticalOffset > 0.15f) {
        return MlKitFaceDetector.FacePositionStatus.NOT_CENTERED
    }

    val faceArea = face.boundingBox.width() * face.boundingBox.height()
    val imageArea = imageWidth * imageHeight
    val faceRatio = faceArea / imageArea

    return when {
        faceRatio < 0.08f -> MlKitFaceDetector.FacePositionStatus.TOO_FAR
        faceRatio > 0.35f -> MlKitFaceDetector.FacePositionStatus.TOO_CLOSE
        else -> MlKitFaceDetector.FacePositionStatus.GOOD
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
