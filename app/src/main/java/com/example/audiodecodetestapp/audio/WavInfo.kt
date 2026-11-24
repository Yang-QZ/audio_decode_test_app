package com.example.audiodecodetestapp.audio

/**
 * WAV（RIFF/WAVE）关键格式信息。
 *
 * @param audioFormat 1 = PCM，3 = IEEE Float
 */
data class WavInfo(
    val audioFormat: Int,
    val channels: Int,
    val sampleRate: Int,
    val bitsPerSample: Int,
    val dataOffset: Long,
    val dataSize: Long
) {
    val durationMs: Long
        get() {
            val bytesPerSecond = sampleRate.toLong() * channels * (bitsPerSample / 8)
            return if (bytesPerSecond > 0) dataSize * 1000L / bytesPerSecond else 0L
        }

    val formatLabel: String
        get() = when (audioFormat) {
            1 -> "PCM"
            3 -> "IEEE Float"
            else -> "0x%02X".format(audioFormat)
        }

    val channelLabel: String
        get() = when (channels) {
            1 -> "单声道"
            2 -> "立体声"
            else -> "${channels} 声道"
        }
}
