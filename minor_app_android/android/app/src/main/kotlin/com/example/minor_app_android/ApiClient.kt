package com.example.minor_app_android

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Cliente HTTP nativo para comunicarse con el backend.
 * Usa HttpURLConnection para no agregar dependencias externas.
 *
 * Diseñado para correr desde el executor del ScreenMonitorService
 * (nunca en el main thread).
 */
object ApiClient {

    private const val TAG = "MinorApi"
    private const val PREFS_NAME = "minor_app_prefs"
    private const val KEY_MINOR_ID = "minor_user_id"
    private const val KEY_DEVICE_ID = "device_id"

    // URL del backend — IP local de la máquina de desarrollo
    // Cambiar si cambia la red
    private var baseUrl = "http://192.168.110.131:3000"

    private var minorId: String? = null
    private var deviceId: String? = null

    // ══════════════════════════════════════════════════
    // INICIALIZACIÓN
    // ══════════════════════════════════════════════════

    /**
     * Inicializar el ApiClient con el contexto de la app.
     * Lee el minorId guardado o auto-registra el dispositivo.
     *
     * @param context Application context
     * @param backendUrl URL del backend (ej: "http://192.168.1.100:3000")
     */
    fun initialize(context: Context, backendUrl: String? = null) {
        backendUrl?.let { baseUrl = it }

        val prefs = getPrefs(context)
        deviceId = prefs.getString(KEY_DEVICE_ID, null)

        if (deviceId == null) {
            deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
            prefs.edit().putString(KEY_DEVICE_ID, deviceId).apply()
        }

        minorId = prefs.getString(KEY_MINOR_ID, null)

        if (minorId != null) {
            Log.d(TAG, "MinorId loaded from prefs: $minorId")
        } else {
            Log.d(TAG, "No minorId found, will auto-register on first use")
        }
    }

    /**
     * Auto-registrar el dispositivo como menor en el backend.
     * Idempotente: si ya existe, devuelve el existente.
     * Debe llamarse desde un hilo de background.
     */
    fun autoRegister(context: Context): Result<String> {
        if (minorId != null) {
            return Result.success(minorId!!)
        }

        return try {
            val body = JSONObject().apply {
                put("deviceId", deviceId)
                put("name", "Menor-${deviceId?.take(6) ?: "unknown"}")
            }

            val response = post("/users/minor/auto-register", body)

            if (response.has("user")) {
                val userId = response.getJSONObject("user").getString("id")
                minorId = userId
                getPrefs(context).edit().putString(KEY_MINOR_ID, userId).apply()
                Log.d(TAG, "Auto-registered minor: $userId (new=${response.optBoolean("registered", true)})")
                Result.success(userId)
            } else {
                Result.failure(Exception("Response sin campo 'user': $response"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Auto-register failed: ${e.message}")
            Result.failure(e)
        }
    }

    // ══════════════════════════════════════════════════
    // SUBIR EVENTO DE IA
    // ══════════════════════════════════════════════════

    fun postAiEvent(
        context: Context,
        source: String,          // "NLP", "VISION", "OCR_EMOJI"
        platform: String,        // "whatsapp", "instagram", etc.
        riskType: String,        // "reclutamiento", "armas", etc.
        riskLevel: String,       // "LOW", "MEDIUM", "HIGH", "CRITICAL"
        summary: String,
        rawText: String? = null,
        emojiTags: List<String> = emptyList(),
        score: Float? = null,
        visionLabel: String? = null,
        visionObjects: List<String> = emptyList(),
        detectedUser: String? = null,
        screenContext: String? = null
    ): Result<String> {
        // Asegurar que tenemos minorId
        val id = minorId ?: autoRegister(context).getOrNull()
        if (id == null) {
            return Result.failure(Exception("No se pudo obtener minorId"))
        }

        return try {
            val body = JSONObject().apply {
                put("minorId", id)
                put("source", source)
                put("platform", platform)
                put("riskType", riskType)
                put("riskLevel", riskLevel)
                put("summary", summary)
                put("rawText", rawText ?: JSONObject.NULL)
                put("emojiTags", JSONArray(emojiTags))
                put("score", score?.toDouble() ?: JSONObject.NULL)
                put("visionLabel", visionLabel ?: JSONObject.NULL)
                put("visionObjects", JSONArray(visionObjects))
                put("detectedUser", detectedUser ?: JSONObject.NULL)
                put("screenContext", screenContext ?: JSONObject.NULL)
            }

            val response = post("/ai-events", body)
            val eventId = response.getString("id")
            Log.d(TAG, "AI Event uploaded: $eventId (risk=$riskLevel, type=$riskType)")
            Result.success(eventId)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to upload AI event: ${e.message}")
            Result.failure(e)
        }
    }

    // ══════════════════════════════════════════════════
    // HTTP HELPERS
    // ══════════════════════════════════════════════════

    private fun post(path: String, body: JSONObject): JSONObject {
        val url = URL("$baseUrl$path")
        val conn = url.openConnection() as HttpURLConnection

        return try {
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8")
            conn.doOutput = true
            conn.connectTimeout = 5000
            conn.readTimeout = 5000

            OutputStreamWriter(conn.outputStream, "UTF-8").use { writer ->
                writer.write(body.toString())
                writer.flush()
            }

            val responseCode = conn.responseCode
            val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
            val responseText = BufferedReader(InputStreamReader(stream, "UTF-8")).use { it.readText() }

            if (responseCode !in 200..299) {
                throw Exception("HTTP $responseCode: $responseText")
            }

            JSONObject(responseText)

        } finally {
            conn.disconnect()
        }
    }

    // ══════════════════════════════════════════════════
    // UTILIDADES
    // ══════════════════════════════════════════════════

    fun getMinorId(): String? = minorId

    fun isRegistered(): Boolean = minorId != null

    /**
     * Mapea packageName a nombre de plataforma legible.
     */
    fun mapPackageToPlatform(packageName: String): String = when (packageName) {
        "com.whatsapp", "com.whatsapp.w4b" -> "whatsapp"
        "com.instagram.android"            -> "instagram"
        "com.zhiliaoapp.musically",
        "com.ss.android.ugc.trill"         -> "tiktok"
        "org.telegram.messenger",
        "org.telegram.messenger.web"       -> "telegram"
        "com.facebook.katana"              -> "facebook"
        "com.facebook.orca"                -> "messenger"
        "com.snapchat.android"             -> "snapchat"
        "com.twitter.android"              -> "twitter"
        "kik.android"                      -> "kik"
        "com.discord"                      -> "discord"
        "com.skype.raider"                 -> "skype"
        else                               -> packageName
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}
