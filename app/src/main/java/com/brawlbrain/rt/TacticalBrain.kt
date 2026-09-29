package com.brawlbrain.rt

import android.os.SystemClock

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

        val scored = enemies.map { enemy ->
            val d = distance(px, py, enemy.cx, enemy.cy)
            val confidence = enemy.confidence
            val size = (enemy.width * enemy.height * 5f).coerceIn(0f, 1f)
            val centrality = 1f - distance(0.5f, 0.5f, enemy.cx, enemy.cy)
            val score = 0.48f * (1f - d) +
                0.22f * confidence +
                0.18f * size +
                0.12f * centrality
            enemy to score
        }.sortedByDescending { it.second }

        val target = scored.firstOrNull()?.first
        val nearest = target?.let { distance(px, py, it.cx, it.cy) } ?: 1f

        val now = SystemClock.elapsedRealtime()
        val dt = if (lastTime == 0L) 0.1f else ((now - lastTime).coerceAtLeast(16L) / 1000f)
        val vx = if (target == null) 0f else ((target.cx - lastTargetX) / dt).coerceIn(-2.2f, 2.2f)
        val vy = if (target == null) 0f else ((target.cy - lastTargetY) / dt).coerceIn(-2.2f, 2.2f)

        if (target != null) {
            lastTargetX = target.cx
            lastTargetY = target.cy
            lastTime = now
        }

        val leadTime = when (config.role) {
            "Assassin" -> 0.16f
            "Shooter" -> 0.12f
            "Thrower" -> 0.18f
            else -> 0.10f
        }

        val leadX = ((target?.cx ?: lastTargetX) + vx * leadTime).coerceIn(0.02f, 0.98f)
        val leadY = ((target?.cy ?: lastTargetY) + vy * leadTime).coerceIn(0.02f, 0.98f)

        val closeThreat = if (target == null) 0f
        else (1f - nearest / 0.38f).coerceIn(0f, 1f)

        val numericPressure = (
            (enemies.size - teammates.size).coerceAtLeast(0) / 3f
        ).coerceIn(0f, 1f)

        val openFlank = enemies.count { enemy ->
            kotlin.math.abs(enemy.cx - px) > 0.28f && kotlin.math.abs(enemy.cy - py) > 0.18f
        } / 3f.coerceAtLeast(1f)
        val isolation = if (target == null) 0f else (
            1f - teammates.minOfOrNull { distance(target.cx, target.cy, it.cx, it.cy) }?.div(0.42f) ?: 1f
        ).coerceIn(0f, 1f)

        val cover = if (walls.isEmpty()) 0f else (
            walls.count { it.label == "wall" || it.label == "close_bush" } / 6f
        ).coerceIn(0f, 1f)

        val roleRisk = when (config.role) {
            "Assassin" -> 0.86f
            "Tank" -> 0.72f
            "Shooter" -> 0.52f
            "Thrower" -> 0.44f
            "Support" -> 0.38f
            else -> 0.60f
        }

        val modeRisk = when (config.gameMode) {
            "Showdown", "Knockout", "Bounty" -> 0.14f
            "Brawl Ball", "Heist" -> -0.08f
            "Hot Zone", "Gem Grab" -> 0.02f
            else -> 0f
        }

        val rawThreat = (
            0.46f * closeThreat +
            0.18f * numericPressure +
            0.15f * openFlank +
            0.12f * isolation +
            0.09f * (1f - cover)
        ).coerceIn(0f, 1f)

        stableThreat = stableThreat * 0.68f + rawThreat * 0.32f
        val opportunity = (
            0.38f * (1f - stableThreat) +
            0.25f * if (target == null) 0f else target.confidence +
            0.17f * (1f - numericPressure) +
            0.10f * cover +
            0.10f * roleRisk - modeRisk
        ).coerceIn(0f, 1f)

        val recommendation = when {
            target == null -> Recommendation.RESET
            stableThreat >= 0.72f -> Recommendation.RETREAT
            opportunity >= 0.68f -> Recommendation.PRESSURE
            nearest <= 0.22f -> Recommendation.TRACK
            else -> Recommendation.HOLD
        }

        val detail = when (recommendation) {
            Recommendation.RETREAT -> "Отходи к укрытию; риск слишком высокий"
            Recommendation.PRESSURE -> "Есть окно: цель уязвима, можно давить"
            Recommendation.TRACK -> "Цель близко: следи за траекторией и не стой на месте"
            Recommendation.HOLD -> "Держи угол и не отдавай пространство"
            Recommendation.RESET -> "Мало информации: сначала найди противника"
        }

        val escapeAngle = when {
            target == null -> 0f
            else -> {
                val ex = px - target.cx
                val ey = py - target.cy
                val m = max(0.001f, hypot(ex, ey))
                ex / m
            }
        }

        val escapeX = if (target == null) 0f else escapeAngle
        val escapeY = if (target == null) 0f else {
            val ey = py - target.cy
            val ex = px - target.cx
            val m = max(0.001f, hypot(ex, ey))
            ey / m
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
            role = config.role
        )
    }

    private fun distance(ax: Float, ay: Float, bx: Float, by: Float): Float {
        return min(1f, hypot(ax - bx, ay - by) * 1.45f)
    }
}