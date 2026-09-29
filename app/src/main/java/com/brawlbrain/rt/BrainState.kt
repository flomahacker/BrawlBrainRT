package com.brawlbrain.rt

data class Detection(
    val label: String,
    val confidence: Float,
    val cx: Float,
    val cy: Float,
    val width: Float,
    val height: Float
)

data class BrainFrame(
    val player: Detection?,
    val enemies: List<Detection>,
    val teammates: List<Detection>,
    val walls: List<Detection>,
    val recommendation: Recommendation,
    val target: Detection?,
    val threat: Float,
    val enemyCount: Int,
    val fps: Float,
    val inferenceMs: Long,
    val engine: String
)

enum class Recommendation(val title: String, val subtitle: String) {
    HOLD("ДЕРЖИ ПОЗИЦИЮ", "не лезь первым"),
    PRESSURE("ДАВИ", "есть окно для давления"),
    RETREAT("ОТХОДИ", "слишком высокий риск"),
    TRACK("ТРЕКИНГ", "следи за ближайшим врагом"),
    RESET("РЕСЕТ", "собери информацию")
}