// android/app/src/main/kotlin/com/example/minor_app_android/ScreenMonitorService.kt

package com.example.minor_app_android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.FlutterEngineCache
import io.flutter.embedding.engine.dart.DartExecutor
import io.flutter.plugin.common.MethodChannel
import java.util.concurrent.Executors

class ScreenMonitorService : Service() {

    companion object {
        // Actions que recibe este servicio
        const val ACTION_START                   = "com.minorapp.START"
        const val ACTION_STOP                    = "com.minorapp.STOP"
        const val ACTION_ACCESSIBILITY_CONNECTED = "com.minorapp.ACCESSIBILITY_CONNECTED"
        const val ACTION_OCR_RESULT              = "com.minorapp.OCR_RESULT"

        // Notification
        private const val CHANNEL_ID   = "minor_app_monitor"
        private const val CHANNEL_NAME = "Monitor de seguridad"
        private const val NOTIF_ID     = 1001

        // MethodChannel — mismo ID que en AppAccessibilityService
        private const val CHANNEL_FLUTTER = "com.minorapp/monitor"

        // Estado global accesible para otros componentes
        var isRunning = false
            private set
    }

    // ══════════════════════════════════════════════════
    // DEPENDENCIAS INTERNAS
    // ══════════════════════════════════════════════════
    private val executor        = Executors.newSingleThreadExecutor()
    private val mainHandler     = Handler(Looper.getMainLooper())
    private var wakeLock: PowerManager.WakeLock? = null
    private var methodChannel: MethodChannel? = null
    private var flutterEngine: FlutterEngine? = null

    // Cola de resultados pendientes de enviar al backend
    // Cuando no hay red, se acumulan aquí y se drenan cuando vuelve
    private val pendingResults = ArrayDeque<OcrTokens>()
    private val MAX_PENDING    = 50

    // Estado del sistema
    private var accessibilityConnected = false
    private var sessionStartTime       = 0L

    // ══════════════════════════════════════════════════
    // CICLO DE VIDA DEL SERVICE
    // ══════════════════════════════════════════════════
    override fun onCreate() {
        super.onCreate()
        sessionStartTime = System.currentTimeMillis()
        setupNotificationChannel()
        setupFlutterEngine()
        acquireWakeLock()
        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {

            ACTION_START -> {
                startForeground(NOTIF_ID, buildNotification("Protección activa"))
            }

            ACTION_STOP -> {
                stopSelf()
            }

            // El AccessibilityService notifica que está listo
            ACTION_ACCESSIBILITY_CONNECTED -> {
                accessibilityConnected = true
                updateNotification("Monitoreando conversaciones")
                sendToFlutter("accessibility_ready", mapOf(
                    "timestamp" to System.currentTimeMillis()
                ))
            }

            // Llega un OcrTokens procesado desde el AccessibilityService
            // En este punto el NLP ya debería recibirlo — por ahora lo encolamos
            ACTION_OCR_RESULT -> {
                intent.extras?.let { extras ->
                    handleOcrResult(extras)
                }
            }
        }

        // START_STICKY — el SO reinicia el servicio si lo mata
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        isRunning = false
        releaseWakeLock()
        flutterEngine?.destroy()
        executor.shutdown()

        // Intentar reiniciarse automáticamente
        val restartIntent = Intent(this, BootReceiver::class.java).apply {
            action = BootReceiver.ACTION_RESTART_SERVICE
        }
        sendBroadcast(restartIntent)

        super.onDestroy()
    }

    // ══════════════════════════════════════════════════
    // MANEJO DE RESULTADOS OCR
    // Recibe los extras del Intent con el OcrTokens,
    // los reconstruye y los enruta al NLP cuando esté listo.
    // Por ahora los encola y notifica a Flutter.
    // ══════════════════════════════════════════════════
    private fun handleOcrResult(extras: android.os.Bundle) {
        executor.execute {
            val tokens = OcrTokens(
                cleanText     = extras.getString("clean_text", ""),
                emojis        = extras.getStringArrayList("emojis")?.toList() ?: emptyList(),
                packageName   = extras.getString("package_name", ""),
                screenContext = AppAccessibilityService.ScreenContext.valueOf(
                    extras.getString("screen_context", "UNKNOWN")
                ),
                timestamp     = extras.getLong("timestamp", System.currentTimeMillis())
            )

            // Encolar para el NLP
            enqueueForNlp(tokens)

            // Notificar a Flutter que hay actividad (sin datos sensibles)
            mainHandler.post {
                sendToFlutter("activity_detected", mapOf(
                    "package_name"   to tokens.packageName,
                    "screen_context" to tokens.screenContext.name,
                    "emoji_count"    to tokens.emojis.size,
                    "timestamp"      to tokens.timestamp
                ))
            }
        }
    }

    // ══════════════════════════════════════════════════
    // COLA PARA EL NLP
    // Cuando el modelo NLP esté integrado, aquí se llama.
    // Por ahora solo mantiene la cola con un límite.
    // ══════════════════════════════════════════════════
    private fun enqueueForNlp(tokens: OcrTokens) {
        if (pendingResults.size >= MAX_PENDING) {
            pendingResults.removeFirst() // Descartar el más antiguo
        }
        pendingResults.addLast(tokens)

        // TODO: cuando el NLP esté listo, llamar aquí:
        // NlpProcessor.analyze(tokens) { result -> handleNlpResult(result) }
    }

    // ══════════════════════════════════════════════════
    // PLACEHOLDER — recibe resultado del NLP
    // Se implementa cuando el modelo esté integrado
    // ══════════════════════════════════════════════════
    fun handleNlpResult(result: Map<String, Any?>) {
        val alertLevel = result["alert_level"] as? String ?: return

        // Actualizar notificación si hay riesgo alto
        if (alertLevel == "HIGH" || alertLevel == "CRITICAL") {
            updateNotification("⚠ Actividad sospechosa detectada")
        }

        // Notificar a Flutter con el resultado completo
        mainHandler.post {
            sendToFlutter("nlp_result", result)
        }

        // TODO: persistir en Room y enviar al backend según alertLevel
    }

    // ══════════════════════════════════════════════════
    // FLUTTER ENGINE
    // Crea un engine headless cacheado para que el
    // MethodChannel funcione aunque Flutter no esté visible
    // ══════════════════════════════════════════════════
    private fun setupFlutterEngine() {
        // Reutilizar engine si ya existe en cache
        val cached = FlutterEngineCache.getInstance().get("main_engine")
        if (cached != null) {
            flutterEngine = cached
            setupMethodChannel(cached)
            return
        }

        // Crear engine headless nuevo
        val engine = FlutterEngine(this)
        engine.dartExecutor.executeDartEntrypoint(
            DartExecutor.DartEntrypoint.createDefault()
        )
        FlutterEngineCache.getInstance().put("main_engine", engine)
        flutterEngine = engine
        setupMethodChannel(engine)
    }

    private fun setupMethodChannel(engine: FlutterEngine) {
        methodChannel = MethodChannel(engine.dartExecutor.binaryMessenger, CHANNEL_FLUTTER)

        // Escuchar llamadas desde Flutter → Kotlin
        methodChannel?.setMethodCallHandler { call, result ->
            when (call.method) {
                "startMonitoring" -> {
                    result.success(mapOf(
                        "running"                 to isRunning,
                        "accessibility_connected" to accessibilityConnected,
                        "session_start"           to sessionStartTime
                    ))
                }
                "stopMonitoring" -> {
                    stopSelf()
                    result.success(null)
                }
                "getStatus" -> {
                    result.success(mapOf(
                        "running"                 to isRunning,
                        "accessibility_connected" to accessibilityConnected,
                        "pending_results"         to pendingResults.size,
                        "session_duration_ms"     to (System.currentTimeMillis() - sessionStartTime)
                    ))
                }
                else -> result.notImplemented()
            }
        }
    }

    private fun sendToFlutter(method: String, data: Map<String, Any?>) {
        methodChannel?.invokeMethod(method, data)
    }

    // ══════════════════════════════════════════════════
    // NOTIFICACIÓN PERSISTENTE
    // Android requiere que los ForegroundServices
    // muestren una notificación visible al usuario
    // ══════════════════════════════════════════════════
    private fun setupNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW  // Sin sonido ni vibración
            ).apply {
                description       = "Servicio de monitoreo de seguridad para menores"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET // No visible en lockscreen
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(status: String): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Protección activa")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setContentIntent(pendingIntent)
            .setOngoing(true)           // No se puede deslizar para cerrar
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .build()
    }

    private fun updateNotification(status: String) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIF_ID, buildNotification(status))
    }

    // ══════════════════════════════════════════════════
    // WAKE LOCK
    // Evita que el CPU duerma durante el procesamiento
    // ══════════════════════════════════════════════════
    private fun acquireWakeLock() {
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "minorapp::MonitorWakeLock"
        ).apply {
            acquire(10 * 60 * 1000L) // Máximo 10 minutos, se renueva solo
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }
}