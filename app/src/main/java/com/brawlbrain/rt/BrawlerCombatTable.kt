package com.brawlbrain.rt

data class BrawlerCombatProfile(
    val attackRangeRaw: Float,
    val superRangeRaw: Float
)

object BrawlerCombatTable {
    // Raw source-unit values. Keep these centralized so the UI never hard-codes radii.
    private val profiles = mapOf(
        "Buzz" to BrawlerCombatProfile(attackRangeRaw = 8f, superRangeRaw = 30f),
        "Tick" to BrawlerCombatProfile(attackRangeRaw = 26f, superRangeRaw = 10f)
    )

    fun profile(name: String): BrawlerCombatProfile =
        profiles[name] ?: BrawlerCombatProfile(attackRangeRaw = 8f, superRangeRaw = 10f)
}
