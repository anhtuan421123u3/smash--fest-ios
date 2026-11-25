package com.smashfest.core.economy

import com.smashfest.core.model.PlayerProfile

object LivesManager {
    const val MAX_LIVES = 5
    const val REGEN_INTERVAL_MS = 30L * 60_000L // 30 minutes
    const val REFILL_COIN_COST = 100

    fun syncRegeneration(profile: PlayerProfile, nowMs: Long): PlayerProfile {
        if (profile.lives >= MAX_LIVES) {
            return profile.copy(lives = MAX_LIVES, lastLifeTimestampMs = nowMs)
        }
        val anchor = if (profile.lastLifeTimestampMs <= 0L) nowMs else profile.lastLifeTimestampMs
        val elapsed = (nowMs - anchor).coerceAtLeast(0L)
        val gained = (elapsed / REGEN_INTERVAL_MS).toInt()
        if (gained <= 0) {
            return if (profile.lastLifeTimestampMs <= 0L) profile.copy(lastLifeTimestampMs = anchor) else profile
        }
        val newLives = (profile.lives + gained).coerceAtMost(MAX_LIVES)
        val newAnchor = if (newLives >= MAX_LIVES) {
            nowMs
        } else {
            anchor + gained.toLong() * REGEN_INTERVAL_MS
        }
        return profile.copy(lives = newLives, lastLifeTimestampMs = newAnchor)
    }

    fun consumeLifeOnFail(profile: PlayerProfile, nowMs: Long): PlayerProfile {
        val synced = syncRegeneration(profile, nowMs)
        if (synced.lives <= 0) return synced
        val wasFull = synced.lives >= MAX_LIVES
        val updatedLives = (synced.lives - 1).coerceAtLeast(0)
        val nextTimestamp = if (wasFull) nowMs else synced.lastLifeTimestampMs
        return synced.copy(lives = updatedLives, lastLifeTimestampMs = nextTimestamp)
    }

    fun grantRewardedAdLife(profile: PlayerProfile, nowMs: Long): PlayerProfile {
        val synced = syncRegeneration(profile, nowMs)
        val newLives = (synced.lives + 1).coerceAtMost(MAX_LIVES)
        val ts = if (newLives >= MAX_LIVES) nowMs else synced.lastLifeTimestampMs
        return synced.copy(lives = newLives, lastLifeTimestampMs = ts)
    }

    fun buyFullLivesWithCoins(profile: PlayerProfile, nowMs: Long): PlayerProfile? {
        val synced = syncRegeneration(profile, nowMs)
        if (synced.coins < REFILL_COIN_COST || synced.lives >= MAX_LIVES) return null
        return synced.copy(
            coins = synced.coins - REFILL_COIN_COST,
            lives = MAX_LIVES,
            lastLifeTimestampMs = nowMs
        )
    }

    fun msUntilNextLife(profile: PlayerProfile, nowMs: Long): Long {
        val synced = syncRegeneration(profile, nowMs)
        if (synced.lives >= MAX_LIVES) return 0L
        val elapsed = (nowMs - synced.lastLifeTimestampMs).coerceAtLeast(0L)
        return (REGEN_INTERVAL_MS - (elapsed % REGEN_INTERVAL_MS)).coerceAtLeast(0L)
    }
}
