package com.brawlbrain.rt

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

class DetectionTracker(
    private val ghostTtlMs: Long = 5000L,
    private val maxTracks: Int = 10,
    private val maxMatchDistance: Float = 0.14f,
    private val minIou: Float = 0.04f
) {
    private class Kalman1D(var position: Float, var velocity: Float = 0f) {
        private var p00 = 0.02f
        private var p01 = 0f
        private var p11 = 0.80f

        fun predict(dtRaw: Float) {
            val dt = dtRaw.coerceIn(0.005f, 0.40f)
            position += velocity * dt
            val q = 0.18f
            val n00 = p00 + dt * (2f * p01 + dt * p11) + q * dt * dt * dt * 0.25f
            val n01 = p01 + dt * p11 + q * dt * dt * 0.5f
            val n11 = p11 + q * dt
            p00 = n00
            p01 = n01
            p11 = n11
        }

        fun update(measurement: Float) {
            val r = 0.0045f
            val innovation = measurement - position
            val s = max(0.0001f, p00 + r)
            val k0 = p00 / s
            val k1 = p01 / s
            position += k0 * innovation
            velocity += k1 * innovation
            val oldP00 = p00
            val oldP01 = p01
            p00 = (1f - k0) * oldP00
            p01 = (1f - k0) * oldP01
            p11 -= k1 * oldP01
        }
    }

    private class Track(
        val id: Int,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        confidence: Float,
        now: Long
    ) {
        val kx = Kalman1D(x)
        val ky = Kalman1D(y)
        var width = width
        var height = height
        var confidence = confidence
        var lastSeenAt = now
        var lastUpdateAt = now
        var visible = true

        fun predict(now: Long) {
            val dt = ((now - lastUpdateAt).coerceIn(0L, 400L) / 1000f)
            if (dt > 0f) {
                kx.predict(dt)
                ky.predict(dt)
                lastUpdateAt = now
            }
            visible = false
        }

        fun update(d: Detection, now: Long) {
            val dt = ((now - lastUpdateAt).coerceIn(1L, 400L) / 1000f)
            kx.predict(dt)
            ky.predict(dt)
            kx.update(d.cx)
            ky.update(d.cy)
            width = width * 0.68f + d.width * 0.32f
            height = height * 0.68f + d.height * 0.32f
            confidence = confidence * 0.72f + d.confidence * 0.28f
            lastSeenAt = now
            lastUpdateAt = now
            visible = true
        }

        fun distanceTo(d: Detection): Float =
            hypot(kx.position - d.cx, ky.position - d.cy)

        fun box(): Detection = Detection(
            label = "enemy",
            confidence = confidence.coerceIn(0f, 1f),
            cx = kx.position.coerceIn(-0.20f, 1.20f),
            cy = ky.position.coerceIn(-0.20f, 1.20f),
            width = width.coerceIn(0.01f, 0.60f),
            height = height.coerceIn(0.01f, 0.60f),
            id = id
        )
    }

    data class Snapshot(
        val visible: List<Detection>,
        val visuals: List<EnemyTrackVisual>,
        val trackCount: Int
    )

    private val tracks = ArrayList<Track>(maxTracks)
    private var nextId = 1

    fun reset() {
        tracks.clear()
        nextId = 1
    }

    fun update(detections: List<Detection>, now: Long): Snapshot {
        for (track in tracks) track.predict(now)

        val candidates = detections
            .filter { it.label == "enemy" && it.confidence > 0.15f }
            .take(maxTracks)

        val matchedTrack = BooleanArray(tracks.size)
        val matchedDetection = BooleanArray(candidates.size)

        while (true) {
            var bestTrack = -1
            var bestDetection = -1
            var bestScore = 0f

            for (ti in tracks.indices) {
                if (matchedTrack[ti]) continue
                val predicted = tracks[ti].box()

                for (di in candidates.indices) {
                    if (matchedDetection[di]) continue
                    val candidate = candidates[di]
                    val overlap = iou(predicted, candidate)
                    val center = tracks[ti].distanceTo(candidate)
                    if (overlap < minIou && center > maxMatchDistance) continue

                    val score = overlap + (1f - center / maxMatchDistance).coerceIn(0f, 1f) * 0.16f
                    if (score > bestScore) {
                        bestScore = score
                        bestTrack = ti
                        bestDetection = di
                    }
                }
            }

            if (bestTrack < 0) break
            tracks[bestTrack].update(candidates[bestDetection], now)
            matchedTrack[bestTrack] = true
            matchedDetection[bestDetection] = true
        }

        for (di in candidates.indices) {
            if (matchedDetection[di] || tracks.size >= maxTracks) continue
            val d = candidates[di]
            tracks += Track(
                id = allocateId(),
                x = d.cx,
                y = d.cy,
                width = d.width,
                height = d.height,
                confidence = d.confidence,
                now = now
            )
        }

        tracks.removeAll { now - it.lastSeenAt > ghostTtlMs }
        return snapshot(now)
    }

    fun predictOnly(now: Long): Snapshot {
        for (track in tracks) track.predict(now)
        tracks.removeAll { now - it.lastSeenAt > ghostTtlMs }
        return snapshot(now)
    }

    private fun snapshot(now: Long): Snapshot {
        val visible = ArrayList<Detection>(tracks.size)
        val visuals = ArrayList<EnemyTrackVisual>(tracks.size)

        for (track in tracks) {
            val d = track.box()
            val ageMs = (now - track.lastSeenAt).coerceAtLeast(0L)
            val isVisible = track.visible && ageMs < 150L

            if (isVisible) visible += d

            val leadSeconds = 0.38f
            val predictedX = track.kx.position + track.kx.velocity * leadSeconds
            val predictedY = track.ky.position + track.ky.velocity * leadSeconds

            visuals += EnemyTrackVisual(
                id = track.id,
                x = d.cx,
                y = d.cy,
                predictedX = predictedX,
                predictedY = predictedY,
                vx = track.kx.velocity,
                vy = track.ky.velocity,
                confidence = track.confidence.coerceIn(0f, 1f),
                visible = isVisible,
                ageMs = ageMs,
                ageText = "\${(ageMs / 1000L).coerceIn(0L, 5L)}s",
                idText = when (track.id) {
                    1 -> "#1"; 2 -> "#2"; 3 -> "#3"; 4 -> "#4"; 5 -> "#5"
                    6 -> "#6"; 7 -> "#7"; 8 -> "#8"; 9 -> "#9"; 10 -> "#10"
                    else -> "#"
                }
            )
        }

        return Snapshot(visible, visuals, tracks.size)
    }

    private fun allocateId(): Int {
        val id = nextId
        nextId = if (nextId >= 999) 1 else nextId + 1
        return id
    }

    private fun iou(a: Detection, b: Detection): Float {
        val ax1 = a.cx - a.width / 2f
        val ay1 = a.cy - a.height / 2f
        val ax2 = a.cx + a.width / 2f
        val ay2 = a.cy + a.height / 2f
        val bx1 = b.cx - b.width / 2f
        val by1 = b.cy - b.height / 2f
        val bx2 = b.cx + b.width / 2f
        val by2 = b.cy + b.height / 2f

        val iw = max(0f, min(ax2, bx2) - max(ax1, bx1))
        val ih = max(0f, min(ay2, by2) - max(ay1, by1))
        val intersection = iw * ih
        val union = a.width * a.height + b.width * b.height - intersection
        return if (union <= 0f) 0f else intersection / union
    }
}
