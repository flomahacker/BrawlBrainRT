package com.brawlbrain.rt

import android.content.Context
import android.graphics.*
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class OverlayView(context: Context) : View(context) {

    private var config = BrainPrefs.load(context)
    private var frame = emptyFrame()

    private val glyphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    private val debugPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private enum class Signal {
        ATTACK,
        DODGE,
        RETREAT,
        CONTROL,
        WAIT,
        FREE
    }

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
        if (width <= 0 || height <= 0) return

        val ui = resources.displayMetrics.density *
            (config.hudScalePercent.coerceIn(70, 120) / 100f)

        drawSignalCard(canvas, ui)

        val signal = signal()
        if (config.showAdvice) {
            drawFocus(canvas, signal)
            drawActionArrow(canvas, signal, ui)

            if (signal == Signal.DODGE || frame.projectileDetected) {
                drawIncomingThreat(canvas, ui)
            }

            if (signal == Signal.CONTROL) {
                drawControlRoute(canvas, ui)
            }
        } else {
            if (config.showEnemies) {
                frame.enemies.forEach { drawEnemy(canvas, it) }
            }
            if (config.showTeammates) {
                frame.teammates.forEach { drawTeammate(canvas, it) }
            }
        }

        if (config.showDebug) {
            drawDebug(canvas, ui)
        }
    }

    private fun drawSignalCard(canvas: Canvas, ui: Float) {
        val left = 16f * ui
        val top = 16f * ui
        val right = left + 74f * ui
        val bottom = top + 66f * ui
        val signal = signal()
        val color = signalColor(signal)

        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = withAlpha(0x10151E, 218)
        }
        canvas.drawRoundRect(left, top, right, bottom, 18f * ui, 18f * ui, bg)

        val strip = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = color
        }
        canvas.drawRoundRect(
            left,
            top,
            left + 6f * ui,
            bottom,
            5f * ui,
            5f * ui,
            strip
        )

        glyphPaint.textSize = 34f * ui
        glyphPaint.color = Color.WHITE
        canvas.drawText(signalGlyph(signal), left + 40f * ui, top + 44f * ui, glyphPaint)

        val score = frame.intelFocusScore.coerceIn(0f, 1f)
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f * ui
            strokeCap = Paint.Cap.ROUND
            color = withAlpha(color, 90)
        }
        val rr = RectF(
            right - 18f * ui,
            top + 10f * ui,
            right - 8f * ui,
            top + 20f * ui
        )
        canvas.drawArc(rr, -90f, 360f, false, ring)

        ring.color = color
        canvas.drawArc(rr, -90f, 360f * score, false, ring)

        if (frame.projectileDetected) {
            val bolt = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = if (frame.projectileThreat >= 0.58f) {
                    0xFFFF5368.toInt()
                } else {
                    0xFFFFC857.toInt()
                }
            }
            canvas.drawCircle(
                left + 62f * ui,
                bottom - 12f * ui,
                5f * ui,
                bolt
            )
        }
    }

    private fun drawFocus(canvas: Canvas, signal: Signal) {
        val score = frame.intelFocusScore
        if (score < 0.25f) return

        val x = frame.intelFocusX.coerceIn(0f, 1f) * width
        val y = frame.intelFocusY.coerceIn(0f, 1f) * height
        val radius = when (signal) {
            Signal.ATTACK -> 31f
            Signal.DODGE -> 28f
            Signal.RETREAT -> 24f
            Signal.CONTROL -> 27f
            Signal.WAIT -> 29f
            Signal.FREE -> 23f
        }

        val color = signalColor(signal)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 5f
            strokeCap = Paint.Cap.ROUND
            color = color
        }

        val arm = 14f
        drawBracket(canvas, x - radius, y - radius, -1f, -1f, arm, p)
        drawBracket(canvas, x + radius, y - radius, 1f, -1f, arm, p)
        drawBracket(canvas, x - radius, y + radius, -1f, 1f, arm, p)
        drawBracket(canvas, x + radius, y + radius, 1f, 1f, arm, p)

        if (signal == Signal.ATTACK) {
            val inner = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2.5f
                color = withAlpha(color, 150)
            }
            canvas.drawCircle(x, y, radius - 12f, inner)
        }
    }

    private fun drawBracket(
        canvas: Canvas,
        x: Float,
        y: Float,
        sx: Float,
        sy: Float,
        arm: Float,
        paint: Paint
    ) {
        canvas.drawLine(x, y, x + sx * arm, y, paint)
        canvas.drawLine(x, y, x, y + sy * arm, paint)
    }

    private fun drawActionArrow(canvas: Canvas, signal: Signal, ui: Float) {
        val player = frame.player ?: return

        val ax = frame.actionX
        val ay = frame.actionY
        val len = hypot(ax.toDouble(), ay.toDouble()).toFloat()
        if (len < 0.18f) return

        val px = player.cx.coerceIn(0f, 1f) * width
        val py = player.cy.coerceIn(0f, 1f) * height

        val arrowLength = when (signal) {
            Signal.DODGE, Signal.RETREAT -> 108f * ui
            Signal.ATTACK -> 124f * ui
            Signal.CONTROL -> 104f * ui
            Signal.WAIT -> 82f * ui
            Signal.FREE -> 70f * ui
        }

        val ex = px + ax / len * arrowLength
        val ey = py + ay / len * arrowLength
        val angle = atan2(ey - py, ex - px)

        val color = signalColor(signal)
        val shadow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 15f * ui
            strokeCap = Paint.Cap.ROUND
            color = withAlpha(color, 42)
        }
        canvas.drawLine(px, py, ex, ey, shadow)

        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 7f * ui
            strokeCap = Paint.Cap.ROUND
            color = color
        }
        canvas.drawLine(px, py, ex, ey, paint)

        val head = 19f * ui
        val path = Path().apply {
            moveTo(ex, ey)
            lineTo(
                ex - cos(angle - 0.58f) * head,
                ey - sin(angle - 0.58f) * head
            )
            lineTo(
                ex - cos(angle + 0.58f) * head,
                ey - sin(angle + 0.58f) * head
            )
            close()
        }
        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = color
        }
        canvas.drawPath(path, fill)

        val playerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f * ui
            color = Color.WHITE
        }
        canvas.drawCircle(px, py, 18f * ui, playerPaint)
    }

    private fun drawIncomingThreat(canvas: Canvas, ui: Float) {
        val player = frame.player ?: return
        val enemy = frame.target ?: return

        val px = player.cx * width
        val py = player.cy * height
        val ex = enemy.cx * width
        val ey = enemy.cy * height

        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 5f * ui
            strokeCap = Paint.Cap.ROUND
            color = 0xFFFF5368.toInt()
            pathEffect = DashPathEffect(
                floatArrayOf(12f * ui, 9f * ui),
                0f
            )
        }
        canvas.drawLine(ex, ey, px, py, line)

        val dx = px - ex
        val dy = py - ey
        val len = maxOf(1f, hypot(dx.toDouble(), dy.toDouble()).toFloat())
        val ux = dx / len
        val uy = dy / len

        val marker = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = 0xFFFF5368.toInt()
        }

        val threatLevel = frame.projectileThreat.coerceIn(0.2f, 1f)
        val count = 2 + (threatLevel * 3f).toInt()

        for (i in 1..count) {
            val t = i / (count + 1f)
            val mx = ex + dx * t
            val my = ey + dy * t
            val rr = (4f + threatLevel * 2f) * ui
            canvas.drawCircle(
                mx - ux * i * 1.5f * ui,
                my - uy * i * 1.5f * ui,
                rr,
                marker
            )
        }

        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 6f * ui
            color = 0xFFFF5368.toInt()
        }
        canvas.drawCircle(px, py, 34f * ui, ring)
    }

    private fun drawControlRoute(canvas: Canvas, ui: Float) {
        val player = frame.player ?: return
        val ax = frame.actionX
        val ay = frame.actionY
        val len = hypot(ax.toDouble(), ay.toDouble()).toFloat()
        if (len < 0.18f) return

        val px = player.cx * width
        val py = player.cy * height
        val color = signalColor(Signal.CONTROL)

        val diamond = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = withAlpha(color, 220)
        }

        for (i in 1..3) {
            val t = i / 3.6f
            val x = px + ax / len * (48f + 52f * t) * ui
            val y = py + ay / len * (48f + 52f * t) * ui
            val size = 8f * ui
            val path = Path().apply {
                moveTo(x, y - size)
                lineTo(x + size, y)
                lineTo(x, y + size)
                lineTo(x - size, y)
                close()
            }
            canvas.drawPath(path, diamond)
        }
    }

    private fun drawDebug(canvas: Canvas, ui: Float) {
        val text = "\${frame.fps.toInt()} FPS  \${frame.inferenceMs}ms"
        debugPaint.textSize = 12f * ui
        debugPaint.color = 0xD8FFFFFF.toInt()

        val bg = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = 0xCC10151E.toInt()
        }

        val left = 16f * ui
        val top = 90f * ui
        val right = left + debugPaint.measureText(text) + 18f * ui
        val bottom = top + 24f * ui

        canvas.drawRoundRect(left, top, right, bottom, 8f * ui, 8f * ui, bg)
        canvas.drawText(text, left + 9f * ui, top + 17f * ui, debugPaint)
    }

    private fun drawEnemy(canvas: Canvas, d: Detection) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = 0x88FF596B.toInt()
        }
        canvas.drawCircle(
            d.cx * width,
            d.cy * height,
            12f + d.confidence * 8f,
            p
        )
    }

    private fun drawTeammate(canvas: Canvas, d: Detection) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 4f
            color = 0xAA62D6A2.toInt()
        }
        canvas.drawCircle(d.cx * width, d.cy * height, 18f, p)
    }

    private fun signal(): Signal {
        return when (frame.intelActionTitle) {
            "ОКНО BUZZ", "ОКНО ВЫСТРЕЛА" -> Signal.ATTACK
            "СНАРЯД" -> Signal.DODGE
            "RESET" -> Signal.RETREAT
            "ЗАКРОЙ ПУТЬ", "ЗОНИРУЙ", "ДАЛЬНЯЯ ЗОНА" -> Signal.CONTROL
            "НЕ ВХОДИ", "НЕ ЛОВИ ЛОБ", "ЖДИ УГОЛ" -> Signal.WAIT
            else -> Signal.FREE
        }
    }

    private fun signalGlyph(signal: Signal): String {
        return when (signal) {
            Signal.ATTACK -> "➜"
            Signal.DODGE -> "↝"
            Signal.RETREAT -> "↙"
            Signal.CONTROL -> "◇"
            Signal.WAIT -> "○"
            Signal.FREE -> "✓"
        }
    }

    private fun signalColor(signal: Signal): Int {
        return when (signal) {
            Signal.ATTACK -> 0xFF67F58A.toInt()
            Signal.DODGE -> 0xFFFF5368.toInt()
            Signal.RETREAT -> 0xFFFF5368.toInt()
            Signal.CONTROL -> 0xFF6DB7FF.toInt()
            Signal.WAIT -> 0xFFFFD166.toInt()
            Signal.FREE -> 0xFF8BE0FF.toInt()
        }
    }

    private fun withAlpha(color: Int, alpha: Int): Int =
        (alpha.coerceIn(0, 255) shl 24) or (color and 0x00FFFFFF)

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
