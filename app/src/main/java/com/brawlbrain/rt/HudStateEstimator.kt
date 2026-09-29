package com.brawlbrain.rt

import android.media.Image
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Lightweight, configurable HUD sampler.
 *
 * It intentionally avoids OCR. The health/ammo/super ROIs are color-density based and
 * can be tuned from BrainPrefs for a different device/UI scale.
 */
class HudStateEstimator {

    private val sampleW = 64
    private val sampleH = 36
    private val pixels = ByteArray(sampleW * sampleH * 3)

    fun analyze(
        image: Image,
        captureW: Int,
        captureH: Int,
        player: Detection?,
        config: BrainConfig
    ): HudState {
        val plane = image.planes[0]
        val buffer: ByteBuffer = plane.buffer.duplicate()
        val stride = plane.pixelStride
        val rowStride = plane.rowStride

        val px = player?.cx ?: 0.5f
        val py = player?.cy ?: 0.5f

        val hp = sampleHealth(
            buffer, stride, rowStride, captureW, captureH,
            px, py, config
        )

        val ammo = sampleAmmo(
            buffer, stride, rowStride, captureW, captureH, config
        )

        val superCharge = sampleSuper(
            buffer, stride, rowStride, captureW, captureH, config
        )

        val maxAmmo = 3
        return HudState(
            hp = hp.first,
            hpConfidence = hp.second,
            ammo = ammo.first,
            maxAmmo = maxAmmo,
            superCharge = superCharge.first,
            superReady = superCharge.first >= config.superReadyThreshold &&
                superCharge.second >= 0.16f,
            ammoConfidence = ammo.second,
            superConfidence = superCharge.second
        )
    }

    private fun sampleHealth(
        buffer: ByteBuffer,
        pixelStride: Int,
        rowStride: Int,
        captureW: Int,
        captureH: Int,
        px: Float,
        py: Float,
        config: BrainConfig
    ): Pair<Float, Float> {
        val left = (px - config.hpScanHalfWidth).coerceIn(0f, 1f)
        val right = (px + config.hpScanHalfWidth).coerceIn(0f, 1f)
        val top = (py - config.hpScanTopOffset).coerceIn(0f, 1f)
        val bottom = (py - config.hpScanBottomOffset).coerceIn(0f, 1f)

        var bestFill = 0f
        var bestConfidence = 0f

        for (row in 0 until 8) {
            val yn = top + (bottom - top) * (row + 0.5f) / 8f
            var colored = 0
            var bright = 0

            for (col in 0 until 42) {
                val xn = left + (right - left) * (col + 0.5f) / 42f
                val rgb = readRgb(
                    buffer, pixelStride, rowStride,
                    captureW, captureH, xn, yn
                )
                val h = hue(rgb[0], rgb[1], rgb[2])
                val s = saturation(rgb[0], rgb[1], rgb[2])
                val v = max(rgb[0], max(rgb[1], rgb[2]))

                if (s > 0.32f && v > 0.35f && h in 35f..155f) {
                    colored++
                    if (v > 0.58f) bright++
                }
            }

            val confidence = (colored / 42f).coerceIn(0f, 1f)
            if (confidence > bestConfidence) {
                bestConfidence = confidence
                bestFill = (bright / 42f).coerceIn(0f, 1f)
            }
        }

        return Pair(bestFill, bestConfidence)
    }

    private fun sampleAmmo(
        buffer: ByteBuffer,
        pixelStride: Int,
        rowStride: Int,
        captureW: Int,
        captureH: Int,
        config: BrainConfig
    ): Pair<Int, Float> {
        val centersX = floatArrayOf(
            config.ammoLeft + (config.ammoRight - config.ammoLeft) * 0.18f,
            config.ammoLeft + (config.ammoRight - config.ammoLeft) * 0.50f,
            config.ammoLeft + (config.ammoRight - config.ammoLeft) * 0.82f
        )

        var filled = 0
        var totalConfidence = 0f

        for (centerX in centersX) {
            var score = 0f
            for (dy in -2..2) {
                for (dx in -2..2) {
                    val xn = (centerX + dx * 0.0045f).coerceIn(0f, 1f)
                    val yn = (
                        config.ammoTop +
                            (config.ammoBottom - config.ammoTop) * 0.50f +
                            dy * 0.006f
                        ).coerceIn(0f, 1f)

                    val rgb = readRgb(
                        buffer, pixelStride, rowStride,
                        captureW, captureH, xn, yn
                    )
                    val h = hue(rgb[0], rgb[1], rgb[2])
                    val s = saturation(rgb[0], rgb[1], rgb[2])
                    val v = max(rgb[0], max(rgb[1], rgb[2]))

                    if (s > 0.22f && v > 0.55f && h in 25f..75f) {
                        score += 1f
                    }
                }
            }

            val confidence = (score / 25f).coerceIn(0f, 1f)
            totalConfidence += confidence
            if (confidence >= 0.28f) filled++
        }

        return Pair(
            filled.coerceIn(0, 3),
            (totalConfidence / 3f).coerceIn(0f, 1f)
        )
    }

    private fun sampleSuper(
        buffer: ByteBuffer,
        pixelStride: Int,
        rowStride: Int,
        captureW: Int,
        captureH: Int,
        config: BrainConfig
    ): Pair<Float, Float> {
        var gold = 0f
        var total = 0

        for (iy in -7..7) {
            for (ix in -7..7) {
                if (ix * ix + iy * iy > 49) continue

                val xn = (config.superButtonX + ix * config.superButtonRadius / 7f)
                    .coerceIn(0f, 1f)
                val yn = (config.superButtonY + iy * config.superButtonRadius / 7f)
                    .coerceIn(0f, 1f)

                val rgb = readRgb(
                    buffer, pixelStride, rowStride,
                    captureW, captureH, xn, yn
                )
                val h = hue(rgb[0], rgb[1], rgb[2])
                val s = saturation(rgb[0], rgb[1], rgb[2])
                val v = max(rgb[0], max(rgb[1], rgb[2]))

                total++
                if (s > 0.28f && v > 0.55f && h in 28f..80f) {
                    gold += 1f
                }
            }
        }

        val fraction = if (total == 0) 0f else (gold / total).coerceIn(0f, 1f)
        val confidence = (abs(fraction - 0.12f) / 0.35f).coerceIn(0f, 1f)
        return Pair(fraction, confidence)
    }

    private fun readRgb(
        buffer: ByteBuffer,
        pixelStride: Int,
        rowStride: Int,
        width: Int,
        height: Int,
        x: Float,
        y: Float
    ): IntArray {
        val sx = (x * (width - 1)).toInt().coerceIn(0, width - 1)
        val sy = (y * (height - 1)).toInt().coerceIn(0, height - 1)
        val offset = sy * rowStride + sx * pixelStride
        if (offset < 0 || offset + 2 >= buffer.limit()) return intArrayOf(0, 0, 0)

        return intArrayOf(
            buffer.get(offset).toInt() and 0xFF,
            buffer.get(offset + 1).toInt() and 0xFF,
            buffer.get(offset + 2).toInt() and 0xFF
        )
    }

    private fun saturation(r8: Int, g8: Int, b8: Int): Float {
        val r = r8 / 255f
        val g = g8 / 255f
        val b = b8 / 255f
        val maxV = max(r, max(g, b))
        val minV = min(r, min(g, b))
        return if (maxV <= 0f) 0f else (maxV - minV) / maxV
    }

    private fun hue(r8: Int, g8: Int, b8: Int): Float {
        val r = r8 / 255f
        val g = g8 / 255f
        val b = b8 / 255f
        val maxV = max(r, max(g, b))
        val minV = min(r, min(g, b))
        val d = maxV - minV
        if (d < 0.0001f) return 0f

        var h = when (maxV) {
            r -> 60f * (((g - b) / d) % 6f)
            g -> 60f * ((b - r) / d + 2f)
            else -> 60f * ((r - g) / d + 4f)
        }

        if (h < 0f) h += 360f
        return h
    }
}
