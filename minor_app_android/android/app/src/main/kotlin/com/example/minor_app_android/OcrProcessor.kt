// android/app/src/main/kotlin/com/example/minor_app_android/OcrProcessor.kt

package com.example.minor_app_android

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await

object OcrProcessor {

    // ML Kit recognizer — reutilizar la misma instancia
    // es más eficiente que crear una nueva por cada imagen
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    // ══════════════════════════════════════════════════
    // ENTRADA 1 — Texto del árbol de accesibilidad
    // Llamado desde AppAccessibilityService
    // ══════════════════════════════════════════════════
    fun processFromAccessibility(content: AppAccessibilityService.ExtractedContent): OcrTokens {
        return OcrTokens(
            cleanText     = cleanText(content.rawText),
            emojis        = content.emojis.distinct(),
            source        = OcrSource.ACCESSIBILITY,
            packageName   = content.packageName,
            screenContext = content.screenContext,
            timestamp     = content.timestamp
        )
    }

    // ══════════════════════════════════════════════════
    // ENTRADA 2 — Imagen recibida en DM o feed
    // ML Kit extrae texto e imágis dentro del bitmap
    // Llamado como suspend function desde una coroutine
    // ══════════════════════════════════════════════════
    suspend fun processFromImage(
        bitmap: Bitmap,
        packageName: String,
        screenContext: AppAccessibilityService.ScreenContext
    ): OcrTokens {
        val image = InputImage.fromBitmap(bitmap, 0)

        return try {
            val result = recognizer.process(image).await()

            // Unir todos los bloques de texto detectados
            val fullText = result.textBlocks
                .joinToString(" ") { block ->
                    block.lines.joinToString(" ") { it.text }
                }

            val cleaned = cleanText(fullText)
            val emojis  = extractEmojis(cleaned)

            OcrTokens(
                cleanText     = cleaned,
                emojis        = emojis,
                source        = OcrSource.ML_KIT,
                packageName   = packageName,
                screenContext = screenContext,
                timestamp     = System.currentTimeMillis(),
                confidence    = averageConfidence(result.textBlocks)
            )

        } catch (e: Exception) {
            // Si ML Kit falla, devolver tokens vacíos para no romper el pipeline
            OcrTokens(
                cleanText     = "",
                emojis        = emptyList(),
                source        = OcrSource.ML_KIT,
                packageName   = packageName,
                screenContext = screenContext,
                timestamp     = System.currentTimeMillis(),
                error         = e.message
            )
        }
    }

    // ══════════════════════════════════════════════════
    // MERGE — combina resultado de accesibilidad + imagen
    // cuando ambas fuentes están disponibles para el
    // mismo evento de pantalla
    // ══════════════════════════════════════════════════
    fun merge(
        accessibilityTokens: OcrTokens,
        imageTokens: OcrTokens
    ): OcrTokens {
        val combinedText   = "${accessibilityTokens.cleanText} ${imageTokens.cleanText}".trim()
        val combinedEmojis = (accessibilityTokens.emojis + imageTokens.emojis).distinct()

        return OcrTokens(
            cleanText     = cleanText(combinedText),
            emojis        = combinedEmojis,
            source        = OcrSource.MERGED,
            packageName   = accessibilityTokens.packageName,
            screenContext = accessibilityTokens.screenContext,
            timestamp     = accessibilityTokens.timestamp
        )
    }

    // ══════════════════════════════════════════════════
    // LIMPIEZA DE TEXTO
    // ══════════════════════════════════════════════════
    private fun cleanText(raw: String): String {
        return raw
            .replace(Regex("\\d{1,2}:\\d{2}(\\s?(AM|PM|am|pm))?"), "")
            .replace(Regex("\\b\\d+\\s?(mensaje|message|notif)\\b", RegexOption.IGNORE_CASE), "")
            .replace(Regex("[\\r\\n]+"), " ")
            .replace(Regex("\\s{2,}"), " ")
            .trim()
    }

    // ══════════════════════════════════════════════════
    // EXTRACTOR DE EMOJIS
    // ══════════════════════════════════════════════════
    private val emojiRegex = Regex(
        "[\\uD83C-\\uDBFF\\uDC00-\\uDFFF]+" +
        "|[\\u2600-\\u27FF]" +
        "|[\\u2300-\\u23FF]"
    )

    private fun extractEmojis(text: String): List<String> {
        return emojiRegex.findAll(text)
            .map { it.value.trim() }
            .filter { it.isNotEmpty() }
            .toList()
    }

    // ══════════════════════════════════════════════════
    // CONFIANZA PROMEDIO DE ML KIT
    // ══════════════════════════════════════════════════
    private fun averageConfidence(
        blocks: List<com.google.mlkit.vision.text.Text.TextBlock>
    ): Float {
        val allElements = blocks.flatMap { block ->
            block.lines.flatMap { it.elements }
        }
        if (allElements.isEmpty()) return 0f
        return allElements.mapNotNull { it.confidence }.average().toFloat()
    }

    // ══════════════════════════════════════════════════
    // LIBERAR RECURSOS — llamar en onDestroy del servicio
    // ══════════════════════════════════════════════════
    fun close() {
        recognizer.close()
    }
}

// ══════════════════════════════════════════════════
// FUENTE DEL OCR — para que el NLP sepa el origen
// y pueda ponderar diferente (imagen vs texto nativo)
// ══════════════════════════════════════════════════
enum class OcrSource {
    ACCESSIBILITY,  // Árbol de UI — texto nativo
    ML_KIT,         // Imagen procesada con ML Kit
    MERGED          // Combinación de ambos
}

// ══════════════════════════════════════════════════
// OUTPUT — tokens listos para el NLP
// ══════════════════════════════════════════════════
data class OcrTokens(
    val cleanText:     String,
    val emojis:        List<String>,
    val source:        OcrSource,
    val packageName:   String,
    val screenContext: AppAccessibilityService.ScreenContext,
    val timestamp:     Long,
    val confidence:    Float? = null,   // Solo presente si source == ML_KIT
    val error:         String? = null   // Solo presente si hubo fallo
) {
    val isValid: Boolean get() = error == null && cleanText.isNotBlank()

    fun toMap(): Map<String, Any?> = mapOf(
        "clean_text"     to cleanText,
        "emojis"         to emojis,
        "source"         to source.name,
        "package_name"   to packageName,
        "screen_context" to screenContext.name,
        "timestamp"      to timestamp,
        "confidence"     to confidence,
        "is_valid"       to isValid
    )
}
