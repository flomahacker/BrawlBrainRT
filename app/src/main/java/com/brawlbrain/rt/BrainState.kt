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
    val recommendationDetail: String,
    val target: Detection?,
    val targetLeadX: Float,
    val targetLeadY: Float,
    val escapeX: Float,
    val escapeY: Float,
    val threat: Float,
    val opportunity: Float,
    val cover: Float,
    val isolation: Float,
    val enemyCount: Int,
    val fps: Float,
    val inferenceMs: Long,
    val engine: String,
    val gameMode: String,
    val role: String,
    val projectileThreat: Float = 0f,
    val projectileEtaMs: Int = 0,
    val projectileDetected: Boolean = false
)

enum class Recommendation(val title: String) {
    HOLD("ДЕРЖИ"),
    PRESSURE("ДАВИ"),
    RETREAT("ОТХОДИ"),
    TRACK("ТРЕК"),
    RESET("ИНФОРМАЦИЯ")
}