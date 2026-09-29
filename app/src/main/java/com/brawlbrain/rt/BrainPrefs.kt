package com.brawlbrain.rt

import android.content.Context

data class BrainConfig(
    val brawler: String = "Buzz",
    val role: String = "Universal",
    val gameMode: String = "Universal",
    val performance: String = "Balanced",
    val entityIntervalMs: Long = 80L,
    val wallIntervalMs: Long = 900L,
    val confidencePercent: Int = 38,
    val hudOpacityPercent: Int = 82,
    val hudScalePercent: Int = 96,
    val showEnemies: Boolean = false,
    val showTeammates: Boolean = false,
    val showWalls: Boolean = false,
    val showTargetLine: Boolean = false,
    val showThreat: Boolean = false,
    val showAdvice: Boolean = true,
    val showDebug: Boolean = false,
    val reducedMotion: Boolean = true,
    val frameLongEdge: Int = 720,
    val autoDodge: Boolean = false,
    val dodgeStrengthPercent: Int = 82,
    val dodgeReactionMs: Long = 90L,
    val dodgeCooldownMs: Long = 90L,

    // Visual-memory / prediction
    val showGhosts: Boolean = true,
    val ghostTtlMs: Long = 5000L,
    val predictionLeadMs: Long = 380L,
    val pipelineDelayMs: Long = 80L,
    val predictionMaxSeconds: Float = 0.55f,
    val showPrediction: Boolean = true,

    // Range rings. Brawler raw ranges are kept here and converted by screen scale.
    val showRangeRings: Boolean = true,
    val showEnemyRangeRings: Boolean = false,
    val rangeUnitScreenFraction: Float = 0.032f,
    val attackRangeRaw: Float = 8f,
    val enemyDefaultRangeRaw: Float = 8f,

    // Critical warnings / hysteresis.
    val showWarnings: Boolean = true,
    val showSuperPulse: Boolean = true,
    val hpEnterThreshold: Float = 0.28f,
    val hpExitThreshold: Float = 0.36f,
    val nearbyEnemyEnterDistance: Float = 0.34f,
    val nearbyEnemyExitDistance: Float = 0.40f,
    val surroundedEnterMinAngleRad: Float = 1.10f,
    val surroundedExitMinAngleRad: Float = 0.80f,
    val warningCooldownMs: Long = 1500L,
    val warningMinOnMs: Long = 450L,

    // Showdown gas heuristic.
    val gasEnterEdgeDelta: Float = 0.10f,
    val gasExitEdgeDelta: Float = 0.06f,
    val gasDirectionMin: Float = 0.10f,

    // HUD reader ROIs. Values are normalized to the full captured frame.
    // They are deliberately configurable because skins / aspect ratios / UI scale differ.
    val hpScanHalfWidth: Float = 0.075f,
    val hpScanTopOffset: Float = 0.105f,
    val hpScanBottomOffset: Float = 0.055f,
    val ammoLeft: Float = 0.80f,
    val ammoTop: Float = 0.72f,
    val ammoRight: Float = 0.97f,
    val ammoBottom: Float = 0.94f,
    val superButtonX: Float = 0.79f,
    val superButtonY: Float = 0.86f,
    val superButtonRadius: Float = 0.075f,
    val superReadyThreshold: Float = 0.28f
)

object BrainPrefs {
    private const val NAME = "brawlbrain"
    private const val BRAWLER = "brawler"
    private const val ROLE = "role"
    private const val MODE = "mode"
    private const val PERFORMANCE = "performance"
    private const val ENTITY_INTERVAL = "entity_interval"
    private const val WALL_INTERVAL = "wall_interval"
    private const val CONFIDENCE = "confidence"
    private const val HUD_OPACITY = "hud_opacity"
    private const val HUD_SCALE = "hud_scale"
    private const val SHOW_ENEMIES = "show_enemies"
    private const val SHOW_TEAMMATES = "show_teammates"
    private const val SHOW_WALLS = "show_walls"
    private const val SHOW_TARGET = "show_target"
    private const val SHOW_THREAT = "show_threat"
    private const val SHOW_ADVICE = "show_advice"
    private const val SHOW_DEBUG = "show_debug"
    private const val REDUCED_MOTION = "reduced_motion"
    private const val AUTO_DODGE = "auto_dodge"
    private const val DODGE_STRENGTH = "dodge_strength"
    private const val DODGE_REACTION = "dodge_reaction"
    private const val DODGE_COOLDOWN = "dodge_cooldown"
    private const val SHOW_GHOSTS = "show_ghosts"
    private const val SHOW_PREDICTION = "show_prediction"
    private const val PIPELINE_DELAY = "pipeline_delay"
    private const val SHOW_RANGE_RINGS = "show_range_rings"
    private const val SHOW_ENEMY_RANGE_RINGS = "show_enemy_range_rings"
    private const val RANGE_SCALE = "range_scale"
    private const val ATTACK_RANGE_RAW = "attack_range_raw"
    private const val ENEMY_DEFAULT_RANGE_RAW = "enemy_default_range_raw"
    private const val SHOW_WARNINGS = "show_warnings"
    private const val SHOW_SUPER_PULSE = "show_super_pulse"
    private const val HP_ENTER = "hp_enter"
    private const val HP_EXIT = "hp_exit"
    private const val NEAR_ENTER = "near_enter"
    private const val NEAR_EXIT = "near_exit"
    private const val SURROUND_ENTER = "surround_enter"
    private const val SURROUND_EXIT = "surround_exit"
    private const val WARNING_COOLDOWN = "warning_cooldown"
    private const val WARNING_MIN_ON = "warning_min_on"

    fun load(context: Context): BrainConfig {
        val p = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        val performance = p.getString(PERFORMANCE, "Balanced") ?: "Balanced"

        val defaultEntity = when (performance) {
            "Battery", "Battery Saver" -> 96L
            "Quality" -> 68L
            else -> 80L
        }
        val defaultWall = when (performance) {
            "Battery", "Battery Saver" -> 1500L
            "Quality" -> 700L
            else -> 900L
        }
        val defaultEdge = when (performance) {
            "Battery", "Battery Saver" -> 576
            "Quality" -> 960
            else -> 720
        }

        return BrainConfig(
            brawler = p.getString(BRAWLER, "Buzz") ?: "Buzz",
            role = p.getString(ROLE, "Universal") ?: "Universal",
            gameMode = p.getString(MODE, "Universal") ?: "Universal",
            performance = performance,
            entityIntervalMs = p.getLong(ENTITY_INTERVAL, defaultEntity).coerceIn(60L, 1000L),
            wallIntervalMs = p.getLong(WALL_INTERVAL, defaultWall),
            confidencePercent = p.getInt(CONFIDENCE, 38),
            hudOpacityPercent = p.getInt(HUD_OPACITY, 82),
            hudScalePercent = p.getInt(HUD_SCALE, 96),
            showEnemies = p.getBoolean(SHOW_ENEMIES, false),
            showTeammates = p.getBoolean(SHOW_TEAMMATES, false),
            showWalls = p.getBoolean(SHOW_WALLS, false),
            showTargetLine = p.getBoolean(SHOW_TARGET, false),
            showThreat = p.getBoolean(SHOW_THREAT, false),
            showAdvice = p.getBoolean(SHOW_ADVICE, true),
            showDebug = p.getBoolean(SHOW_DEBUG, false),
            reducedMotion = p.getBoolean(REDUCED_MOTION, true),
            frameLongEdge = defaultEdge,
            autoDodge = p.getBoolean(AUTO_DODGE, false),
            dodgeStrengthPercent = p.getInt(DODGE_STRENGTH, 82).coerceIn(35, 100),
            dodgeReactionMs = p.getLong(DODGE_REACTION, 90L).coerceIn(45L, 250L),
            dodgeCooldownMs = p.getLong(DODGE_COOLDOWN, 90L).coerceIn(60L, 260L),
            showGhosts = p.getBoolean(SHOW_GHOSTS, true),
            predictionLeadMs = p.getLong("prediction_lead", 380L).coerceIn(300L, 500L),
            pipelineDelayMs = p.getLong(PIPELINE_DELAY, 80L).coerceIn(0L, 180L),
            showPrediction = p.getBoolean(SHOW_PREDICTION, true),
            showRangeRings = p.getBoolean(SHOW_RANGE_RINGS, true),
            showEnemyRangeRings = p.getBoolean(SHOW_ENEMY_RANGE_RINGS, false),
            rangeUnitScreenFraction = p.getFloat(RANGE_SCALE, 0.032f).coerceIn(0.005f, 0.080f),
            attackRangeRaw = p.getFloat(ATTACK_RANGE_RAW, defaultAttackRange("Buzz")),
            enemyDefaultRangeRaw = p.getFloat(ENEMY_DEFAULT_RANGE_RAW, 8f),
            showWarnings = p.getBoolean(SHOW_WARNINGS, true),
            showSuperPulse = p.getBoolean(SHOW_SUPER_PULSE, true),
            hpEnterThreshold = p.getFloat(HP_ENTER, 0.28f).coerceIn(0.05f, 0.60f),
            hpExitThreshold = p.getFloat(HP_EXIT, 0.36f).coerceIn(0.10f, 0.80f),
            nearbyEnemyEnterDistance = p.getFloat(NEAR_ENTER, 0.34f).coerceIn(0.10f, 0.80f),
            nearbyEnemyExitDistance = p.getFloat(NEAR_EXIT, 0.40f).coerceIn(0.10f, 0.90f),
            surroundedEnterMinAngleRad = p.getFloat(SURROUND_ENTER, 1.10f).coerceIn(0.40f, 2.80f),
            surroundedExitMinAngleRad = p.getFloat(SURROUND_EXIT, 0.80f).coerceIn(0.30f, 2.50f),
            warningCooldownMs = p.getLong(WARNING_COOLDOWN, 1500L).coerceIn(250L, 5000L),
            warningMinOnMs = p.getLong(WARNING_MIN_ON, 450L).coerceIn(150L, 2000L)
        )
    }

    fun save(context: Context, config: BrainConfig) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(BRAWLER, config.brawler)
            .putString(ROLE, config.role)
            .putString(MODE, config.gameMode)
            .putString(PERFORMANCE, config.performance)
            .putLong(ENTITY_INTERVAL, config.entityIntervalMs.coerceIn(60L, 1000L))
            .putLong(WALL_INTERVAL, config.wallIntervalMs)
            .putInt(CONFIDENCE, config.confidencePercent)
            .putInt(HUD_OPACITY, config.hudOpacityPercent)
            .putInt(HUD_SCALE, config.hudScalePercent)
            .putBoolean(SHOW_ENEMIES, config.showEnemies)
            .putBoolean(SHOW_TEAMMATES, config.showTeammates)
            .putBoolean(SHOW_WALLS, config.showWalls)
            .putBoolean(SHOW_TARGET, config.showTargetLine)
            .putBoolean(SHOW_THREAT, config.showThreat)
            .putBoolean(SHOW_ADVICE, config.showAdvice)
            .putBoolean(SHOW_DEBUG, config.showDebug)
            .putBoolean(REDUCED_MOTION, config.reducedMotion)
            .putBoolean(AUTO_DODGE, config.autoDodge)
            .putInt(DODGE_STRENGTH, config.dodgeStrengthPercent.coerceIn(35, 100))
            .putLong(DODGE_REACTION, config.dodgeReactionMs.coerceIn(45L, 250L))
            .putLong(DODGE_COOLDOWN, config.dodgeCooldownMs.coerceIn(60L, 260L))
            .putBoolean(SHOW_GHOSTS, config.showGhosts)
            .putLong("prediction_lead", config.predictionLeadMs.coerceIn(300L, 500L))
            .putLong(PIPELINE_DELAY, config.pipelineDelayMs.coerceIn(0L, 180L))
            .putBoolean(SHOW_PREDICTION, config.showPrediction)
            .putBoolean(SHOW_RANGE_RINGS, config.showRangeRings)
            .putBoolean(SHOW_ENEMY_RANGE_RINGS, config.showEnemyRangeRings)
            .putFloat(RANGE_SCALE, config.rangeUnitScreenFraction)
            .putFloat(ATTACK_RANGE_RAW, config.attackRangeRaw)
            .putFloat(ENEMY_DEFAULT_RANGE_RAW, config.enemyDefaultRangeRaw)
            .putBoolean(SHOW_WARNINGS, config.showWarnings)
            .putBoolean(SHOW_SUPER_PULSE, config.showSuperPulse)
            .putFloat(HP_ENTER, config.hpEnterThreshold)
            .putFloat(HP_EXIT, config.hpExitThreshold)
            .putFloat(NEAR_ENTER, config.nearbyEnemyEnterDistance)
            .putFloat(NEAR_EXIT, config.nearbyEnemyExitDistance)
            .putFloat(SURROUND_ENTER, config.surroundedEnterMinAngleRad)
            .putFloat(SURROUND_EXIT, config.surroundedExitMinAngleRad)
            .putLong(WARNING_COOLDOWN, config.warningCooldownMs)
            .putLong(WARNING_MIN_ON, config.warningMinOnMs)
            .apply()
    }

    private fun defaultAttackRange(brawler: String): Float {
        return when (brawler) {
            "Tick" -> 26f
            "Buzz" -> 8f
            else -> 8f
        }
    }

    fun reset(context: Context) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
