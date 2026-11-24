package com.example.audiodecodetestapp.audio

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

/**
 * Media3（ExoPlayer）实现：Jetpack 官方媒体库，默认走系统 MediaCodec 解码。
 */
class Media3EnginePlayer(private val context: Context) : EnginePlayer {

    private var player: ExoPlayer? = null

    override fun play(uri: Uri, wav: WavInfo) {
        stop()
        player = ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(uri))
            prepare()
            play()
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
