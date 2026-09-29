package com.brawlbrain.rt

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

class TacticalBrain {

    fun decide(
        detections: List<Detection>,
        walls: List<Detection>,
        fps: Float,
        inferenceMs: Long,
        engine: String
    ): BrainFrame {
        val player = detections.firstOrNull { it.label == "player" }
        val enemies = detections.filter { it.label == "enemy" }
        val teammates = detections.filter { it.label == "teammate" }

        val px = player?.cx ?: 0.5f
        val py = player?.cy ?: 0.5f
        val target = enemies.minByOrNull { distance(px, py, it.cx, it.cy) }
        val nearest = target?.let { distance(px, py, it.cx, it.cy) } ?: 1f

        val closeThreat = (1f - nearest / 0.34f).coerceIn(0f, 1f)
        val crowdThreat = (enemies.size / 4f).coerceIn(0f, 1f) * 0.22f
        val coverSignal = (
            walls.count { it.label == "wall" || it.label == "close_bush" } / 7f
        ).coerceIn(0f, 1f) * 0.12f

        val threat = (
            0.68f * closeThreat +
            0.22f * crowdThreat +
            0.10f * coverSignal
        ).coerceIn(0f, 1f)

        val recommendation = when {
            target == null && teammates.isEmpty() -> Recommendation.RESET
            threat >= 0.78f -> Recommendation.RETREAT
            nearest < 0.28f -> Recommendation.TRACK
            nearest < 0.52f -> Recommendation.HOLD
            enemies.isNotEmpty() -> Recommendation.PRESSURE
            else -> Recommendation.RESET
        }

        return BrainFrame(
            player,
            enemies,
            teammates,
            walls,
            recommendation,
            target,
            threat,
            enemies.size,
            fps,
            inferenceMs,
            engine
        )
    }

    private fun distance(ax: Float, ay: Float, bx: Float, by: Float): Float {
        return min(1f, hypot(ax - bx, ay - by) * 1.45f)
    }
}