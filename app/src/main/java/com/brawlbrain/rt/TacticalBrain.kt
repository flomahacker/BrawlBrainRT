package com.brawlbrain.rt

import android.os.SystemClock
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

class TacticalBrain {
    private var lastTargetX = 0.5f
    private var lastTargetY = 0.5f
    private var lastTime = 0L
    private var stableThreat = 0f

    fun decide(
        detections: List<Detection>,
        walls: List<Detection>,
        fps: Float,
        inferenceMs: Long,
        engine: String,
        config: BrainConfig
    ): BrainFrame {
        val player = detections.firstOrNull { it.label == "player" }
        val enemies = detections.filter { it.label == "enemy" }
        val teammates = detections.filter { it.label == "teammate" }

        val px = player?.cx ?: 0.5f
        val py = player?.cy ?: 0.5f

        val target = enemies.minByOrNull { distance(px, py, it.cx, it.cy) }
        val nearest = target?.let { distance(px, py, it.cx, it.cy) } ?: 1f

        val now = SystemClock.elapsedRealtime()
        val dt = if (lastTime == 0L) 0.12f
        else ((now - lastTime).coerceAtLeast(16L) / 1000f)

        val vx = if (target == null) 0f
        else ((target.cx - lastTargetX) / dt).coerceIn(-2.0f, 2.0f)
        val vy = if (target == null) 0f
        else ((target.cy - lastTargetY) / dt).coerceIn(-2.0f, 2.0f)

        if (target != null) {
            lastTargetX = target.cx
            lastTargetY = target.cy
            lastTime = now
        }

        val leadTime = when (config.brawler) {
            "Buzz" -> 0.13f
            "Tick" -> 0.20f
            else -> 0.12f
        }
        val leadX = ((target?.cx ?: lastTargetX) + vx * leadTime).coerceIn(0.02f, 0.98f)
        val leadY = ((target?.cy ?: lastTargetY) + vy * leadTime).coerceIn(0.02f, 0.98f)

        val closeThreat = if (target == null) 0f
        else (1f - nearest / 0.38f).coerceIn(0f, 1f)

        val enemyPressure = (enemies.size / 3f).coerceIn(0f, 1f)
        val allySupport = (teammates.size / 2f).coerceIn(0f, 1f)
        val enemySpread = if (enemies.isEmpty()) 0f else {
            val avg = enemies.map { distance(px, py, it.cx, it.cy) }.average().toFloat()
            (1f - avg).coerceIn(0f, 1f)
        }

        val cover = if (walls.isEmpty()) 0f else
            (walls.count { it.label == "wall" || it.label == "close_bush" } / 6f).coerceIn(0f, 1f)

        val isolation = if (target == null || teammates.isEmpty()) 0f else {
            val allyDistance = teammates.minOf {
                distance(target.cx, target.cy, it.cx, it.cy)
            }
            (1f - allyDistance / 0.42f).coerceIn(0f, 1f)
        }

        val rawThreat = (
            0.52f * closeThreat +
            0.18f * enemyPressure +
            0.16f * enemySpread +
            0.08f * isolation +
            0.06f * (1f - cover)
        ).coerceIn(0f, 1f)

        stableThreat = stableThreat * 0.72f + rawThreat * 0.28f

        val opportunityBase = (
            0.44f * (1f - stableThreat) +
            0.22f * (target?.confidence ?: 0f) +
            0.16f * allySupport +
            0.10f * cover +
            0.08f * (1f - enemyPressure)
        ).coerceIn(0f, 1f)

        val opportunity = when (config.brawler) {
            "Buzz" -> (opportunityBase + if (nearest in 0.16f..0.48f) 0.16f else 0f - if (nearest < 0.12f) 0.10f else 0f)
                .coerceIn(0f, 1f)
            "Tick" -> (opportunityBase + if (nearest > 0.35f) 0.12f else -0.08f)
                .coerceIn(0f, 1f)
            else -> opportunityBase
        }

        val recommendation: Recommendation
        val detail: String

        when (config.brawler) {
            "Buzz" -> when {
                target == null -> {
                    recommendation = Recommendation.RESET
                    detail = "Ищи цель: держись рядом с укрытием и заряжай зону"
                }
                stableThreat >= 0.74f -> {
                    recommendation = Recommendation.RETREAT
                    detail = "Не входи сейчас: слишком много угроз вокруг цели"
                }
                nearest in 0.20f..0.52f && opportunity >= 0.62f -> {
                    recommendation = Recommendation.PRESSURE
                    detail = "ОКНО BUZZ: сближение выгодно, цель на рабочей дистанции"
                }
                nearest < 0.20f -> {
                    recommendation = Recommendation.TRACK
                    detail = "БУЗЗ: не стой в лобовой — двигайся вокруг цели"
                }
                else -> {
                    recommendation = Recommendation.HOLD
                    detail = "БУЗЗ: заряжай супер и жди удобный угол входа"
                }
            }
            "Tick" -> when {
                target == null -> {
                    recommendation = Recommendation.RESET
                    detail = "ТИК: держи дистанцию и ищи маршрут для мин"
                }
                stableThreat >= 0.62f && nearest < 0.46f -> {
                    recommendation = Recommendation.RETREAT
                    detail = "ТИК: отступай — враг слишком близко к опасной зоне"
                }
                nearest > 0.40f && opportunity >= 0.50f -> {
                    recommendation = Recommendation.PRESSURE
                    detail = "ТИК: ЗОНИРУЙ вход и бросай мины в прогнозируемый путь"
                }
                nearest < 0.30f -> {
                    recommendation = Recommendation.TRACK
                    detail = "ТИК: цель сблизилась — уходи диагонально, не назад по прямой"
                }
                else -> {
                    recommendation = Recommendation.HOLD
                    detail = "ТИК: держи дальнюю дистанцию и перекрывай пути отхода"
                }
            }
            else -> {
                recommendation = when {
                    target == null -> Recommendation.RESET
                    stableThreat >= 0.72f -> Recommendation.RETREAT
                    opportunity >= 0.66f -> Recommendation.PRESSURE
                    nearest <= 0.22f -> Recommendation.TRACK
                    else -> Recommendation.HOLD
                }
                detail = when (recommendation) {
                    Recommendation.RETREAT -> "Отходи к безопасной позиции"
                    Recommendation.PRESSURE -> "Есть окно для давления"
                    Recommendation.TRACK -> "Следи за траекторией цели"
                    Recommendation.HOLD -> "Держи позицию"
                    Recommendation.RESET -> "Собери информацию о поле"
                }
            }
        }

        val escapeX: Float
        val escapeY: Float
        if (target == null) {
            escapeX = 0f
            escapeY = 0f
        } else {
            val ex = px - target.cx
            val ey = py - target.cy
            val m = max(0.001f, hypot(ex, ey))
            escapeX = ex / m
            escapeY = ey / m
        }

        return BrainFrame(
            player = player,
            enemies = enemies,
            teammates = teammates,
            walls = walls,
            recommendation = recommendation,
            recommendationDetail = detail,
            target = target,
            targetLeadX = leadX,
            targetLeadY = leadY,
            escapeX = escapeX,
            escapeY = escapeY,
            threat = stableThreat,
            opportunity = opportunity,
            cover = cover,
            isolation = isolation,
            enemyCount = enemies.size,
            fps = fps,
            inferenceMs = inferenceMs,
            engine = engine,
            gameMode = config.gameMode,
            role = config.brawler
        )
    }

    private fun distance(ax: Float, ay: Float, bx: Float, by: Float): Float {
        return min(1f, hypot(ax - bx, ay - by) * 1.45f)
    }
}