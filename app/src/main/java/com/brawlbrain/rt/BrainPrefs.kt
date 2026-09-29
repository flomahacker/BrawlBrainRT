package com.brawlbrain.rt

import android.content.Context

data class BrainConfig(
    val brawler: String = "Buzz",
    val gameMode: String = "Universal",
    val performance: String = "12X Smooth",
    val entityIntervalMs: Long = 155L,
    val wallIntervalMs: Long = 1600L,
    val confidencePercent: Int = 38,
    val hudOpacityPercent: Int = 86,
    val hudScalePercent: Int = 96,
    val showEnemies: Boolean = true,
    val showTeammates: Boolean = false,
    val showWalls: Boolean = false,
    val showTargetLine: Boolean = true,
    val showThreat: Boolean = true,
    val showAdvice: Boolean = true,
    val showDebug: Boolean = false,
    val reducedMotion: Boolean = true,
    val frameLongEdge: Int = 720
)

object BrainPrefs {
    private const val NAME = "brawlbrain"
    private const val BRAWLER = "brawler"
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

    fun load(context: Context): BrainConfig {
        val p = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        val performance = p.getString(PERFORMANCE, "12X Smooth") ?: "12X Smooth"

        val defaultEntity = when (performance) {
            "Battery" -> 220L
            "Quality" -> 105L
            else -> 150L
        }
        val defaultWall = when (performance) {
            "Battery" -> 1600L
            "Quality" -> 800L
            else -> 1200L
        }
        val defaultEdge = when (performance) {
            "Battery" -> 576
            "Quality" -> 960
            else -> 720
        }

        return BrainConfig(
            brawler = p.getString(BRAWLER, "Buzz") ?: "Buzz",
            gameMode = p.getString(MODE, "Universal") ?: "Universal",
            performance = performance,
            entityIntervalMs = p.getLong(ENTITY_INTERVAL, defaultEntity),
            wallIntervalMs = p.getLong(WALL_INTERVAL, defaultWall),
            confidencePercent = p.getInt(CONFIDENCE, 38),
            hudOpacityPercent = p.getInt(HUD_OPACITY, 86),
            hudScalePercent = p.getInt(HUD_SCALE, 96),
            showEnemies = p.getBoolean(SHOW_ENEMIES, true),
            showTeammates = p.getBoolean(SHOW_TEAMMATES, false),
            showWalls = p.getBoolean(SHOW_WALLS, false),
            showTargetLine = p.getBoolean(SHOW_TARGET, true),
            showThreat = p.getBoolean(SHOW_THREAT, true),
            showAdvice = p.getBoolean(SHOW_ADVICE, true),
            showDebug = p.getBoolean(SHOW_DEBUG, false),
            reducedMotion = p.getBoolean(REDUCED_MOTION, true),
            frameLongEdge = defaultEdge
        )
    }

    fun save(context: Context, config: BrainConfig) {
        context.getSharedPreferences(NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(BRAWLER, config.brawler)
            .putString(MODE, config.gameMode)
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