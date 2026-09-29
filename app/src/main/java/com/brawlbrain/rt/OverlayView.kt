package com.brawlbrain.rt

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import java.util.Locale

class OverlayView(context: Context) : View(context) {

    private var config = BrainPrefs.load(context)

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    private val smallPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var frame = emptyFrame()

    fun updateConfig(newConfig: BrainConfig) {
        config = newConfig
        postInvalidateOnAnimation()
    }

    fun submit(next: BrainFrame) {
        frame = next
        postInvalidateOnAnimation()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()
        val scale = config.hudScalePercent / 100f

        canvas.save()
        canvas.scale(scale, scale)

        val panelW = 560f
        val panelH = if (config.showDebug) 202f else 170f
        drawPanel(canvas, 22f, 22f, panelW, panelH)
        drawHeader(canvas)

        if (config.showThreat) drawThreatRing(canvas, w / scale)
        if (config.showEnemies) frame.enemies.forEach { drawEnemy(canvas, it) }
        if (config.showTeammates) frame.teammates.forEach { drawTeammate(canvas, it) }
        if (config.showWalls) frame.walls.forEach { if (it.label != "bush") drawWall(canvas, it) }

        frame.player?.let { drawPlayer(canvas, it) }

        if (config.showTargetLine) {
            frame.target?.let { drawTargetGuide(canvas, it) }
        }

        canvas.restore()
    }

    private fun drawPanel(canvas: Canvas, left: Float, top: Float, right: Float, bottom: Float) {
        val alpha = (config.hudOpacityPercent.coerceIn(40, 100) * 2.15f)
            .toInt()
            .coerceIn(120, 235)

        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = (alpha shl 24) or 0x000B0F17
        }
        canvas.drawRoundRect(left, top, right, bottom, 28f, 28f, bg)

        val accent = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = accentColor()
        }
        canvas.drawRoundRect(left, top, left + 10f, bottom, 6f, 6f, accent)
    }

    private fun drawHeader(canvas: Canvas) {
        textPaint.textSize = 28f
        textPaint.color = Color.WHITE
        canvas.drawText(
            if (config.showAdvice) frame.recommendation.title else "BRAWLBRAIN",
            54f,
            68f,
            textPaint
        )

        smallPaint.textSize = 17f
        smallPaint.color = 0xFFE1E6F0.toInt()

        val detail = if (config.showAdvice) {
            frame.recommendationDetail
        } else {
            "VISION ONLINE • ${frame.role} • ${frame.gameMode}"
        }
        canvas.drawText(detail, 54f, 98f, smallPaint)

        smallPaint.textSize = 16f
        smallPaint.color = 0xFFACB5C7.toInt()

        if (config.showThreat) {
            canvas.drawText(
                "Угроза ${percent(frame.threat)}%   Окно ${percent(frame.opportunity)}%   Целей ${frame.enemyCount}",
                54f,
                128f,
                smallPaint
            )
        } else {
            canvas.drawText(
                "Целей ${frame.enemyCount}   Укрытие ${percent(frame.cover)}%   Изоляция ${percent(frame.isolation)}%",
                54f,
                128f,
                smallPaint
            )
        }

        if (config.showDebug) {
            canvas.drawText(
        if (config.showDebug) {
            canvas.drawText(
                "Vision " + String.format(Locale.US, "%.1f", frame.fps) +
                    " FPS • " + frame.inferenceMs + "ms • " + frame.engine,
                54f,
                158f,
                smallPaint
            )
        }

        if (config.autoDodge) {
            val dodgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFF8FD3FF.toInt()
                textSize = 14f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }
            canvas.drawText("DODGE ONLY • ON", 54f, 184f, dodgePaint)
        }
        }
    }

    private fun drawThreatRing(canvas: Canvas, screenW: Float) {
        val back = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 9f
            strokeCap = Paint.Cap.ROUND
            color = 0x55343B4D
        }
        val front = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 9f
            strokeCap = Paint.Cap.ROUND
            color = accentColor()
        }

        val rr = RectF(screenW - 132f, 47f, screenW - 60f, 119f)
        canvas.drawArc(rr, -90f, 360f, false, back)
        canvas.drawArc(rr, -90f, 360f * frame.threat, false, front)
    }

    private fun drawPlayer(canvas: Canvas, d: Detection) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 5f
            color = 0xFF7CA7FF.toInt()
        }
        canvas.drawCircle(d.cx * width, d.cy * height, 24f, p)
    }

    private fun drawEnemy(canvas: Canvas, d: Detection) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = if (d.confidence >= 0.60f) 0xFFFF596B.toInt()
            else 0xFFFFAD67.toInt()
        }

        canvas.drawCircle(
            d.cx * width,
            d.cy * height,
            15f + d.confidence * 9f,
            p
        )

        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            color = 0xAAFFFFFF.toInt()
        }
        canvas.drawCircle(d.cx * width, d.cy * height, 26f, ring)
    }

    private fun drawTeammate(canvas: Canvas, d: Detection) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            color = 0xFF62D6A2.toInt()
        }
        canvas.drawCircle(d.cx * width, d.cy * height, 20f, p)
    }

    private fun drawWall(canvas: Canvas, d: Detection) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            color = if (d.label == "close_bush") 0x8879D69C.toInt()
            else 0x88CBD4E5.toInt()
        }

        val left = (d.cx - d.width / 2f) * width
        val top = (d.cy - d.height / 2f) * height
        val right = (d.cx + d.width / 2f) * width
        val bottom = (d.cy + d.height / 2f) * height

        canvas.drawRoundRect(RectF(left, top, right, bottom), 12f, 12f, p)
    }

    private fun drawTargetGuide(canvas: Canvas, target: Detection) {
        val player = frame.player ?: return

        val x1 = player.cx * width
        val y1 = player.cy * height
        val x2 = frame.targetLeadX * width
        val y2 = frame.targetLeadY * height

        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 5f
            color = accentColor()
            pathEffect = if (config.reducedMotion) {
                DashPathEffect(floatArrayOf(14f, 10f), 0f)
            } else {
                null
            }
        }

        canvas.drawLine(x1, y1, x2, y2, p)

        val angle = atan2(y2 - y1, x2 - x1)
        val size = 28f
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

        if (target.confidence > 0.5f) {
            val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 4f
                color = accentColor()
            }
            canvas.drawCircle(target.cx * width, target.cy * height, 34f, ring)
        }
    }

    private fun accentColor(): Int {
        return when (frame.recommendation) {
            Recommendation.RETREAT -> 0xFFFF5F78.toInt()
            Recommendation.PRESSURE -> 0xFF7DFF9A.toInt()
            Recommendation.HOLD -> 0xFFFFD166.toInt()
            Recommendation.TRACK -> 0xFF77B7FF.toInt()
            Recommendation.RESET -> 0xFFB8C0D3.toInt()
        }
    }

    private fun percent(value: Float): Int =
        (value.coerceIn(0f, 1f) * 100f).toInt()

    private fun emptyFrame() = BrainFrame(
        player = null,
        enemies = emptyList(),
        teammates = emptyList(),
        walls = emptyList(),
        recommendation = Recommendation.RESET,
        recommendationDetail = "Сканирую поле",
        target = null,
        targetLeadX = 0.5f,
        targetLeadY = 0.5f,
        escapeX = 0f,
        escapeY = 0f,
        threat = 0f,
        opportunity = 0f,
        cover = 0f,
        isolation = 0f,
        enemyCount = 0,
        fps = 0f,
        inferenceMs = 0L,
        engine = "INIT",
        gameMode = "Universal",
        role = "Universal"
    )
}