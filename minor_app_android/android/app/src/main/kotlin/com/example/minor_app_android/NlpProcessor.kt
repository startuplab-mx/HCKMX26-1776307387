// android/app/src/main/kotlin/com/example/minor_app_android/NlpProcessor.kt

package com.example.minor_app_android

import android.content.Context
import android.content.res.AssetFileDescriptor
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
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
        private const val MODEL_PATH       = "models/nlp_model.tflite"
        private const val MAX_TOKENS       = 128        // Longitud máxima de secuencia
        private const val VOCAB_SIZE       = 10000      // Ajustar al vocab del modelo
        private const val NUM_THREADS      = 2          // Threads de CPU para inferencia
        private const val OUTPUT_CLASSES   = 5          // Número de clases de salida

        // Labels de salida — alinear con el orden del modelo
        // Verificar con el equipo NLP el orden exacto
        val OUTPUT_LABELS = arrayOf(
            "SAFE",
            "PROFILING",
            "RECRUITMENT",
            "COERCION",
            "CRITICAL"
        )
    }

    // ══════════════════════════════════════════════════
    // INTÉRPRETE TFLITE
    // ══════════════════════════════════════════════════
    private var interpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null
    private var isInitialized = false

    // ══════════════════════════════════════════════════
    // INICIALIZACIÓN — cargar modelo desde assets
    // Llamar una sola vez al arrancar el servicio
    // ══════════════════════════════════════════════════
    fun initialize(): Result<Unit> {
        return try {
            val modelBuffer = loadModelFromAssets()

            // Intentar GPU Delegate primero, fallback a CPU
            val options = Interpreter.Options().apply {
                try {
                    gpuDelegate = GpuDelegate()
                    addDelegate(gpuDelegate!!)
                } catch (e: Exception) {
                    // GPU no disponible en este device — usar CPU
                    gpuDelegate = null
                    numThreads = NUM_THREADS
                }
            }

            interpreter = Interpreter(modelBuffer, options)
            isInitialized = true
            Result.success(Unit)

        } catch (e: Exception) {
            Result.failure(Exception("Error cargando $MODEL_PATH: ${e.message}"))
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

            // 4. Buffer de salida — probabilidades por clase
            val outputBuffer = Array(1) { FloatArray(OUTPUT_CLASSES) }

            // 5. Inferencia
            interpreter!!.run(inputBuffer, outputBuffer)

            // 6. Interpretar salida
            buildNlpResult(outputBuffer[0], tokens)

        } catch (e: Exception) {
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
    // TOKENIZADOR SIMPLE
    // Convierte texto a IDs numéricos por carácter/palabra
    // IMPORTANTE: este tokenizador debe coincidir con el
    // que usó el equipo NLP al entrenar el modelo.
    // Si usaron BPE, SentencePiece o WordPiece, hay que
    // reemplazar esto con el vocab correspondiente.
    // ══════════════════════════════════════════════════
    private val vocabCache = mutableMapOf<String, Int>()

    private fun tokenize(text: String): IntArray {
        val words = text.lowercase()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

        val ids = IntArray(MAX_TOKENS) { 0 } // 0 = padding token

        words.take(MAX_TOKENS).forEachIndexed { index, word ->
            ids[index] = vocabCache.getOrPut(word) {
                // Hash simple del word como ID
                // Reemplazar con lookup real al vocab del modelo
                (word.hashCode().and(0x7FFFFFFF) % (VOCAB_SIZE - 1)) + 1
            }
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
        // Índice y score de la clase con mayor probabilidad
        val topIndex = probabilities.indices.maxByOrNull { probabilities[it] } ?: 0
        val topLabel = OUTPUT_LABELS[topIndex]
        val topScore = probabilities[topIndex]

        // Mapa completo de probabilidades por label
        val scoreMap = OUTPUT_LABELS
            .zip(probabilities.toList())
            .toMap()

        // Alert level basado en label + score
        val alertLevel = when {
            topLabel == "CRITICAL"                        -> AlertLevel.CRITICAL
            topLabel == "RECRUITMENT" && topScore > 0.75f -> AlertLevel.HIGH
            topLabel == "RECRUITMENT"                     -> AlertLevel.MEDIUM
            topLabel == "COERCION"   && topScore > 0.75f -> AlertLevel.HIGH
            topLabel == "COERCION"                        -> AlertLevel.MEDIUM
            topLabel == "PROFILING"  && topScore > 0.80f -> AlertLevel.MEDIUM
            topLabel == "PROFILING"                       -> AlertLevel.LOW
            else                                          -> AlertLevel.NONE
        }

        return NlpResult(
            label        = topLabel,
            riskScore    = topScore,
            alertLevel   = alertLevel,
            allScores    = scoreMap,
            hasRisk      = alertLevel != AlertLevel.NONE,
            packageName  = tokens.packageName,
            screenContext = tokens.screenContext,
            ocrSource    = tokens.source,
            emojis       = tokens.emojis,
            timestamp    = tokens.timestamp
        )
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
        gpuDelegate?.close()
        interpreter = null
        gpuDelegate = null
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
