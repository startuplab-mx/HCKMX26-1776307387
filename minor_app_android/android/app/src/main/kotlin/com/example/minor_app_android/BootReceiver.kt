// android/app/src/main/kotlin/com/minorapp/BootReceiver.kt

package com.example.minor_app_android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class BootReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_RESTART_SERVICE = "com.minorapp.RESTART_SERVICE"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            // Dispositivo encendido / reiniciado
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON" -> {
                startMonitorService(context)
            }

            // El ScreenMonitorService se destruyó y pide reiniciarse
            ACTION_RESTART_SERVICE -> {
                startMonitorService(context)
            }
        }
    }

    private fun startMonitorService(context: Context) {
        val serviceIntent = Intent(context, ScreenMonitorService::class.java).apply {
            action = ScreenMonitorService.ACTION_START
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Android 8+ requiere startForegroundService para services en background
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }
}