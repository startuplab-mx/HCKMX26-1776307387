package com.example.minor_app_android

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import android.view.Display
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.Executor

object ScreenshotOcrProcessor {
    private const val TAG = "ScreenshotOcr"

    fun captureAndProcess(
        service: AccessibilityService,
        executor: Executor,
        packageName: String,
        screenContext: AppAccessibilityService.ScreenContext,
        onResult: (OcrTokens) -> Unit
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            Log.d(TAG, "takeScreenshot requires API 30+")
            return
        }

        service.takeScreenshot(Display.DEFAULT_DISPLAY, executor, object : AccessibilityService.TakeScreenshotCallback {
            override fun onSuccess(screenshotResult: AccessibilityService.ScreenshotResult) {
                try {
                    val hardwareBuffer = screenshotResult.hardwareBuffer
                    val bitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, screenshotResult.colorSpace)
                    if (bitmap == null) {
                        Log.w(TAG, "FLAG_SECURE detected or null bitmap")
                        return
                    }
                    
                    // Copiar de hardware a software para que ML Kit pueda leerlo
                    val softwareBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                    hardwareBuffer.close()

                    CoroutineScope(Dispatchers.Default).launch {
                        val tokens = OcrProcessor.processFromImage(softwareBitmap, packageName, screenContext)
                        if (tokens.isValid) {
                            val finalTokens = tokens.copy(source = OcrSource.SCREENSHOT)
                            onResult(finalTokens)
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing screenshot: ${e.message}")
                }
            }

            override fun onFailure(errorCode: Int) {
                Log.w(TAG, "FLAG_SECURE detected or screenshot failed, errorCode=$errorCode")
            }
        })
    }
}
