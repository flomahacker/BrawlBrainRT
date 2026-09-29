package com.brawlbrain.rt

data class Detection(
    val label: String,
    val confidence: Float,
    val cx: Float,
    val cy: Float,
    val width: Float,
    val height: Float,
    val id: Int = 0
)

data class HudState(
    val hp: Float = 1f,
    val hpConfidence: Float = 0f,
    val ammo: Int = -1,
    val maxAmmo: Int = 3,
    val superCharge: Float = 0f,
    val superReady: Boolean = false,
    val ammoConfidence: Float = 0f,
    val superConfidence: Float = 0f
)

data class CriticalWarning(
    val type: WarningType = WarningType.NONE,
    val dir1X: Float = 0f,
    val dir1Y: Float = 0f,
    val dir2X: Float = 0f,
    val dir2Y: Float = 0f,
    val active: Boolean = false
)

enum class WarningType {
    NONE,
    LOW_HP,
    SURROUNDED,
    GAS
}

data class EnemyTrackVisual(
    val id: Int,
    val x: Float,
    val y: Float,
    val predictedX: Float,
    val predictedY: Float,
    val vx: Float,
    val vy: Float,
    val confidence: Float,
    val visible: Boolean,
    val ageMs: Long,
    val ageText: String
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
    val projectileDetected: Boolean = false,
    val intelActionTitle: String = "ФОКУС",
    val intelActionDetail: String = "Собери информацию о поле",
    val intelFocusX: Float = 0.5f,
    val intelFocusY: Float = 0.5f,
    val intelFocusScore: Float = 0f,
    val fireWindow: Float = 0f,
    val actionX: Float = 0f,
    val actionY: Float = 0f,
    val trackVisuals: List<EnemyTrackVisual> = emptyList(),
    val trackCount: Int = 0,
    val hud: HudState = HudState(),
    val warning: CriticalWarning = CriticalWarning(),
    val gasDetected: Boolean = false,
    val safeZoneX: Float = 0f,
    val safeZoneY: Float = 0f,
    val debugText: String = ""
)

enum class Recommendation(val title: String) {
    HOLD("ДЕРЖИ"),
    PRESSURE("ДАВИ"),
    RETREAT("ОТХОДИ"),
    TRACK("ТРЕК"),
    RESET("ИНФОРМАЦИЯ")
}
