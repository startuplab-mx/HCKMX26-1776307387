package com.example.minor_app_android

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.graphics.Bitmap
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class VisionProcessor(private val context: Context) {

    companion object {
        private const val TAG = "MinorVision"
        private const val MODEL_PATH = "models/vision_model_lastest.tflite"
        private const val LABELS_PATH = "models/vision_labels.txt"
        
        private const val INPUT_SIZE = 640
        private const val NUM_THREADS = 4
        private const val CONFIDENCE_THRESHOLD = 0.40f
    }

    private var interpreter: Interpreter? = null
    private var isInitialized = false
    private var labels: List<String> = emptyList()

    fun initialize(): Result<Unit> {
        return try {
            labels = loadLabels()
            Log.d(TAG, "Vision labels loaded: $labels")

            val modelBuffer = loadModelFromAssets()
            val options = Interpreter.Options().apply {
                numThreads = NUM_THREADS
            }
            interpreter = Interpreter(modelBuffer, options)
            
            val inputShape = interpreter!!.getInputTensor(0).shape()
            Log.d(TAG, "Vision Model input shape: ${inputShape.toList()}")

            isInitialized = true
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing VisionProcessor: ${e.message}")
            Result.failure(e)
        }
    }

    fun analyze(bitmap: Bitmap): VisionResult {
        if (!isInitialized || interpreter == null) {
            return VisionResult(emptyList(), "error", false, "Processor not initialized")
        }

        return try {
            val inputBuffer = preprocessBitmap(bitmap)
            
            // YOLOv8 TFLite output is typically [1, 4 + num_classes, 8400]
            // For 4 classes (armas, botellas, dinero, animales), shape is [1, 8, 8400]
            val outputTensor = interpreter!!.getOutputTensor(0)
            val outputShape = outputTensor.shape() // [1, 8, 8400]
            
            val numRows = outputShape[1] // 8 (4 coords + 4 classes)
            val numCols = outputShape[2] // 8400
            
            val outputBuffer = Array(1) { Array(numRows) { FloatArray(numCols) } }
            interpreter!!.run(inputBuffer, outputBuffer)

            val detections = decodeYOLOv8(outputBuffer[0], numRows, numCols)
            val topDetection = detections.maxByOrNull { it.confidence }

            val summary = if (detections.isEmpty()) "safe" else "detected_${topDetection?.label}"
            
            // Tier Logic from vision_config.yaml
            val tier1Count = detections.count { it.label == "armas" }
            val tier2Count = detections.count { it.label in listOf("botellas", "dinero", "animales") }
            
            val (alertLabel, isFlagged) = when {
                tier1Count >= 1 && tier2Count >= 2 -> "ALERTA RECLUTAMIENTO" to true
                tier1Count >= 1 && tier2Count >= 1 -> "FLAG ASPIRACIONALIDAD" to true
                tier1Count >= 1                   -> "FLAG PASIVO" to true
                else                               -> "SIN ALERTA" to false
            }

            Log.d(TAG, "Vision Analysis: detections=${detections.size}, tier1=$tier1Count, tier2=$tier2Count, result=$alertLabel")

            VisionResult(
                detections = detections,
                primaryLabel = alertLabel,
                isFlagged = isFlagged
            )

        } catch (e: Exception) {
            Log.e(TAG, "Error during vision analysis: ${e.message}")
            VisionResult(emptyList(), "error", false, e.message)
        }
    }

    private fun preprocessBitmap(bitmap: Bitmap): ByteBuffer {
        val resized = Bitmap.createScaledBitmap(bitmap, INPUT_SIZE, INPUT_SIZE, true)
        val buffer = ByteBuffer.allocateDirect(1 * INPUT_SIZE * INPUT_SIZE * 3 * 4)
        buffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        resized.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)

        for (pixel in pixels) {
            buffer.putFloat(((pixel shr 16) and 0xFF) / 255.0f)
            buffer.putFloat(((pixel shr 8) and 0xFF) / 255.0f)
            buffer.putFloat((pixel and 0xFF) / 255.0f)
        }
        return buffer
    }

    private fun decodeYOLOv8(output: Array<FloatArray>, numRows: Int, numCols: Int): List<Detection> {
        val detections = mutableListOf<Detection>()
        
        // output format: [x, y, w, h, class0, class1, class2, class3] x 8400
        for (col in 0 until numCols) {
            var maxClassScore = 0f
            var classId = -1
            
            // classes start at index 4
            for (row in 4 until numRows) {
                if (output[row][col] > maxClassScore) {
                    maxClassScore = output[row][col]
                    classId = row - 4
                }
            }
            
            if (maxClassScore > CONFIDENCE_THRESHOLD) {
                detections.add(Detection(
                    label = labels.getOrElse(classId) { "unknown" },
                    confidence = maxClassScore
                ))
            }
        }
        
        // Simple filter: only unique classes with their max confidence to avoid noise
        // (In a real detector we'd use NMS, but for this tier logic, existence is enough)
        return detections.groupBy { it.label }
            .map { entry -> entry.value.maxBy { it.confidence } }
    }

    private fun loadLabels(): List<String> {
        return context.assets.open(LABELS_PATH).bufferedReader().readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }

    private fun loadModelFromAssets(): ByteBuffer {
        val afd: AssetFileDescriptor = context.assets.openFd(MODEL_PATH)
        val channel: FileChannel = FileInputStream(afd.fileDescriptor).channel
        return channel.map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)
    }

    fun close() {
        interpreter?.close()
        interpreter = null
    }
}

data class Detection(
    val label: String,
    val confidence: Float
)

data class VisionResult(
    val detections: List<Detection>,
    val primaryLabel: String,
    val isFlagged: Boolean,
    val error: String? = null
)
