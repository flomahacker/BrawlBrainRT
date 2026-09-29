package com.brawlbrain.rt

import android.content.Context

data class BrainConfig(
    val gameMode: String = "Universal",
    val role: String = "Universal",
    val performance: String = "Balanced",
    val entityIntervalMs: Long = 125L,
    val wallIntervalMs: Long = 600L,
    val confidencePercent: Int = 34,
    val hudOpacityPercent: Int = 88,
    val hudScalePercent: Int = 100,
    val showEnemies: Boolean = true,
    val showTeammates: Boolean = true,
    val showWalls: Boolean = true,
    val showTargetLine: Boolean = true,
    val showThreat: Boolean = true,
    val showAdvice: Boolean = true,
    val showDebug: Boolean = true,
    val reducedMotion: Boolean = true
)

object BrainPrefs {
    private const val NAME = "brawlbrain"
    private const val MODE = "mode"
    private const val ROLE = "role"
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

    fun load(context: Context): BrainConfig {
        val p = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        return BrainConfig(
            gameMode = p.getString(MODE, "Universal") ?: "Universal",
            role = p.getString(ROLE, "Universal") ?: "Universal",
            performance = p.getString(PERFORMANCE, "Balanced") ?: "Balanced",
            entityIntervalMs = p.getLong(ENTITY_INTERVAL, 125L),
            wallIntervalMs = p.getLong(WALL_INTERVAL, 600L),
            confidencePercent = p.getInt(CONFIDENCE, 34),
            hudOpacityPercent = p.getInt(HUD_OPACITY, 88),
            hudScalePercent = p.getInt(HUD_SCALE, 100),
            showEnemies = p.getBoolean(SHOW_ENEMIES, true),
            showTeammates = p.getBoolean(SHOW_TEAMMATES, true),
            showWalls = p.getBoolean(SHOW_WALLS, true),
            showTargetLine = p.getBoolean(SHOW_TARGET, true),
            showThreat = p.getBoolean(SHOW_THREAT, true),
            showAdvice = p.getBoolean(SHOW_ADVICE, true),
            showDebug = p.getBoolean(SHOW_DEBUG, true),
            reducedMotion = p.getBoolean(REDUCED_MOTION, true)
        )
    }

    fun save(context: Context, config: BrainConfig) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(MODE, config.gameMode)
            .putString(ROLE, config.role)
            .putString(PERFORMANCE, config.performance)
            .putLong(ENTITY_INTERVAL, config.entityIntervalMs)
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
            .apply()
    }

    fun reset(context: Context) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }
}