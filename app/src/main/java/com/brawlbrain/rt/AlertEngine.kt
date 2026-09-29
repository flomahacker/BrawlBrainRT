package com.brawlbrain.rt

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max

class AlertEngine {
    private var active = WarningType.NONE
    private var activeSince = 0L
    private var lastTrigger = 0L

    fun update(
        player: Detection?,
        enemies: List<Detection>,
        hud: HudState,
        zone: SafeZoneState,
        config: BrainConfig,
        now: Long,
        enabled: Boolean,
        showdown: Boolean
    ): CriticalWarning {
        if (!enabled || player == null) {
            active = WarningType.NONE
            return CriticalWarning()
        }

        val nearEnter = enemies.any {
            distance(player, it) <= config.nearbyEnemyEnterDistance
        }
        val nearExit = enemies.none {
            distance(player, it) <= config.nearbyEnemyExitDistance
        }

        val hpEnter = hud.hpConfidence >= 0.16f &&
            hud.hp <= config.hpEnterThreshold && nearEnter
        val hpExit = hud.hpConfidence < 0.10f ||
            hud.hp >= config.hpExitThreshold || nearExit

        val surroundEnter = surrounded(player, enemies, config.surroundedEnterMinAngleRad)
        val surroundExit = !surrounded(player, enemies, config.surroundedExitMinAngleRad)

        val zoneEnter = showdown && zone.detected &&
            hypot(zone.x.toDouble(), zone.y.toDouble()).toFloat() >= config.gasDirectionMin
        val zoneExit = !showdown || !zone.detected ||
            zone.score < config.gasExitEdgeDelta

        active = when (active) {
            WarningType.LOW_HP ->
                if (hpExit && now - activeSince >= config.warningMinOnMs) WarningType.NONE
                else WarningType.LOW_HP
            WarningType.SURROUNDED ->
                if (surroundExit && now - activeSince >= config.warningMinOnMs) WarningType.NONE
                else WarningType.SURROUNDED
            WarningType.GAS ->
                if (zoneExit && now - activeSince >= config.warningMinOnMs) WarningType.NONE
                else WarningType.GAS
            WarningType.NONE -> WarningType.NONE
        }

        if (active == WarningType.NONE &&
            now - lastTrigger >= config.warningCooldownMs
        ) {
            when {
                hpEnter -> activate(WarningType.LOW_HP, now)
                surroundEnter -> activate(WarningType.SURROUNDED, now)
                zoneEnter -> activate(WarningType.GAS, now)
            }
        }

        return when (active) {
            WarningType.LOW_HP -> {
                val d = escapeFromNearest(player, enemies)
                CriticalWarning(WarningType.LOW_HP, d.first, d.second, active = true)
            }
            WarningType.SURROUNDED -> {
                val d = twoEscape(player, enemies, config.surroundedExitMinAngleRad)
                CriticalWarning(
                    WarningType.SURROUNDED,
                    d[0], d[1], d[2], d[3], true
                )
            }
            WarningType.GAS ->
                CriticalWarning(
                    WarningType.GAS,
                    zone.x, zone.y,
                    active = true
                )
            WarningType.NONE -> CriticalWarning()
        }
    }

    private fun activate(type: WarningType, now: Long) {
        active = type
        activeSince = now
        lastTrigger = now
    }

    private fun distance(a: Detection, b: Detection): Float =
        hypot(a.cx - b.cx, a.cy - b.cy) * 1.45f

    private fun surrounded(
        player: Detection,
        enemies: List<Detection>,
        minimumAngle: Float
    ): Boolean {
        val angles = FloatArray(6)
        var count = 0
        for (enemy in enemies) {
            if (distance(player, enemy) > 0.62f) continue
            angles[count++] = atan2(
                (enemy.cy - player.cy).toDouble(),
                (enemy.cx - player.cx).toDouble()
            ).toFloat()
            if (count == angles.size) break
        }
        if (count < 2) return false

        for (i in 0 until count) {
            for (j in i + 1 until count) {
                val raw = abs(angles[i] - angles[j])
                val wrapped = minOf(raw, (Math.PI * 2.0 - raw).toFloat())
                if (wrapped >= minimumAngle) return true
            }
        }
        return false
    }

    private fun escapeFromNearest(
        player: Detection,
        enemies: List<Detection>
    ): Pair<Float, Float> {
        var best = Float.MAX_VALUE
        var ex = player.cx
        var ey = player.cy
        for (enemy in enemies) {
            val d = distance(player, enemy)
            if (d < best) {
                best = d
                ex = enemy.cx
                ey = enemy.cy
            }
        }
        return normalized(player.cx - ex, player.cy - ey)
    }

    private fun twoEscape(
        player: Detection,
        enemies: List<Detection>,
        minimumAngle: Float
    ): FloatArray {
        var bestGap = 0f
        var result = floatArrayOf(0f, -1f, 0f, 1f)

        for (i in enemies.indices) {
            val a = enemies[i]
            val ad = distance(player, a)
            if (ad > 0.62f) continue
            val aa = atan2(
                (a.cy - player.cy).toDouble(),
                (a.cx - player.cx).toDouble()
            ).toFloat()

            for (j in i + 1 until enemies.size) {
                val b = enemies[j]
                val bd = distance(player, b)
                if (bd > 0.62f) continue
                val ba = atan2(
                    (b.cy - player.cy).toDouble(),
                    (b.cx - player.cx).toDouble()
                ).toFloat()
                val raw = abs(aa - ba)
                val gap = minOf(raw, (Math.PI * 2.0 - raw).toFloat())
                if (gap >= minimumAngle && gap > bestGap) {
                    bestGap = gap
                    val da = normalized(player.cx - a.cx, player.cy - a.cy)
                    val db = normalized(player.cx - b.cx, player.cy - b.cy)
                    result = floatArrayOf(da.first, da.second, db.first, db.second)
                }
            }
        }
        return result
    }

    private fun normalized(x: Float, y: Float): Pair<Float, Float> {
        val m = max(0.001f, hypot(x, y))
        return Pair(x / m, y / m)
    }
}
