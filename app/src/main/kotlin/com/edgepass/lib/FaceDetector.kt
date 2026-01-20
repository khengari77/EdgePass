package com.edgepass.lib

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Rect
import android.media.FaceDetector
import android.util.Log
import java.io.File

class FaceDetector(private val context: Context) {

    companion object {
        private const val TAG = "FaceDetector"
        private const val MAX_FACES = 1
    }

    private var isInitialized = true

    data class FaceInfo(
        val boundingBox: Rect,
        val confidence: Float
    )

    fun detect(bitmap: Bitmap): List<FaceInfo> {
        if (!isInitialized) {
            Log.e(TAG, "FaceDetector not initialized")
            return emptyList()
        }

        return try {
            val results = mutableListOf<FaceInfo>()
            val width = bitmap.width
            val height = bitmap.height
            
            if (width < 64 || height < 64) {
                Log.w(TAG, "Image too small for face detection: ${width}x${height}")
                return emptyList()
            }

            val normalizedBitmap = bitmap.copy(Bitmap.Config.RGB_565, true)
            val androidDetector = FaceDetector(normalizedBitmap.width, normalizedBitmap.height, MAX_FACES)
            val faces = arrayOfNulls<FaceDetector.Face>(MAX_FACES)
            val faceCount = androidDetector.findFaces(normalizedBitmap, faces)

            Log.d(TAG, "FaceDetector raw found $faceCount faces")

            for (i in 0 until faceCount) {
                val face = faces[i]
                if (face != null) {
                    val midPoint = PointF()
                    face.getMidPoint(midPoint)
                    val midX = midPoint.x
                    val midY = midPoint.y
                    val eyeDistance = face.eyesDistance()
                    val confidence = face.confidence()
                    
                    if (eyeDistance < 50f) {
                        Log.d(TAG, "Rejected face with too small eye distance: $eyeDistance")
                        continue
                    }
                    
                    if (confidence < 0.5f) {
                        Log.d(TAG, "Rejected face with low confidence: $confidence")
                        continue
                    }
                    
                    if (midX < 0 || midY < 0 || midX > width || midY > height) {
                        continue
                    }
                    
                    val left = (midX - eyeDistance).toInt().coerceAtLeast(0)
                    val top = (midY - eyeDistance).toInt().coerceAtLeast(0)
                    val right = (midX + eyeDistance).toInt().coerceAtMost(width)
                    val bottom = (midY + eyeDistance * 1.5f).toInt().coerceAtMost(height)
                    
                    if (right <= left || bottom <= top) {
                        continue
                    }
                    
                    val boxArea = (right - left) * (bottom - top)
                    val imageArea = width * height
                    if (boxArea > imageArea * 0.9) {
                        continue
                    }
                    
                    val rect = Rect(left, top, right, bottom)
                    results.add(FaceInfo(rect, confidence))
                    Log.d(TAG, "Valid face: mid=($midX, $midY), eyes=$eyeDistance, confidence=$confidence, rect=$rect")
                }
            }

            if (normalizedBitmap != bitmap) {
                normalizedBitmap.recycle()
            }

            Log.d(TAG, "Final face count: ${results.size}")
            results
        } catch (e: Exception) {
            Log.e(TAG, "Face detection failed: ${e.message}")
            emptyList()
        }
    }

    fun detectFromBytes(imageBytes: ByteArray): List<FaceInfo> {
        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        return if (bitmap != null) {
            val results = detect(bitmap)
            bitmap.recycle()
            results
        } else {
            emptyList()
        }
    }

    fun release() {
        isInitialized = false
    }
}
