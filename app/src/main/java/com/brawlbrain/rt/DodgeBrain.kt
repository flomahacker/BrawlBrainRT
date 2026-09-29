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
        cfg: BrainConfig,
        projectile: ProjectileThreat? = null
    ): DodgeCommand {
        if (!cfg.autoDodge || player == null) {
            resetWhenOff(cfg.autoDodge)
            return DodgeCommand(false, 0f, 0f, "OFF")
        }

        if (projectile?.urgent == true && projectile.enemy != null &&
            now - lastDodgeAt >= 55L
        ) {
            val emergency = emergencyDodge(
                player,
                projectile.enemy,
                cfg.dodgeStrengthPercent,
                now
            )
            if (emergency.shouldDodge) return emergency
        }

        if (enemies.isEmpty()) {
            return DodgeCommand(false, 0f, 0f, "NO-TARGET")
        }

        val candidate = enemies
            .filter { it.confidence >= cfg.confidencePercent / 100f && distance(player, it) >= 0.24f }
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

        val projectileAssist = projectile?.score ?: 0f
        val combinedDanger = max(danger, projectileAssist * 0.82f)

        val should = combinedDanger >= 0.36f
        if (!should) return DodgeCommand(false, 0f, 0f, "WAIT")

        val lateral = vx * nx + vy * ny
        if (abs(lateral) > 0.18f) {
            side = if (lateral > 0f) -1f else 1f
        } else {
            side = -side
        }

        val strength = (
            cfg.dodgeStrengthPercent.coerceIn(35, 100) / 100f
        ) * (1f + combinedDanger * 0.26f)
        val s = strength.coerceIn(0.35f, 1f)
        val forwardBreak = if (closing > 0.1f) 0.18f else 0.08f

        var commandX = nx * side * s + dx / lineLen * forwardBreak
        var commandY = ny * side * s + dy / lineLen * forwardBreak

        val commandLen = hypot(commandX, commandY)
        if (commandLen > 1f) {
            commandX /= commandLen
            commandY /= commandLen
        }

        lastDodgeAt = now

        val angle = Math.toDegrees(atan2(commandY.toDouble(), commandX.toDouble())).toInt()
        return DodgeCommand(
            true,
            commandX.coerceIn(-1f, 1f),
            commandY.coerceIn(-1f, 1f),
            "DODGE $angle° • danger ${(combinedDanger * 100f).toInt()}%"
        )
    }

    private fun emergencyDodge(
        player: Detection,
        enemy: Detection,
        strengthPercent: Int,
        now: Long
    ): DodgeCommand {
        val dx = player.cx - enemy.cx
        val dy = player.cy - enemy.cy
        val len = max(0.001f, hypot(dx, dy))

        val perpX = -dy / len
        val perpY = dx / len

        side = -side

        val strength = (
            strengthPercent.coerceIn(45, 100) / 100f
        ).coerceIn(0.45f, 1f)

        var x = perpX * side * strength + dx / len * 0.12f
        var y = perpY * side * strength + dy / len * 0.12f

        val m = hypot(x, y)
        if (m > 1f) {
            x /= m
            y /= m
        }

        lastDodgeAt = now

        val angle = Math.toDegrees(atan2(y.toDouble(), x.toDouble())).toInt()
        return DodgeCommand(
            true,
            x.coerceIn(-1f, 1f),
            y.coerceIn(-1f, 1f),
            "PROJECTILE EVADE $angle°"
        )
    }

    private fun resetWhenOff(enabled: Boolean) {
        if (enabled) return
        lastEnemy = null
        lastAt = 0L
        lastDodgeAt = 0L
        side = 1f
    }

    private fun distance(a: Detection, b: Detection): Float =
        (hypot(a.cx - b.cx, a.cy - b.cy) * 1.45f).coerceIn(0f, 1f)
}