package com.example.audiodecodetestapp.model

/**
 * Android 16 上主流的音频播放接口。
 *
 * 只列出"默认配置下确实能用"的接口；某种编码格式若无法用某接口播放，
 * 就不会出现在该格式的 [CodecFormat.engines] 列表中。
 */
enum class PlaybackEngine(
    val title: String,
    val subtitle: String
) {
    AUDIO_TRACK(
        "AudioTrack",
        "最原始的 PCM 播放接口，只能直接播放原始 PCM 数据"
    ),
    MEDIA_PLAYER(
        "MediaPlayer",
        "系统内置播放器，支持平台解码的主流格式"
    ),
    MEDIA3(
        "Media3 (ExoPlayer)",
        "Jetpack 官方媒体库，基于系统媒体解码器"
    ),
    SOUND_POOL(
        "SoundPool",
        "系统音效接口，适合短音频，有大小与时长限制"
    ),
    AAUDIO(
        "AAudio",
        "低延迟 PCM 接口（Android 8.0+，NDK 实现）"
    )
}
