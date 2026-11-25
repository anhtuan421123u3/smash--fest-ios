package com.smashfest.core.model

enum class DifficultyTier(val label: String, val coinReward: Int) {
    NORMAL("Normal", 20),
    HARD("Hard", 50),
    SUPER_HARD("Super Hard", 100)
}

enum class MaterialType(
    val isPrimaryTarget: Boolean,
    val fractureSpeed: Double,
    val mass: Double,
    val restitution: Double
) {
    FRAGILE_JAR(isPrimaryTarget = true, fractureSpeed = 2.2, mass = 1.0, restitution = 0.22),
    GLASS_BOTTLE(isPrimaryTarget = true, fractureSpeed = 1.7, mass = 0.75, restitution = 0.26),
    WOOD_CRATE(isPrimaryTarget = false, fractureSpeed = 5.4, mass = 2.0, restitution = 0.14),
    HEAVY_STONE(isPrimaryTarget = false, fractureSpeed = 18.0, mass = 7.5, restitution = 0.08),
    EXPLOSIVE_BARREL(isPrimaryTarget = false, fractureSpeed = 2.8, mass = 2.5, restitution = 0.16)
}

enum class BlockShape {
    JAR,
    BOTTLE,
    BOX,
    PLANK,
    SPHERE,
    BARREL
}

enum class StageArchetype {
    PYRAMID,
    TWIN_TOWERS,
    BRIDGE_SPAN,
    CAROUSEL_PLATFORM,
    STONE_CRUSHER,
    GRAND_CITADEL
}

enum class BoosterType(val displayName: String, val coinCost: Int) {
    NONE("Standard", 0),
    GIANT_BALL("Giant Ball", 60),
    ROCKET_SHOT("Rocket Shot", 85)
}

data class Vec3(
    val x: Double,
    val y: Double,
    val z: Double
)

data class BlockSpec(
    val id: Int,
    val material: MaterialType,
    val shape: BlockShape,
    val position: Vec3,
    val size: Vec3,
    val colorHex: Long,
    val motionAmplitudeX: Double = 0.0,
    val motionSpeed: Double = 0.0
)

data class LevelConfig(
    val levelNumber: Int,
    val difficulty: DifficultyTier,
    val coinReward: Int,
    val archetype: StageArchetype,
    val ammoCount: Int,
    val platformMoveX: Double,
    val platformMoveSpeed: Double,
    val blocks: List<BlockSpec>,
    val themeSeed: Long
)

data class PlayerProfile(
    val currentLevel: Int = 1,
    val coins: Int = 120,
    val lives: Int = 5,
    val lastLifeTimestampMs: Long = 0L,
    val noAdsOwned: Boolean = false,
    val offlineMode: Boolean = false,
    val giantBalls: Int = 2,
    val rocketShots: Int = 1,
    val festivalCups: Int = 0,
    val dailyChallengeDoneDay: Long = 0L
)
