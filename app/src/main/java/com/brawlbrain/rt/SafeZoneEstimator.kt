package com.brawlbrain.rt

import android.media.Image
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.max

data class SafeZoneState(
    val detected: Boolean = false,
    val x: Float = 0f,
    val y: Float = 0f,
    val score: Float = 0f
)

class SafeZoneEstimator {
    private val cols = 24
    private val rows = 14

    fun analyze(
        image: Image,
        captureW: Int,
        captureH: Int,
        config: BrainConfig,
        enabled: Boolean
    ): SafeZoneState {
        if (!enabled) return SafeZoneState()

        val plane = image.planes[0]
        val buffer: ByteBuffer = plane.buffer.duplicate()
        val stride = plane.pixelStride
        val rowStride = plane.rowStride

        var left = 0f
        var right = 0f
        var top = 0f
        var bottom = 0f
        var center = 0f

        for (gy in 1 until rows - 1) {
            for (gx in 1 until cols - 1) {
                val x = gx / (cols - 1f)
                val y = gy / (rows - 1f)
                val score = cloudScore(buffer, stride, rowStride, captureW, captureH, x, y)

                if (gx <= 3) left += score
                if (gx >= cols - 4) right += score
                if (gy <= 2) top += score
                if (gy >= rows - 3) bottom += score
                if (gx in cols / 3..cols * 2 / 3 && gy in rows / 3..rows * 2 / 3) {
                    center += score
                }
            }
        }

        val leftAvg = left / ((rows - 2) * 3f).coerceAtLeast(1f)
        val rightAvg = right / ((rows - 2) * 3f).coerceAtLeast(1f)
        val topAvg = top / ((cols - 2) * 2f).coerceAtLeast(1f)
        val bottomAvg = bottom / ((cols - 2) * 2f).coerceAtLeast(1f)
        val centerAvg = center / 45f

        val edgeAvg = (leftAvg + rightAvg + topAvg + bottomAvg) * 0.25f
        val delta = edgeAvg - centerAvg

        if (delta < config.gasEnterEdgeDelta) {
            return SafeZoneState(false, 0f, 0f, delta.coerceAtLeast(0f))
        }

        val dx = (leftAvg - rightAvg).coerceIn(-1f, 1f)
        val dy = (topAvg - bottomAvg).coerceIn(-1f, 1f)
        val magnitude = max(abs(dx), abs(dy)).coerceIn(0f, 1f)

        if (magnitude < config.gasDirectionMin) {
            return SafeZoneState(true, 0f, 0f, delta.coerceIn(0f, 1f))
        }

        return SafeZoneState(
            detected = true,
            x = dx / magnitude,
            y = dy / magnitude,
            score = delta.coerceIn(0f, 1f)
        )
    }

    private fun cloudScore(
        buffer: ByteBuffer,
        pixelStride: Int,
        rowStride: Int,
        width: Int,
        height: Int,
        x: Float,
        y: Float
    ): Float {
        val sx = (x * (width - 1)).toInt().coerceIn(0, width - 1)
        val sy = (y * (height - 1)).toInt().coerceIn(0, height - 1)
        val offset = sy * rowStride + sx * pixelStride
        if (offset < 0 || offset + 2 >= buffer.limit()) return 0f

        val r = (buffer.get(offset).toInt() and 0xFF) / 255f
        val g = (buffer.get(offset + 1).toInt() and 0xFF) / 255f
        val b = (buffer.get(offset + 2).toInt() and 0xFF) / 255f

        val maxV = max(r, max(g, b))
        val minV = min(r, min(g, b))
        val d = maxV - minV
        if (d < 0.08f || maxV < 0.16f) return 0f

        var h = when (maxV) {
            r -> 60f * (((g - b) / d) % 6f)
            g -> 60f * ((b - r) / d + 2f)
            else -> 60f * ((r - g) / d + 4f)
        }
        if (h < 0f) h += 360f

        val hueWeight = when {
            h in 255f..325f -> 1f
            h in 235f..255f -> 0.55f
            h in 325f..345f -> 0.45f
            else -> 0f
        }
        return (hueWeight * (d / maxV)).coerceIn(0f, 1f)
    }
}
