package com.example.audiocodecdebugger;

import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;

import java.util.Locale;

public final class CodecEnumerator {
    private CodecEnumerator() {}

    public static String enumerateAac() {
        StringBuilder out = new StringBuilder();
        MediaCodecInfo[] codecs = new MediaCodecList(MediaCodecList.ALL_CODECS).getCodecInfos();
        for (MediaCodecInfo info : codecs) {
            for (String type : info.getSupportedTypes()) {
                if (!MediaFormat.MIMETYPE_AUDIO_AAC.equalsIgnoreCase(type)) continue;
                String direction = info.isEncoder() ? "encoder" : "decoder";
                out.append(String.format(Locale.US,
                        "%s\n  name=%s\n  type=%s\n  softwareOnly=%s hardwareAccelerated=%s vendor=%s alias=%s\n",
                        direction, info.getName(), type, info.isSoftwareOnly(),
                        info.isHardwareAccelerated(), info.isVendor(), info.isAlias()));
                try {
                    MediaCodecInfo.CodecCapabilities capabilities = info.getCapabilitiesForType(type);
                    MediaCodecInfo.AudioCapabilities audio = capabilities.getAudioCapabilities();
                    out.append(String.format(Locale.US,
                            "  sampleRates=%s channels=%d..%d bitrate=%d..%d\n",
                            java.util.Arrays.toString(audio.getSupportedSampleRates()),
                            audio.getMinInputChannelCount(), audio.getMaxInputChannelCount(),
                            audio.getBitrateRange().getLower(), audio.getBitrateRange().getUpper()));
                } catch (RuntimeException e) {
                    out.append("  capabilities=<unavailable: ").append(e.getMessage()).append(">\n");
                }
            }
        }
        if (out.length() == 0) out.append("No AAC codec reported by MediaCodecList.\n");
        return out.toString();
    }
}
