package com.brawlbrain.rt

import android.media.Image
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

data class ProjectileThreat(
    val detected: Boolean,
    val urgent: Boolean,
    val score: Float,
    val etaMs: Int,
    val enemy: Detection?,
    val reason: String
)

class ProjectileThreatAnalyzer {

    private val gridW = 160
    private val gridH = 90
    private val total = gridW * gridH

    private var previous: ByteArray? = null
    private var previousBlobs: List<MotionBlob> = emptyList()

    private data class MotionBlob(
        val x: Float,
        val y: Float,
        val size: Int,
        val width: Int,
        val height: Int
    )

    fun reset() {
        previous = null
        previousBlobs = emptyList()
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

        if (old == null || player == null || enemies.isEmpty()) {
            previousBlobs = emptyList()
            return null
        }

        val threshold = adaptiveThreshold(current, old)
        val currentBlobs = findMotionBlobs(current, old, threshold)
        val best = evaluateBlobs(currentBlobs, previousBlobs, player, enemies)
        previousBlobs = currentBlobs

        if (best == null) return null

        val urgent = best.score >= 0.58f || best.nearPlayer
        return ProjectileThreat(
            detected = true,
            urgent = urgent,
            score = best.score,
            etaMs = best.etaMs.coerceIn(40, 380),
            enemy = best.enemy,
            reason = if (urgent) "PROJECTILE • DANGER" else "PROJECTILE • TRACK"
        )
    }

    private data class Candidate(
        val enemy: Detection,
        val score: Float,
        val etaMs: Int,
        val nearPlayer: Boolean
    )

    private fun evaluateBlobs(
        current: List<MotionBlob>,
        previous: List<MotionBlob>,
        player: Detection,
        enemies: List<Detection>
    ): Candidate? {
        var best: Candidate? = null

        for (blob in current) {
            if (blob.size !in 2..40) continue
            if (blob.width > 15 || blob.height > 15) continue

            for (enemy in enemies.take(4)) {
                if (enemy.confidence < 0.30f) continue

                val ex = enemy.cx
                val ey = enemy.cy
                val px = player.cx
                val py = player.cy
                val lineX = px - ex
                val lineY = py - ey
                val lineLen2 = lineX * lineX + lineY * lineY
                if (lineLen2 < 0.035f) continue

                val t = ((blob.x - ex) * lineX + (blob.y - ey) * lineY) / lineLen2
                if (t < 0.08f || t > 1.05f) continue

                val projectedX = ex + lineX * t
                val projectedY = ey + lineY * t
                val corridorDist = hypot(blob.x - projectedX, blob.y - projectedY)
                if (corridorDist > 0.060f) continue

                val previousMatch = nearestPrevious(blob, previous)
                val previousT = previousMatch?.let {
                    ((it.x - ex) * lineX + (it.y - ey) * lineY) / lineLen2
                }

                val advance = if (previousT != null) {
                    (t - previousT).coerceIn(-0.25f, 0.25f)
                } else {
                    0f
                }

                val directionScore = if (advance > 0.006f) {
                    (advance / 0.070f).coerceIn(0f, 1f)
                } else {
                    0f
                }

                val distanceToPlayer = hypot(blob.x - px, blob.y - py)
                val nearPlayer = distanceToPlayer < 0.14f
                val nearScore = (1f - distanceToPlayer / 0.34f).coerceIn(0f, 1f)
                val compactness = (1f - blob.size / 48f).coerceIn(0.15f, 1f)
                val corridorScore = (1f - corridorDist / 0.060f).coerceIn(0f, 1f)

                val score = (
                    directionScore * 0.46f +
                    nearScore * 0.22f +
                    compactness * 0.16f +
                    corridorScore * 0.16f
                ).coerceIn(0f, 1f)

                val canDetectWithoutHistory = nearPlayer && compactness >= 0.42f
                if (score < 0.34f && !canDetectWithoutHistory) continue

                val eta = (
                    ((1f - t).coerceIn(0.05f, 1f) * 310f) -
                        directionScore * 55f
                ).toInt()

                val candidate = Candidate(enemy, score, eta, nearPlayer)
                if (best == null || candidate.score > best!!.score) {
                    best = candidate
                }
            }
        }

        return best
    }

    private fun nearestPrevious(
        current: MotionBlob,
        previous: List<MotionBlob>
    ): MotionBlob? {
        var best: MotionBlob? = null
        var bestDistance = 0.10f

        for (old in previous) {
            val d = hypot(old.x - current.x, old.y - current.y)
            if (d < bestDistance) {
                bestDistance = d
                best = old
            }
        }

        return best
    }

    private fun findMotionBlobs(
        current: ByteArray,
        old: ByteArray,
        threshold: Int
    ): List<MotionBlob> {
        val visited = BooleanArray(total)
        val queue = IntArray(total)
        val result = ArrayList<MotionBlob>(12)

        for (y in 1 until gridH - 1) {
            for (x in 1 until gridW - 1) {
                val start = y * gridW + x
                if (visited[start]) continue

                val delta = abs(
                    (current[start].toInt() and 0xFF) -
                        (old[start].toInt() and 0xFF)
                )
                if (delta < threshold) {
                    visited[start] = true
                    continue
                }

                var head = 0
                var tail = 0
                queue[tail++] = start
                visited[start] = true

                var count = 0
                var sumX = 0
                var sumY = 0
                var minX = x
                var maxX = x
                var minY = y
                var maxY = y

                while (head < tail && count <= 48) {
                    val index = queue[head++]
                    val cy = index / gridW
                    val cx = index - cy * gridW

                    count++
                    sumX += cx
                    sumY += cy
                    minX = min(minX, cx)
                    maxX = max(maxX, cx)
                    minY = min(minY, cy)
                    maxY = max(maxY, cy)

                    for (oy in -1..1) {
                        for (ox in -1..1) {
                            if (ox == 0 && oy == 0) continue

                            val nx = cx + ox
                            val ny = cy + oy
                            if (nx <= 0 || nx >= gridW - 1 || ny <= 0 || ny >= gridH - 1) continue

                            val ni = ny * gridW + nx
                            if (visited[ni]) continue

                            val nd = abs(
                                (current[ni].toInt() and 0xFF) -
                                    (old[ni].toInt() and 0xFF)
                            )

                            if (nd >= threshold) {
                                visited[ni] = true
                                if (tail < queue.size) queue[tail++] = ni
                            }
                        }
                    }
                }

                if (count !in 2..40) continue

                val width = maxX - minX + 1
                val height = maxY - minY + 1
                if (width > 15 || height > 15) continue

                result.add(
                    MotionBlob(
                        x = (sumX / count).toFloat() / gridW.toFloat(),
                        y = (sumY / count).toFloat() / gridH.toFloat(),
                        size = count,
                        width = width,
                        height = height
                    )
                )

                if (result.size >= 12) return result
            }
        }

        return result
    }

    private fun adaptiveThreshold(
        current: ByteArray,
        old: ByteArray
    ): Int {
        var sum = 0
        var count = 0
        var y = 3

        while (y < gridH - 3) {
            var x = 3
            while (x < gridW - 3) {
                val i = y * gridW + x
                sum += abs(
                    (current[i].toInt() and 0xFF) -
                        (old[i].toInt() and 0xFF)
                )
                count++
                x += 8
            }
            y += 8
        }

        val mean = if (count == 0) 0 else sum / count
        return max(22, mean * 3 + 10).coerceAtMost(55)
    }

    private fun buildGray(
        image: Image,
        captureW: Int,
        captureH: Int
    ): ByteArray {
        val out = ByteArray(total)
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

                out[y * gridW + x] =
                    ((77 * r + 150 * g + 29 * b) shr 8).toByte()
            }
        }

        return out
    }
}
