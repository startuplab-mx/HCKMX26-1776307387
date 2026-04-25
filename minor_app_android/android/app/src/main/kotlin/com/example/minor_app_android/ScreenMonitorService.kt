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
import android.util.Log
import androidx.core.app.NotificationCompat
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.FlutterEngineCache
import io.flutter.embedding.engine.dart.DartExecutor
import io.flutter.plugin.common.MethodChannel
import java.util.concurrent.Executors

class ScreenMonitorService : Service() {

    companion object {
        private const val TAG = "MinorMonitor"

        const val ACTION_START                   = "com.minorapp.START"
        const val ACTION_STOP                    = "com.minorapp.STOP"
        const val ACTION_ACCESSIBILITY_CONNECTED = "com.minorapp.ACCESSIBILITY_CONNECTED"
        const val ACTION_OCR_RESULT              = "com.minorapp.OCR_RESULT"

        private const val CHANNEL_ID     = "minor_app_monitor"
        private const val CHANNEL_NAME   = "Monitor de seguridad"
        private const val NOTIF_ID       = 1001
        private const val CHANNEL_FLUTTER = "com.minorapp/monitor"

        var isRunning = false
            private set
    }

    // ══════════════════════════════════════════════════
    // DEPENDENCIAS
    // ══════════════════════════════════════════════════
    private val executor    = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var wakeLock:      PowerManager.WakeLock? = null
    private var methodChannel: MethodChannel?         = null
    private var flutterEngine: FlutterEngine?         = null

    // NLP — inicializado en onCreate
    private lateinit var nlpProcessor: NlpProcessor

    // Cola de tokens pendientes — máx 50 cuando no hay red
    private val pendingResults = ArrayDeque<OcrTokens>()
    private val MAX_PENDING    = 50

    private var accessibilityConnected = false
    private var sessionStartTime       = 0L

    // ══════════════════════════════════════════════════
    // CICLO DE VIDA
    // ══════════════════════════════════════════════════
    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "ScreenMonitorService created")
        sessionStartTime = System.currentTimeMillis()

        setupNotificationChannel()
        setupFlutterEngine()
        acquireWakeLock()

        // Inicializar NLP — intentar GPU, fallback a CPU automático
        nlpProcessor = NlpProcessor(applicationContext)
        nlpProcessor.initialize()
            .onSuccess {
                Log.d(TAG, "NLP processor ready")
                sendToFlutter("nlp_ready", mapOf(
                    "timestamp" to System.currentTimeMillis()
                ))
            }
            .onFailure { e ->
                Log.e(TAG, "NLP initialization failed", e)
                sendToFlutter("nlp_init_error", mapOf(
                    "error" to (e.message ?: "Error desconocido")
                ))
            }

        isRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand action=${intent?.action}")
        when (intent?.action) {
            ACTION_START -> {
                startForeground(NOTIF_ID, buildNotification("Protección activa"))
            }
            ACTION_STOP -> {
                stopSelf()
            }
            ACTION_ACCESSIBILITY_CONNECTED -> {
                accessibilityConnected = true
                updateNotification("Monitoreando conversaciones")
                sendToFlutter("accessibility_ready", mapOf(
                    "timestamp" to System.currentTimeMillis()
                ))
            }
            ACTION_OCR_RESULT -> {
                intent.extras?.let { handleOcrResult(it) }
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "ScreenMonitorService destroyed")
        isRunning = false

        // Liberar recursos en orden
        nlpProcessor.close()
        OcrProcessor.close()
        releaseWakeLock()
        flutterEngine?.destroy()
        executor.shutdown()

        // Pedir reinicio al BootReceiver
        sendBroadcast(Intent(this, BootReceiver::class.java).apply {
            action = BootReceiver.ACTION_RESTART_SERVICE
        })

        super.onDestroy()
    }

    // ══════════════════════════════════════════════════
    // PIPELINE OCR → NLP
    // ══════════════════════════════════════════════════
    private fun handleOcrResult(extras: android.os.Bundle) {
        executor.execute {
            // Reconstruir OcrTokens desde el Intent
            val tokens = OcrTokens(
                cleanText     = extras.getString("clean_text", ""),
                emojis        = extras.getStringArrayList("emojis")?.toList() ?: emptyList(),
                source        = OcrSource.valueOf(
                    extras.getString("ocr_source", "ACCESSIBILITY")
                ),
                packageName   = extras.getString("package_name", ""),
                screenContext = AppAccessibilityService.ScreenContext.valueOf(
                    extras.getString("screen_context", "UNKNOWN")
                ),
                timestamp     = extras.getLong("timestamp", System.currentTimeMillis())
            )
            Log.d(
                TAG,
                "Received OCR tokens package=${tokens.packageName}, context=${tokens.screenContext}, source=${tokens.source}, textLength=${tokens.cleanText.length}, emojis=${tokens.emojis.size}"
            )

            // Notificar a Flutter que hay actividad (sin datos sensibles)
            mainHandler.post {
                sendToFlutter("activity_detected", mapOf(
                    "package_name"   to tokens.packageName,
                    "screen_context" to tokens.screenContext.name,
                    "emoji_count"    to tokens.emojis.size,
                    "timestamp"      to tokens.timestamp
                ))
            }

            // Pasar al NLP directamente
            processWithNlp(tokens)
        }
    }

    private fun processWithNlp(tokens: OcrTokens) {
        // Si el texto está vacío y no hay emojis, no vale la pena analizar
        if (tokens.cleanText.isBlank() && tokens.emojis.isEmpty()) return

        val result = nlpProcessor.analyze(tokens)
        Log.d(
            TAG,
            "NLP analyzed label=${result.label}, risk=${result.riskScore}, alert=${result.alertLevel}, hasRisk=${result.hasRisk}, error=${result.error}"
        )

        when {
            result.error != null -> {
                // Error en inferencia — encolar para reintentar
                enqueuePending(tokens)
            }

            result.hasRisk -> {
                handleNlpResult(result)
            }

            // Sin riesgo — no hacer nada, no saturar Flutter ni DB
        }
    }

    private fun handleNlpResult(result: NlpResult) {
        Log.d(TAG, "Handling NLP risk result ${result.toMap()}")
        // Actualizar notificación según nivel
        when (result.alertLevel) {
            AlertLevel.CRITICAL -> updateNotification("Alerta crítica detectada")
            AlertLevel.HIGH     -> updateNotification("Actividad sospechosa detectada")
            else                -> { /* mantener notificación actual */ }
        }

        // Enviar a Flutter para mostrar en UI
        mainHandler.post {
            sendToFlutter("nlp_result", result.toMap())
        }

        // TODO: persistir en Room
        // TODO: si alertLevel >= HIGH, enviar al backend
    }

    // ══════════════════════════════════════════════════
    // COLA DE PENDIENTES
    // Para cuando el NLP falla o no hay red
    // ══════════════════════════════════════════════════
    private fun enqueuePending(tokens: OcrTokens) {
        if (pendingResults.size >= MAX_PENDING) {
            pendingResults.removeFirst()
        }
        pendingResults.addLast(tokens)
    }

    // Drenar la cola — llamar cuando el NLP se recupere o vuelva la red
    fun drainPendingQueue() {
        executor.execute {
            while (pendingResults.isNotEmpty()) {
                val tokens = pendingResults.removeFirst()
                processWithNlp(tokens)
            }
        }
    }

    // ══════════════════════════════════════════════════
    // FLUTTER ENGINE + METHOD CHANNEL
    // ══════════════════════════════════════════════════
    private fun setupFlutterEngine() {
        val cached = FlutterEngineCache.getInstance().get("main_engine")
        if (cached != null) {
            flutterEngine = cached
            setupMethodChannel(cached)
            return
        }

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
        methodChannel?.setMethodCallHandler { call, result ->
            when (call.method) {
                "startMonitoring" -> result.success(mapOf(
                    "running"                 to isRunning,
                    "accessibility_connected" to accessibilityConnected,
                    "session_start"           to sessionStartTime
                ))
                "stopMonitoring"  -> { stopSelf(); result.success(null) }
                "getStatus"       -> result.success(mapOf(
                    "running"                 to isRunning,
                    "accessibility_connected" to accessibilityConnected,
                    "pending_results"         to pendingResults.size,
                    "session_duration_ms"     to (System.currentTimeMillis() - sessionStartTime)
                ))
                "drainQueue"      -> { drainPendingQueue(); result.success(null) }
                else              -> result.notImplemented()
            }
        }
    }

    private fun sendToFlutter(method: String, data: Map<String, Any?>) {
        Log.d(TAG, "Sending Flutter event $method")
        methodChannel?.invokeMethod(method, data)
    }

    // ══════════════════════════════════════════════════
    // NOTIFICACIÓN
    // ══════════════════════════════════════════════════
    private fun setupNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description          = "Servicio de monitoreo de seguridad para menores"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }
            getSystemService(NotificationManager::class.java)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(status: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Protección activa")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_lock_silent_mode_off)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .build()
    }

    private fun updateNotification(status: String) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIF_ID, buildNotification(status))
    }

    // ══════════════════════════════════════════════════
    // WAKE LOCK
    // ══════════════════════════════════════════════════
    private fun acquireWakeLock() {
        wakeLock = (getSystemService(POWER_SERVICE) as PowerManager)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "minorapp::MonitorWakeLock")
            .apply { acquire(10 * 60 * 1000L) }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }
}
