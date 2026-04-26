// android/app/src/main/kotlin/com/example/minor_app_android/AppAccessibilityService.kt

package com.example.minor_app_android

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.FlutterEngineCache
import io.flutter.plugin.common.MethodChannel
import java.util.concurrent.Executors

class AppAccessibilityService : AccessibilityService() {
    companion object {
        private const val TAG = "MinorAccessibility"
    }

    // ══════════════════════════════════════════════════
    // APPS OBJETIVO — solo procesamos estas para no
    // saturar el sistema con tráfico irrelevante
    // ══════════════════════════════════════════════════
    private val targetPackages = setOf(
        "com.whatsapp",                    // WhatsApp
        "com.whatsapp.w4b",               // WhatsApp Business
        "com.instagram.android",           // Instagram
        "com.zhiliaoapp.musically",        // TikTok
        "com.ss.android.ugc.trill",        // TikTok (variante)
        "org.telegram.messenger",          // Telegram
        "org.telegram.messenger.web",      // Telegram Web
        "com.facebook.katana",             // Facebook
        "com.facebook.orca",              // Messenger
        "com.snapchat.android",           // Snapchat
        "com.twitter.android",            // Twitter/X
        "kik.android",                    // Kik
        "com.discord",                    // Discord
        "com.skype.raider",               // Skype
    )

    // ══════════════════════════════════════════════════
    // ESTADO INTERNO
    // ══════════════════════════════════════════════════
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    // Texto del último evento procesado — evita duplicados
    private var lastProcessedText: String = ""
    private var lastProcessedTimestamp: Long = 0L
    private val DEBOUNCE_MS = 2000L // No procesar el mismo texto en 2s
    private var lastScreenshotTimestamp: Long = 0L

    // App activa en primer plano
    private var currentPackage: String = ""

    // MethodChannel hacia Flutter
    private var methodChannel: MethodChannel? = null
    private val CHANNEL = "com.minorapp/ocr"
    
    private lateinit var visionProcessor: VisionProcessor

    // ══════════════════════════════════════════════════
    // CICLO DE VIDA
    // ══════════════════════════════════════════════════
    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility service connected")

        // Configurar el servicio programáticamente como respaldo al XML
        val info = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED or
                         AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                         AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED or
                         AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED or
                         AccessibilityEvent.TYPE_VIEW_FOCUSED or
                         AccessibilityEvent.TYPE_VIEW_SCROLLED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS or
                    AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                    AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS or
                    AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
            notificationTimeout = DEBOUNCE_MS
        }
        serviceInfo = info

        // Conectar con el MethodChannel de Flutter si el engine está en cache
        setupMethodChannel()

        // Notificar al ScreenMonitorService que el accessibility está activo
        val intent = Intent(this, ScreenMonitorService::class.java).apply {
            action = ScreenMonitorService.ACTION_ACCESSIBILITY_CONNECTED
        }
        startService(intent)

        // Inicializar Vision para capturas
        visionProcessor = VisionProcessor(applicationContext)
        visionProcessor.initialize()
            .onSuccess { Log.d(TAG, "Vision processor initialized") }
            .onFailure { e -> Log.e(TAG, "Vision initialization failed: ${e.message}") }
    }

    override fun onInterrupt() {
        // El sistema interrumpió el servicio — log para diagnóstico
        Log.w(TAG, "Accessibility service interrupted")
        sendToFlutter("accessibility_interrupted", mapOf("timestamp" to System.currentTimeMillis()))
    }

    override fun onDestroy() {
        Log.d(TAG, "Accessibility service destroyed")
        visionProcessor.close()
        executor.shutdown()
        super.onDestroy()
    }

    // ══════════════════════════════════════════════════
    // EVENTO PRINCIPAL — entrada de todos los eventos de UI
    // ══════════════════════════════════════════════════
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        val packageName = event.packageName?.toString() ?: return

        // Ignorar apps que no son objetivo
        if (packageName !in targetPackages) {
            if (packageName != currentPackage) {
                Log.d(TAG, "Ignoring non-target package $packageName")
            }
            currentPackage = packageName
            return
        }

        currentPackage = packageName

        // Procesar en hilo separado para no bloquear el UI thread
        executor.execute {
            processEvent(event, packageName)
        }
    }

    // ══════════════════════════════════════════════════
    // PROCESAMIENTO DEL EVENTO
    // ══════════════════════════════════════════════════
    private fun processEvent(event: AccessibilityEvent, packageName: String) {
        val rootNode = rootInActiveWindow ?: return

        try {
            // Extraer todo el texto visible en la ventana actual
            val extractedContent = extractContent(rootNode, packageName)

            if (extractedContent.rawText.isBlank() && extractedContent.emojis.isEmpty() && !extractedContent.hasMediaNode) {
                Log.d(TAG, "Empty accessibility content from $packageName")
                return
            }

            // Debounce — no reprocesar el mismo texto
            val contentHash = extractedContent.rawText.hashCode().toString()
            val now = System.currentTimeMillis()
            if (contentHash == lastProcessedText && (now - lastProcessedTimestamp) < DEBOUNCE_MS) {
                Log.d(TAG, "Debounced duplicate content from $packageName")
                return
            }
            lastProcessedText = contentHash
            lastProcessedTimestamp = now

            // Enviar al OcrProcessor para mapeo de emojis y limpieza
            val result = OcrProcessor.processFromAccessibility(extractedContent)
            Log.d(
                TAG,
                "OCR result pkg=${result.packageName}, ctx=${result.screenContext}, TEXT=\"${result.cleanText}\", EMOJIS=${result.emojis}"
            )

            // Enviar a Flutter y a ScreenMonitorService (solo si hay texto nativo válido)
            if (result.isValid) {
                mainHandler.post {
                    sendToFlutter("ocr_result", result.toMap())
                }
                forwardToMonitorService(result)
            }

            // Procesar imágenes o videos detectados con Screenshot y ML Kit
            if (extractedContent.hasMediaNode) {
                val nowTime = System.currentTimeMillis()
                val cooldown = if (extractedContent.hasVideoNode) 5000L else 2000L
                if (nowTime - lastScreenshotTimestamp > cooldown) {
                    lastScreenshotTimestamp = nowTime
                    ScreenshotOcrProcessor.captureAndProcess(
                        this,
                        executor,
                        packageName,
                        extractedContent.screenContext,
                        visionProcessor
                    ) { screenshotResult, visionResult ->
                        Log.d(TAG, "Screenshot OCR result TEXT=\"${screenshotResult.cleanText}\"")
                        if (visionResult != null && visionResult.isFlagged) {
                            Log.d(TAG, "Screenshot Vision result: ${visionResult.primaryLabel}")
                        }
                        
                        mainHandler.post {
                            sendToFlutter("ocr_result", screenshotResult.toMap())
                        }
                        forwardToMonitorService(screenshotResult, visionResult)
                    }
                }
            }

        } finally {
            rootNode.recycle()
        }
    }

    // ══════════════════════════════════════════════════
    // EXTRACCIÓN DE CONTENIDO
    // Recorre el árbol de nodos de accesibilidad y recolecta
    // texto, emojis y contexto de la pantalla actual
    // ══════════════════════════════════════════════════
    private fun extractContent(
        root: AccessibilityNodeInfo,
        packageName: String
    ): ExtractedContent {
        val textBuilder = StringBuilder()
        val emojis = mutableListOf<String>()
        val nodeTexts = mutableListOf<String>()
        val mediaFlags = BooleanArray(2) // [0] = hasMedia, [1] = hasVideo

        traverseNodes(root, textBuilder, emojis, nodeTexts, mediaFlags, depth = 0)

        // Extraer username/contacto de la persona con quien interactúa
        val detectedUsername = UsernameExtractor.extract(root, packageName)

        return ExtractedContent(
            rawText          = textBuilder.toString().trim(),
            emojis           = emojis.distinct(),
            nodeTexts        = nodeTexts,
            packageName      = packageName,
            timestamp        = System.currentTimeMillis(),
            screenContext    = detectScreenContext(packageName, nodeTexts),
            hasMediaNode     = mediaFlags[0],
            hasVideoNode     = mediaFlags[1],
            detectedUsername = detectedUsername
        )
    }

    private fun traverseNodes(
        node: AccessibilityNodeInfo?,
        textBuilder: StringBuilder,
        emojis: MutableList<String>,
        nodeTexts: MutableList<String>,
        mediaFlags: BooleanArray,
        depth: Int
    ) {
        if (node == null || depth > 20) return // Límite de profundidad para evitar loops

        // Extraer texto del nodo
        val text = node.text?.toString()
        val contentDesc = node.contentDescription?.toString()
        val className = node.className?.toString()?.lowercase() ?: ""
        
        val descLower = contentDesc?.lowercase() ?: ""
        val textLower = text?.lowercase() ?: ""

        // Detección de Media
        if (className.contains("imageview") || descLower.contains("imagen") || descLower.contains("image") || textLower.contains("imagen") || textLower.contains("image")) {
            mediaFlags[0] = true
        }
        if (className.contains("videoview") || className.contains("textureview") || descLower.contains("video") || textLower.contains("video")) {
            mediaFlags[0] = true
            mediaFlags[1] = true
        }

        listOfNotNull(text, contentDesc).forEach { raw ->
            if (raw.isNotBlank()) {
                textBuilder.append(raw).append(" ")
                nodeTexts.add(raw)

                // Extraer emojis del texto usando regex Unicode
                val foundEmojis = extractEmojis(raw)
                emojis.addAll(foundEmojis)
            }
        }

        // Recursión sobre nodos hijos
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            traverseNodes(child, textBuilder, emojis, nodeTexts, mediaFlags, depth + 1)
            child?.recycle()
        }
    }

    // ══════════════════════════════════════════════════
    // EXTRACTOR DE EMOJIS — regex Unicode completo
    // Detecta emojis simples, secuencias ZWJ y variantes
    // ══════════════════════════════════════════════════
    private val emojiRegex = Regex(
        "(?:[\\x{1F000}-\\x{1FAFF}]|[\\x{2600}-\\x{27BF}]|[\\x{2300}-\\x{23FF}]|[\\x{FE0E}\\x{FE0F}]|\\x{200D})+"
    )

    private fun extractEmojis(text: String): List<String> {
        return emojiRegex.findAll(text).map { it.value.trim() }.filter { it.isNotEmpty() }.toList()
    }

    // ══════════════════════════════════════════════════
    // CONTEXTO DE PANTALLA
    // Detecta si estamos en DM, feed, comentarios, etc.
    // Esto alimenta el platform_context del Modelo 1
    // ══════════════════════════════════════════════════
    private fun detectScreenContext(packageName: String, texts: List<String>): ScreenContext {
        val allText = texts.joinToString(" ").lowercase()

        return when {
            // Indicadores de conversación privada
            allText.contains("escribe un mensaje") ||
            allText.contains("message") ||
            allText.contains("reply") ||
            allText.contains("enviar")             -> ScreenContext.DIRECT_MESSAGE

            // Indicadores de comentarios
            allText.contains("comentar") ||
            allText.contains("comment") ||
            allText.contains("responder")          -> ScreenContext.COMMENTS

            // Indicadores de perfil
            allText.contains("seguir") ||
            allText.contains("follow") ||
            allText.contains("seguidores")         -> ScreenContext.PROFILE

            // Default: feed o contenido
            else                                   -> ScreenContext.FEED
        }
    }

    // ══════════════════════════════════════════════════
    // COMUNICACIÓN
    // ══════════════════════════════════════════════════
    private fun setupMethodChannel() {
        val engine = FlutterEngineCache.getInstance().get("main_engine")
        engine?.let {
            methodChannel = MethodChannel(it.dartExecutor.binaryMessenger, CHANNEL)
        }
    }

    private fun sendToFlutter(method: String, data: Map<String, Any?>) {
        Log.d(TAG, "Sending Flutter event $method")
        methodChannel?.invokeMethod(method, data)
    }

    private fun forwardToMonitorService(result: OcrTokens, visionResult: VisionResult? = null) {
        val intent = Intent(this, ScreenMonitorService::class.java).apply {
            action = ScreenMonitorService.ACTION_OCR_RESULT
            putExtra("clean_text", result.cleanText)
            putStringArrayListExtra("emojis", ArrayList(result.emojis))
            putExtra("package_name", result.packageName)
            putExtra("screen_context", result.screenContext.name)
            putExtra("timestamp", result.timestamp)
            putExtra("ocr_source", result.source.name)
            putExtra("detected_username", result.detectedUsername)
            
            visionResult?.let {
                putExtra("vision_label", it.primaryLabel)
                putExtra("vision_flagged", it.isFlagged)
            }
        }
        startService(intent)
    }

    // ══════════════════════════════════════════════════
    // DATA CLASSES
    // ══════════════════════════════════════════════════
    data class ExtractedContent(
        val rawText: String,
        val emojis: List<String>,
        val nodeTexts: List<String>,
        val packageName: String,
        val timestamp: Long,
        val screenContext: ScreenContext,
        val hasMediaNode: Boolean = false,
        val hasVideoNode: Boolean = false,
        val detectedUsername: String? = null
    )

    enum class ScreenContext {
        DIRECT_MESSAGE, COMMENTS, PROFILE, FEED, UNKNOWN
    }
}
