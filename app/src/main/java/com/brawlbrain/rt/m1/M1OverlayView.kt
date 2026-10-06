package com.brawlbrain.rt.m1

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class M1OverlayView(
    context: Context,
    private val state: M1OverlayState
) : View(context) {

    private val density = resources.displayMetrics.density
    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.0f * density
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        textSize = 11.5f * density
        typeface = android.graphics.Typeface.create("sans", android.graphics.Typeface.BOLD)
    }
    private val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xCC0B0F14.toInt()
    }
    private val panelStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.0f * density
        color = 0x667B8AA0
    }
    private val labelBackground = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val rect = RectF()

    init {
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
        isClickable = false
        isFocusable = false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        synchronized(state) {
            val sx = w / max(1, state.sourceWidth)
            val sy = h / max(1, state.sourceHeight)

            for (i in 0 until state.detectionCount) {
                val type = state.types[i]
                val color = when (type) {
                    1 -> 0xFF27E6A5.toInt()
                    2 -> 0xFFFF4D67.toInt()
                    3 -> 0xFFFFB020.toInt()
                    4 -> 0xFFFF7A3D.toInt()
                    else -> 0xFF9AA9BC.toInt()
                }

                boxPaint.color = color
                labelBackground.color = color
                rect.set(
                    state.left[i] * sx,
                    state.top[i] * sy,
                    state.right[i] * sx,
                    state.bottom[i] * sy
                )
                canvas.drawRoundRect(rect, 5f * density, 5f * density, boxPaint)

                val labelName = when (type) {
                    1 -> "ИГРОК"
                    2 -> "ВРАГ"
                    3 -> "СНАРЯД"
                    4 -> "ЗОНА"
                    else -> "ОБЪЕКТ"
                }
                val label = labelName + " " + (state.confidence[i] * 100f).toInt() + "%"

                val labelWidth = textPaint.measureText(label) + 10f * density
                val labelTop = max(0f, rect.top - 18f * density)
                canvas.drawRoundRect(
                    rect.left,
                    labelTop,
                    min(w, rect.left + labelWidth),
                    labelTop + 17f * density,
                    4f * density,
                    4f * density,
                    labelBackground
                )
                textPaint.color = 0xFF091018.toInt()
                canvas.drawText(
                    label,
                    rect.left + 5f * density,
                    labelTop + 12f * density,
                    textPaint
                )
            }

            val panelW = min(w - 16f * density, 245f * density)
            val panelH = 92f * density
            rect.set(
                8f * density,
                8f * density,
                8f * density + panelW,
                8f * density + panelH
            )
            canvas.drawRoundRect(rect, 12f * density, 12f * density, panelPaint)
            canvas.drawRoundRect(rect, 12f * density, 12f * density, panelStroke)

            textPaint.color = 0xFFF5F7FA.toInt()
            textPaint.textSize = 11.5f * density
            canvas.drawText("BRAWL DODGE AI · M1", 18f * density, 27f * density, textPaint)

            textPaint.color = 0xFFB9C4D0.toInt()
            val fpsText = String.format(Locale.US, "CV: %d объектов · %.1f FPS", state.detectionCount, state.pipelineFps)
            canvas.drawText(fpsText, 18f * density, 45f * density, textPaint)

            val timingText = String.format(
                Locale.US,
                "Захват: %.1f ms · CV: %.1f ms",
                state.captureAgeMs,
                state.processMs
            )
            canvas.drawText(timingText, 18f * density, 61f * density, textPaint)

            val thermal = when (state.thermalStatus) {
                0 -> "норма"
                1 -> "лёгкий"
                2 -> "умеренный"
                3 -> "сильный"
                4 -> "аварийный"
                5 -> "критический"
                else -> "N/A"
            }
            canvas.drawText("THERMAL: " + thermal + " · TOUCH: OFF", 18f * density, 77f * density, textPaint)

            textPaint.color = 0xFF8290A2.toInt()
            canvas.drawText(
                state.sourceWidth.toString() + "×" + state.sourceHeight + " · только debug-режим",
                18f * density,
                92f * density,
                textPaint
            )
        }
    }
}
