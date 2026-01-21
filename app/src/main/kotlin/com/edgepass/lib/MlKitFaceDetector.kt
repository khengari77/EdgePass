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

class MlKitFaceDetector(private val context: Context) {

    companion object {
        private const val TAG = "FaceDetector"
    }

    private var mlKitDetector: com.google.mlkit.vision.face.FaceDetector? = null
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    data class FaceInfo(
        val boundingBox: RectF,
        val confidence: Float,
        val landmarks: List<RectF>
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
                .setMinFaceSize(0.4f)
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
                    val box = face.boundingBox
                    val confidence = if (face.trackingId != null) 1.0f else 0.8f

                    val landmarks = mutableListOf<RectF>()

                    result.add(
                        FaceInfo(
                            boundingBox = RectF(
                                box.left.toFloat(),
                                box.top.toFloat(),
                                box.right.toFloat(),
                                box.bottom.toFloat()
                            ),
                            confidence = confidence,
                            landmarks = landmarks
                        )
                    )
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
