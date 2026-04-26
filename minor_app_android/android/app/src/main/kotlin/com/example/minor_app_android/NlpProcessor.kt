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

    companion object {
        private const val TAG            = "MinorNlp"
        private const val MODEL_PATH     = "models/nlp_model_lastest.tflite"
        private const val VOCAB_PATH     = "models/vocab.txt"
        private const val MAX_TOKENS     = 60   // Igual que el notebook del equipo NLP
        private const val NUM_THREADS    = 2
    }

    private var interpreter: Interpreter? = null
    private var isInitialized = false

    private var vocab: Map<String, Int> = emptyMap()
    private val unkId = 1  // [UNK] = índice 0 en vocab, pero usamos 1 como fallback seguro

    // ══════════════════════════════════════════════════
    // INICIALIZACIÓN
    // ══════════════════════════════════════════════════
    fun initialize(): Result<Unit> {
        return try {
            vocab = loadVocab()
            Log.d(TAG, "Vocab loaded: ${vocab.size} tokens")

            val modelBuffer = loadModelFromAssets()
            val options = Interpreter.Options().apply { numThreads = NUM_THREADS }
            interpreter = Interpreter(modelBuffer, options)

            // Log del shape real del tensor para diagnóstico
            val inputShape  = interpreter!!.getInputTensor(0).shape()
            val inputType   = interpreter!!.getInputTensor(0).dataType()
            val outputShape = interpreter!!.getOutputTensor(0).shape()
            Log.d(TAG, "Input  shape=${inputShape.toList()} type=$inputType")
            Log.d(TAG, "Output shape=${outputShape.toList()}")

            isInitialized = true
            Result.success(Unit)

        } catch (e: Exception) {
            Result.failure(Exception("Error inicializando NlpProcessor: ${e.message}"))
        }
    }

    // ══════════════════════════════════════════════════
    // ENTRY POINT
    // ══════════════════════════════════════════════════
    fun analyze(tokens: OcrTokens): NlpResult {
        if (!isInitialized || interpreter == null) {
            return NlpResult.error("NlpProcessor no inicializado", tokens)
        }
        if (!tokens.isValid) {
            return NlpResult.empty(tokens)
        }

        return try {
            val inputText  = buildInputText(tokens)
            val inputIds   = tokenize(inputText)
            val inputBuffer = prepareInputBuffer(inputIds)

            // Output = un solo float (probabilidad de reclutamiento)
            // Igual que el notebook: output[0][0]
            val outputBuffer = Array(1) { FloatArray(1) }
            interpreter!!.run(inputBuffer, outputBuffer)

            val score = outputBuffer[0][0]
            buildNlpResult(score, tokens)

        } catch (e: Exception) {
            Log.e(TAG, "Inference error: ${e.message}")
            NlpResult.error(e.message ?: "Error en inferencia", tokens)
        }
    }

    // ══════════════════════════════════════════════════
    // TEXTO DE ENTRADA — texto + emojis combinados
    // ══════════════════════════════════════════════════
    private fun buildInputText(tokens: OcrTokens): String {
        val emojiString = tokens.emojis.joinToString(" ")
        return "${tokens.cleanText} $emojiString".trim()
    }

    // ══════════════════════════════════════════════════
    // TOKENIZADOR — usa vocab.txt real del modelo
    // ══════════════════════════════════════════════════
    private fun tokenize(text: String): IntArray {
        val words = text.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        val ids   = IntArray(MAX_TOKENS) { 0 } // 0 = padding

        var unkCount = 0
        words.take(MAX_TOKENS).forEachIndexed { index, word ->
            val id = vocab.getOrDefault(word, -1)
            if (id == -1) {
                ids[index] = unkId
                unkCount++
            } else {
                ids[index] = id
            }
        }

        Log.d(TAG, "tokenize: words=${words.size}, unk=$unkCount / ${minOf(words.size, MAX_TOKENS)}")
        return ids
    }

    // ══════════════════════════════════════════════════
    // BUFFER — INT64 (8 bytes por token)
    // El notebook usa dtype=np.int64, no int32
    // ══════════════════════════════════════════════════
    private fun prepareInputBuffer(ids: IntArray): ByteBuffer {
        val buffer = ByteBuffer
            .allocateDirect(MAX_TOKENS * 8)  // 8 bytes = INT64
            .apply { order(ByteOrder.nativeOrder()) }
        ids.forEach { buffer.putLong(it.toLong()) }
        buffer.rewind()
        return buffer
    }

    // ══════════════════════════════════════════════════
    // INTERPRETAR SALIDA
    // score >= 0.5 → reclutamiento (igual que el notebook)
    // ══════════════════════════════════════════════════
    private fun buildNlpResult(score: Float, tokens: OcrTokens): NlpResult {
        val label = if (score >= 0.5f) "reclutamiento" else "safe"

        val alertLevel = when {
            label == "reclutamiento" && score > 0.85f -> AlertLevel.HIGH
            label == "reclutamiento" && score > 0.65f -> AlertLevel.MEDIUM
            label == "reclutamiento"                  -> AlertLevel.LOW
            else                                      -> AlertLevel.NONE
        }

        Log.d(TAG, "NLP result: label=$label score=$score alert=$alertLevel")

        return NlpResult(
            label            = label,
            riskScore        = score,
            alertLevel       = alertLevel,
            allScores        = mapOf("safe" to (1f - score), "reclutamiento" to score),
            hasRisk          = alertLevel != AlertLevel.NONE,
            packageName      = tokens.packageName,
            screenContext    = tokens.screenContext,
            ocrSource        = tokens.source,
            emojis           = tokens.emojis,
            timestamp        = tokens.timestamp,
            detectedUsername = tokens.detectedUsername
        )
    }

    // ══════════════════════════════════════════════════
    // CARGAR VOCAB
    // Línea N = ID N (0-indexado)
    // ══════════════════════════════════════════════════
    private fun loadVocab(): Map<String, Int> {
        val map = mutableMapOf<String, Int>()
        context.assets.open(VOCAB_PATH).bufferedReader().useLines { lines ->
            lines.forEachIndexed { index, token ->
                val trimmed = token.trim()
                if (trimmed.isNotEmpty()) map[trimmed] = index
            }
        }
        return map
    }

    // ══════════════════════════════════════════════════
    // CARGAR MODELO
    // ══════════════════════════════════════════════════
    private fun loadModelFromAssets(): ByteBuffer {
        val afd: AssetFileDescriptor = context.assets.openFd(MODEL_PATH)
        val channel: FileChannel = FileInputStream(afd.fileDescriptor).channel
        return channel.map(FileChannel.MapMode.READ_ONLY, afd.startOffset, afd.declaredLength)
    }

    fun close() {
        interpreter?.close()
        interpreter = null
        isInitialized = false
    }
}

// ══════════════════════════════════════════════════
// DATA CLASSES
// ══════════════════════════════════════════════════
data class NlpResult(
    val label:            String,
    val riskScore:        Float,
    val alertLevel:       AlertLevel,
    val allScores:        Map<String, Float>,
    val hasRisk:          Boolean,
    val packageName:      String,
    val screenContext:    AppAccessibilityService.ScreenContext,
    val ocrSource:        OcrSource,
    val emojis:           List<String>,
    val timestamp:        Long,
    val error:            String? = null,
    val detectedUsername: String? = null
) {
    companion object {
        fun empty(tokens: OcrTokens) = NlpResult(
            label            = "safe",
            riskScore        = 0f,
            alertLevel       = AlertLevel.NONE,
            allScores        = emptyMap(),
            hasRisk          = false,
            packageName      = tokens.packageName,
            screenContext    = tokens.screenContext,
            ocrSource        = tokens.source,
            emojis           = tokens.emojis,
            timestamp        = tokens.timestamp,
            detectedUsername = tokens.detectedUsername
        )

        fun error(message: String, tokens: OcrTokens) = NlpResult(
            label            = "ERROR",
            riskScore        = 0f,
            alertLevel       = AlertLevel.NONE,
            allScores        = emptyMap(),
            hasRisk          = false,
            packageName      = tokens.packageName,
            screenContext    = tokens.screenContext,
            ocrSource        = tokens.source,
            emojis           = tokens.emojis,
            timestamp        = tokens.timestamp,
            error            = message,
            detectedUsername = tokens.detectedUsername
        )
    }

    fun toMap(): Map<String, Any?> = mapOf(
        "label"             to label,
        "risk_score"        to riskScore,
        "alert_level"       to alertLevel.name,
        "all_scores"        to allScores,
        "has_risk"          to hasRisk,
        "package_name"      to packageName,
        "screen_context"    to screenContext.name,
        "ocr_source"        to ocrSource.name,
        "emojis"            to emojis,
        "timestamp"         to timestamp,
        "error"             to error,
        "detected_username" to detectedUsername
    )
}

enum class AlertLevel { NONE, LOW, MEDIUM, HIGH, CRITICAL }