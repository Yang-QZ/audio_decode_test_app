package com.example.audiodecodetestapp.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.net.Uri
import java.io.File

/**
 * SoundPool 实现：系统音效接口，内部会解码短音频，适合短提示音。
 *
 * 注意：
 * - SoundPool 不支持直接吃 SAF 的 Uri，这里先把文件复制到应用缓存目录再按路径加载；
 * - SoundPool 对单条音频有大小限制（约 1 MB），长 WAV 会加载失败，提前拦截并提示。
 */
class SoundPoolEnginePlayer(private val context: Context) : EnginePlayer {

    private var soundPool: SoundPool? = null
    private var tempFile: File? = null
    private var soundId = 0

    override fun play(uri: Uri, wav: WavInfo) {
        stop()
        if (wav.dataSize > MAX_BYTES) {
            throw IllegalArgumentException(
                "SoundPool 仅适合短音频，当前 PCM 数据 ${wav.dataSize / 1024} KB 超过约 1 MB 限制"
            )
        }

        val cacheFile = File(context.cacheDir, "soundpool_${System.nanoTime()}.wav")
        context.contentResolver.openInputStream(uri)?.use { input ->
            cacheFile.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IllegalStateException("无法读取音频文件")

        val pool = SoundPool.Builder()
            .setMaxStreams(1)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .build()
        soundPool = pool
        tempFile = cacheFile
        soundId = pool.load(cacheFile.absolutePath, 1)
        pool.setOnLoadCompleteListener { sp, id, status ->
            if (status == 0) {
                sp.play(id, 1f, 1f, 1, 0, 1f)
            }
        }
    }

    override fun stop() {
        soundPool?.let {
            try {
                it.autoPause()
                it.release()
            } catch (_: Exception) {
            }
        }
        soundPool = null
        soundId = 0
        tempFile?.delete()
        tempFile = null
    }

    override fun release() = stop()

    private companion object {
        const val MAX_BYTES = 1_048_576L
    }
}
