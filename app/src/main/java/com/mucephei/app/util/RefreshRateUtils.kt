package com.mucephei.app.util

import android.content.Context
import android.os.Build
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

/**
 * CompositionLocal providing the highest detected refresh rate of the current display.
 * Defaults to 60f if not yet detected or unavailable.
 */
val LocalHighestRefreshRate = staticCompositionLocalOf { 60f }

/**
 * Detects the highest supported refresh rate of the current display.
 */
@Composable
fun rememberHighestRefreshRate(): Float {
    val context = LocalContext.current
    return remember(context) {
        val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val display = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                context.display ?: windowManager.defaultDisplay
            } catch (_: Exception) {
                windowManager.defaultDisplay
            }
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay
        }
        
        val modes = display?.supportedModes
        modes?.maxByOrNull { it.refreshRate }?.refreshRate ?: 60f
    }
}

/**
 * Modifier that signals the system to prefer the highest available refresh rate when this 
 * component is active. On Android 15+, it uses the [View.setRequestedFrameRate] API.
 */
fun Modifier.preferredHighestRefreshRate(): Modifier = composed {
    val view = LocalView.current
    val highestRate = LocalHighestRefreshRate.current
    
    LaunchedEffect(highestRate) {
        if (highestRate > 60f) {
            if (Build.VERSION.SDK_INT >= 35) { // Android 15 (API 35)
                try {
                    val setRequestedFrameRateMethod = View::class.java.getMethod("setRequestedFrameRate", Float::class.java)
                    setRequestedFrameRateMethod.invoke(view, highestRate)
                } catch (_: Exception) {
                    // Ignore if not supported or failed
                }
            }
        }
    }
    this
}
