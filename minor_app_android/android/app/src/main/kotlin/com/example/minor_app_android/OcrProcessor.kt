// android/app/src/main/kotlin/com/example/minor_app_android/OcrProcessor.kt

package com.example.minor_app_android

import com.example.minor_app_android.AppAccessibilityService.ExtractedContent
import com.example.minor_app_android.AppAccessibilityService.ScreenContext

object OcrProcessor {

    // ══════════════════════════════════════════════════
    // ENTRY POINT — recibe contenido crudo del
    // AccessibilityService y devuelve tokens limpios
    // para el NLP. Sin lógica de riesgo aquí.
    // ══════════════════════════════════════════════════
    fun process(content: ExtractedContent): OcrTokens {
        return OcrTokens(
            cleanText      = cleanText(content.rawText),
            emojis         = content.emojis.distinct(),
            packageName    = content.packageName,
            screenContext  = content.screenContext,
            timestamp      = content.timestamp
        )
    }

    // ══════════════════════════════════════════════════
    // LIMPIEZA DE TEXTO
    // Elimina ruido del árbol de UI — labels de botones,
    // timestamps, contadores de notificaciones, etc.
    // ══════════════════════════════════════════════════
    private fun cleanText(raw: String): String {
        return raw
            .replace(Regex("\\d{1,2}:\\d{2}(\\s?(AM|PM|am|pm))?"), "")  // timestamps
            .replace(Regex("\\b\\d+\\s?(mensaje|message|notif)\\b", RegexOption.IGNORE_CASE), "")  // contadores
            .replace(Regex("[\\r\\n]+"), " ")   // saltos de línea → espacio
            .replace(Regex("\\s{2,}"), " ")     // espacios múltiples
            .trim()
    }
}

// ══════════════════════════════════════════════════
// OUTPUT — tokens listos para el NLP
// ══════════════════════════════════════════════════
data class OcrTokens(
    val cleanText:     String,
    val emojis:        List<String>,
    val packageName:   String,
    val screenContext: ScreenContext,
    val timestamp:     Long
) {
    fun toMap(): Map<String, Any?> = mapOf(
        "clean_text"     to cleanText,
        "emojis"         to emojis,
        "package_name"   to packageName,
        "screen_context" to screenContext.name,
        "timestamp"      to timestamp
    )
}