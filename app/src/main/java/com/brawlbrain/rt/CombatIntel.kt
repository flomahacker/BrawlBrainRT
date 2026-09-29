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
            val dt = if (previous == null) {
                0.10f
            } else {
                ((now - previous.at).coerceIn(40L, 250L) / 1000f)
            }

            val vx = if (previous == null) 0f
            else ((enemy.cx - previous.x) / dt).coerceIn(-2.5f, 2.5f)

            val vy = if (previous == null) 0f
            else ((enemy.cy - previous.y) / dt).coerceIn(-2.5f, 2.5f)

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

        val toTarget = direction(
            player.cx, player.cy,
            best.enemy.cx, best.enemy.cy
        )
        val awayFromTarget = Pair(-toTarget.first, -toTarget.second)
        val lateral = Pair(-toTarget.second, toTarget.first)
        val predictedTarget = Pair(
            (best.enemy.cx + best.vx * 0.18f).coerceIn(0.04f, 0.96f),
            (best.enemy.cy + best.vy * 0.18f).coerceIn(0.04f, 0.96f)
        )

        val action: Pair<Float, Float>
        val title: String
        val detail: String

        if (projectileUrgent) {
            val side = if (best.vx * lateral.first + best.vy * lateral.second >= 0f) {
                -1f
            } else {
                1f
            }
            action = Pair(
                lateral.first * side * 0.92f + awayFromTarget.first * 0.16f,
                lateral.second * side * 0.92f + awayFromTarget.second * 0.16f
            )
            title = "СНАРЯД"
            detail = "Сместись поперёк линии огня • ETA " + (projectile?.etaMs ?: 0) + " мс"
        } else {
            when (brawler) {
                "Buzz" -> when {
                    best.distance < 0.34f && best.isolation > 0.52f -> {
                        action = toTarget
                        title = "ОКНО BUZZ"
                        detail = "Цель изолирована — хороший момент для входа или супер-цепочки"
                    }

                    best.distance < 0.42f && best.closing > 0.35f -> {
                        val side = if (
                            best.vx * lateral.first + best.vy * lateral.second >= 0f
                        ) -1f else 1f
                        action = Pair(
                            lateral.first * side * 0.88f + toTarget.first * 0.20f,
                            lateral.second * side * 0.88f + toTarget.second * 0.20f
                        )
                        title = "НЕ ЛОВИ ЛОБ"
                        detail = "Враг сокращает дистанцию — смести угол и контратакуй"
                    }

                    best.isolation < 0.30f && best.distance < 0.58f -> {
                        action = awayFromTarget
                        title = "НЕ ВХОДИ"
                        detail = "Цель прикрыта — выйди из прямого размена"
                    }

                    else -> {
                        val flankPoint = Pair(
                            (best.enemy.cx + lateral.first * 0.24f).coerceIn(0.04f, 0.96f),
                            (best.enemy.cy + lateral.second * 0.24f).coerceIn(0.04f, 0.96f)
                        )
                        action = direction(
                            player.cx, player.cy,
                            flankPoint.first, flankPoint.second
                        )
                        title = "ЖДИ УГОЛ"
                        detail = "Сместись к углу и дождись изоляции цели"
                    }
                }

                "Tick" -> when {
                    best.distance < 0.30f -> {
                        action = awayFromTarget
                        title = "RESET"
                        detail = "Враг слишком близко — немедленно разрывай дистанцию"
                    }

                    best.isolation > 0.50f && best.distance > 0.36f -> {
                        action = direction(
                            player.cx, player.cy,
                            predictedTarget.first, predictedTarget.second
                        )
                        title = "ЗАКРОЙ ПУТЬ"
                        detail = "Цель изолирована — перекрой её следующий маршрут"
                    }

                    enemies.size >= 2 && best.distance > 0.34f -> {
                        var centerX = 0f
                        var centerY = 0f
                        enemies.take(3).forEach {
                            centerX += it.cx
                            centerY += it.cy
                        }
                        val count = minOf(enemies.size, 3)
                        centerX /= count
                        centerY /= count

                        action = direction(
                            player.cx, player.cy,
                            centerX, centerY
                        )
                        title = "ЗОНИРУЙ"
                        detail = "Держи пространство между несколькими целями"
                    }

                    else -> {
                        action = normalize(
                            lateral.first * 0.72f + awayFromTarget.first * 0.35f,
                            lateral.second * 0.72f + awayFromTarget.second * 0.35f
                        )
                        title = "ДАЛЬНЯЯ ЗОНА"
                        detail = "Сохраняй дистанцию и вынуждай врага идти по предсказуемой линии"
                    }
                }

                else -> {
                    if (fireWindow >= 0.70f) {
                        action = toTarget
                        title = "ОКНО ВЫСТРЕЛА"
                        detail = "Цель сейчас движется мало — открывай огонь"
                    } else {
                        action = lateral
                        title = "ФОКУС"
                        detail = "Сместись на линию атаки выбранной цели"
                    }
                }
            }
        }

        remember(enemies, now)

        val safeAction = normalize(action.first, action.second)

        return CombatIntelResult(
            title,
            detail,
            best.enemy,
            best.score,
            best.enemy.cx,
            best.enemy.cy,
            safeAction.first,
            safeAction.second,
            fireWindow
        )
    }

    private fun direction(
        fromX: Float,
        fromY: Float,
        toX: Float,
        toY: Float
    ): Pair<Float, Float> {
        return normalize(toX - fromX, toY - fromY)
    }

    private fun normalize(x: Float, y: Float): Pair<Float, Float> {
        val len = max(0.001f, hypot(x, y))
        return Pair(
            (x / len).coerceIn(-1f, 1f),
            (y / len).coerceIn(-1f, 1f)
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
