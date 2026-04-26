// android/app/src/main/kotlin/com/example/minor_app_android/OpenRouterClient.kt

package com.example.minor_app_android

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cliente para OpenRouter API — extrae @username de texto OCR de TikTok
 * usando Gemini Flash 1.5 como modelo.
 *
 * Usa HttpURLConnection nativo (misma estrategia que ApiClient)
 * para no agregar dependencias externas como OkHttp.
 *
 * ⚠ Debe ejecutarse en hilo de background (executor del ScreenMonitorService).
 */
object OpenRouterClient {

    private const val TAG = "OpenRouter"
    private const val API_URL = "https://openrouter.ai/api/v1/chat/completions"
    private const val MODEL = "google/gemini-3-flash-preview"
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 15_000

    /**
     * Extrae el @username del creador de un video de TikTok
     * a partir del texto OCR capturado de la pantalla.
     *
     * @param ocrText Texto extraído de la pantalla de TikTok
     * @return El @username encontrado, o null si no se pudo determinar
     */
    fun extractUsername(ocrText: String): String? {
        val apiKey = BuildConfig.OPENROUTER_API_KEY
        if (apiKey.isBlank()) {
            Log.w(TAG, "OPENROUTER_API_KEY no configurada, saltando extracción de username")
            return null
        }

        val prompt = """
            Eres un experto en extraer metadatos de capturas de pantalla.
            Analiza el siguiente texto crudo de OCR y devuelve exclusivamente el username del creador.

            REGLAS DE EXTRACCIÓN:
            1. Identifica el nombre del perfil. Suele estar arriba de la descripción o cerca de "hace X día(s)".
            2. El OCR a veces omite el '@'. Si identificas el nombre del usuario pero no tiene el '@', DEBES agregarlo al inicio.
            3. Ignora texto de sistema: "Buscar", "Agregar comentario", "Contiene: sonido original", "hace 3 día(s)".
            4. Ignora hashtags (#) y menciones dentro del texto de la descripción.
            5. Si el texto es ambiguo, prioriza la palabra que aparece justo antes de la descripción del video o justo después de "Agregar comentario".
            6. Responde ÚNICAMENTE con el @username (ejemplo: @usuario123). 
            7. Si es absolutamente imposible identificarlo, responde: null
            Texto OCR: $ocrText
        """.trimIndent()

        val messagesArray = JSONArray().put(
            JSONObject().apply {
                put("role", "user")
                put("content", prompt)
            }
        )

        val requestBody = JSONObject().apply {
            put("model", MODEL)
            put("messages", messagesArray)
        }

        return try {
            val url = URL(API_URL)
            val conn = url.openConnection() as HttpURLConnection

            try {
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
                conn.setRequestProperty("Authorization", "Bearer $apiKey")
                conn.doOutput = true
                conn.connectTimeout = CONNECT_TIMEOUT_MS
                conn.readTimeout = READ_TIMEOUT_MS

                OutputStreamWriter(conn.outputStream, "UTF-8").use { writer ->
                    writer.write(requestBody.toString())
                    writer.flush()
                }

                val responseCode = conn.responseCode
                val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
                val responseText = BufferedReader(InputStreamReader(stream, "UTF-8")).use { it.readText() }

                if (responseCode !in 200..299) {
                    Log.e(TAG, "HTTP $responseCode: $responseText")
                    return null
                }

                val json = JSONObject(responseText)
                val result = json
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
                    .trim()

                Log.d(TAG, "OpenRouter response: $result")

                if (result.lowercase() == "null" || result.isBlank()) null else result

            } finally {
                conn.disconnect()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error extracting username: ${e.message}")
            null
        }
    }
}
