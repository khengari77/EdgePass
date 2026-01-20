package com.edgepass.lib

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.nio.FloatBuffer

class PassportProcessor(private val context: Context) {

    companion object {
        private const val TAG = "PassportProcessor"
        const val STANDARD_SAUDI_EVISA = 0
        const val STANDARD_US = 1
        const val STANDARD_SCHENGEN = 2
        const val STANDARD_GENERAL_ID = 3
        const val STANDARD_UK = 4
        const val STANDARD_INDIA = 5
        const val STANDARD_CUSTOM = 99

        @Volatile
        private var instance: PassportProcessor? = null

        fun getInstance(context: Context): PassportProcessor {
            return instance ?: synchronized(this) {
                instance ?: PassportProcessor(context.applicationContext).also { instance = it }
            }
        }

        init {
            System.loadLibrary("edgepass_core")
        }
    }

    private val backgroundRemover: BackgroundRemover? = null

    val version: String by lazy {
        nativeVersion()
    }

    val isInitialized: Boolean
        get() = nativeCheckInit()

    init {
        setupModels(context)
        val modelPath = context.filesDir.absolutePath
        nativeInitEngine(modelPath)
        Log.d(TAG, "Initialized with model path: $modelPath")
    }

    private fun setupModels(context: Context) {
        val modelsDir = context.filesDir.resolve("models")
        if (!modelsDir.exists()) {
            modelsDir.mkdirs()
            copyModelsFromAssets(context, modelsDir)
        }
    }

    private fun copyModelsFromAssets(context: Context, destDir: java.io.File) {
        try {
            context.assets.list("models")?.forEach { modelFile ->
                val source = context.assets.open("models/$modelFile")
                val dest = destDir.resolve(modelFile)
                source.use { input ->
                    dest.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d(TAG, "Copied model: $modelFile")
            }
        } catch (e: Exception) {
            Log.w(TAG, "No models in assets, using empty directory")
        }
    }

    fun generate(
        imageBytes: ByteArray,
        standard: Int = STANDARD_SAUDI_EVISA,
        suitBytes: ByteArray? = null,
        faceCenterX: Float? = null,
        faceCenterY: Float? = null,
        removeBackground: Boolean = false
    ): ByteArray? {
        if (removeBackground) {
            return try {
                Log.d(TAG, "Using ONNX Runtime for background removal")
                generateWithOnnx(imageBytes, standard, removeBackground)
            } catch (e: Exception) {
                Log.e(TAG, "ONNX background removal failed, falling back to Rust: ${e.message}")
                nativeGenerate(imageBytes, standard, suitBytes, faceCenterX, faceCenterY, removeBackground)
            }
        }
        return nativeGenerate(imageBytes, standard, suitBytes, faceCenterX, faceCenterY, removeBackground)
    }

    private fun generateWithOnnx(imageBytes: ByteArray, standard: Int, removeBackground: Boolean): ByteArray? {
        val originalBitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size) ?: return null
        val processedBitmap = if (removeBackground) {
            val backgroundRemoved = BackgroundRemover(context).removeBackground(originalBitmap)
            if (backgroundRemoved != null) {
                Log.d(TAG, "Background removed successfully")
                backgroundRemoved
            } else {
                Log.w(TAG, "Background removal failed, using original")
                originalBitmap
            }
        } else {
            originalBitmap
        }

        val outputStream = java.io.ByteArrayOutputStream()
        processedBitmap.compress(Bitmap.CompressFormat.JPEG, 95, outputStream)

        if (processedBitmap != originalBitmap) {
            processedBitmap.recycle()
        }
        originalBitmap.recycle()

        return outputStream.toByteArray()
    }

    external fun nativeInitEngine(modelPath: String)

    external fun nativeGenerate(
        imageBytes: ByteArray,
        standard: Int,
        suitBytes: ByteArray?,
        faceCenterX: Float?,
        faceCenterY: Float?,
        removeBackground: Boolean
    ): ByteArray?

    external fun nativeCheckInit(): Boolean

    external fun nativeVersion(): String
}
