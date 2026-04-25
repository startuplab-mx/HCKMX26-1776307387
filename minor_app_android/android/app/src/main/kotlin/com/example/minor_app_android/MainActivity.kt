// android/app/src/main/kotlin/com/minorapp/MainActivity.kt

package com.example.minor_app_android

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.embedding.engine.FlutterEngineCache
import io.flutter.plugin.common.MethodChannel

class MainActivity : FlutterActivity() {

    private val CHANNEL_MONITOR = "com.minorapp/monitor"
    private val CHANNEL_OCR     = "com.minorapp/ocr"
    private val TAG             = "MinorMainActivity"

    // ══════════════════════════════════════════════════
    // FLUTTER ENGINE — cachear para que el
    // ScreenMonitorService lo reutilice cuando
    // Flutter no está en primer plano
    // ══════════════════════════════════════════════════
    override fun provideFlutterEngine(context: android.content.Context): FlutterEngine {
        val cached = FlutterEngineCache.getInstance().get("main_engine")
        if (cached != null) return cached

        val engine = FlutterEngine(context)
        FlutterEngineCache.getInstance().put("main_engine", engine)
        return engine
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d(TAG, "MainActivity created")

        // Arrancar el ForegroundService al abrir la app
        startMonitorService()
    }

    // ══════════════════════════════════════════════════
    // METHOD CHANNELS
    // Registrar los dos canales: monitor (servicio)
    // y ocr (resultados del AccessibilityService)
    // ══════════════════════════════════════════════════
    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        // Canal principal — control del servicio desde Flutter
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL_MONITOR)
            .setMethodCallHandler { call, result ->
                when (call.method) {

                    // Flutter pregunta el estado actual del sistema
                    "getStatus" -> {
                        Log.d(TAG, "Flutter requested monitor status")
                        result.success(mapOf(
                            "service_running"         to ScreenMonitorService.isRunning,
                            "accessibility_enabled"   to isAccessibilityEnabled(),
                            "notification_permission" to hasNotificationPermission()
                        ))
                    }

                    // Flutter pide abrir ajustes de accesibilidad
                    // El usuario activa el servicio manualmente ahí
                    "openAccessibilitySettings" -> {
                        Log.d(TAG, "Opening accessibility settings")
                        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                        startActivity(intent)
                        result.success(null)
                    }

                    // Flutter pide abrir ajustes de notificaciones
                    "openNotificationSettings" -> {
                        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                            }
                        } else {
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:$packageName")
                            }
                        }
                        startActivity(intent)
                        result.success(null)
                    }

                    // Flutter arranca el servicio manualmente
                    "startMonitoring" -> {
                        Log.d(TAG, "Flutter requested startMonitoring")
                        startMonitorService()
                        result.success(null)
                    }

                    // Flutter detiene el servicio
                    "stopMonitoring" -> {
                        stopService(Intent(this, ScreenMonitorService::class.java))
                        result.success(null)
                    }

                    else -> result.notImplemented()
                }
            }

        // Canal OCR — Flutter puede escuchar resultados en tiempo real
        // Los eventos llegan desde AppAccessibilityService → ScreenMonitorService → aquí
        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL_OCR)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    // Por ahora solo ACK — los datos llegan por invokeMethod desde Kotlin
                    "ready" -> result.success(null)
                    else    -> result.notImplemented()
                }
            }
    }

    // ══════════════════════════════════════════════════
    // HELPERS
    // ══════════════════════════════════════════════════
    private fun startMonitorService() {
        Log.d(TAG, "Starting ScreenMonitorService")
        val intent = Intent(this, ScreenMonitorService::class.java).apply {
            action = ScreenMonitorService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun isAccessibilityEnabled(): Boolean {
        val service = "${packageName}/${AppAccessibilityService::class.java.canonicalName}"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.contains(service)
    }

    private fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
        } else {
            true // Android 12 y menor no requiere permiso explícito
        }
    }
}
