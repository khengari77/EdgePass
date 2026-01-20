package com.edgepass.lib

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.io.File
import java.io.FileOutputStream
import java.nio.FloatBuffer

class BackgroundRemover(private val context: Context) {

    companion object {
        private const val TAG = "BackgroundRemover"
        private const val MODEL_PATH = "models/background_remover.onnx"
        private const val MODEL_NAME = "background_remover.onnx"
        private const val INPUT_SIZE = 1024
    }

    private var session: OrtSession? = null
    private var env: OrtEnvironment? = null
    private var isInitialized = false

    init {
        loadModel()
    }

    private fun loadModel() {
        try {
            env = OrtEnvironment.getEnvironment()
            val modelFile = File(context.filesDir, MODEL_NAME)
            if (!modelFile.exists()) {
                copyModelFromAssets(modelFile)
            }
            val sessionOptions = OrtSession.SessionOptions()
            session = env?.createSession(modelFile.absolutePath, sessionOptions)
            Log.d(TAG, "ONNX Runtime session created")
            isInitialized = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load model: ${e.message}")
            release()
        }
    }

    private fun copyModelFromAssets(destFile: File) {
        try {
            context.assets.open(MODEL_PATH).use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            Log.d(TAG, "Model copied to: ${destFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to copy model: ${e.message}")
        }
    }

    fun removeBackground(input: Bitmap): Bitmap? {
        if (!isInitialized || session == null || env == null) {
            Log.e(TAG, "Model not initialized")
            return null
        }
        return try {
            Log.d(TAG, "Starting background removal for ${input.width}x${input.height}")
            val resized = Bitmap.createScaledBitmap(input, INPUT_SIZE, INPUT_SIZE, true)
            val inputData = preprocess(resized)
            val inputShape = longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())
            val inputBuffer = FloatBuffer.wrap(inputData)
            val inputTensor = OnnxTensor.createTensor(env!!, inputBuffer, inputShape)
            val outputs = session?.run(mapOf("input" to inputTensor))
            val outputTensor = outputs?.get(0)
            val outputData = outputTensor?.value as? FloatBuffer
            val result = postprocess(outputData, INPUT_SIZE, INPUT_SIZE, input.width, input.height)
            resized.recycle()
            Log.d(TAG, "Background removal complete")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Background removal failed: ${e.message}")
            null
        }
    }

    private fun preprocess(bitmap: Bitmap): FloatArray {
        val width = bitmap.width
        val height = bitmap.height
        val pixels = IntArray(width * height)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)
        val floatData = FloatArray(3 * width * height)
        for (i in pixels.indices) {
            val pixel = pixels[i]
            floatData[i] = Color.red(pixel) / 255.0f
            floatData[width * height + i] = Color.green(pixel) / 255.0f
            floatData[2 * width * height + i] = Color.blue(pixel) / 255.0f
        }
        return floatData
    }

    private fun postprocess(outputBuffer: FloatBuffer?, outputWidth: Int, outputHeight: Int, targetWidth: Int, targetHeight: Int): Bitmap {
        if (outputBuffer == null) {
            return Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888).apply {
                Canvas(this).drawColor(Color.WHITE)
            }
        }
        val result = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(result)
        canvas.drawColor(Color.WHITE)
        val alphaMask = Bitmap.createBitmap(outputWidth, outputHeight, Bitmap.Config.ARGB_8888)
        for (y in 0 until outputHeight) {
            for (x in 0 until outputWidth) {
                val alpha = outputBuffer.get()
                val normalizedAlpha = ((alpha.coerceIn(0f, 1f)) * 255).toInt()
                alphaMask.setPixel(x, y, Color.argb(normalizedAlpha, 255, 255, 255))
            }
        }
        val scaledMask = Bitmap.createScaledBitmap(alphaMask, targetWidth, targetHeight, true)
        alphaMask.recycle()
        scaledMask.recycle()
        return result
    }

    fun release() {
        isInitialized = false
        Log.d(TAG, "ONNX Runtime resources marked for release")
    }
}
