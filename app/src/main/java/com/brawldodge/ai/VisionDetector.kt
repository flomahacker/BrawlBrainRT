package com.brawldodge.ai

import android.media.Image
import kotlin.math.max
import kotlin.math.min

class VisionDetector {

    fun analyze(image: Image, width: Int, height: Int): List<Box> {
        val plane = image.planes.firstOrNull() ?: return emptyList()
        val pixelStride = plane.pixelStride.coerceAtLeast(4)
        val rowStride = plane.rowStride
        val buffer = plane.buffer.duplicate()

        val step = 4
        val gridW = (width + step - 1) / step
        val gridH = (height + step - 1) / step
        val size = gridW * gridH

        val red = BooleanArray(size)
        val cyan = BooleanArray(size)
        val orange = BooleanArray(size)

        fun channel(x: Int, y: Int, offset: Int): Int {
            val pos = y * rowStride + x * pixelStride + offset
            if (pos < 0 || pos >= buffer.limit()) return 0
            return buffer.get(pos).toInt() and 0xFF
        }

        for (gy in 0 until gridH) {
            val y = min(height - 1, gy * step)
            val ny = y.toFloat() / height
            if (ny < 0.05f || ny > 0.86f) continue

            for (gx in 0 until gridW) {
                val x = min(width - 1, gx * step)
                val nx = x.toFloat() / width
                if (nx < 0.01f || nx > 0.99f) continue

                val r = channel(x, y, 0)
                val g = channel(x, y, 1)
                val b = channel(x, y, 2)
                val index = gy * gridW + gx

                red[index] =
                    r > 145 &&
                    r > g * 1.32f &&
                    r > b * 1.32f &&
                    r - g > 28

                cyan[index] =
                    g > 105 &&
                    b > 110 &&
                    r < 165 &&
                    g > r * 1.15f

                orange[index] =
                    r > 165 &&
                    g > 90 &&
                    b < 135 &&
                    r - g > 38
            }
        }

        val result = ArrayList<Box>(12)

        for (c in components(red, gridW, gridH, width, height, step)) {
            val aspect = c.width / c.height.coerceAtLeast(0.01f)
            val kind = if (
                (aspect >= 2.4f && c.height < 0.06f) ||
                c.area >= 12
            ) {
                Box.Kind.ENEMY
            } else {
                Box.Kind.PROJECTILE
            }

            val confidence = if (kind == Box.Kind.ENEMY) {
                (0.56f + c.area / 160f).coerceIn(0.56f, 0.96f)
            } else {
                (0.48f + c.area / 50f).coerceIn(0.48f, 0.92f)
            }

            result += Box(
                kind,
                confidence,
                c.left,
                c.top,
                c.right,
                c.bottom
            )
        }

        val player = components(cyan, gridW, gridH, width, height, step)
            .filter { it.area in 2..500 && it.width < 0.45f && it.height < 0.45f }
            .minByOrNull {
                val dx = it.cx - 0.5f
                val dy = it.cy - 0.5f
                dx * dx + dy * dy
            }

        if (player != null) {
            result += Box(
                Box.Kind.PLAYER,
                (0.62f + player.area / 300f).coerceIn(0.62f, 0.94f),
                player.left,
                player.top,
                player.right,
                player.bottom
            )
        }

        for (c in components(orange, gridW, gridH, width, height, step)
            .filter { it.area >= 10 }) {
            result += Box(
                Box.Kind.AREA,
                (0.50f + c.area / 240f).coerceIn(0.50f, 0.88f),
                c.left,
                c.top,
                c.right,
                c.bottom
            )
        }

        return result
            .sortedWith(compareBy<Box> { it.kind.ordinal }.thenByDescending { it.confidence })
            .take(16)
    }

    private data class Component(
        val left: Float,
        val top: Float,
        val right: Float,
        val bottom: Float,
        val area: Int
    ) {
        val width: Float get() = right - left
        val height: Float get() = bottom - top
        val cx: Float get() = (left + right) * 0.5f
        val cy: Float get() = (top + bottom) * 0.5f
    }

    private fun components(
        mask: BooleanArray,
        gridW: Int,
        gridH: Int,
        width: Int,
        height: Int,
        step: Int
    ): List<Component> {
        val seen = BooleanArray(mask.size)
        val queue = IntArray(mask.size)
        val result = ArrayList<Component>()

        for (start in mask.indices) {
            if (!mask[start] || seen[start]) continue

            var head = 0
            var tail = 0
            queue[tail++] = start
            seen[start] = true

            var minX = start % gridW
            var maxX = minX
            var minY = start / gridW
            var maxY = minY
            var area = 0

            while (head < tail) {
                val idx = queue[head++]
                val x = idx % gridW
                val y = idx / gridW

                minX = min(minX, x)
                maxX = max(maxX, x)
                minY = min(minY, y)
                maxY = max(maxY, y)
                area++

                val neighbors = intArrayOf(
                    if (x > 0) idx - 1 else -1,
                    if (x + 1 < gridW) idx + 1 else -1,
                    if (y > 0) idx - gridW else -1,
                    if (y + 1 < gridH) idx + gridW else -1
                )

                for (n in neighbors) {
                    if (n >= 0 && mask[n] && !seen[n]) {
                        seen[n] = true
                        queue[tail++] = n
                    }
                }
            }

            if (area >= 2) {
                result += Component(
                    left = (minX * step).toFloat() / width,
                    top = (minY * step).toFloat() / height,
                    right = min(width, (maxX + 1) * step).toFloat() / width,
                    bottom = min(height, (maxY + 1) * step).toFloat() / height,
                    area = area
                )
            }
        }

        return result
    }
}
