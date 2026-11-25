package com.smashfest.core.economy

import com.smashfest.core.model.PlayerProfile

enum class IapProductId {
    NO_ADS,
    FAIL_OFFER,
    STARTER_PACK,
    CARNIVAL_CHEST,
    MEGA_VAULT,
    ROYAL_TREASURY
}

data class IapProduct(
    val id: IapProductId,
    val title: String,
    val subtitle: String,
    val priceLabel: String,
    val coinsGranted: Int,
    val giantBallsGranted: Int = 0,
    val rocketShotsGranted: Int = 0,
    val grantsNoAds: Boolean = false,
    val grantsFullLives: Boolean = false,
    val extraCannonballs: Int = 0
)

object ShopCatalog {
    val allProducts: List<IapProduct> = listOf(
        IapProduct(
            id = IapProductId.NO_ADS,
            title = "No Ads Bundle",
            subtitle = "Remove all interstitial ads forever + 250 Coins",
            priceLabel = "$9.99",
            coinsGranted = 250,
            grantsNoAds = true
        ),
        IapProduct(
            id = IapProductId.FAIL_OFFER,
            title = "Festival Rescue Offer",
            subtitle = "+2 Extra Cannonballs + 5 Lives + 300 Coins",
            priceLabel = "$4.99",
            coinsGranted = 300,
            grantsFullLives = true,
            extraCannonballs = 2
        ),
        IapProduct(
            id = IapProductId.STARTER_PACK,
            title = "Starter Coin Pouch",
            subtitle = "500 Festival Coins + 1 Giant Ball",
            priceLabel = "$1.99",
            coinsGranted = 500,
            giantBallsGranted = 1
        ),
        IapProduct(
            id = IapProductId.CARNIVAL_CHEST,
            title = "Carnival Chest",
            subtitle = "1,500 Coins + 3 Giant Balls + 2 Rocket Shots",
            priceLabel = "$4.99",
            coinsGranted = 1500,
            giantBallsGranted = 3,
            rocketShotsGranted = 2
        ),
        IapProduct(
            id = IapProductId.MEGA_VAULT,
            title = "Mega Smash Vault",
            subtitle = "7,000 Coins + 6 Giant Balls + 4 Rocket Shots",
            priceLabel = "$19.99",
            coinsGranted = 7000,
            giantBallsGranted = 6,
            rocketShotsGranted = 4
        ),
        IapProduct(
            id = IapProductId.ROYAL_TREASURY,
            title = "Royal Festival Treasury",
            subtitle = "25,000 Coins + 20 Giant Balls + 15 Rocket Shots",
            priceLabel = "$54.99",
            coinsGranted = 25000,
            giantBallsGranted = 20,
            rocketShotsGranted = 15
        )
    )

    fun findProduct(id: IapProductId): IapProduct =
        allProducts.first { it.id == id }

    /**
     * Interstitial ads appear at a moderate pace (every 3rd completed level starting at Lv.3)
     * and are suppressed completely when offline or when No Ads ($9.99) is owned.
     */
    fun shouldShowInterstitialAfterLevel(profile: PlayerProfile, completedLevel: Int): Boolean {
        if (profile.noAdsOwned || profile.offlineMode) return false
        return completedLevel >= 3 && completedLevel % 3 == 0
    }

    fun applyPurchase(profile: PlayerProfile, id: IapProductId, nowMs: Long = 0L): PlayerProfile {
        val product = findProduct(id)
        return profile.copy(
            coins = profile.coins + product.coinsGranted,
            giantBalls = profile.giantBalls + product.giantBallsGranted,
            rocketShots = profile.rocketShots + product.rocketShotsGranted,
            noAdsOwned = profile.noAdsOwned || product.grantsNoAds,
            lives = if (product.grantsFullLives) LivesManager.MAX_LIVES else profile.lives,
            lastLifeTimestampMs = if (product.grantsFullLives && nowMs > 0L) nowMs else profile.lastLifeTimestampMs
        )
    }
}
