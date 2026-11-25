package com.smashfest.core.level

import com.smashfest.core.model.BlockShape
import com.smashfest.core.model.BlockSpec
import com.smashfest.core.model.DifficultyTier
import com.smashfest.core.model.LevelConfig
import com.smashfest.core.model.MaterialType
import com.smashfest.core.model.StageArchetype
import com.smashfest.core.model.Vec3

object LevelGenerator {

    private class SeededRng(seed: Long) {
        private var state: Long = seed xor 0x5DEECE66DL

        fun nextLong(): Long {
            state = state * 6364136223846793005L + 1442695040888963407L
            return state
        }

        fun nextInt(bound: Int): Int {
            val v = (nextLong() ushr 17) % bound.toLong()
            return if (v < 0) (v + bound).toInt() else v.toInt()
        }

        fun nextDouble(): Double {
            return ((nextLong() ushr 11) and ((1L shl 53) - 1)).toDouble() / (1L shl 53).toDouble()
        }
    }

    private val jarColors = listOf(0xFF2ED5C4L, 0xFFFF6B6BL, 0xFFFFC83DL, 0xFF4DABF7L, 0xFFE667FFL)
    private val bottleColors = listOf(0xFF40E0D0L, 0xFF74C0FCL, 0xFFA9E34BL, 0xFFFF8787L)
    private val woodColor = 0xFFD99B52L
    private val stoneColor = 0xFF7D8597L
    private val barrelColor = 0xFFE03131L

    fun difficultyForLevel(levelNumber: Int): DifficultyTier {
        return when {
            levelNumber % 10 == 0 -> DifficultyTier.SUPER_HARD
            levelNumber % 5 == 0 -> DifficultyTier.HARD
            else -> DifficultyTier.NORMAL
        }
    }

    fun generate(levelNumber: Int): LevelConfig {
        val safeLevel = levelNumber.coerceAtLeast(1)
        val rng = SeededRng(safeLevel.toLong() * 2654435761L + 97L)
        val difficulty = difficultyForLevel(safeLevel)

        val archetypes = StageArchetype.entries
        val archetype = when {
            safeLevel == 1 -> StageArchetype.PYRAMID
            safeLevel == 2 -> StageArchetype.TWIN_TOWERS
            safeLevel == 3 -> StageArchetype.BRIDGE_SPAN
            difficulty == DifficultyTier.SUPER_HARD -> StageArchetype.GRAND_CITADEL
            else -> archetypes[(safeLevel + rng.nextInt(archetypes.size)) % archetypes.size]
        }

        val platformBaseY = 1.0
        val centerZ = -4.2 - (rng.nextDouble() * 1.4)
        val blocks = mutableListOf<BlockSpec>()
        var nextId = 1

        fun addBlock(
            material: MaterialType,
            shape: BlockShape,
            x: Double,
            bottomY: Double,
            z: Double,
            w: Double,
            h: Double,
            d: Double,
            color: Long,
            motionAmp: Double = 0.0,
            motionSpeed: Double = 0.0
        ): Double {
            blocks.add(
                BlockSpec(
                    id = nextId++,
                    material = material,
                    shape = shape,
                    position = Vec3(x, bottomY + h * 0.5, z),
                    size = Vec3(w, h, d),
                    colorHex = color,
                    motionAmplitudeX = motionAmp,
                    motionSpeed = motionSpeed
                )
            )
            return bottomY + h
        }

        fun pickTargetMaterial(): MaterialType {
            return if (rng.nextInt(10) < 6) MaterialType.FRAGILE_JAR else MaterialType.GLASS_BOTTLE
        }

        fun addTarget(x: Double, bottomY: Double, z: Double, scale: Double = 1.0) {
            val mat = pickTargetMaterial()
            val shape = if (mat == MaterialType.FRAGILE_JAR) BlockShape.JAR else BlockShape.BOTTLE
            val color = if (mat == MaterialType.FRAGILE_JAR) {
                jarColors[rng.nextInt(jarColors.size)]
            } else {
                bottleColors[rng.nextInt(bottleColors.size)]
            }
            val w = (if (mat == MaterialType.FRAGILE_JAR) 0.68 else 0.48) * scale
            val h = (if (mat == MaterialType.FRAGILE_JAR) 0.92 else 1.05) * scale
            addBlock(mat, shape, x, bottomY, z, w, h, w, color)
        }

        when (archetype) {
            StageArchetype.PYRAMID -> {
                val baseCols = if (difficulty == DifficultyTier.NORMAL) 3 else 4
                val spacing = 0.96
                val startX = -((baseCols - 1) * spacing) * 0.5
                for (i in 0 until baseCols) {
                    val top1 = addBlock(MaterialType.WOOD_CRATE, BlockShape.BOX, startX + i * spacing, platformBaseY, centerZ, 0.82, 0.72, 0.82, woodColor)
                    addTarget(startX + i * spacing, top1, centerZ)
                }
                val topRowCount = (baseCols - 1).coerceAtLeast(2)
                val plankY = platformBaseY + 0.72 + 0.92
                val plankTop = addBlock(MaterialType.WOOD_CRATE, BlockShape.PLANK, 0.0, plankY, centerZ, baseCols * 0.95, 0.22, 0.85, woodColor)
                for (j in 0 until topRowCount) {
                    val tx = -((topRowCount - 1) * 0.85) * 0.5 + j * 0.85
                    addTarget(tx, plankTop, centerZ, scale = 0.94)
                }
            }

            StageArchetype.TWIN_TOWERS -> {
                for (side in listOf(-1.45, 1.45)) {
                    var y = platformBaseY
                    y = addBlock(MaterialType.WOOD_CRATE, BlockShape.BOX, side, y, centerZ, 0.92, 0.76, 0.92, woodColor)
                    addTarget(side - 0.34, y, centerZ, 0.85)
                    addTarget(side + 0.34, y, centerZ, 0.85)
                    val deckY = addBlock(MaterialType.WOOD_CRATE, BlockShape.PLANK, side, y + 0.92, centerZ, 1.35, 0.20, 0.88, woodColor)
                    addTarget(side, deckY, centerZ)
                }
                // Center firework keg triggers twin chain reaction when hit cleanly
                addBlock(MaterialType.EXPLOSIVE_BARREL, BlockShape.BARREL, 0.0, platformBaseY, centerZ, 0.72, 0.90, 0.72, barrelColor)
                addTarget(0.0, platformBaseY + 0.90, centerZ, 0.9)
            }

            StageArchetype.BRIDGE_SPAN -> {
                val leftTop = addBlock(MaterialType.WOOD_CRATE, BlockShape.BOX, -1.65, platformBaseY, centerZ, 0.65, 1.30, 0.85, woodColor)
                val rightTop = addBlock(MaterialType.WOOD_CRATE, BlockShape.BOX, 1.65, platformBaseY, centerZ, 0.65, 1.30, 0.85, woodColor)
                val bridgeTop = addBlock(MaterialType.WOOD_CRATE, BlockShape.PLANK, 0.0, maxOf(leftTop, rightTop), centerZ, 4.10, 0.24, 0.92, woodColor)
                for (i in -2..2) {
                    if (i == 0 && difficulty != DifficultyTier.NORMAL) {
                        addBlock(MaterialType.HEAVY_STONE, BlockShape.SPHERE, 0.0, bridgeTop, centerZ, 0.85, 0.85, 0.85, stoneColor)
                    } else {
                        addTarget(i * 0.75, bridgeTop, centerZ)
                    }
                }
                addTarget(-0.65, platformBaseY, centerZ)
                addTarget(0.65, platformBaseY, centerZ)
            }

            StageArchetype.CAROUSEL_PLATFORM -> {
                val deckTop = addBlock(MaterialType.WOOD_CRATE, BlockShape.PLANK, 0.0, platformBaseY, centerZ, 3.6, 0.26, 1.1, woodColor)
                for (i in -1..1) {
                    val pillarTop = addBlock(MaterialType.WOOD_CRATE, BlockShape.BOX, i * 1.15, deckTop, centerZ, 0.62, 0.85, 0.62, woodColor)
                    addTarget(i * 1.15, pillarTop, centerZ)
                }
                addTarget(-0.55, deckTop, centerZ)
                addTarget(0.55, deckTop, centerZ)
                if (difficulty != DifficultyTier.NORMAL) {
                    addBlock(MaterialType.EXPLOSIVE_BARREL, BlockShape.BARREL, 0.0, deckTop, centerZ + 0.2, 0.68, 0.86, 0.68, barrelColor)
                }
            }

            StageArchetype.STONE_CRUSHER -> {
                // Heavy stones perched on wooden supports over rows of fragile jars
                for (x in listOf(-1.25, 1.25)) {
                    addTarget(x, platformBaseY, centerZ)
                    val pillarTop = addBlock(MaterialType.WOOD_CRATE, BlockShape.BOX, x * 0.45, platformBaseY, centerZ, 0.54, 1.25, 0.68, woodColor)
                    val shelfTop = addBlock(MaterialType.WOOD_CRATE, BlockShape.PLANK, x * 0.72, pillarTop, centerZ, 1.45, 0.22, 0.88, woodColor)
                    addBlock(MaterialType.HEAVY_STONE, BlockShape.SPHERE, x * 0.92, shelfTop, centerZ, 0.86, 0.86, 0.86, stoneColor)
                    addTarget(x * 0.45, shelfTop, centerZ, 0.9)
                }
                addTarget(0.0, platformBaseY, centerZ)
            }

            StageArchetype.GRAND_CITADEL -> {
                val cols = 4
                for (i in 0 until cols) {
                    val x = -1.65 + i * 1.10
                    val pTop = addBlock(
                        if (i == 0 || i == cols - 1) MaterialType.HEAVY_STONE else MaterialType.WOOD_CRATE,
                        BlockShape.BOX,
                        x,
                        platformBaseY,
                        centerZ,
                        0.72,
                        1.05,
                        0.82,
                        if (i == 0 || i == cols - 1) stoneColor else woodColor
                    )
                    addTarget(x, pTop, centerZ)
                }
                val tier2Top = addBlock(MaterialType.WOOD_CRATE, BlockShape.PLANK, 0.0, platformBaseY + 1.05 + 0.92, centerZ, 4.2, 0.24, 0.94, woodColor)
                addTarget(-1.2, tier2Top, centerZ)
                addBlock(MaterialType.EXPLOSIVE_BARREL, BlockShape.BARREL, 0.0, tier2Top, centerZ, 0.72, 0.88, 0.72, barrelColor)
                addTarget(1.2, tier2Top, centerZ)
                val crownTop = addBlock(MaterialType.WOOD_CRATE, BlockShape.PLANK, 0.0, tier2Top + 0.88, centerZ, 2.1, 0.22, 0.82, woodColor)
                addTarget(-0.55, crownTop, centerZ)
                addTarget(0.55, crownTop, centerZ)
            }
        }

        // Moving target platform for timing challenge (gradually introduced from level 4+)
        val platformAmp = when {
            safeLevel < 4 -> 0.0
            archetype == StageArchetype.CAROUSEL_PLATFORM -> 1.65
            difficulty == DifficultyTier.SUPER_HARD -> 1.55
            difficulty == DifficultyTier.HARD -> 1.15
            safeLevel >= 12 && safeLevel % 2 == 0 -> 0.85
            else -> 0.0
        }
        val platformSpeed = if (platformAmp > 0.0) {
            1.15 + (safeLevel % 7) * 0.14
        } else {
            0.0
        }

        val targetCount = blocks.count { it.material.isPrimaryTarget }
        val baseAmmo = when (difficulty) {
            DifficultyTier.NORMAL -> ((targetCount + 1) / 2 + 2).coerceIn(3, 6)
            DifficultyTier.HARD -> ((targetCount + 1) / 2 + 1).coerceIn(3, 7)
            DifficultyTier.SUPER_HARD -> ((targetCount + 1) / 2 + 2).coerceIn(4, 8)
        }

        return LevelConfig(
            levelNumber = safeLevel,
            difficulty = difficulty,
            coinReward = difficulty.coinReward,
            archetype = archetype,
            ammoCount = baseAmmo,
            platformMoveX = platformAmp,
            platformMoveSpeed = platformSpeed,
            blocks = blocks,
            themeSeed = safeLevel.toLong() * 31337L
        )
    }
}
