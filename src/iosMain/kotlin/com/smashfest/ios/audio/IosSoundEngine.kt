package com.smashfest.ios.audio

import com.smashfest.core.audio.AsmrSoundType
import com.smashfest.core.audio.AsmrSynthesizer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.AVFAudio.AVAudioPlayer
import platform.AVFAudio.AVAudioSession
import platform.AVFAudio.AVAudioSessionCategoryAmbient
import platform.AVFAudio.setActive
import platform.Foundation.NSData
import platform.Foundation.create
import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle

@OptIn(ExperimentalForeignApi::class)
object IosSoundEngine {
    private val cachedWavs = mutableMapOf<AsmrSoundType, NSData>()
    private val activePlayers = mutableListOf<AVAudioPlayer>()
    private var audioSessionReady = false
    private val lightHaptic = UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleLight)
    private val heavyHaptic = UIImpactFeedbackGenerator(style = UIImpactFeedbackStyle.UIImpactFeedbackStyleHeavy)
    var muted: Boolean = false

    fun warmUp() {
        if (!audioSessionReady) {
            val session = AVAudioSession.sharedInstance()
            session.setCategory(AVAudioSessionCategoryAmbient, error = null)
            session.setActive(true, error = null)
            audioSessionReady = true
        }
        if (cachedWavs.isEmpty()) {
            AsmrSoundType.entries.forEach { type ->
                val bytes = AsmrSynthesizer.synthesizeWav(type)
                cachedWavs[type] = bytes.toNSData()
            }
        }
        lightHaptic.prepare()
        heavyHaptic.prepare()
    }

    fun play(type: AsmrSoundType, volume: Float = 0.85f) {
        if (muted) return
        warmUp()
        val data = cachedWavs[type] ?: return
        val player = AVAudioPlayer(data = data, error = null)
        player.volume = volume
        player.prepareToPlay()
        player.play()
        // Keep strong references until playback completes
        activePlayers.removeAll { !it.isPlaying() }
        if (activePlayers.size > 12) {
            activePlayers.removeAt(0)
        }
        activePlayers.add(player)
    }

    fun haptic(heavy: Boolean = false) {
        if (heavy) {
            heavyHaptic.impactOccurred()
        } else {
            lightHaptic.impactOccurred()
        }
    }

    private fun ByteArray.toNSData(): NSData = usePinned { pinned ->
        NSData.create(bytes = pinned.addressOf(0), length = size.toULong())
    }
}
