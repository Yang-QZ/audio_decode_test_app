package com.example.audiodecodetestapp.audio

import android.net.Uri

/**
 * 统一的播放接口抽象。五种实现（AudioTrack / MediaPlayer / Media3 / SoundPool / AAudio）
 * 都实现这个接口，便于在播放页里按 [com.example.audiodecodetestapp.model.PlaybackEngine] 切换。
 */
interface EnginePlayer {
    /** 开始播放；参数不支持时抛异常，由调用方提示。 */
    fun play(uri: Uri, wav: WavInfo)

    /** 停止并释放本次播放占用的资源。 */
    fun stop()

    /** 释放；默认等同于 stop。 */
    fun release() = stop()
}
