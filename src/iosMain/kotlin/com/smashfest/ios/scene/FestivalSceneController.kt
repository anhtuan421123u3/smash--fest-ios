package com.smashfest.ios.scene

import com.smashfest.core.audio.AsmrSoundType
import com.smashfest.core.model.BlockShape
import com.smashfest.core.model.BlockSpec
import com.smashfest.core.model.BoosterType
import com.smashfest.core.model.DifficultyTier
import com.smashfest.core.model.LevelConfig
import com.smashfest.core.model.MaterialType
import com.smashfest.core.physics.Ballistics
import com.smashfest.ios.audio.IosSoundEngine
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreGraphics.CGRect
import platform.Foundation.NSTimer
import platform.SceneKit.SCNBox
import platform.SceneKit.SCNCamera
import platform.SceneKit.SCNCylinder
import platform.SceneKit.SCNFloor
import platform.SceneKit.SCNGeometry
import platform.SceneKit.SCNLight
import platform.SceneKit.SCNLightTypeAmbient
import platform.SceneKit.SCNLightTypeDirectional
import platform.SceneKit.SCNMaterial
import platform.SceneKit.SCNNode
import platform.SceneKit.SCNPhysicsBody
import platform.SceneKit.SCNPhysicsShape
import platform.SceneKit.SCNScene
import platform.SceneKit.SCNSphere
import platform.SceneKit.SCNVector3Make
import platform.SceneKit.SCNView
import platform.UIKit.UIColor
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@OptIn(ExperimentalForeignApi::class)
class FestivalSceneController(
    frame: CValue<CGRect>,
    private val onBlockSmashed: (blockId: Int, material: MaterialType, chainCount: Int) -> Unit,
    private val onShotSettled: () -> Unit
) {
    val scnView: SCNView = SCNView(frame = frame, options = null)
    private val scene = SCNScene()

    private val cannonMountNode = SCNNode.node()
    private val cannonBarrelNode = SCNNode.node()
    private val stagePlatformNode = SCNNode.node()
    private val trajectoryDots = mutableListOf<SCNNode>()

    private data class TrackedBlock(
        val spec: BlockSpec,
        val node: SCNNode,
        var prevVx: Float = 0f,
        var prevVy: Float = 0f,
        var prevVz: Float = 0f,
        var broken: Boolean = false,
        var settleFrames: Int = 0
    )

    private val trackedBlocks = mutableListOf<TrackedBlock>()
    private val activeProjectiles = mutableListOf<Pair<SCNNode, BoosterType>>()
    private val transientNodes = mutableListOf<Pair<SCNNode, Int>>() // node to remaining frames

    private var currentLevel: LevelConfig? = null
    private var simTime: Double = 0.0
    private var physicsTimer: NSTimer? = null
    private var shotActive = false
    private var shotElapsedFrames = 0

    var yawDeg: Double = 0.0
        private set
    var pitchDeg: Double = 18.0
        private set
    var powerNorm: Double = 0.82
        private set

    init {
        scnView.scene = scene
        scnView.antialiasingMode = platform.SceneKit.SCNAntialiasingMode.SCNAntialiasingModeMultisampling4X
        scnView.allowsCameraControl = false
        scnView.backgroundColor = colorFromHex(0xFF1A1038L)

        scene.physicsWorld.gravity = SCNVector3Make(0f, Ballistics.GRAVITY.toFloat(), 0f)
        setupCameraAndLights()
        setupFestivalArena()
        setupCannon()
        setupTrajectoryDots()
        startPhysicsLoop()
    }

    fun resize(frame: CValue<CGRect>) {
        scnView.setFrame(frame)
    }

    fun loadLevel(level: LevelConfig) {
        currentLevel = level
        shotActive = false
        shotElapsedFrames = 0
        simTime = 0.0

        // Clear previous round blocks & projectiles
        trackedBlocks.forEach { it.node.removeFromParentNode() }
        trackedBlocks.clear()
        activeProjectiles.forEach { it.first.removeFromParentNode() }
        activeProjectiles.clear()
        transientNodes.forEach { it.first.removeFromParentNode() }
        transientNodes.clear()

        // Festival sky tint based on difficulty tier
        scnView.backgroundColor = when (level.difficulty) {
            DifficultyTier.NORMAL -> colorFromHex(0xFF1C2B5AL)
            DifficultyTier.HARD -> colorFromHex(0xFF3E1938L)
            DifficultyTier.SUPER_HARD -> colorFromHex(0xFF140B29L)
        }

        stagePlatformNode.position = SCNVector3Make(0f, 0.5f, -4.8f)

        for (spec in level.blocks) {
            val geom = geometryFor(spec)
            val mat = SCNMaterial.material()
            mat.diffuse.contents = colorFromHex(spec.colorHex)
            if (spec.material == MaterialType.GLASS_BOTTLE) {
                mat.transparency = 0.78
                mat.specular.contents = UIColor.whiteColor
                mat.shininess = 0.9
            } else if (spec.material == MaterialType.FRAGILE_JAR) {
                mat.specular.contents = UIColor.whiteColor
                mat.shininess = 0.55
            }
            geom.materials = listOf(mat)

            val node = SCNNode.nodeWithGeometry(geom)
            node.position = SCNVector3Make(
                spec.position.x.toFloat(),
                spec.position.y.toFloat(),
                spec.position.z.toFloat()
            )

            val body = SCNPhysicsBody.dynamicBody()
            body.mass = spec.material.mass
            body.restitution = spec.material.restitution
            body.friction = 0.65
            body.damping = 0.08
            body.angularDamping = 0.14
            body.physicsShape = SCNPhysicsShape.shapeWithGeometry(geom, options = null)
            node.physicsBody = body

            scene.rootNode.addChildNode(node)
            trackedBlocks.add(TrackedBlock(spec = spec, node = node))
        }

        updateAim(0.0, 18.0, 0.82)
    }

    fun updateAim(yaw: Double, pitch: Double, power: Double) {
        yawDeg = yaw.coerceIn(-28.0, 28.0)
        pitchDeg = pitch.coerceIn(7.0, 42.0)
        powerNorm = power.coerceIn(0.38, 1.0)

        cannonMountNode.eulerAngles = SCNVector3Make(0f, (-yawDeg * PI / 180.0).toFloat(), 0f)
        cannonBarrelNode.eulerAngles = SCNVector3Make((pitchDeg * PI / 180.0).toFloat(), 0f, 0f)

        val vel = Ballistics.computeLaunchVelocity(yawDeg, pitchDeg, powerNorm)
        val pts = Ballistics.sampleTrajectory(vel, Ballistics.CANNON_MUZZLE, steps = trajectoryDots.size, dt = 0.045)
        for (i in trajectoryDots.indices) {
            val p = pts[i]
            trajectoryDots[i].position = SCNVector3Make(p.x.toFloat(), p.y.toFloat(), p.z.toFloat())
            trajectoryDots[i].hidden = shotActive
        }
    }

    fun fireShot(booster: BoosterType) {
        val vel = Ballistics.computeLaunchVelocity(yawDeg, pitchDeg, powerNorm)
        val radius = when (booster) {
            BoosterType.GIANT_BALL -> 0.52
            BoosterType.ROCKET_SHOT -> 0.30
            BoosterType.NONE -> 0.28
        }
        val ballGeom = SCNSphere.sphereWithRadius(radius)
        val mat = SCNMaterial.material()
        mat.diffuse.contents = when (booster) {
            BoosterType.GIANT_BALL -> colorFromHex(0xFFFFD43BL)
            BoosterType.ROCKET_SHOT -> colorFromHex(0xFFFF4D4DL)
            BoosterType.NONE -> colorFromHex(0xFF2B3144L)
        }
        mat.specular.contents = UIColor.whiteColor
        ballGeom.materials = listOf(mat)

        val ballNode = SCNNode.nodeWithGeometry(ballGeom)
        ballNode.position = SCNVector3Make(
            Ballistics.CANNON_MUZZLE.x.toFloat(),
            Ballistics.CANNON_MUZZLE.y.toFloat(),
            Ballistics.CANNON_MUZZLE.z.toFloat()
        )
        val body = SCNPhysicsBody.dynamicBody()
        body.mass = when (booster) {
            BoosterType.GIANT_BALL -> 7.5
            BoosterType.ROCKET_SHOT -> 4.2
            BoosterType.NONE -> 3.0
        }
        body.restitution = 0.35
        body.friction = 0.3
        body.continuousCollisionDetectionThreshold = 0.08
        ballNode.physicsBody = body

        val speedMul = if (booster == BoosterType.ROCKET_SHOT) 1.28 else 1.0
        body.velocity = SCNVector3Make(
            (vel.x * speedMul).toFloat(),
            (vel.y * speedMul).toFloat(),
            (vel.z * speedMul).toFloat()
        )

        scene.rootNode.addChildNode(ballNode)
        activeProjectiles.add(ballNode to booster)
        shotActive = true
        shotElapsedFrames = 0
        trajectoryDots.forEach { it.hidden = true }
        IosSoundEngine.play(AsmrSoundType.CANNON_FIRE, volume = 0.9f)
        IosSoundEngine.haptic(heavy = true)
    }

    fun spawnVictoryFireworks() {
        val colors = listOf(0xFFFFD43BL, 0xFF2ED5C4L, 0xFFFF6B6BL, 0xFF9775FAL)
        for (i in 0 until 24) {
            val confetti = SCNBox.boxWithWidth(0.16, height = 0.16, length = 0.05, chamferRadius = 0.02)
            val mat = SCNMaterial.material()
            mat.diffuse.contents = colorFromHex(colors[i % colors.size])
            confetti.materials = listOf(mat)
            val node = SCNNode.nodeWithGeometry(confetti)
            val angle = i * (2.0 * PI / 24.0)
            node.position = SCNVector3Make(0f, 3.8f, -4.5f)
            val body = SCNPhysicsBody.dynamicBody()
            body.mass = 0.12
            body.velocity = SCNVector3Make(
                (cos(angle) * 5.2).toFloat(),
                (4.5 + (i % 4) * 1.1).toFloat(),
                (sin(angle) * 4.2).toFloat()
            )
            node.physicsBody = body
            scene.rootNode.addChildNode(node)
            transientNodes.add(node to 120)
        }
        IosSoundEngine.play(AsmrSoundType.VICTORY_FANFARE)
    }

    private fun setupCameraAndLights() {
        val camNode = SCNNode.node()
        val camera = SCNCamera.camera()
        camera.fieldOfView = 56.0
        camera.zNear = 0.1
        camera.zFar = 100.0
        camNode.camera = camera
        camNode.position = SCNVector3Make(0f, 4.4f, 12.2f)
        camNode.eulerAngles = SCNVector3Make(-0.19f, 0f, 0f)
        scene.rootNode.addChildNode(camNode)

        val sunNode = SCNNode.node()
        val sun = SCNLight.light()
        sun.type = SCNLightTypeDirectional
        sun.intensity = 1150.0
        sun.castsShadow = true
        sunNode.light = sun
        sunNode.eulerAngles = SCNVector3Make(-0.85f, 0.45f, 0f)
        scene.rootNode.addChildNode(sunNode)

        val ambNode = SCNNode.node()
        val amb = SCNLight.light()
        amb.type = SCNLightTypeAmbient
        amb.intensity = 420.0
        amb.color = colorFromHex(0xFFD8D0FFL)
        ambNode.light = amb
        scene.rootNode.addChildNode(ambNode)
    }

    private fun setupFestivalArena() {
        val floorGeom = SCNFloor.floor()
        floorGeom.reflectivity = 0.12
        val floorMat = SCNMaterial.material()
        floorMat.diffuse.contents = colorFromHex(0xFF28204BL)
        floorGeom.materials = listOf(floorMat)
        val floorNode = SCNNode.nodeWithGeometry(floorGeom)
        floorNode.physicsBody = SCNPhysicsBody.staticBody()
        scene.rootNode.addChildNode(floorNode)

        // Elevated festival pedestal where target structures stand
        val platGeom = SCNBox.boxWithWidth(6.8, height = 1.0, length = 3.8, chamferRadius = 0.08)
        val platMat = SCNMaterial.material()
        platMat.diffuse.contents = colorFromHex(0xFFFFB703L)
        platGeom.materials = listOf(platMat)
        stagePlatformNode.geometry = platGeom
        stagePlatformNode.position = SCNVector3Make(0f, 0.5f, -4.8f)
        val platBody = SCNPhysicsBody.kinematicBody()
        platBody.friction = 0.85
        stagePlatformNode.physicsBody = platBody
        scene.rootNode.addChildNode(stagePlatformNode)

        // Decorative festival arch pillars
        for (px in listOf(-4.5f, 4.5f)) {
            val pillarGeom = SCNCylinder.cylinderWithRadius(0.24, height = 6.0)
            val pMat = SCNMaterial.material()
            pMat.diffuse.contents = colorFromHex(0xFFFF4D6DL)
            pillarGeom.materials = listOf(pMat)
            val pillar = SCNNode.nodeWithGeometry(pillarGeom)
            pillar.position = SCNVector3Make(px, 3.0f, -6.6f)
            scene.rootNode.addChildNode(pillar)
        }
    }

    private fun setupCannon() {
        cannonMountNode.position = SCNVector3Make(0f, 0.7f, 8.6f)
        scene.rootNode.addChildNode(cannonMountNode)

        val baseGeom = SCNCylinder.cylinderWithRadius(0.55, height = 0.45)
        val baseMat = SCNMaterial.material()
        baseMat.diffuse.contents = colorFromHex(0xFFFFB703L)
        baseGeom.materials = listOf(baseMat)
        val baseNode = SCNNode.nodeWithGeometry(baseGeom)
        cannonMountNode.addChildNode(baseNode)

        val barrelGeom = SCNCylinder.cylinderWithRadius(0.26, height = 1.6)
        val barrelMat = SCNMaterial.material()
        barrelMat.diffuse.contents = colorFromHex(0xFF2B3144L)
        barrelMat.specular.contents = UIColor.whiteColor
        barrelGeom.materials = listOf(barrelMat)
        val barrelMesh = SCNNode.nodeWithGeometry(barrelGeom)
        barrelMesh.eulerAngles = SCNVector3Make((PI / 2.0).toFloat(), 0f, 0f)
        barrelMesh.position = SCNVector3Make(0f, 0.2f, -0.45f)

        cannonBarrelNode.position = SCNVector3Make(0f, 0.35f, 0f)
        cannonBarrelNode.addChildNode(barrelMesh)
        cannonMountNode.addChildNode(cannonBarrelNode)
    }

    private fun setupTrajectoryDots() {
        val dotGeom = SCNSphere.sphereWithRadius(0.065)
        val dotMat = SCNMaterial.material()
        dotMat.diffuse.contents = colorFromHex(0xFFFFF3B0L)
        dotGeom.materials = listOf(dotMat)
        repeat(14) {
            val dot = SCNNode.nodeWithGeometry(dotGeom)
            dot.opacity = 0.85
            scene.rootNode.addChildNode(dot)
            trajectoryDots.add(dot)
        }
    }

    private fun startPhysicsLoop() {
        physicsTimer = NSTimer.scheduledTimerWithTimeInterval(
            interval = 1.0 / 30.0,
            repeats = true,
            block = { _ -> stepSimulation(1.0 / 30.0) }
        )
    }

    private fun stepSimulation(dt: Double) {
        simTime += dt
        val level = currentLevel
        if (level != null && level.platformMoveX > 0.0) {
            val targetX = (sin(simTime * level.platformMoveSpeed) * level.platformMoveX).toFloat()
            stagePlatformNode.position = SCNVector3Make(targetX, 0.5f, -4.8f)
        }

        // Lifetime cleanup for 3D shards & confetti
        val iter = transientNodes.listIterator()
        while (iter.hasNext()) {
            val pair = iter.next()
            val rem = pair.second - 1
            if (rem <= 0) {
                pair.first.removeFromParentNode()
                iter.remove()
            } else {
                iter.set(pair.first to rem)
            }
        }

        // Rocket & cannonball impact scan + velocity-delta fracture detection
        for (tb in trackedBlocks) {
            if (tb.broken) continue
            val pNode = tb.node.presentationNode
            val (px, py, pz) = pNode.position.useContents { Triple(x, y, z) }
            val body = tb.node.physicsBody ?: continue
            val (vx, vy, vz) = body.velocity.useContents { Triple(x, y, z) }

            val dvx = vx - tb.prevVx
            val dvy = vy - tb.prevVy
            val dvz = vz - tb.prevVz
            val deltaSpeed = sqrt((dvx * dvx + dvy * dvy + dvz * dvz).toDouble())
            tb.prevVx = vx
            tb.prevVy = vy
            tb.prevVz = vz

            var hitByProjectile = false
            for ((ball, booster) in activeProjectiles) {
                val (bx, by, bz) = ball.presentationNode.position.useContents { Triple(x, y, z) }
                val dx = bx - px
                val dy = by - py
                val dz = bz - pz
                val distSq = (dx * dx + dy * dy + dz * dz).toDouble()
                val hitRadius = if (booster == BoosterType.GIANT_BALL) 1.05 else 0.72
                if (distSq < hitRadius * hitRadius) {
                    hitByProjectile = true
                    if (booster == BoosterType.ROCKET_SHOT) {
                        triggerRadialBlast(px, py, pz, radius = 3.2f, strength = 22.0)
                    }
                    break
                }
            }

            val droppedOffStage = py < 0.65f
            val shouldShatter = hitByProjectile ||
                droppedOffStage ||
                (shotElapsedFrames > 2 && Ballistics.willFracture(tb.spec.material, deltaSpeed))

            if (shouldShatter && tb.spec.material != MaterialType.HEAVY_STONE) {
                fractureBlock(tb, px, py, pz)
            }
        }

        if (shotActive) {
            shotElapsedFrames += 1
            // Settle after 3.2 seconds or when balls drop below stage
            val allBallsSlowOrGone = activeProjectiles.all { (ball, _) ->
                val (_, by, bz) = ball.presentationNode.position.useContents { Triple(x, y, z) }
                val (bvx, bvy, bvz) = ball.physicsBody?.velocity?.useContents { Triple(x, y, z) } ?: Triple(0f, 0f, 0f)
                val spd = bvx * bvx + bvy * bvy + bvz * bvz
                by < 0.4f || bz < -14f || (shotElapsedFrames > 30 && spd < 0.8f)
            }
            if (shotElapsedFrames > 96 || (shotElapsedFrames > 42 && allBallsSlowOrGone)) {
                shotActive = false
                trajectoryDots.forEach { it.hidden = false }
                onShotSettled()
            }
        }
    }

    private fun fractureBlock(tb: TrackedBlock, px: Float, py: Float, pz: Float) {
        tb.broken = true
        tb.node.removeFromParentNode()

        if (tb.spec.material == MaterialType.EXPLOSIVE_BARREL) {
            triggerRadialBlast(px, py, pz, radius = 3.6f, strength = 26.0)
            IosSoundEngine.play(AsmrSoundType.BARREL_BOOM)
            IosSoundEngine.haptic(heavy = true)
        } else {
            val sound = when (tb.spec.material) {
                MaterialType.GLASS_BOTTLE -> AsmrSoundType.GLASS_CHIME
                MaterialType.FRAGILE_JAR -> AsmrSoundType.CERAMIC_SHATTER
                MaterialType.WOOD_CRATE -> AsmrSoundType.WOOD_THUD
                MaterialType.HEAVY_STONE -> AsmrSoundType.STONE_RUMBLE
                MaterialType.EXPLOSIVE_BARREL -> AsmrSoundType.BARREL_BOOM
            }
            IosSoundEngine.play(sound, volume = 0.8f)
            IosSoundEngine.haptic(heavy = false)
        }

        // Spawn 6 3D shards for satisfying visual shatter
        repeat(6) { idx ->
            val shardGeom = SCNBox.boxWithWidth(0.18, height = 0.16, length = 0.14, chamferRadius = 0.02)
            val sMat = SCNMaterial.material()
            sMat.diffuse.contents = colorFromHex(tb.spec.colorHex)
            shardGeom.materials = listOf(sMat)
            val shard = SCNNode.nodeWithGeometry(shardGeom)
            shard.position = SCNVector3Make(px, py, pz)
            val sBody = SCNPhysicsBody.dynamicBody()
            sBody.mass = 0.15
            val ang = idx * (PI / 3.0)
            sBody.velocity = SCNVector3Make(
                (cos(ang) * 4.2).toFloat(),
                (2.4 + (idx % 3) * 0.9).toFloat(),
                (sin(ang) * 4.2).toFloat()
            )
            shard.physicsBody = sBody
            scene.rootNode.addChildNode(shard)
            transientNodes.add(shard to 55)
        }

        val brokenCount = trackedBlocks.count { it.broken }
        onBlockSmashed(tb.spec.id, tb.spec.material, brokenCount)
    }

    private fun triggerRadialBlast(cx: Float, cy: Float, cz: Float, radius: Float, strength: Double) {
        for (tb in trackedBlocks) {
            if (tb.broken) continue
            val (bx, by, bz) = tb.node.presentationNode.position.useContents { Triple(x, y, z) }
            val dx = (bx - cx).toDouble()
            val dy = (by - cy).toDouble() + 0.35
            val dz = (bz - cz).toDouble()
            val dist = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(0.25)
            if (dist <= radius) {
                val factor = (1.0 - dist / radius) * strength
                tb.node.physicsBody?.applyForce(
                    SCNVector3Make(
                        ((dx / dist) * factor).toFloat(),
                        ((dy / dist) * factor + 2.0).toFloat(),
                        ((dz / dist) * factor).toFloat()
                    ),
                    impulse = true
                )
            }
        }
    }

    private fun geometryFor(spec: BlockSpec): SCNGeometry {
        val w = spec.size.x
        val h = spec.size.y
        val d = spec.size.z
        return when (spec.shape) {
            BlockShape.JAR -> SCNCylinder.cylinderWithRadius(w * 0.48, height = h)
            BlockShape.BOTTLE -> SCNCylinder.cylinderWithRadius(w * 0.38, height = h)
            BlockShape.BARREL -> SCNCylinder.cylinderWithRadius(w * 0.48, height = h)
            BlockShape.SPHERE -> SCNSphere.sphereWithRadius(w * 0.5)
            BlockShape.BOX, BlockShape.PLANK -> SCNBox.boxWithWidth(w, height = h, length = d, chamferRadius = 0.04)
        }
    }

    private fun colorFromHex(argb: Long): UIColor {
        val a = ((argb ushr 24) and 0xFF) / 255.0
        val r = ((argb ushr 16) and 0xFF) / 255.0
        val g = ((argb ushr 8) and 0xFF) / 255.0
        val b = (argb and 0xFF) / 255.0
        return UIColor.colorWithRed(r, green = g, blue = b, alpha = if (a == 0.0) 1.0 else a)
    }
}
