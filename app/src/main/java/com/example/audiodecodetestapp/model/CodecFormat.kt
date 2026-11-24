package com.example.audiodecodetestapp.model

/**
 * 音频编码格式的占位数据模型。
 *
 * 现阶段仅用于界面框架展示；后续接入 MediaExtractor/MediaCodec 后，
 * 会替换为对设备真实编解码能力的枚举结果。
 */
data class CodecFormat(
    val name: String,
    val container: String,
    val mime: String,
    val encoder: Boolean,
    val description: String
) {
    companion object {
        val SAMPLE_FORMATS: List<CodecFormat> = listOf(
            CodecFormat("AAC-LC", "M4A / ADTS", "audio/mp4a-latm", true, "最常用的有损格式，软硬解码均支持"),
            CodecFormat("MP3", "MP3", "audio/mpeg", false, "经典有损格式，解码覆盖广泛"),
            CodecFormat("FLAC", "FLAC", "audio/flac", true, "无损格式，API 21+ 支持"),
            CodecFormat("Opus", "Ogg / WebM", "audio/opus", true, "低延迟语音/音乐，API 21+ 支持"),
            CodecFormat("Vorbis", "Ogg", "audio/vorbis", false, "Ogg 容器常用音频格式"),
            CodecFormat("ALAC", "M4A", "audio/alac", false, "Apple 无损编码"),
            CodecFormat("AC-3", "MP4 / MKV", "audio/ac3", false, "杜比数字，部分设备支持"),
            CodecFormat("E-AC-3", "MP4 / MKV", "audio/eac3", false, "杜比数字+（DD+）"),
            CodecFormat("WAV / PCM", "WAV", "audio/raw", true, "未压缩 PCM 数据"),
            CodecFormat("AMR", "3GP", "audio/3gpp", true, "语音编码，窄带/宽带")
        )
    }
}
