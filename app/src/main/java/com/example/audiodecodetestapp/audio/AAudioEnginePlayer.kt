package com.example.audiodecodetestapp.audio

import android.content.Context
import android.net.Uri
import java.io.ByteArrayOutputStream

/**
 * AAudio 实现：Android 8.0+ 的低延迟 PCM 接口，通过 NDK（C++）创建 AAudio 流并写入 PCM。
 *
 * 示例只处理 16 位 PCM；8 位/24 位在进入前会被拦截并提示。
 */
class AAudioEnginePlayer(private val context: Context) : EnginePlayer {

    private var handle = 0L

    override fun play(uri: Uri, wav: WavInfo) {
        stop()
        if (wav.bitsPerSample != 16) {
            throw IllegalArgumentException(
                "AAudio 示例仅支持 16 位 PCM，当前 ${wav.bitsPerSample} 位"
            )
        }
        val pcm = readPcm(uri, wav)
        handle = nativeCreate(wav.sampleRate, wav.channels)
        if (handle == 0L) {
            throw IllegalStateException("AAudio 流创建失败，检查设备是否支持低延迟音频输出")
        }
        nativeStart(handle, pcm)
    }

    override fun stop() {
        if (handle != 0L) {
            nativeStop(handle)
            nativeDestroy(handle)
            handle = 0L
        }
    }

    override fun release() = stop()

    private fun readPcm(uri: Uri, wav: WavInfo): ByteArray {
        context.contentResolver.openInputStream(uri)?.use { input ->
            WavParser.skipFully(input, wav.dataOffset)
            val out = ByteArrayOutputStream(wav.dataSize.toInt().coerceAtLeast(0))
            val buffer = ByteArray(8192)
            var remaining = wav.dataSize
            while (remaining > 0) {
                val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                val read = input.read(buffer, 0, toRead)
                if (read <= 0) break
                out.write(buffer, 0, read)
                remaining -= read
            }
            return out.toByteArray()
        }
        return ByteArray(0)
    }

    private companion object {
        init {
            System.loadLibrary("aaudioplayer")
        }

        @JvmStatic
        private external fun nativeCreate(sampleRate: Int, channels: Int): Long

        @JvmStatic
        private external fun nativeStart(handle: Long, pcm: ByteArray)

        @JvmStatic
        private external fun nativeStop(handle: Long)

        @JvmStatic
        private external fun nativeDestroy(handle: Long)
    }
}
