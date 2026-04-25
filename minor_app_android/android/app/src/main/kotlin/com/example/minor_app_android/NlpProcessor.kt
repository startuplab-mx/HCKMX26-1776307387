// android/app/src/main/kotlin/com/example/minor_app_android/NlpProcessor.kt

package com.example.minor_app_android

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class NlpProcessor(private val context: Context) {

    // ══════════════════════════════════════════════════
    // CONFIGURACIÓN DEL MODELO
    // Ajustar según las specs del modelo .tflite
    // que les pasaron — preguntarle al equipo NLP si no coincide
    // ══════════════════════════════════════════════════
    companion object {
        private const val TAG              = "MinorNlp"
        private const val MODEL_PATH       = "models/nlp_model_lastest.tflite"
        private const val VOCAB_PATH       = "models/vocab.txt"
        private const val LABELS_PATH      = "models/labels.txt"
        private const val MAX_TOKENS       = 120        // Longitud máxima de secuencia
        private const val NUM_THREADS      = 2          // Threads de CPU para inferencia
    }

    // ══════════════════════════════════════════════════
    // INTÉRPRETE TFLITE
    // ══════════════════════════════════════════════════
    private var interpreter: Interpreter? = null
    private var isInitialized = false

    // Vocab cargado desde assets/models/vocab.txt
    private var vocab: Map<String, Int> = emptyMap()

    // Labels cargadas desde assets/models/labels.txt
    // Se usan en lugar de hardcoding para alinear siempre con el modelo
    private var outputLabels: Array<String> = arrayOf("safe", "reclutamiento")

    // ══════════════════════════════════════════════════
    // INICIALIZACIÓN — cargar modelo desde assets
    // Llamar una sola vez al arrancar el servicio
    // ══════════════════════════════════════════════════
    fun initialize(): Result<Unit> {
        return try {
            // Cargar vocab desde assets
            vocab = loadVocab()
            Log.d(TAG, "Vocab loaded: ${vocab.size} tokens")

            // Cargar labels desde assets (sincroniza con el modelo sin cambiar código)
            outputLabels = loadLabels()
            Log.d(TAG, "Labels loaded: ${outputLabels.toList()}")

            val modelBuffer = loadModelFromAssets()

            val options = Interpreter.Options().apply {
                numThreads = NUM_THREADS
            }

            interpreter = Interpreter(modelBuffer, options)
            
            // Log input details for debugging
            val inputTensor = interpreter!!.getInputTensor(0)
            Log.d(TAG, "Model input tensor: index=${inputTensor.index()}, name=${inputTensor.name()}, shape=${inputTensor.shape().contentToString()}, type=${inputTensor.dataType()}")

            // Verificar que OUTPUT_CLASSES coincide con el modelo
            val outputShape = interpreter!!.getOutputTensor(0).shape()
            val modelClasses = outputShape[1]
            Log.d(TAG, "Model output classes: $modelClasses, labels count: ${outputLabels.size}")
            if (modelClasses != outputLabels.size) {
                Log.w(TAG, "WARNING: model outputs $modelClasses classes but labels.txt has ${outputLabels.size} entries!")
            }

            isInitialized = true
            Result.success(Unit)

        } catch (e: Exception) {
            Result.failure(Exception("Error inicializando NlpProcessor: ${e.message}"))
        }
    }

    // ══════════════════════════════════════════════════
    // ENTRY POINT — recibe OcrTokens del OcrProcessor
    // y devuelve NlpResult con score y clasificación
    // ══════════════════════════════════════════════════
    fun analyze(tokens: OcrTokens): NlpResult {
        if (!isInitialized || interpreter == null) {
            return NlpResult.error("NlpProcessor no inicializado", tokens)
        }

        if (!tokens.isValid) {
            return NlpResult.empty(tokens)
        }

        return try {
            // 1. Preparar input — texto + emojis combinados
            val inputText = buildInputText(tokens)

            // 2. Tokenizar a IDs numéricos
            val inputIds = tokenize(inputText)

            // 3. Crear buffer de entrada para TFLite
            val inputBuffer = prepareInputBuffer(inputIds)

            // 4. Buffer de salida — tamaño dinámico desde labels.txt
            val outputBuffer = Array(1) { FloatArray(outputLabels.size) }

            // 5. Inferencia
            interpreter!!.run(inputBuffer, outputBuffer)

            // 6. Interpretar salida
            buildNlpResult(outputBuffer[0], tokens)

        } catch (e: Exception) {
            Log.e(TAG, "Inference error: ${e.message}")
            if (e.message?.contains("gather") == true) {
                Log.e(TAG, "DEBUG: Gather OOB suspected. Check token IDs in log.")
            }
            NlpResult.error(e.message ?: "Error en inferencia", tokens)
        }
    }

    // ══════════════════════════════════════════════════
    // PREPARAR TEXTO DE ENTRADA
    // Combina cleanText + emojis en una sola cadena
    // que el modelo puede procesar
    // ══════════════════════════════════════════════════
    private fun buildInputText(tokens: OcrTokens): String {
        val emojiString = tokens.emojis.joinToString(" ")
        return "${tokens.cleanText} $emojiString".trim()
    }

    // ══════════════════════════════════════════════════
    // TOKENIZADOR — usa vocab.txt del modelo
    // Línea N en vocab.txt = ID N (0-indexado)
    // Índice 0 = padding, índice 1 = [UNK]
    // ══════════════════════════════════════════════════
    private val unkId = 1

    private fun tokenize(text: String): IntArray {
        val words = text.lowercase()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

        val ids = IntArray(MAX_TOKENS) { 0 } // 0 = padding

        var unkCount = 0
        var maxIdFound = 0
        words.take(MAX_TOKENS).forEachIndexed { index, word ->
            val id = vocab.getOrDefault(word, -1)
            if (id == -1) {
                ids[index] = unkId
                unkCount++
            } else {
                ids[index] = id
                if (id > maxIdFound) maxIdFound = id
            }
        }

        Log.d(TAG, "tokenize: words=${words.size}, unk=$unkCount, maxId=$maxIdFound")
        
        // Log individual tokens if we see high IDs
        if (maxIdFound > 100) {
            val highIds = words.take(MAX_TOKENS).mapNotNull { word ->
                val id = vocab[word]
                if (id != null && id > 100) "$word($id)" else null
            }
            Log.d(TAG, "High IDs found: $highIds")
        }

        return ids
    }

    // ══════════════════════════════════════════════════
    // BUFFER DE ENTRADA TFLITE
    // ══════════════════════════════════════════════════
    private fun prepareInputBuffer(ids: IntArray): ByteBuffer {
        // INT32 = 4 bytes por token
        val buffer = ByteBuffer
            .allocateDirect(MAX_TOKENS * 4)
            .apply { order(ByteOrder.nativeOrder()) }

        ids.forEach { buffer.putInt(it) }
        buffer.rewind()
        return buffer
    }

    // ══════════════════════════════════════════════════
    // INTERPRETAR SALIDA DEL MODELO
    // Convierte probabilidades a NlpResult
    // ══════════════════════════════════════════════════
    private fun buildNlpResult(
        probabilities: FloatArray,
        tokens: OcrTokens
    ): NlpResult {
        // índice 0=safe, 1=reclutamiento
        val topIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: 0
        val topLabel = outputLabels.getOrElse(topIndex) { "unknown" }
        val topScore = probabilities[topIndex]

        val scoreMap = outputLabels
            .zip(probabilities.toList())
            .toMap()

        val alertLevel = when {
            topLabel == "reclutamiento" && topScore > 0.75f -> AlertLevel.HIGH
            topLabel == "reclutamiento"                     -> AlertLevel.MEDIUM
            else                                            -> AlertLevel.NONE
        }

        Log.d(TAG, "NLP result: label=$topLabel score=$topScore alert=$alertLevel")

        return NlpResult(
            label         = topLabel,
            riskScore     = topScore,
            alertLevel    = alertLevel,
            allScores     = scoreMap,
            hasRisk       = alertLevel != AlertLevel.NONE,
            packageName   = tokens.packageName,
            screenContext = tokens.screenContext,
            ocrSource     = tokens.source,
            emojis        = tokens.emojis,
            timestamp     = tokens.timestamp
        )
    }

    // ══════════════════════════════════════════════════
    // CARGAR VOCAB DESDE ASSETS
    // Cada línea N = ID N (0-indexado)
    // Línea 0 = string vacío (padding), Línea 1 = [UNK]
    // ══════════════════════════════════════════════════
    private fun loadVocab(): Map<String, Int> {
        val map = mutableMapOf<String, Int>()
        context.assets.open(VOCAB_PATH).bufferedReader().useLines { lines ->
            lines.forEachIndexed { index, token ->
                val trimmed = token.trim()
                if (trimmed.isNotEmpty()) {
                    map[trimmed] = index
                }
            }
        }
        return map
    }

    // ══════════════════════════════════════════════════
    // CARGAR LABELS DESDE ASSETS
    // Cada línea = un label (mismo orden que salida del modelo)
    // ══════════════════════════════════════════════════
    private fun loadLabels(): Array<String> {
        return context.assets.open(LABELS_PATH)
            .bufferedReader()
            .readLines()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toTypedArray()
    }

    // ══════════════════════════════════════════════════
    // CARGAR MODELO DESDE ASSETS
    // ══════════════════════════════════════════════════
    private fun loadModelFromAssets(): ByteBuffer {
        val afd: AssetFileDescriptor = context.assets.openFd(MODEL_PATH)
        val inputStream = FileInputStream(afd.fileDescriptor)
        val channel: FileChannel = inputStream.channel
        return channel.map(
            FileChannel.MapMode.READ_ONLY,
            afd.startOffset,
            afd.declaredLength
        )
    }

    // ══════════════════════════════════════════════════
    // LIBERAR RECURSOS
    // ══════════════════════════════════════════════════
    fun close() {
        interpreter?.close()
        interpreter = null
        isInitialized = false
    }
}

// ══════════════════════════════════════════════════
// OUTPUT DEL NLP — resultado final del pipeline
// ══════════════════════════════════════════════════
data class NlpResult(
    val label:        String,
    val riskScore:    Float,
    val alertLevel:   AlertLevel,
    val allScores:    Map<String, Float>,
    val hasRisk:      Boolean,
    val packageName:  String,
    val screenContext: AppAccessibilityService.ScreenContext,
    val ocrSource:    OcrSource,
    val emojis:       List<String>,
    val timestamp:    Long,
    val error:        String? = null
) {
    companion object {
        fun empty(tokens: OcrTokens) = NlpResult(
            label         = "SAFE",
            riskScore     = 0f,
            alertLevel    = AlertLevel.NONE,
            allScores     = emptyMap(),
            hasRisk       = false,
            packageName   = tokens.packageName,
            screenContext = tokens.screenContext,
            ocrSource     = tokens.source,
            emojis        = tokens.emojis,
            timestamp     = tokens.timestamp
        )

        fun error(message: String, tokens: OcrTokens) = NlpResult(
            label         = "ERROR",
            riskScore     = 0f,
            alertLevel    = AlertLevel.NONE,
            allScores     = emptyMap(),
            hasRisk       = false,
            packageName   = tokens.packageName,
            screenContext = tokens.screenContext,
            ocrSource     = tokens.source,
            emojis        = tokens.emojis,
            timestamp     = tokens.timestamp,
            error         = message
        )
    }

    fun toMap(): Map<String, Any?> = mapOf(
        "label"          to label,
        "risk_score"     to riskScore,
        "alert_level"    to alertLevel.name,
        "all_scores"     to allScores,
        "has_risk"       to hasRisk,
        "package_name"   to packageName,
        "screen_context" to screenContext.name,
        "ocr_source"     to ocrSource.name,
        "emojis"         to emojis,
        "timestamp"      to timestamp,
        "error"          to error
    )
}

enum class AlertLevel {
    NONE,
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}
