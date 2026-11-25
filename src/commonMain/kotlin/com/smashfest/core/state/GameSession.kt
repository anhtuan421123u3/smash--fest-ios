package com.smashfest.core.state

import com.smashfest.core.economy.IapProductId
import com.smashfest.core.economy.LivesManager
import com.smashfest.core.economy.ShopCatalog
import com.smashfest.core.level.LevelGenerator
import com.smashfest.core.model.BoosterType
import com.smashfest.core.model.DifficultyTier
import com.smashfest.core.model.LevelConfig
import com.smashfest.core.model.PlayerProfile

data class RoundResult(
    val won: Boolean,
    val coinsEarned: Int,
    val cupsEarned: Int,
    val stars: Int,
    val showInterstitialAd: Boolean,
    val updatedProfile: PlayerProfile
)

class GameSession(
    initialProfile: PlayerProfile,
    nowMs: Long
) {
    var profile: PlayerProfile = LivesManager.syncRegeneration(initialProfile, nowMs)
        private set

    val level: LevelConfig = LevelGenerator.generate(profile.currentLevel)

    var ammoRemaining: Int = level.ammoCount
        private set

    var chainCount: Int = 0
        private set

    var roundFinished: Boolean = false
        private set

    private val primaryTargetIds: Set<Int> =
        level.blocks.filter { it.material.isPrimaryTarget }.map { it.id }.toSet()

    private val destroyedIds = mutableSetOf<Int>()

    fun fireCannon(
        yawDeg: Double,
        pitchDeg: Double,
        power: Double,
        booster: BoosterType
    ): Boolean {
        if (roundFinished || ammoRemaining <= 0) return false
        when (booster) {
            BoosterType.NONE -> Unit
            BoosterType.GIANT_BALL -> {
                if (profile.giantBalls > 0) {
                    profile = profile.copy(giantBalls = profile.giantBalls - 1)
                } else if (profile.coins >= booster.coinCost) {
                    profile = profile.copy(coins = profile.coins - booster.coinCost)
                } else {
                    return false
                }
            }
            BoosterType.ROCKET_SHOT -> {
                if (profile.rocketShots > 0) {
                    profile = profile.copy(rocketShots = profile.rocketShots - 1)
                } else if (profile.coins >= booster.coinCost) {
                    profile = profile.copy(coins = profile.coins - booster.coinCost)
                } else {
                    return false
                }
            }
        }
        ammoRemaining -= 1
        return true
    }

    fun registerBlockDestroyed(blockId: Int): Int {
        if (destroyedIds.add(blockId)) {
            chainCount += 1
        }
        return chainCount
    }

    fun remainingPrimaryTargets(): Int {
        return primaryTargetIds.count { it !in destroyedIds }
    }

    fun isAllTargetsCleared(): Boolean = remainingPrimaryTargets() == 0

    fun applyFailOfferContinue(nowMs: Long): Boolean {
        if (roundFinished) return false
        profile = ShopCatalog.applyPurchase(profile, IapProductId.FAIL_OFFER, nowMs)
        ammoRemaining += ShopCatalog.findProduct(IapProductId.FAIL_OFFER).extraCannonballs
        return true
    }

    fun evaluateRoundEnd(nowMs: Long): RoundResult {
        if (roundFinished) {
            return RoundResult(
                won = isAllTargetsCleared(),
                coinsEarned = 0,
                cupsEarned = 0,
                stars = 0,
                showInterstitialAd = false,
                updatedProfile = profile
            )
        }
        roundFinished = true
        val won = isAllTargetsCleared()
        if (won) {
            val completed = level.levelNumber
            val coinsEarned = level.coinReward
            val cupsEarned = when (level.difficulty) {
                DifficultyTier.NORMAL -> 10 + ammoRemaining * 3
                DifficultyTier.HARD -> 20 + ammoRemaining * 4
                DifficultyTier.SUPER_HARD -> 35 + ammoRemaining * 5
            }
            val stars = when {
                ammoRemaining >= 2 -> 3
                ammoRemaining == 1 -> 2
                else -> 1
            }
            val showAd = ShopCatalog.shouldShowInterstitialAfterLevel(profile, completed)
            profile = profile.copy(
                currentLevel = completed + 1,
                coins = profile.coins + coinsEarned,
                festivalCups = profile.festivalCups + cupsEarned
            )
            return RoundResult(
                won = true,
                coinsEarned = coinsEarned,
                cupsEarned = cupsEarned,
                stars = stars,
                showInterstitialAd = showAd,
                updatedProfile = profile
            )
        } else {
            profile = LivesManager.consumeLifeOnFail(profile, nowMs)
            return RoundResult(
                won = false,
                coinsEarned = 0,
                cupsEarned = 0,
                stars = 0,
                showInterstitialAd = false,
                updatedProfile = profile
            )
        }
    }
}
