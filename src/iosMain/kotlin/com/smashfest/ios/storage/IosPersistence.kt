package com.smashfest.ios.storage

import com.smashfest.core.economy.LivesManager
import com.smashfest.core.model.PlayerProfile
import platform.Foundation.NSDate
import platform.Foundation.NSUserDefaults
import platform.Foundation.timeIntervalSince1970

object IosPersistence {
    private const val KEY_INITIALIZED = "sf_initialized_v1"
    private const val KEY_LEVEL = "sf_current_level"
    private const val KEY_COINS = "sf_coins"
    private const val KEY_LIVES = "sf_lives"
    private const val KEY_LIFE_TS = "sf_last_life_ts_ms"
    private const val KEY_NO_ADS = "sf_no_ads"
    private const val KEY_OFFLINE = "sf_offline_mode"
    private const val KEY_GIANT = "sf_giant_balls"
    private const val KEY_ROCKET = "sf_rocket_shots"
    private const val KEY_CUPS = "sf_festival_cups"
    private const val KEY_DAILY_DAY = "sf_daily_day"

    fun nowMs(): Long = (NSDate().timeIntervalSince1970 * 1000.0).toLong()

    fun load(): PlayerProfile {
        val defaults = NSUserDefaults.standardUserDefaults
        val now = nowMs()
        if (!defaults.boolForKey(KEY_INITIALIZED)) {
            val fresh = PlayerProfile(lastLifeTimestampMs = now)
            save(fresh)
            return fresh
        }
        val raw = PlayerProfile(
            currentLevel = defaults.integerForKey(KEY_LEVEL).toInt().coerceAtLeast(1),
            coins = defaults.integerForKey(KEY_COINS).toInt().coerceAtLeast(0),
            lives = defaults.integerForKey(KEY_LIVES).toInt().coerceIn(0, LivesManager.MAX_LIVES),
            lastLifeTimestampMs = defaults.doubleForKey(KEY_LIFE_TS).toLong(),
            noAdsOwned = defaults.boolForKey(KEY_NO_ADS),
            offlineMode = defaults.boolForKey(KEY_OFFLINE),
            giantBalls = defaults.integerForKey(KEY_GIANT).toInt().coerceAtLeast(0),
            rocketShots = defaults.integerForKey(KEY_ROCKET).toInt().coerceAtLeast(0),
            festivalCups = defaults.integerForKey(KEY_CUPS).toInt().coerceAtLeast(0),
            dailyChallengeDoneDay = defaults.doubleForKey(KEY_DAILY_DAY).toLong()
        )
        val synced = LivesManager.syncRegeneration(raw, now)
        if (synced != raw) save(synced)
        return synced
    }

    fun save(profile: PlayerProfile) {
        val defaults = NSUserDefaults.standardUserDefaults
        defaults.setBool(true, forKey = KEY_INITIALIZED)
        defaults.setInteger(profile.currentLevel.toLong(), forKey = KEY_LEVEL)
        defaults.setInteger(profile.coins.toLong(), forKey = KEY_COINS)
        defaults.setInteger(profile.lives.toLong(), forKey = KEY_LIVES)
        defaults.setDouble(profile.lastLifeTimestampMs.toDouble(), forKey = KEY_LIFE_TS)
        defaults.setBool(profile.noAdsOwned, forKey = KEY_NO_ADS)
        defaults.setBool(profile.offlineMode, forKey = KEY_OFFLINE)
        defaults.setInteger(profile.giantBalls.toLong(), forKey = KEY_GIANT)
        defaults.setInteger(profile.rocketShots.toLong(), forKey = KEY_ROCKET)
        defaults.setInteger(profile.festivalCups.toLong(), forKey = KEY_CUPS)
        defaults.setDouble(profile.dailyChallengeDoneDay.toDouble(), forKey = KEY_DAILY_DAY)
    }
}
