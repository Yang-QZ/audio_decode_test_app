package com.example.audiodecodetestapp.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri

/**
 * MediaPlayer 实现：最常用的系统播放器，内部走平台解码器。
 */
class MediaPlayerEnginePlayer(private val context: Context) : EnginePlayer {

    private var player: MediaPlayer? = null

    override fun play(uri: Uri, wav: WavInfo) {
        stop()
        player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            setDataSource(context, uri)
            prepare()
            start()
        }
    }

    override fun stop() {
        player?.let {
            try {
                it.stop()
                it.release()
            } catch (_: Exception) {
            }
        }
        player = null
    }

    override fun release() = stop()
}
