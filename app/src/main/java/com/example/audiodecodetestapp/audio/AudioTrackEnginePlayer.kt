package com.example.audiodecodetestapp.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.net.Uri

/**
 * AudioTrack 实现：解析 WAV 头后，把原始 PCM 数据流式写入 AudioTrack。
 * 这是最底层的播放方式，只能直接播放 PCM。
 */
class AudioTrackEnginePlayer(private val context: Context) : EnginePlayer {

    private var track: AudioTrack? = null
    private var worker: Thread? = null

    @Volatile
    private var running = false

    override fun play(uri: Uri, wav: WavInfo) {
        stop()
        if (wav.bitsPerSample != 8 && wav.bitsPerSample != 16) {
            throw IllegalArgumentException(
                "AudioTrack 示例仅支持 8/16 位 PCM，当前 ${wav.bitsPerSample} 位"
            )
        }
        val channelMask = when (wav.channels) {
            1 -> AudioFormat.CHANNEL_OUT_MONO
            2 -> AudioFormat.CHANNEL_OUT_STEREO
            else -> throw IllegalArgumentException(
                "AudioTrack 示例仅支持单/双声道，当前 ${wav.channels} 声道"
            )
        }
        val encoding = if (wav.bitsPerSample == 8) {
            AudioFormat.ENCODING_PCM_8BIT
        } else {
            AudioFormat.ENCODING_PCM_16BIT
        }
        val minBuffer = AudioTrack.getMinBufferSize(wav.sampleRate, channelMask, encoding)
        if (minBuffer <= 0) {
            throw IllegalStateException("AudioTrack 不支持当前采样率/声道组合")
        }

        val audioTrack = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(encoding)
                    .setSampleRate(wav.sampleRate)
                    .setChannelMask(channelMask)
                    .build()
            )
            .setBufferSizeInBytes(minBuffer * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track = audioTrack
        running = true
        audioTrack.play()

        val writer = Thread({
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    WavParser.skipFully(input, wav.dataOffset)
                    val buffer = ByteArray(8192)
                    var remaining = wav.dataSize
                    while (running && remaining > 0) {
                        val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                        val read = input.read(buffer, 0, toRead)
                        if (read <= 0) break
                        audioTrack.write(buffer, 0, read)
                        remaining -= read
                    }
                }
            } catch (_: Exception) {
            }
        }, "AudioTrack-Writer")
        worker = writer
        writer.start()
    }

    override fun stop() {
        running = false
        worker?.join(500)
        worker = null
        track?.let { t ->
            try {
                t.pause()
                t.flush()
                t.stop()
            } catch (_: Exception) {
            }
            t.release()
        }
        track = null
    }

    override fun release() = stop()
}
