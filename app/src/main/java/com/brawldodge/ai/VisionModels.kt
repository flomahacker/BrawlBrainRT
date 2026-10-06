package com.brawldodge.ai

import kotlin.math.hypot

data class Box(
    val kind: Kind,
    val confidence: Float,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val cx: Float get() = (left + right) * 0.5f
    val cy: Float get() = (top + bottom) * 0.5f
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    enum class Kind {
        PLAYER, ENEMY, PROJECTILE, AREA
    }
}

data class VisionSnapshot(
    val online: Boolean = false,
    val fps: Float = 0f,
    val processMs: Float = 0f,
    val captureDelayMs: Float = 0f,
    val boxes: List<Box> = emptyList(),
    val danger: Float = 0f,
    val dangerX: Float = 0f,
    val dangerY: Float = 0f,
    val recommendation: String = "СКАНИРОВАНИЕ",
    val error: String = ""
) {
    companion object {
        fun empty() = VisionSnapshot()
    }
}

fun Box.distanceTo(other: Box): Float =
    hypot(cx - other.cx, cy - other.cy).toFloat()
