package com.edgepass.lib

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import android.util.Log
import android.os.Handler
import android.os.Looper
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class MlKitFaceDetector(private val context: Context) {

    companion object {
        private const val TAG = "FaceDetector"
        private const val MIN_FACE_SIZE = 0.15f
        private const val EXPANSION_RATIO = 2.0f
    }

    private var mlKitDetector: com.google.mlkit.vision.face.FaceDetector? = null
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val isDetecting = AtomicBoolean(false)

    data class FaceInfo(
        val boundingBox: RectF,
        val expandedBox: RectF,
        val confidence: Float,
        val headWidth: Float,
        val headHeight: Float,
        val shoulderWidth: Float,
        val shoulderHeight: Float,
        val landmarks: List<RectF>
    )

    enum class FacePositionStatus {
        NO_FACE,
        TOO_FAR,
        GOOD,
        TOO_CLOSE,
        NOT_CENTERED
    }

    data class DetectionResult(
        val faces: List<FaceInfo>,
        val status: FacePositionStatus,
        val isProcessing: Boolean
    )

    init {
        initializeMlKit()
    }

    private fun initializeMlKit() {
        try {
            val options = FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .setMinFaceSize(MIN_FACE_SIZE)
                .build()

            mlKitDetector = FaceDetection.getClient(options)
            Log.d(TAG, "ML Kit Face Detector initialized successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize ML Kit: ${e.message}")
            e.printStackTrace()
        }
    }

    fun detect(bitmap: Bitmap, callback: (List<FaceInfo>) -> Unit) {
        val detector = mlKitDetector ?: run {
            callback(emptyList())
            return
        }

        executor.execute {
            try {
                val inputImage = InputImage.fromBitmap(bitmap, 0)
                val task: Task<List<Face>> = detector.process(inputImage)
                val faces: List<Face> = Tasks.await(task, 5000, java.util.concurrent.TimeUnit.MILLISECONDS)

                val result = mutableListOf<FaceInfo>()

                for (face in faces) {
                    result.add(createFaceInfo(face, bitmap.width, bitmap.height))
                }

                Log.d(TAG, "Detected ${result.size} faces")

                mainHandler.post {
                    callback(result)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Face detection failed: ${e.message}")
                e.printStackTrace()
                mainHandler.post {
                    callback(emptyList())
                }
            }
        }
    }

    fun detectFromInputImage(image: InputImage, callback: (List<FaceInfo>) -> Unit) {
        val detector = mlKitDetector ?: run {
            callback(emptyList())
            return
        }

        val width = image.width
        val height = image.height

        detector.process(image)
            .addOnSuccessListener { faces ->
                val result = faces.map { createFaceInfo(it, width, height) }
                Log.d(TAG, "Detected ${result.size} faces from InputImage")
                callback(result)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "InputImage detection failed: ${e.message}")
                callback(emptyList())
            }
    }

    fun detectRealtime(bitmap: Bitmap, callback: (DetectionResult) -> Unit) {
        val detector = mlKitDetector ?: run {
            callback(DetectionResult(emptyList(), FacePositionStatus.NO_FACE, false))
            return
        }

        if (!isDetecting.compareAndSet(false, true)) {
            return
        }

        executor.execute {
            try {
                val inputImage = InputImage.fromBitmap(bitmap, 0)
                val task: Task<List<Face>> = detector.process(inputImage)
                val faces: List<Face> = Tasks.await(task, 3000, java.util.concurrent.TimeUnit.MILLISECONDS)

                val result = mutableListOf<FaceInfo>()

                for (face in faces) {
                    result.add(createFaceInfo(face, bitmap.width, bitmap.height))
                }

                val status = evaluateFacePosition(result, bitmap.width, bitmap.height)

                mainHandler.post {
                    callback(DetectionResult(result, status, false))
                    isDetecting.set(false)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Real-time detection failed: ${e.message}")
                mainHandler.post {
                    callback(DetectionResult(emptyList(), FacePositionStatus.NO_FACE, false))
                    isDetecting.set(false)
                }
            }
        }
    }

    private fun createFaceInfo(face: Face, imageWidth: Int, imageHeight: Int): FaceInfo {
        val box = face.boundingBox
        val faceWidth = box.width().toFloat()
        val faceHeight = box.height().toFloat()

        val centerX = box.left + faceWidth / 2
        val centerY = box.top + faceHeight / 2

        val headWidth = faceWidth
        val headHeight = faceHeight
        val shoulderWidth = headWidth * 2.0f
        val shoulderHeight = headHeight * 0.8f

        val expandedLeft = (centerX - headWidth * EXPANSION_RATIO / 2).coerceAtLeast(0f)
        val expandedTop = (centerY - headHeight * EXPANSION_RATIO / 2).coerceAtLeast(0f)
        val expandedRight = (centerX + headWidth * EXPANSION_RATIO / 2).coerceAtMost(imageWidth.toFloat())
        val expandedBottom = (centerY + headHeight * EXPANSION_RATIO / 2).coerceAtMost(imageHeight.toFloat())

        val confidence = if (face.trackingId != null) 1.0f else 0.8f

        return FaceInfo(
            boundingBox = RectF(
                box.left.toFloat(),
                box.top.toFloat(),
                box.right.toFloat(),
                box.bottom.toFloat()
            ),
            expandedBox = RectF(expandedLeft, expandedTop, expandedRight, expandedBottom),
            confidence = confidence,
            headWidth = headWidth,
            headHeight = headHeight,
            shoulderWidth = shoulderWidth,
            shoulderHeight = shoulderHeight,
            landmarks = emptyList()
        )
    }

    private fun evaluateFacePosition(faces: List<FaceInfo>, imageWidth: Int, imageHeight: Int): FacePositionStatus {
        if (faces.isEmpty()) {
            return FacePositionStatus.NO_FACE
        }

        val face = faces.first()
        val faceCenterX = face.boundingBox.centerX()
        val faceCenterY = face.boundingBox.centerY()

        val imageCenterX = imageWidth / 2f
        val imageCenterY = imageHeight / 2f

        val horizontalOffset = kotlin.math.abs(faceCenterX - imageCenterX) / imageWidth
        val verticalOffset = kotlin.math.abs(faceCenterY - imageCenterY) / imageHeight

        if (horizontalOffset > 0.15f || verticalOffset > 0.15f) {
            return FacePositionStatus.NOT_CENTERED
        }

        val faceArea = face.boundingBox.width() * face.boundingBox.height()
        val imageArea = imageWidth * imageHeight
        val faceRatio = faceArea / imageArea

        return when {
            faceRatio < 0.08f -> FacePositionStatus.TOO_FAR
            faceRatio > 0.35f -> FacePositionStatus.TOO_CLOSE
            else -> FacePositionStatus.GOOD
        }
    }

    fun detectFromBytes(imageBytes: ByteArray, callback: (List<FaceInfo>) -> Unit) {
        val bitmap = android.graphics.BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        if (bitmap != null) {
            detect(bitmap) { results ->
                bitmap.recycle()
                callback(results)
            }
        } else {
            callback(emptyList())
        }
    }

    fun release() {
        try {
            mlKitDetector?.close()
            mlKitDetector = null
            executor.shutdown()
            Log.d(TAG, "Face detector released")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release: ${e.message}")
        }
    }
}
