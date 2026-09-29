package com.brawlbrain.rt
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

data class BrainState(
    val enemyX: Float = 0.5f,
    val enemyY: Float = 0.5f,
    val enemyConfidence: Float = 0f,
    val danger: Float = 0f,
    val openSpace: Float = 1f,
    val targetDistance: Float = 1f,
    val velocityX: Float = 0f,
    val velocityY: Float = 0f,
    val frameMs: Long = 0L
)
enum class BrainMode { ASSIST, AUTO }
data class Action(
    val moveX: Float = 0f,
    val moveY: Float = 0f,
    val aimX: Float = 0.5f,
    val aimY: Float = 0.5f,
    val attack: Boolean = false,
    val confidence: Float = 0f,
    val reason: String = "SCAN"
)
class TacticalBrain {
    fun decide(s: BrainState): Action {
        val survival = max(s.danger, 1f - s.openSpace)
        val target = s.enemyConfidence
        val dx = s.enemyX - 0.5f
        val dy = s.enemyY - 0.5f
        val d = min(1f, hypot(dx, dy) * 1.45f)
        var mx: Float
        var my: Float
        if (survival > 0.58f && target > 0.12f) { mx = -dx; my = -dy }
        else { mx = -dx * 0.55f; my = -dy * 0.55f }
        val mag = max(0.0001f, hypot(mx, my))
        val scale = min(1f, 0.28f + 0.72f * survival)
        mx = (mx / mag) * scale
        my = (my / mag) * scale
        val ax = (s.enemyX + s.velocityX * 0.10f).coerceIn(0f, 1f)
        val ay = (s.enemyY + s.velocityY * 0.10f).coerceIn(0f, 1f)
        val attack = target > 0.63f && d < 0.92f && survival < 0.82f
        val confidence = (0.45f * target + 0.35f * (1f - survival) + 0.20f * s.openSpace).coerceIn(0f, 1f)
        return Action(mx, my, ax, ay, attack, confidence,
            reason = when {
                survival > 0.72f -> "DANGER"
                attack -> "TARGET LOCK"
                target > 0.35f -> "TRACK"
                else -> "POSITION"
            })
    }
}