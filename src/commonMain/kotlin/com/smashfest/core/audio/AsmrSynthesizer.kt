package com.smashfest.core.audio

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

enum class AsmrSoundType {
    CANNON_FIRE,
    CERAMIC_SHATTER,
    GLASS_CHIME,
    WOOD_THUD,
    STONE_RUMBLE,
    BARREL_BOOM,
    VICTORY_FANFARE,
    UI_TAP
}

object AsmrSynthesizer {

    fun synthesizeWav(type: AsmrSoundType, sampleRate: Int = 22050): ByteArray {
        val durationSec = when (type) {
            AsmrSoundType.CANNON_FIRE -> 0.24
            AsmrSoundType.CERAMIC_SHATTER -> 0.34
            AsmrSoundType.GLASS_CHIME -> 0.38
            AsmrSoundType.WOOD_THUD -> 0.18
            AsmrSoundType.STONE_RUMBLE -> 0.30
            AsmrSoundType.BARREL_BOOM -> 0.45
            AsmrSoundType.VICTORY_FANFARE -> 0.65
            AsmrSoundType.UI_TAP -> 0.08
        }
        val numSamples = (sampleRate * durationSec).toInt().coerceAtLeast(1)
        val pcm = ShortArray(numSamples)

        var noiseState = 0x12345678L
        fun nextNoise(): Double {
            noiseState = noiseState * 1664525L + 1013904223L
            val bits = ((noiseState ushr 16) and 0xFFFFL).toInt()
            return (bits / 32768.0) - 1.0
        }

        // Pentatonic soft ASMR frequencies (non-harsh, warm chimes)
        val twoPi = 2.0 * PI
        var smoothedNoise = 0.0

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate.toDouble()
            val rawNoise = nextNoise()
            smoothedNoise = smoothedNoise * 0.72 + rawNoise * 0.28

            val sample = when (type) {
                AsmrSoundType.CANNON_FIRE -> {
                    val pitch = 135.0 * exp(-t * 14.0) + 48.0
                    val env = exp(-t * 11.0)
                    (sin(twoPi * pitch * t) * 0.72 + smoothedNoise * 0.35) * env
                }

                AsmrSoundType.CERAMIC_SHATTER -> {
                    val f1 = 1046.5 // C6 warm ceramic ring
                    val f2 = 1318.5 // E6
                    val f3 = 1567.98 // G6
                    val env1 = exp(-t * 16.0)
                    val env2 = exp(-((t - 0.03).coerceAtLeast(0.0)) * 18.0)
                    val env3 = exp(-((t - 0.06).coerceAtLeast(0.0)) * 15.0)
                    val clink = sin(twoPi * f1 * t) * env1 * 0.35 +
                        sin(twoPi * f2 * t) * env2 * 0.30 +
                        sin(twoPi * f3 * t) * env3 * 0.25
                    val crumble = smoothedNoise * exp(-t * 22.0) * 0.20
                    clink + crumble
                }

                AsmrSoundType.GLASS_CHIME -> {
                    val f1 = 1567.98 // G6
                    val f2 = 1975.53 // B6
                    val f3 = 2349.32 // D7 soft shimmer
                    val env = exp(-t * 11.5)
                    (sin(twoPi * f1 * t) * 0.38 +
                        sin(twoPi * f2 * t) * 0.30 +
                        sin(twoPi * f3 * t) * 0.22 +
                        smoothedNoise * exp(-t * 28.0) * 0.14) * env
                }

                AsmrSoundType.WOOD_THUD -> {
                    val env = exp(-t * 22.0)
                    (sin(twoPi * 165.0 * t) * 0.55 +
                        sin(twoPi * 245.0 * t) * 0.25 +
                        smoothedNoise * 0.22) * env
                }

                AsmrSoundType.STONE_RUMBLE -> {
                    val env = exp(-t * 8.5)
                    (sin(twoPi * 68.0 * t) * 0.58 +
                        sin(twoPi * 92.0 * t) * 0.28 +
                        smoothedNoise * 0.30) * env
                }

                AsmrSoundType.BARREL_BOOM -> {
                    val sub = sin(twoPi * (90.0 * exp(-t * 6.0) + 38.0) * t)
                    val env = exp(-t * 5.8)
                    (sub * 0.65 + smoothedNoise * 0.48) * env
                }

                AsmrSoundType.VICTORY_FANFARE -> {
                    val notes = doubleArrayOf(523.25, 659.25, 783.99, 1046.50)
                    val step = (t / 0.13).toInt().coerceIn(0, notes.lastIndex)
                    val localT = t - step * 0.13
                    val freq = notes[step]
                    val env = exp(-localT * 7.0)
                    (sin(twoPi * freq * t) * 0.55 + sin(twoPi * freq * 1.5 * t) * 0.20) * env
                }

                AsmrSoundType.UI_TAP -> {
                    val env = exp(-t * 48.0)
                    sin(twoPi * 880.0 * t) * 0.45 * env
                }
            }

            val clamped = (sample * 0.72).coerceIn(-1.0, 1.0)
            pcm[i] = (clamped * 32767.0).toInt().toShort()
        }

        return encodePcm16Wav(pcm, sampleRate)
    }

    private fun encodePcm16Wav(pcm: ShortArray, sampleRate: Int): ByteArray {
        val dataBytes = pcm.size * 2
        val totalSize = 44 + dataBytes
        val out = ByteArray(totalSize)

        fun writeAscii(offset: Int, text: String) {
            for (i in text.indices) {
                out[offset + i] = text[i].code.toByte()
            }
        }

        fun writeIntLE(offset: Int, value: Int) {
            out[offset] = (value and 0xFF).toByte()
            out[offset + 1] = ((value ushr 8) and 0xFF).toByte()
            out[offset + 2] = ((value ushr 16) and 0xFF).toByte()
            out[offset + 3] = ((value ushr 24) and 0xFF).toByte()
        }

        fun writeShortLE(offset: Int, value: Int) {
            out[offset] = (value and 0xFF).toByte()
            out[offset + 1] = ((value ushr 8) and 0xFF).toByte()
        }

        writeAscii(0, "RIFF")
        writeIntLE(4, 36 + dataBytes)
        writeAscii(8, "WAVE")
        writeAscii(12, "fmt ")
        writeIntLE(16, 16) // PCM subchunk size
        writeShortLE(20, 1) // AudioFormat = PCM
        writeShortLE(22, 1) // NumChannels = Mono
        writeIntLE(24, sampleRate)
        writeIntLE(28, sampleRate * 2) // ByteRate
        writeShortLE(32, 2) // BlockAlign
        writeShortLE(34, 16) // BitsPerSample
        writeAscii(36, "data")
        writeIntLE(40, dataBytes)

        var pos = 44
        for (sample in pcm) {
            val s = sample.toInt()
            out[pos++] = (s and 0xFF).toByte()
            out[pos++] = ((s ushr 8) and 0xFF).toByte()
        }
        return out
    }
}
