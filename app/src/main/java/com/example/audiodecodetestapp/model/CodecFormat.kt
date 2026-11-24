package com.example.audiodecodetestapp.model

/**
 * 音频编码格式的数据模型。
 *
 * [engines] 表示"默认配置下该格式可以使用的播放接口"：
 * - AudioTrack / AAudio 只能直接播放 PCM，所以只有 WAV/PCM 可用；
 * - MediaPlayer / Media3 依赖系统解码器，覆盖主流压缩格式；
 * - SoundPool 只适合短音频（WAV/MP3/OGG 短音效）；
 * - AC-3 / E-AC-3 默认无可用接口（需要 Media3 FFmpeg 扩展或设备自带解码器）。
 */
data class CodecFormat(
    val name: String,
    val container: String,
    val mime: String,
    val encoder: Boolean,
    val description: String,
    val engines: List<PlaybackEngine>
) {
    /** 是否有可用播放接口 */
    val playable: Boolean
        get() = engines.isNotEmpty()

    companion object {
        val SAMPLE_FORMATS: List<CodecFormat> = listOf(
            CodecFormat(
                "AAC-LC", "M4A / ADTS", "audio/mp4a-latm", true,
                "最常用的有损格式，软硬解码均支持",
                listOf(PlaybackEngine.MEDIA_PLAYER, PlaybackEngine.MEDIA3)
            ),
            CodecFormat(
                "MP3", "MP3", "audio/mpeg", false,
                "经典有损格式，解码覆盖广泛",
                listOf(PlaybackEngine.MEDIA_PLAYER, PlaybackEngine.MEDIA3, PlaybackEngine.SOUND_POOL)
            ),
            CodecFormat(
                "FLAC", "FLAC", "audio/flac", true,
                "无损格式，API 21+ 支持",
                listOf(PlaybackEngine.MEDIA_PLAYER, PlaybackEngine.MEDIA3)
            ),
            CodecFormat(
                "Opus", "Ogg / WebM", "audio/opus", true,
                "低延迟语音/音乐，API 21+ 支持",
                listOf(PlaybackEngine.MEDIA_PLAYER, PlaybackEngine.MEDIA3)
            ),
            CodecFormat(
                "Vorbis", "Ogg", "audio/vorbis", false,
                "Ogg 容器常用音频格式",
                listOf(PlaybackEngine.MEDIA_PLAYER, PlaybackEngine.MEDIA3, PlaybackEngine.SOUND_POOL)
            ),
            CodecFormat(
                "ALAC", "M4A", "audio/alac", false,
                "Apple 无损编码，依赖系统解码器",
                listOf(PlaybackEngine.MEDIA_PLAYER, PlaybackEngine.MEDIA3)
            ),
            CodecFormat(
                "AC-3", "MP4 / MKV", "audio/ac3", false,
                "杜比数字，默认配置下无可用接口（需 Media3 FFmpeg 扩展）",
                emptyList()
            ),
            CodecFormat(
                "E-AC-3", "MP4 / MKV", "audio/eac3", false,
                "杜比数字+（DD+），默认配置下无可用接口（需 Media3 FFmpeg 扩展）",
                emptyList()
            ),
            CodecFormat(
                "WAV / PCM", "WAV", "audio/raw", true,
                "未压缩 PCM 数据，五种接口均可直接播放",
                listOf(
                    PlaybackEngine.AUDIO_TRACK,
                    PlaybackEngine.MEDIA_PLAYER,
                    PlaybackEngine.MEDIA3,
                    PlaybackEngine.SOUND_POOL,
                    PlaybackEngine.AAUDIO
                )
            ),
            CodecFormat(
                "AMR", "3GP", "audio/3gpp", true,
                "语音编码，窄带/宽带",
                listOf(PlaybackEngine.MEDIA_PLAYER, PlaybackEngine.MEDIA3)
            )
        )

        fun findByName(name: String): CodecFormat? =
            SAMPLE_FORMATS.firstOrNull { it.name == name }
    }
}
