package com.smashfest.core.physics

import com.smashfest.core.model.MaterialType
import com.smashfest.core.model.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

object Ballistics {
    const val GRAVITY = -14.5
    val CANNON_MUZZLE = Vec3(0.0, 1.35, 8.2)

    fun computeLaunchVelocity(
        yawDegrees: Double,
        pitchDegrees: Double,
        powerNormalized: Double
    ): Vec3 {
        val clampedYaw = yawDegrees.coerceIn(-30.0, 30.0)
        val clampedPitch = pitchDegrees.coerceIn(5.0, 44.0)
        val clampedPower = powerNormalized.coerceIn(0.35, 1.0)

        val yawRad = clampedYaw * PI / 180.0
        val pitchRad = clampedPitch * PI / 180.0
        val speed = 13.5 + clampedPower * 14.5

        val horizontal = speed * cos(pitchRad)
        val vx = horizontal * sin(yawRad)
        val vy = speed * sin(pitchRad)
        val vz = -horizontal * cos(yawRad)
        return Vec3(vx, vy, vz)
    }

    fun sampleTrajectory(
        velocity: Vec3,
        origin: Vec3 = CANNON_MUZZLE,
        steps: Int = 16,
        dt: Double = 0.05
    ): List<Vec3> {
        val count = steps.coerceAtLeast(1)
        return List(count) { i ->
            val t = (i + 1) * dt
            Vec3(
                x = origin.x + velocity.x * t,
                y = origin.y + velocity.y * t + 0.5 * GRAVITY * t * t,
                z = origin.z + velocity.z * t
            )
        }
    }

    fun willFracture(material: MaterialType, impactSpeed: Double): Boolean {
        return impactSpeed >= material.fractureSpeed
    }
}
