package com.brawlbrain.rt

import android.media.Image
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

data class ProjectileThreat(
    val detected: Boolean,
    val urgent: Boolean,
    val score: Float,
    val etaMs: Int,
    val enemy: Detection?,
    val reason: String
)

class ProjectileThreatAnalyzer {

    private val gridW = 144
    private val gridH = 96

    private var previous: ByteArray? = null
    private val tracks = ArrayList<Track>()

    private data class Track(
        var x: Float,
        var y: Float,
        var lastPeakT: Float = -1f,
        var lastPeakAt: Long = 0L
    )

    fun reset() {
        previous = null
        tracks.clear()
    }

    fun analyze(
        image: Image,
        captureW: Int,
        captureH: Int,
        player: Detection?,
        enemies: List<Detection>,
        now: Long
    ): ProjectileThreat? {
        val current = buildGray(image, captureW, captureH)
        val old = previous
        previous = current

        if (player == null || enemies.isEmpty() || old == null) {
            pruneTracks(now)
            return null
        }

        pruneTracks(now)

        val globalMotion = globalMotion(current, old)
        val globalFactor = when {
            globalMotion >= 0.55f -> 0.46f
            globalMotion >= 0.38f -> 0.68f
            else -> 1f
        }

        var best: ProjectileThreat? = null

        enemies.asSequence()
            .filter { it.confidence >= 0.30f }
            .filter {
                val d = hypot(it.cx - player.cx, it.cy - player.cy)
                d >= 0.17f && d <= 0.92f
            }
            .take(3)
            .forEach { enemy ->
                val lineX = player.cx - enemy.cx
                val lineY = player.cy - enemy.cy

                val energies = FloatArray(13)
                var peak = 0f
                var peakIndex = 0
                var totalEnergy = 0f
                var directionalEnergy = 0f
                var weightedT = 0f

                for (i in energies.indices) {
                    val t = 0.15f + (0.72f * i / 12f)
                    val bx = enemy.cx + lineX * t
                    val by = enemy.cy + lineY * t

                    val len = hypot(lineX, lineY).coerceAtLeast(0.001f)
                    val nx = -lineY / len
                    val ny = lineX / len

                    val center = motionAt(current, old, bx, by)
                    val sideA = motionAt(
                        current, old,
                        bx + nx * 0.024f,
                        by + ny * 0.024f
                    )
                    val sideB = motionAt(
                        current, old,
                        bx - nx * 0.024f,
                        by - ny * 0.024f
                    )

                    val energy = max(center, max(sideA, sideB)).coerceIn(0f, 1f)
                    energies[i] = energy

                    if (energy > peak) {
                        peak = energy
                        peakIndex = i
                    }

                    totalEnergy += energy
                    weightedT += t * energy

                    // A shot travelling toward the player should look more like
                    // its previous-frame position just behind it than the position
                    // it would have occupied farther toward the player.
                    val stepX = lineX * 0.075f
                    val stepY = lineY * 0.075f
                    val forwardSimilarity = patchSimilarity(
                        current, old,
                        bx, by,
                        bx - stepX, by - stepY
                    )
                    val backwardSimilarity = patchSimilarity(
                        current, old,
                        bx, by,
                        bx + stepX, by + stepY
                    )

                    val directed = (
                        (forwardSimilarity - backwardSimilarity)
                            .coerceAtLeast(0f) * energy
                    )
                    directionalEnergy += directed
                }

                val peakT = 0.15f + (0.72f * peakIndex / 12f)
                val meanT = if (totalEnergy > 0.001f) {
                    weightedT / totalEnergy
                } else {
                    peakT
                }

                var playerBand = 0f
                var playerCount = 0
                var enemyBand = 0f
                var enemyCount = 0

                for (i in energies.indices) {
                    val t = 0.15f + (0.72f * i / 12f)
                    if (t >= 0.70f) {
                        playerBand += energies[i]
                        playerCount++
                    } else if (t <= 0.34f) {
                        enemyBand += energies[i]
                        enemyCount++
                    }
                }

                playerBand /= max(1, playerCount).toFloat()
                enemyBand /= max(1, enemyCount).toFloat()

                val directionScore = if (totalEnergy > 0.001f) {
                    (directionalEnergy / totalEnergy).coerceIn(0f, 1f)
                } else {
                    0f
                }

                val track = findTrack(enemy)
                val hasHistory =
                    track.lastPeakAt > 0L && now - track.lastPeakAt <= 190L
                val shift = if (hasHistory) peakT - track.lastPeakT else 0f
                val trackAdvance = if (hasHistory) {
                    (shift / 0.16f).coerceIn(0f, 1f)
                } else {
                    0f
                }

                val advanceScore = max(directionScore, trackAdvance)
                val effectivePeak = peak * globalFactor
                val effectivePlayerBand = playerBand * globalFactor

                val corridorScore = (
                    effectivePeak * 0.46f +
                    effectivePlayerBand * 0.16f +
                    advanceScore * 0.38f
                ).coerceIn(0f, 1f)

                val detected = effectivePeak >= 0.20f && (
                    advanceScore >= 0.26f ||
                    effectivePlayerBand >= 0.28f ||
                    (enemyBand * globalFactor >= 0.40f && effectivePeak >= 0.44f)
                )

                val urgent = effectivePeak >= 0.27f && (
                    advanceScore >= 0.40f ||
                    effectivePlayerBand >= 0.40f ||
                    (peakT >= 0.62f && effectivePeak >= 0.38f)
                )

                track.x = enemy.cx
                track.y = enemy.cy
                track.lastPeakT = peakT
                track.lastPeakAt = now

                if (detected) {
                    val eta = (
                        ((1f - meanT).coerceIn(0.10f, 0.92f) * 350f) +
                            if (urgent) 30f else 85f
                    ).toInt().coerceIn(45, 420)

                    val threat = ProjectileThreat(
                        detected = true,
                        urgent = urgent,
                        score = corridorScore,
                        etaMs = eta,
                        enemy = enemy,
                        reason = if (urgent) {
                            "PROJECTILE • DANGER"
                        } else {
                            "PROJECTILE? • TRACK"
                        }
                    )

                    if (best == null || threat.score > best!!.score) {
                        best = threat
                    }
                }
            }

        return best
    }

    private fun findTrack(enemy: Detection): Track {
        var closest: Track? = null
        var closestD = 0.14f

        for (track in tracks) {
            val d = hypot(track.x - enemy.cx, track.y - enemy.cy)
            if (d < closestD) {
                closest = track
                closestD = d
            }
        }

        if (closest != null) return closest

        val created = Track(enemy.cx, enemy.cy)
        tracks.add(created)
        return created
    }

    private fun pruneTracks(now: Long) {
        tracks.removeAll { now - it.lastPeakAt > 500L && it.lastPeakAt > 0L }
        while (tracks.size > 4) tracks.removeAt(0)
    }

    private fun motionAt(
        current: ByteArray,
        old: ByteArray,
        nx: Float,
        ny: Float
    ): Float {
        val gx = (nx.coerceIn(0f, 0.999f) * gridW).toInt()
        val gy = (ny.coerceIn(0f, 0.999f) * gridH).toInt()

        var total = 0
        var count = 0

        for (oy in -1..1) {
            for (ox in -1..1) {
                val x = (gx + ox).coerceIn(0, gridW - 1)
                val y = (gy + oy).coerceIn(0, gridH - 1)
                val i = y * gridW + x
                total += abs(current[i].toInt() - old[i].toInt())
                count++
            }
        }

        return (total / max(1, count)).toFloat() / 54f
    }

    private fun patchSimilarity(
        current: ByteArray,
        old: ByteArray,
        currentX: Float,
        currentY: Float,
        oldX: Float,
        oldY: Float
    ): Float {
        val cx = (currentX.coerceIn(0f, 0.999f) * gridW).toInt()
        val cy = (currentY.coerceIn(0f, 0.999f) * gridH).toInt()
        val ox = (oldX.coerceIn(0f, 0.999f) * gridW).toInt()
        val oy = (oldY.coerceIn(0f, 0.999f) * gridH).toInt()

        var total = 0
        var count = 0

        for (dy in -1..1) {
            for (dx in -1..1) {
                val cxx = (cx + dx).coerceIn(0, gridW - 1)
                val cyy = (cy + dy).coerceIn(0, gridH - 1)
                val oxx = (ox + dx).coerceIn(0, gridW - 1)
                val oyy = (oy + dy).coerceIn(0, gridH - 1)

                total += abs(
                    current[cyy * gridW + cxx].toInt() -
                        old[oyy * gridW + oxx].toInt()
                )
                count++
            }
        }

        val difference = total / max(1, count).toFloat()
        return (1f - difference / 72f).coerceIn(0f, 1f)
    }

    private fun globalMotion(
        current: ByteArray,
        old: ByteArray
    ): Float {
        var total = 0
        var count = 0
        var y = 0

        while (y < gridH) {
            var x = 0
            while (x < gridW) {
                val i = y * gridW + x
                total += abs(current[i].toInt() - old[i].toInt())
                count++
                x += 6
            }
            y += 6
        }

        return (total / max(1, count)).toFloat() / 54f
    }

    private fun buildGray(
        image: Image,
        captureW: Int,
        captureH: Int
    ): ByteArray {
        val out = ByteArray(gridW * gridH)
        val plane = image.planes[0]
        val buffer: ByteBuffer = plane.buffer.duplicate()
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val limit = buffer.limit()

        for (y in 0 until gridH) {
            val sy = (y * captureH / gridH).coerceIn(0, captureH - 1)
            val rowBase = sy * rowStride

            for (x in 0 until gridW) {
                val sx = (x * captureW / gridW).coerceIn(0, captureW - 1)
                val offset = rowBase + sx * pixelStride

                if (offset + 2 >= limit) continue

                val r = buffer.get(offset).toInt() and 0xFF
                val g = buffer.get(offset + 1).toInt() and 0xFF
                val b = buffer.get(offset + 2).toInt() and 0xFF

                out[y * gridW + x] = (
                    (77 * r + 150 * g + 29 * b) shr 8
                ).coerceIn(0, 255).toByte()
            }
        }

        return out
    }
}
