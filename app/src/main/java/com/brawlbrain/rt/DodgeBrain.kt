package com.brawlbrain.rt

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

data class DodgeCommand(
    val shouldDodge: Boolean,
    val x: Float,
    val y: Float,
    val reason: String
)

class DodgeBrain {
    private var lastEnemy: Detection? = null
    private var lastAt = 0L
    private var side = 1f
    private var lastDodgeAt = 0L

    fun decide(
        player: Detection?,
        enemies: List<Detection>,
        now: Long,
        cfg: BrainConfig
    ): DodgeCommand {
        if (!cfg.autoDodge || player == null || enemies.isEmpty()) {
            return DodgeCommand(false, 0f, 0f, "OFF")
        }

        val candidate = enemies
            .filter { it.confidence >= cfg.confidencePercent / 100f && distance(player, it) >= 0.30f }
            .minByOrNull { distance(player, it) }
            ?: return DodgeCommand(false, 0f, 0f, "NO-RANGED-TARGET")

        if (now - lastDodgeAt < cfg.dodgeCooldownMs) {
            return DodgeCommand(false, 0f, 0f, "COOLDOWN")
        }

        val previous = lastEnemy
        val dt = if (previous == null || lastAt == 0L) 0.12f
        else ((now - lastAt).coerceAtLeast(30L) / 1000f)

        val vx = if (previous == null) 0f
        else ((candidate.cx - previous.cx) / dt).coerceIn(-2.5f, 2.5f)
        val vy = if (previous == null) 0f
        else ((candidate.cy - previous.cy) / dt).coerceIn(-2.5f, 2.5f)

        lastEnemy = candidate
        lastAt = now

        val dx = player.cx - candidate.cx
        val dy = player.cy - candidate.cy
        val distance = hypot(dx, dy)

        val closing = (vx * dx + vy * dy) / max(0.001f, distance)
        val predictedX = candidate.cx + vx * (cfg.dodgeReactionMs / 1000f)
        val predictedY = candidate.cy + vy * (cfg.dodgeReactionMs / 1000f)

        val lineX = player.cx - predictedX
        val lineY = player.cy - predictedY
        val lineLen = max(0.001f, hypot(lineX, lineY))
        val nx = -lineY / lineLen
        val ny = lineX / lineLen

        val danger = (
            (0.72f - distance).coerceIn(0f, 1f) * 0.48f +
            abs(closing).coerceIn(0f, 1f) * 0.22f +
            candidate.confidence * 0.30f
        ).coerceIn(0f, 1f)

        val should = danger >= 0.42f
        if (!should) return DodgeCommand(false, 0f, 0f, "WAIT")

        val lateral = vx * nx + vy * ny
        if (abs(lateral) > 0.18f) {
            side = if (lateral > 0f) -1f else 1f
        } else {
            side = -side
        }

        val strength = cfg.dodgeStrengthPercent.coerceIn(35, 100) / 100f
        val forwardBreak = if (closing > 0.1f) 0.18f else 0.08f

        val commandX = (nx * side * strength + dx / lineLen * forwardBreak).coerceIn(-1f, 1f)
        val commandY = (ny * side * strength + dy / lineLen * forwardBreak).coerceIn(-1f, 1f)

        lastDodgeAt = now

        val angle = Math.toDegrees(atan2(commandY.toDouble(), commandX.toDouble())).toInt()
        return DodgeCommand(true, commandX, commandY, "DODGE $angle°")
    }

    private fun distance(a: Detection, b: Detection): Float =
        (hypot(a.cx - b.cx, a.cy - b.cy) * 1.45f).coerceIn(0f, 1f)
}