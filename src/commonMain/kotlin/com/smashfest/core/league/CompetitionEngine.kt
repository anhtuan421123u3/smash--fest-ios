package com.smashfest.core.league

data class LeaderboardEntry(
    val rank: Int,
    val name: String,
    val cups: Int,
    val badge: String,
    val isPlayer: Boolean
)

object CompetitionEngine {
    const val UNLOCK_LEVEL = 10

    private val rivalNames = listOf(
        "Linh_Festival", "CarnivalKing", "ShatterPro", "Mia_Boom", "KaiCannon",
        "HanaVase", "RioSpark", "ZoeSmasher", "Bao_ASMR", "TaroTower",
        "LunaGlass", "MaxBoulder", "NoraPop", "KenjiFest", "ElenaGold",
        "DuyBreaker", "SamirArc", "ChloeStar", "ViktorKeg"
    )

    fun isUnlocked(currentLevel: Int): Boolean = currentLevel >= UNLOCK_LEVEL

    fun buildLeaderboard(
        playerName: String,
        playerCups: Int,
        daySeed: Long
    ): List<LeaderboardEntry> {
        val raw = mutableListOf<Triple<String, Int, Boolean>>()
        raw.add(Triple(playerName, playerCups.coerceAtLeast(0), true))

        var state = daySeed xor 0x9E3779B97F4A7C15UL.toLong()
        for (i in rivalNames.indices) {
            state = state * 6364136223846793005L + 1442695040888963407L
            val jitter = ((state ushr 24) % 95L).toInt().let { if (it < 0) it + 95 else it }
            val baseCups = 420 - i * 18 + (jitter - 45)
            raw.add(Triple(rivalNames[i], baseCups.coerceAtLeast(25), false))
        }

        val sorted = raw.sortedByDescending { it.second }
        return sorted.mapIndexed { index, (name, cups, isPlayer) ->
            val rank = index + 1
            val badge = when (rank) {
                1 -> "GOLD"
                2 -> "SILVER"
                3 -> "BRONZE"
                else -> "#$rank"
            }
            LeaderboardEntry(
                rank = rank,
                name = name,
                cups = cups,
                badge = badge,
                isPlayer = isPlayer
            )
        }
    }
}
