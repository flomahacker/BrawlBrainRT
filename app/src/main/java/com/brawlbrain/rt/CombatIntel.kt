package com.brawlbrain.rt

import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

data class CombatIntelResult(
    val actionTitle: String,
    val actionDetail: String,
    val focus: Detection?,
    val focusScore: Float,
    val focusX: Float,
    val focusY: Float,
    val actionX: Float,
    val actionY: Float,
    val fireWindow: Float
)

class CombatIntel {

    private data class Track(
        var x: Float,
        var y: Float,
        var at: Long
    )

    private data class EnemyInfo(
        val enemy: Detection,
        val distance: Float,
        val vx: Float,
        val vy: Float,
        val closing: Float,
        val isolation: Float,
        val score: Float
    )

    private val tracks = ArrayList<Track>()

    fun decide(
        player: Detection?,
        enemies: List<Detection>,
        teammates: List<Detection>,
        projectile: ProjectileThreat?,
        brawler: String,
        now: Long
    ): CombatIntelResult {
        if (player == null || enemies.isEmpty()) {
            return CombatIntelResult(
                "СВОБОДНО",
                "Цель не найдена — используй момент для позиции и перезарядки",
                null,
                0f,
                player?.cx ?: 0.5f,
                player?.cy ?: 0.5f,
                0f,
                0f,
                0f
            )
        }

        val scored = enemies.map { enemy ->
            val d = hypot(enemy.cx - player.cx, enemy.cy - player.cy)
            val previous = nearestTrack(enemy, now)
            val dt = if (previous == null) 0.10f else ((now - previous.at).coerceIn(40L, 250L) / 1000f)
            val vx = if (previous == null) 0f else (enemy.cx - previous.x) / dt
            val vy = if (previous == null) 0f else (enemy.cy - previous.y) / dt

            val lineLen = d.coerceAtLeast(0.001f)
            val ux = (player.cx - enemy.cx) / lineLen
            val uy = (player.cy - enemy.cy) / lineLen
            val closing = (vx * ux + vy * uy).coerceIn(-2f, 2f)

            val allyDistance = teammates.minOfOrNull {
                hypot(it.cx - enemy.cx, it.cy - enemy.cy)
            } ?: 1f
            val isolation = (1f - allyDistance / 0.46f).coerceIn(0f, 1f)
            val proximity = (1f - d / 0.78f).coerceIn(0f, 1f)
            val pressure = ((closing + 0.15f) / 1.15f).coerceIn(0f, 1f)

            val score = (
                proximity * 0.43f +
                pressure * 0.30f +
                enemy.confidence * 0.17f +
                isolation * 0.10f
            ).coerceIn(0f, 1f)

            EnemyInfo(enemy, d, vx, vy, closing, isolation, score)
        }.sortedByDescending { it.score }

        val best = scored.first()
        val projectileUrgent = projectile?.urgent == true

        val fireWindow = (
            1f - hypot(best.vx, best.vy).coerceIn(0f, 2f) / 2f
        ).coerceIn(0f, 1f) * (
            1f - abs(best.closing).coerceIn(0f, 1f) * 0.35f
        )

        val actionX: Float
        val actionY: Float
        val title: String
        val detail: String

        if (projectileUrgent) {
            val dx = player.cx - best.enemy.cx
            val dy = player.cy - best.enemy.cy
            val len = max(0.001f, hypot(dx, dy))
            actionX = -dy / len
            actionY = dx / len
            title = "СНАРЯД"
            detail = "Сместись поперёк линии огня • ETA " + (projectile?.etaMs ?: 0) + " мс"
        } else {
            when (brawler) {
                "Buzz" -> when {
                    best.distance < 0.34f && best.isolation > 0.52f -> {
                        actionX = 0f
                        actionY = 0f
                        title = "ОКНО BUZZ"
                        detail = "Цель изолирована — хороший момент для входа или супер-цепочки"
                    }
                    best.distance < 0.42f && best.closing > 0.35f -> {
                        actionX = -best.vy
                        actionY = best.vx
                        title = "НЕ ЛОВИ ЛОБ"
                        detail = "Враг сокращает дистанцию — сместись поперёк и контратакуй"
                    }
                    best.isolation < 0.30f && best.distance < 0.58f -> {
                        actionX = 0f
                        actionY = 0f
                        title = "НЕ ВХОДИ"
                        detail = "Цель прикрыта — дождись, пока союзники отделятся"
                    }
                    else -> {
                        actionX = 0f
                        actionY = 0f
                        title = "ЖДИ УГОЛ"
                        detail = "Ищи изолированную цель вместо прямого размена"
                    }
                }

                "Tick" -> when {
                    best.distance < 0.30f -> {
                        val dx = player.cx - best.enemy.cx
                        val dy = player.cy - best.enemy.cy
                        val len = max(0.001f, hypot(dx, dy))
                        actionX = dx / len
                        actionY = dy / len
                        title = "RESET"
                        detail = "Враг слишком близко — отходи диагонально"
                    }
                    best.isolation > 0.50f && best.distance > 0.36f -> {
                        actionX = best.vx
                        actionY = best.vy
                        title = "ЗАКРОЙ ПУТЬ"
                        detail = "Цель изолирована — ставь мины перед её направлением движения"
                    }
                    enemies.size >= 2 && best.distance > 0.34f -> {
                        actionX = 0f
                        actionY = 0f
                        title = "ЗОНИРУЙ"
                        detail = "Не гонись за одной целью — перекрой общий маршрут группы"
                    }
                    else -> {
                        actionX = 0f
                        actionY = 0f
                        title = "ДАЛЬНЯЯ ЗОНА"
                        detail = "Сохраняй дистанцию и наказывай предсказуемое движение"
                    }
                }

                else -> {
                    actionX = 0f
                    actionY = 0f
                    title = if (fireWindow >= 0.70f) "ОКНО ВЫСТРЕЛА" else "ФОКУС"
                    detail = if (fireWindow >= 0.70f) {
                        "Цель сейчас движется мало — хороший момент для твоего выстрела"
                    } else {
                        "Фокус на самой опасной цели по дистанции и давлению"
                    }
                }
            }
        }

        remember(enemies, now)

        return CombatIntelResult(
            title,
            detail,
            best.enemy,
            best.score,
            best.enemy.cx,
            best.enemy.cy,
            actionX.coerceIn(-1f, 1f),
            actionY.coerceIn(-1f, 1f),
            fireWindow
        )
    }

    private fun nearestTrack(enemy: Detection, now: Long): Track? {
        var best: Track? = null
        var bestD = 0.10f
        for (track in tracks) {
            val d = hypot(track.x - enemy.cx, track.y - enemy.cy)
            if (d < bestD && now - track.at <= 250L) {
                best = track
                bestD = d
            }
        }
        return best
    }

    private fun remember(enemies: List<Detection>, now: Long) {
        for (enemy in enemies) {
            val existing = nearestTrack(enemy, now)
            if (existing != null) {
                existing.x = enemy.cx
                existing.y = enemy.cy
                existing.at = now
            } else {
                tracks.add(Track(enemy.cx, enemy.cy, now))
            }
        }
        tracks.removeAll { now - it.at > 450L }
        while (tracks.size > 8) tracks.removeAt(0)
    }
}