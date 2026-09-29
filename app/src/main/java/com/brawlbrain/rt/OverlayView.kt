package com.brawlbrain.rt

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import java.util.Locale

class OverlayView(context: Context) : View(context) {

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textSize = 28f
    }
    private val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textSize = 20f
    }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5f
    }

    private var frame = BrainFrame(
        null,
        emptyList(),
        emptyList(),
        emptyList(),
        Recommendation.RESET,
        null,
        0f,
        0,
        0f,
        0L,
        "INIT"
    )

    fun submit(next: BrainFrame) {
        frame = next
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        drawPanel(canvas, 24f, 24f, 560f, 194f)
        drawStatus(canvas, w)

        frame.player?.let {
            drawPlayer(canvas, it.cx * w, it.cy * h)
        }

        for (enemy in frame.enemies) {
            drawEnemy(canvas, enemy.cx * w, enemy.cy * h, enemy.confidence)
        }

        for (wall in frame.walls) {
            if (wall.label == "wall" || wall.label == "close_bush") {
                drawWall(canvas, wall)
            }
        }

        frame.target?.let { drawTargetGuide(canvas, w, h, it) }
    }

    private fun drawPanel(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float) {
        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = 0xD80A0D14.toInt()
        }
        canvas.drawRoundRect(left, top, right, bottom, 28f, 28f, bg)

        val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = accentColor()
        }
        canvas.drawRoundRect(left, top, left + 10f, bottom, 6f, 6f, accent)
    }

    private fun drawStatus(canvas: Canvas, screenW: Float) {
        val recommendation = frame.recommendation
        textPaint.color = Color.WHITE
        canvas.drawText(recommendation.title, 54f, 68f, textPaint)

        smallPaint.color = 0xFFE0E4EE.toInt()
        canvas.drawText(recommendation.subtitle, 54f, 100f, smallPaint)

        smallPaint.color = 0xFFADB6C8.toInt()
        canvas.drawText(
            "Угроза " + (frame.threat * 100f).toInt() +
                "%  •  целей " + frame.enemyCount +
                "  •  " + frame.inferenceMs + "ms",
            54f,
            132f,
            smallPaint
        )

        canvas.drawText(
            "Vision " + String.format(Locale.US, "%.1f", frame.fps) +
                " FPS  •  " + frame.engine,
            54f,
            164f,
            smallPaint
        )

        val ringBack = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 10f
            strokeCap = Paint.Cap.ROUND
            color = 0x55333A4A
        }
        val ringFront = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 10f
            strokeCap = Paint.Cap.ROUND
            color = accentColor()
        }

        val rr = RectF(screenW - 132f, 50f, screenW - 60f, 122f)
        canvas.drawArc(rr, -90f, 360f, false, ringBack)
        canvas.drawArc(rr, -90f, 360f * frame.threat, false, ringFront)
    }

    private fun drawPlayer(canvas: Canvas, x: Float, y: Float) {
        stroke.color = 0xFF7EA5FF.toInt()
        canvas.drawCircle(x, y, 24f, stroke)
    }

    private fun drawEnemy(canvas: Canvas, x: Float, y: Float, confidence: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = if (confidence >= 0.60f) 0xFFFF5B6B.toInt()
            else 0xFFFFAA61.toInt()
        }
        canvas.drawCircle(x, y, 18f + confidence * 8f, p)

        stroke.color = 0x88FFFFFF.toInt()
        canvas.drawCircle(x, y, 28f, stroke)
    }

    private fun drawWall(canvas: Canvas, d: Detection) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            color = 0x88C4CDDE.toInt()
        }

        val left = (d.cx - d.width / 2f) * width
        val top = (d.cy - d.height / 2f) * height
        val right = (d.cx + d.width / 2f) * width
        val bottom = (d.cy + d.height / 2f) * height

        canvas.drawRoundRect(RectF(left, top, right, bottom), 12f, 12f, p)
    }

    private fun drawTargetGuide(canvas: Canvas, w: Float, h: Float, target: Detection) {
        val player = frame.player ?: return

        val x1 = player.cx * w
        val y1 = player.cy * h
        val x2 = target.cx * w
        val y2 = target.cy * h

        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = accentColor()
            style = Paint.Style.STROKE
            strokeWidth = 5f
            pathEffect = DashPathEffect(floatArrayOf(14f, 10f), 0f)
        }
        canvas.drawLine(x1, y1, x2, y2, line)

        val angle = atan2(y2 - y1, x2 - x1)
        val size = 30f
        val leftX = x2 - cos(angle - 0.55f) * size
        val leftY = y2 - sin(angle - 0.55f) * size
        val rightX = x2 - cos(angle + 0.55f) * size
        val rightY = y2 - sin(angle + 0.55f) * size

        val arrow = Path().apply {
            moveTo(x2, y2)
            lineTo(leftX, leftY)
            lineTo(rightX, rightY)
            close()
        }

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = accentColor()
        }
        canvas.drawPath(arrow, fill)
    }

    private fun accentColor(): Int {
        return when (frame.recommendation) {
            Recommendation.RETREAT -> 0xFFFF5E73.toInt()
            Recommendation.PRESSURE -> 0xFF7CFF9B.toInt()
            Recommendation.HOLD -> 0xFFFFD166.toInt()
            Recommendation.TRACK -> 0xFF78B8FF.toInt()
            Recommendation.RESET -> 0xFFB9C0D2.toInt()
        }
    }
}