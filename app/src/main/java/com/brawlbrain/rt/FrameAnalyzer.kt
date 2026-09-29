package com.brawlbrain.rt
import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

class FrameAnalyzer {
    private var lastEnemyX = 0.5f
    private var lastEnemyY = 0.5f
    private var lastT = System.nanoTime()
    fun analyze(src: Bitmap): BrainState {
        val w = src.width
        val h = src.height
        var redCount = 0
        var bestScore = 0f
        var bx = 0f
        var by = 0f
        var bright = 0
        val step = 3
        var pixels = 0
        var y = h / 10
        while (y < h * 9 / 10) {
            var x = w / 10
            while (x < w * 9 / 10) {
                val c = src.getPixel(x, y)
                val r = Color.red(c)
                val g = Color.green(c)
                val b = Color.blue(c)
                if (r > 175 && r > g * 1.25f && r > b * 1.20f) {
                    redCount++
                    val local = (r - (g + b) * 0.5f) / 255f
                    if (local > bestScore) { bestScore = local; bx = x.toFloat() / w; by = y.toFloat() / h }
                }
                if (max(r, max(g, b)) > 210) bright++
                pixels++
                x += step
            }
            y += step
        }
        val confidence = min(1f, redCount / max(1f, pixels * 0.010f))
        val t = System.nanoTime()
        val dt = max(1L, t - lastT) / 1_000_000_000f
        val vx = ((bx - lastEnemyX) / dt).coerceIn(-2f, 2f)
        val vy = ((by - lastEnemyY) / dt).coerceIn(-2f, 2f)
        lastEnemyX = bx
        lastEnemyY = by
        lastT = t
        val danger = (confidence * 0.72f +
            (1f - (hypot(bx - .5f, by - .5f) * 1.6f).coerceIn(0f,1f)) * 0.28f).coerceIn(0f, 1f)
        val openSpace = (1f - bright.toFloat() / max(1, pixels) * 1.7f).coerceIn(0f, 1f)
        return BrainState(bx, by, confidence, danger, openSpace,
            hypot(bx - .5f, by - .5f).coerceIn(0f, 1f), vx, vy, 0L)
    }
}