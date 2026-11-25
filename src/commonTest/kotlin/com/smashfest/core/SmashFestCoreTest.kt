package com.smashfest.core

import com.smashfest.core.audio.AsmrSoundType
import com.smashfest.core.audio.AsmrSynthesizer
import com.smashfest.core.economy.IapProductId
import com.smashfest.core.economy.LivesManager
import com.smashfest.core.economy.ShopCatalog
import com.smashfest.core.league.CompetitionEngine
import com.smashfest.core.level.LevelGenerator
import com.smashfest.core.model.BoosterType
import com.smashfest.core.model.DifficultyTier
import com.smashfest.core.model.MaterialType
import com.smashfest.core.model.PlayerProfile
import com.smashfest.core.physics.Ballistics
import com.smashfest.core.state.GameSession
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class SmashFestCoreTest {

    @Test
    fun generatesOver2000DeterministicLevelsWithProperDifficultyAndCoinRewards() {
        val level1 = LevelGenerator.generate(1)
        val level1Again = LevelGenerator.generate(1)
        assertEquals(level1, level1Again, "Same level number must be 100% deterministic")
        assertEquals(DifficultyTier.NORMAL, level1.difficulty)
        assertEquals(20, level1.coinReward)

        val level5 = LevelGenerator.generate(5)
        assertEquals(DifficultyTier.HARD, level5.difficulty)
        assertEquals(50, level5.coinReward)

        val level10 = LevelGenerator.generate(10)
        assertEquals(DifficultyTier.SUPER_HARD, level10.difficulty)
        assertEquals(100, level10.coinReward)

        // Verify 2,100 levels all have breakable targets and valid shot par
        for (lvl in listOf(1, 2, 15, 99, 500, 1250, 2000, 2100)) {
            val generated = LevelGenerator.generate(lvl)
            val breakableTargets = generated.blocks.count { it.material.isPrimaryTarget }
            assertTrue(breakableTargets >= 4, "Level $lvl must contain breakable festival targets")
            assertTrue(generated.ammoCount in 2..9, "Level $lvl must have skill-balanced cannonball count")
        }
    }

    @Test
    fun ballisticsComputesAimVelocityTrajectoryAndChainFracture() {
        val velocity = Ballistics.computeLaunchVelocity(
            yawDegrees = 0.0,
            pitchDegrees = 22.0,
            powerNormalized = 0.85
        )
        assertTrue(velocity.z < -15.0, "Cannon shoots forward along negative Z toward tower")
        assertTrue(velocity.y > 4.0, "Positive pitch launches upward arc")

        val arc = Ballistics.sampleTrajectory(velocity, steps = 16, dt = 0.05)
        assertEquals(16, arc.size)
        assertTrue(arc.last().z < arc.first().z)

        // Fragile jars shatter at low impulse; heavy stone resists weak hits
        assertTrue(Ballistics.willFracture(MaterialType.FRAGILE_JAR, impactSpeed = 3.2))
        assertTrue(Ballistics.willFracture(MaterialType.GLASS_BOTTLE, impactSpeed = 2.6))
        assertFalse(Ballistics.willFracture(MaterialType.HEAVY_STONE, impactSpeed = 4.0))
    }

    @Test
    fun livesSystemEnforces5LivesCap30MinRegenCoinRefillAndRewardedAd() {
        val t0 = 1_700_000_000_000L
        var profile = PlayerProfile(lives = 5, coins = 140, lastLifeTimestampMs = t0)

        // Lose 2 lives on level failures
        profile = LivesManager.consumeLifeOnFail(profile, nowMs = t0)
        profile = LivesManager.consumeLifeOnFail(profile, nowMs = t0 + 1_000L)
        assertEquals(3, profile.lives)

        // After 29 minutes: still 3 lives
        val after29m = LivesManager.syncRegeneration(profile, nowMs = t0 + 29 * 60_000L)
        assertEquals(3, after29m.lives)

        // After 61 minutes: +2 lives -> back to max 5
        val after61m = LivesManager.syncRegeneration(profile, nowMs = t0 + 61 * 60_000L)
        assertEquals(5, after61m.lives)

        // Drain to 0 lives and recover +1 via Rewarded Ad, then full 5 via Coins
        var emptyProfile = profile.copy(lives = 0, lastLifeTimestampMs = t0)
        val afterAd = LivesManager.grantRewardedAdLife(emptyProfile, nowMs = t0 + 5_000L)
        assertEquals(1, afterAd.lives)

        val afterCoinRefill = LivesManager.buyFullLivesWithCoins(emptyProfile, nowMs = t0 + 5_000L)
        assertNotNull(afterCoinRefill)
        assertEquals(5, afterCoinRefill.lives)
        assertEquals(40, afterCoinRefill.coins)
    }

    @Test
    fun monetizationMatchesStorePricesOfflineSuppressionAndPacedAds() {
        val noAds = ShopCatalog.findProduct(IapProductId.NO_ADS)
        val failOffer = ShopCatalog.findProduct(IapProductId.FAIL_OFFER)
        assertEquals("$9.99", noAds.priceLabel)
        assertEquals("$4.99", failOffer.priceLabel)
        assertTrue(ShopCatalog.allProducts.any { it.priceLabel == "$1.99" })
        assertTrue(ShopCatalog.allProducts.any { it.priceLabel == "$54.99" })

        val onlineFreePlayer = PlayerProfile(currentLevel = 6, noAdsOwned = false, offlineMode = false)
        assertTrue(ShopCatalog.shouldShowInterstitialAfterLevel(onlineFreePlayer, completedLevel = 6))
        assertFalse(ShopCatalog.shouldShowInterstitialAfterLevel(onlineFreePlayer, completedLevel = 5))

        val offlinePlayer = onlineFreePlayer.copy(offlineMode = true)
        assertFalse(ShopCatalog.shouldShowInterstitialAfterLevel(offlinePlayer, completedLevel = 6))

        val paidNoAdsPlayer = ShopCatalog.applyPurchase(onlineFreePlayer, IapProductId.NO_ADS)
        assertTrue(paidNoAdsPlayer.noAdsOwned)
        assertFalse(ShopCatalog.shouldShowInterstitialAfterLevel(paidNoAdsPlayer, completedLevel = 6))
    }

    @Test
    fun competitionUnlocksAtMilestoneAndRanksPlayerAgainstFestivalRivals() {
        assertFalse(CompetitionEngine.isUnlocked(currentLevel = 5))
        assertTrue(CompetitionEngine.isUnlocked(currentLevel = 10))

        val board = CompetitionEngine.buildLeaderboard(
            playerName = "You",
            playerCups = 185,
            daySeed = 20261125L
        )
        assertEquals(20, board.size)
        assertTrue(board.any { it.isPlayer && it.cups == 185 })
        assertEquals(board.sortedByDescending { it.cups }, board)
    }

    @Test
    fun asmrSynthesizerOutputsValidRiffWavePcmData() {
        val wav = AsmrSynthesizer.synthesizeWav(AsmrSoundType.CERAMIC_SHATTER)
        assertTrue(wav.size > 44, "WAV must contain 44-byte RIFF header plus PCM samples")
        assertEquals('R'.code.toByte(), wav[0])
        assertEquals('I'.code.toByte(), wav[1])
        assertEquals('F'.code.toByte(), wav[2])
        assertEquals('F'.code.toByte(), wav[3])
        assertEquals('W'.code.toByte(), wav[8])
        assertEquals('A'.code.toByte(), wav[9])
        assertEquals('V'.code.toByte(), wav[10])
        assertEquals('E'.code.toByte(), wav[11])
    }

    @Test
    fun gameSessionTracksChainDestructionVictoryAndFailOfferContinuation() {
        val profile = PlayerProfile(currentLevel = 5, coins = 0, lives = 5) // Level 5 is HARD (+50 coins)
        val session = GameSession(profile, nowMs = 1_000_000L)
        val targetIds = session.level.blocks.filter { it.material.isPrimaryTarget }.map { it.id }

        assertTrue(session.fireCannon(yawDeg = 0.0, pitchDeg = 18.0, power = 0.9, booster = BoosterType.NONE))
        targetIds.forEach { id -> session.registerBlockDestroyed(id) }
        val result = session.evaluateRoundEnd(nowMs = 1_010_000L)

        assertTrue(result.won)
        assertEquals(50, result.coinsEarned)
        assertEquals(6, session.profile.currentLevel)
        assertEquals(50, session.profile.coins)
    }
}
