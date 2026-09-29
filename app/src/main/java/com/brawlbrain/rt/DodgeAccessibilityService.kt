package com.brawlbrain.rt

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

class DodgeAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile var instance: DodgeAccessibilityService? = null
        @Volatile var screenW: Int = 1
        @Volatile var screenH: Int = 1
    }

    private val main = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        instance = this
        val dm = resources.displayMetrics
        screenW = dm.widthPixels
        screenH = dm.heightPixels
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    fun dodge(x: Float, y: Float, strength: Float) {
        val w = screenW.toFloat()
        val h = screenH.toFloat()

        val centerX = w * 0.15f
        val centerY = h * 0.80f
        val radius = minOf(w, h) * 0.115f

        val endX = (centerX + x.coerceIn(-1f, 1f) * radius * strength)
            .coerceIn(w * 0.03f, w * 0.30f)
        val endY = (centerY + y.coerceIn(-1f, 1f) * radius * strength)
            .coerceIn(h * 0.66f, h * 0.94f)

        val path = Path().apply {
            moveTo(centerX, centerY)
            lineTo(endX, endY)
        }

        val stroke = GestureDescription.StrokeDescription(
            path,
            0L,
            105L
        )

        val gesture = GestureDescription.Builder()
            .addStroke(stroke)
            .build()

        dispatchGesture(
            gesture,
            null,
            main
        )
    }
}
