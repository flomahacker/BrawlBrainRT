package com.brawldodge.ai

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class DodgeOverlayView(context: Context) : View(context) {

    @Volatile
    private var snapshot = VisionSnapshot.empty()

    private val density = resources.displayMetrics.density
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        typeface = android.graphics.Typeface.create(
            "sans",
            android.graphics.Typeface.BOLD
        )
    }
    private val rect = RectF()
    private val arrowPath = Path()

    fun submit(next: VisionSnapshot) {
        snapshot = next
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        val s = snapshot
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        drawHud(canvas, s)

        for (box in s.boxes) {
            val color = when (box.kind) {
                Box.Kind.PLAYER -> 0xFF31E6A6.toInt()
                Box.Kind.ENEMY -> 0xFFFF4E68.toInt()
                Box.Kind.PROJECTILE -> 0xFFFFB52C.toInt()
                Box.Kind.AREA -> 0xFFFF7C3D.toInt()
            }

            stroke.color = color
            stroke.strokeWidth =
                if (box.kind == Box.Kind.PROJECTILE) 2.0f * density else 2.4f * density

            rect.set(
                box.left * w,
                box.top * h,
                box.right * w,
                box.bottom * h
            )
            canvas.drawRoundRect(rect, 7f * density, 7f * density, stroke)
        }

        if (s.danger > 0.45f) {
            drawDanger(canvas, s)
        }
    }

    private fun drawHud(canvas: Canvas, s: VisionSnapshot) {
        val scale = density
        val panelW = min(width - 20f * scale, 310f * scale)
        val panelH = if (s.error.isBlank()) 116f * scale else 138f * scale

        fill.color = 0xDB0B111A.toInt()
        rect.set(
            10f * scale,
            10f * scale,
            10f * scale + panelW,
            10f * scale + panelH
        )
        canvas.drawRoundRect(rect, 14f * scale, 14f * scale, fill)

        text.textSize = 13f * scale
        text.color = 0xFFF7F9FC.toInt()
        canvas.drawText("BRAWL DODGE AI", 22f * scale, 32f * scale, text)

        text.textSize = 11f * scale
        text.color = if (s.online) 0xFF31E6A6.toInt() else 0xFFFFB52C.toInt()
        canvas.drawText(
            if (s.online) "● VISION ONLINE" else "● WAITING FOR CAPTURE",
            22f * scale,
            49f * scale,
            text
        )

        text.color = 0xFFC5CFDC.toInt()
        canvas.drawText(
            String.format(Locale.US, "FPS %.1f  ·  CV %.1f ms", s.fps, s.processMs),
            22f * scale,
            67f * scale,
            text
        )

        canvas.drawText(
            String.format(Locale.US, "CAP %.1f ms  ·  BOX %d", s.captureDelayMs, s.boxes.size),
            22f * scale,
            84f * scale,
            text
        )

        text.color = when {
            s.danger > 0.72f -> 0xFFFF4E68.toInt()
            s.danger > 0.45f -> 0xFFFFB52C.toInt()
            else -> 0xFF8D9CAF.toInt()
        }
        canvas.drawText(
            "THREAT " + String.format(Locale.US, "%.0f", s.danger * 100f) +
                "%  ·  " + s.recommendation,
            22f * scale,
            101f * scale,
            text
        )

        if (s.error.isNotBlank()) {
            text.textSize = 9.5f * scale
            text.color = 0xFFFF7C8B.toInt()
            canvas.drawText(s.error.take(48), 22f * scale, 120f * scale, text)
        }
    }

    private fun drawDanger(canvas: Canvas, s: VisionSnapshot) {
        val scale = density
        val alpha = if (s.danger > 0.72f) 88 else 52
        fill.color = (alpha shl 24) or 0x00FF4762

        val edge = 16f * scale
        canvas.drawRect(0f, 0f, width.toFloat(), edge, fill)
        canvas.drawRect(0f, height - edge, width.toFloat(), height.toFloat(), fill)
        canvas.drawRect(0f, 0f, edge, height.toFloat(), fill)
        canvas.drawRect(width - edge, 0f, width.toFloat(), height.toFloat(), fill)

        val angle = atan2(s.dangerY.toDouble(), s.dangerX.toDouble())
        val cx = width * 0.5f
        val cy = height * 0.5f
        val len = min(width, height) * 0.17f
        val ex = cx + cos(angle).toFloat() * len
        val ey = cy + sin(angle).toFloat() * len

        stroke.color = 0xFFFF4E68.toInt()
        stroke.strokeWidth = 5f * scale
        canvas.drawLine(cx, cy, ex, ey, stroke)

        val head = 18f * scale
        arrowPath.reset()
        arrowPath.moveTo(ex, ey)
        arrowPath.lineTo(
            ex - cos(angle - 0.55).toFloat() * head,
            ey - sin(angle - 0.55).toFloat() * head
        )
        arrowPath.lineTo(
            ex - cos(angle + 0.55).toFloat() * head,
            ey - sin(angle + 0.55).toFloat() * head
        )
        arrowPath.close()

        fill.color = 0xFFFF4E68.toInt()
        canvas.drawPath(arrowPath, fill)
    }
}
