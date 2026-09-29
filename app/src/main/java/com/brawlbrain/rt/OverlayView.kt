package com.brawlbrain.rt

import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

class OverlayView(context: Context) : View(context) {

    private var config = BrainPrefs.load(context)
    private var frame = emptyFrame()
    private var lastRenderSignature = Long.MIN_VALUE
    private var pulseUntil = 0L

    // Preallocated drawing state: do not allocate in onDraw.
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG)
    private val softStroke = Paint(Paint.ANTI_ALIAS_FLAG)
    private val text = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arrowPath = Path()
    private val rect = RectF()
    private val dash = DashPathEffect(floatArrayOf(12f, 9f), 0f)

    companion object {
        // Deliberately avoid red/green: the overlay must not resemble detector classes.
        private const val CYAN = 0xE862E8FF.toInt()
        private const val VIOLET = 0xD88C7CFF.toInt()
        private const val AMBER = 0xE8FFD166.toInt()
        private const val BLUE = 0xE86DB7FF.toInt()
        private const val MAGENTA = 0xE8FF5EDB.toInt()
        private const val WHITE = 0xE8FFFFFF.toInt()
        private const val PANEL = 0xB510151E.toInt()
    }

    fun updateConfig(newConfig: BrainConfig) {
        config = newConfig
        markChanged()
    }

    fun submit(next: BrainFrame) {
        val signature = visualSignature(next)
        val wasSuperReady = frame.hud.superReady
        frame = next
        if (signature != lastRenderSignature) {
            lastRenderSignature = signature
            if (next.hud.superReady && !wasSuperReady) {
                pulseUntil = SystemClock.elapsedRealtime() + 700L
            }
            invalidate()
        }
    }

    private fun markChanged() {
        lastRenderSignature = Long.MIN_VALUE
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (width <= 0 || height <= 0) return

        val scale = config.hudScalePercent.coerceIn(70, 120) / 100f

        if (config.showGhosts || config.showPrediction) {
            drawTracks(canvas, scale)
        }

        if (config.showRangeRings) {
            drawPlayerRange(canvas, scale)
        }

        if (config.showEnemyRangeRings) {
            drawEnemyRanges(canvas, scale)
        }

        if (config.showWarnings && frame.warning.active) {
            drawWarning(canvas, scale)
        }

        if (config.showSuperPulse && frame.hud.superReady) {
            drawSuperReady(canvas, scale)
        }

        if (config.showDebug) {
            drawDebug(canvas, scale)
        }

        if (frame.hud.superReady && SystemClock.elapsedRealtime() < pulseUntil) {
            postInvalidateOnAnimation()
        }
    }

    private fun drawTracks(canvas: Canvas, scale: Float) {
        for (track in frame.trackVisuals) {
            if (track.visible) {
                if (config.showPrediction) {
                    drawPrediction(canvas, track, scale)
                }
            } else if (config.showGhosts) {
                drawGhost(canvas, track, scale)
            }
        }
    }

    private fun drawPrediction(canvas: Canvas, track: EnemyTrackVisual, scale: Float) {
        val speed = hypot(track.vx.toDouble(), track.vy.toDouble()).toFloat()
        if (speed < 0.18f || track.confidence < 0.22f) return

        val x = track.predictedX.coerceIn(0f, 1f) * width
        val y = track.predictedY.coerceIn(0f, 1f) * height
        val currentX = track.x.coerceIn(0f, 1f) * width
        val currentY = track.y.coerceIn(0f, 1f) * height

        softStroke.style = Paint.Style.STROKE
        softStroke.strokeWidth = 3f * scale
        softStroke.color = VIOLET
        softStroke.pathEffect = dash
        canvas.drawLine(currentX, currentY, x, y, softStroke)

        fill.style = Paint.Style.FILL
        fill.color = AMBER
        canvas.drawCircle(x, y, 5f * scale, fill)

        stroke.style = Paint.Style.STROKE
        stroke.strokeWidth = 2f * scale
        stroke.color = WHITE
        canvas.drawCircle(x, y, 8f * scale, stroke)
    }

    private fun drawGhost(canvas: Canvas, track: EnemyTrackVisual, scale: Float) {
        val age = track.ageMs.coerceIn(0L, config.ghostTtlMs)
        val fade = (1f - age.toFloat() / config.ghostTtlMs.toFloat()).coerceIn(0.20f, 0.78f)

        val xRaw = track.x
        val yRaw = track.y
        val x = xRaw.coerceIn(0f, 1f) * width
        val y = yRaw.coerceIn(0f, 1f) * height

        fill.style = Paint.Style.FILL
        fill.color = withAlpha(VIOLET, (95f * fade).toInt())
        canvas.drawCircle(x, y, 26f * scale, fill)

        stroke.style = Paint.Style.STROKE
        stroke.strokeWidth = 2.5f * scale
        stroke.color = withAlpha(VIOLET, (210f * fade).toInt())

        rect.set(
            x - 30f * scale,
            y - 30f * scale,
            x + 30f * scale,
            y + 30f * scale
        )
        canvas.drawOval(rect, stroke)

        // Compact memory label. Prepared before drawing; no String allocation in onDraw.
        text.textSize = 11f * scale
        text.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        text.color = withAlpha(WHITE, (220f * fade).toInt())
        canvas.drawText(track.ageText, x + 33f * scale, y - 25f * scale, text)

        drawGhostId(canvas, track.id, x, y, scale)

        val offscreen = track.predictedX < 0f || track.predictedX > 1f ||
            track.predictedY < 0f || track.predictedY > 1f

        if (offscreen) {
            val dx = track.predictedX - track.x
            val dy = track.predictedY - track.y
            drawEdgeArrow(canvas, dx, dy, scale, VIOLET)
        }
    }

    private fun drawGhostId(
        canvas: Canvas,
        id: Int,
        x: Float,
        y: Float,
        scale: Float
    ) {
        text.textSize = 9f * scale
        text.typeface = Typeface.DEFAULT
        text.color = withAlpha(WHITE, 150)
        canvas.drawText(frame.trackVisuals.firstOrNull { it.id == id }?.idText ?: "#", x - 12f * scale, y + 39f * scale, text)
    }

    private fun drawPlayerRange(canvas: Canvas, scale: Float) {
        val player = frame.player ?: return
        val raw = config.attackRangeRaw.coerceAtLeast(0.1f)
        val radius = raw * config.rangeUnitScreenFraction *
            minOf(width, height).toFloat()

        stroke.style = Paint.Style.STROKE
        stroke.strokeWidth = 2.2f * scale
        stroke.color = withAlpha(CYAN, 145)
        stroke.pathEffect = null
        canvas.drawCircle(
            player.cx.coerceIn(0f, 1f) * width,
            player.cy.coerceIn(0f, 1f) * height,
            radius.coerceAtMost(minOf(width, height) * 0.48f),
            stroke
        )
    }

    private fun drawEnemyRanges(canvas: Canvas, scale: Float) {
        val raw = config.enemyDefaultRangeRaw.coerceAtLeast(0.1f)
        val radius = raw * config.rangeUnitScreenFraction *
            minOf(width, height).toFloat()

        stroke.style = Paint.Style.STROKE
        stroke.strokeWidth = 1.5f * scale
        stroke.color = withAlpha(BLUE, 80)

        for (enemy in frame.enemies) {
            canvas.drawCircle(
                enemy.cx * width,
                enemy.cy * height,
                radius.coerceAtMost(minOf(width, height) * 0.48f),
                stroke
            )
        }
    }

    private fun drawWarning(canvas: Canvas, scale: Float) {
        when (frame.warning.type) {
            WarningType.LOW_HP -> drawLowHpEdge(canvas, scale)
            WarningType.SURROUNDED -> {
                drawEdgeArrow(canvas, frame.warning.dir1X, frame.warning.dir1Y, scale, MAGENTA)
                drawEdgeArrow(canvas, frame.warning.dir2X, frame.warning.dir2Y, scale, MAGENTA)
            }
            WarningType.GAS -> {
                drawEdgeArrow(canvas, frame.warning.dir1X, frame.warning.dir1Y, scale, AMBER)
            }
            WarningType.NONE -> Unit
        }
    }

    private fun drawLowHpEdge(canvas: Canvas, scale: Float) {
        val pulse = 0.62f
        fill.style = Paint.Style.FILL
        fill.color = withAlpha(MAGENTA, (90f * pulse).toInt())

        val edge = 18f * scale
        canvas.drawRect(0f, 0f, width.toFloat(), edge, fill)
        canvas.drawRect(0f, height - edge, width.toFloat(), height.toFloat(), fill)
        canvas.drawRect(0f, 0f, edge, height.toFloat(), fill)
        canvas.drawRect(width - edge, 0f, width.toFloat(), height.toFloat(), fill)
    }

    private fun drawEdgeArrow(
        canvas: Canvas,
        dx: Float,
        dy: Float,
        scale: Float,
        color: Int
    ) {
        val len = hypot(dx.toDouble(), dy.toDouble()).toFloat()
        if (len < 0.12f) return

        val ux = dx / len
        val uy = dy / len

        val edgePadding = 54f * scale
        val cx: Float
        val cy: Float

        if (abs(ux) > abs(uy)) {
            cx = if (ux >= 0f) width - edgePadding else edgePadding
            cy = (height * (0.5f + uy * 0.30f)).coerceIn(edgePadding, height - edgePadding)
        } else {
            cy = if (uy >= 0f) height - edgePadding else edgePadding
            cx = (width * (0.5f + ux * 0.30f)).coerceIn(edgePadding, width - edgePadding)
        }

        val length = 46f * scale
        val ex = cx + ux * length
        val ey = cy + uy * length
        val angle = atan2(uy.toDouble(), ux.toDouble())

        stroke.style = Paint.Style.STROKE
        stroke.strokeWidth = 6f * scale
        stroke.strokeCap = Paint.Cap.ROUND
        stroke.color = color
        canvas.drawLine(cx, cy, ex, ey, stroke)

        arrowPath.reset()
        val head = 17f * scale
        arrowPath.moveTo(ex, ey)
        arrowPath.lineTo(
            ex - cos(angle - 0.58f) * head,
            ey - sin(angle - 0.58f) * head
        )
        arrowPath.lineTo(
            ex - cos(angle + 0.58f) * head,
            ey - sin(angle + 0.58f) * head
        )
        arrowPath.close()

        fill.style = Paint.Style.FILL
        fill.color = color
        canvas.drawPath(arrowPath, fill)

    }

    private fun drawSuperReady(canvas: Canvas, scale: Float) {
        val cx = frame.hudButtonX(config) * width
        val cy = frame.hudButtonY(config) * height
        val base = config.superButtonRadius * minOf(width, height).toFloat()

        stroke.style = Paint.Style.STROKE
        stroke.strokeCap = Paint.Cap.ROUND
        stroke.color = AMBER
        stroke.strokeWidth = 2.4f * scale
        canvas.drawCircle(cx, cy, base, stroke)

        val now = SystemClock.elapsedRealtime()
        if (now < pulseUntil) {
            val p = 1f - (now % 350L).toFloat() / 350f
            stroke.color = withAlpha(AMBER, (110f * (1f - p)).toInt())
            stroke.strokeWidth = 1.8f * scale
            canvas.drawCircle(cx, cy, base * (1.0f + 0.16f * p), stroke)
        }
    }

    private fun drawDebug(canvas: Canvas, scale: Float) {
        val left = 16f * scale
        val top = 16f * scale
        val right = minOf(width.toFloat() - 16f * scale, 420f * scale)
        val bottom = top + 34f * scale

        fill.style = Paint.Style.FILL
        fill.color = PANEL
        rect.set(left, top, right, bottom)
        canvas.drawRoundRect(rect, 12f * scale, 12f * scale, fill)

        text.textSize = 11f * scale
        text.typeface = Typeface.DEFAULT
        text.color = WHITE
        canvas.drawText(
            frame.debugText,
            left + 10f * scale,
            top + 22f * scale,
            text
        )
    }

    private fun visualSignature(next: BrainFrame): Long {
        var h = 17L
        fun mix(v: Long) {
            h = h * 31L + v
        }

        mix(next.warning.type.ordinal.toLong())
        mix(next.hud.superReady.compareTo(false).toLong())
        mix((next.hud.ammo + 2).toLong())

        for (t in next.trackVisuals) {
            mix(t.id.toLong())
            mix((t.x * 1000f).toInt().toLong())
            mix((t.y * 1000f).toInt().toLong())
            mix((t.predictedX * 1000f).toInt().toLong())
            mix((t.predictedY * 1000f).toInt().toLong())
            mix(t.ageText.hashCode().toLong())
        }

        mix((next.safeZoneX * 1000f).toInt().toLong())
        mix((next.safeZoneY * 1000f).toInt().toLong())
        return h
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
private fun BrainFrame.hudButtonX(config: BrainConfig): Float = config.superButtonX
private fun BrainFrame.hudButtonY(config: BrainConfig): Float = config.superButtonY
