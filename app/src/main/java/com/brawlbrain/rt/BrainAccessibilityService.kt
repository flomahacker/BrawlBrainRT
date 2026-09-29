package com.brawlbrain.rt
import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import kotlin.math.min

class BrainAccessibilityService : AccessibilityService() {
    companion object {
        @Volatile var instance: BrainAccessibilityService? = null
        @Volatile var autoEnabled = false
    }
    private val main = Handler(Looper.getMainLooper())
    override fun onServiceConnected() { instance = this }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit
    override fun onDestroy() { instance = null; super.onDestroy() }
    fun apply(action: Action, screenW: Int, screenH: Int) {
        if (!autoEnabled) return
        val cx = screenW * 0.155f
        val cy = screenH * 0.79f
        val radius = min(screenW, screenH) * 0.12f
        swipe(cx, cy, cx + action.moveX * radius, cy + action.moveY * radius, 90)
        if (action.attack) {
            swipe(screenW * 0.82f, screenH * 0.78f, screenW * action.aimX, screenH * action.aimY, 75)
        }
    }
    private fun swipe(x1: Float, y1: Float, x2: Float, y2: Float, duration: Long) {
        val p = Path().apply { moveTo(x1, y1); lineTo(x2, y2) }
        val stroke = GestureDescription.StrokeDescription(p, 0, duration)
        val g = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(g, null, main)
    }
}