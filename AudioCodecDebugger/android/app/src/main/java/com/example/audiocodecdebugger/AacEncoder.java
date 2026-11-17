package com.example.audiocodecdebugger;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;

import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Map;

/** Raw little-endian signed 16-bit interleaved PCM to AAC in an M4A/MP4 container. */
public final class AacEncoder {
    public static final class Result {
        public long inputBytes;
        public long inputFrames;
        public int outputPackets;
        public long outputBytes;
        public File outputFile;
        public File traceFile;
        @Override public String toString() {
            return String.format(java.util.Locale.US,
                    "AAC encode complete: pcmBytes=%d pcmFrames=%d packets=%d aacBytes=%d\nM4A=%s\nTrace=%s",
                    inputBytes, inputFrames, outputPackets, outputBytes, outputFile, traceFile);
        }
    }

    public static Result encode(File pcm, File m4a, File trace, int sampleRate,
                                int channels, int bitRate) throws IOException {
        if (sampleRate <= 0 || channels <= 0 || channels > 8 || bitRate <= 0) {
            throw new IllegalArgumentException("Invalid PCM rate/channel/bitrate");
        }
        final int frameBytes = channels * 2;
        Result result = new Result();
        result.outputFile = m4a;
        result.traceFile = trace;
        MediaCodec codec = null;
        MediaMuxer muxer = null;
        boolean codecStarted = false, muxerStarted = false;
        try (InputStream input = new FileInputStream(pcm); TraceLogger logger = new TraceLogger(trace, "default-aac-encoder")) {
            MediaFormat format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channels);
            format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
            format.setInteger(MediaFormat.KEY_BIT_RATE, bitRate);
            format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 16384);
            codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            codec.start();
            codecStarted = true;
            muxer = new MediaMuxer(m4a.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            int trackIndex = -1;
            boolean inputEos = false, outputEos = false;
            long lastProgressNs = System.nanoTime();
            while (!outputEos) {
                if (!inputEos) {
                    int index = codec.dequeueInputBuffer(10_000);
                    if (index >= 0) {
                        ByteBuffer buffer = codec.getInputBuffer(index);
                        if (buffer == null) throw new IOException("Null encoder input");
                        buffer.clear();
                        int capacity = buffer.remaining() - buffer.remaining() % frameBytes;
                        if (capacity == 0) throw new IOException("Encoder input smaller than one PCM frame");
                        byte[] bytes = new byte[capacity];
                        int count = readChunk(input, bytes);
                        if (count < 0) {
                            codec.queueInputBuffer(index, 0, 0,
                                    result.inputFrames * 1_000_000L / sampleRate,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            logger.event("input_eos");
                            inputEos = true;
                        } else {
                            if (count % frameBytes != 0) throw new EOFException("PCM ends with a partial interleaved frame");
                            buffer.put(bytes, 0, count);
                            long ptsUs = result.inputFrames * 1_000_000L / sampleRate;
                            codec.queueInputBuffer(index, 0, count, ptsUs, 0);
                            Map<String, Object> fields = TraceLogger.fields();
                            fields.put("frameIndex", result.inputFrames);
                            fields.put("ptsUs", ptsUs);
                            fields.put("inputBytes", count);
                            fields.put("queueTimeNs", System.nanoTime());
                            logger.event("input", fields);
                            result.inputBytes += count;
                            result.inputFrames += count / frameBytes;
                        }
                        lastProgressNs = System.nanoTime();
                    }
                }

                long startNs = System.nanoTime();
                int index = codec.dequeueOutputBuffer(info, 10_000);
                long waitNs = System.nanoTime() - startNs;
                if (index >= 0) {
                    try {
                        ByteBuffer buffer = codec.getOutputBuffer(index);
                        if (buffer == null) throw new IOException("Null encoder output");
                        if (!muxerStarted) throw new IOException("Encoded output arrived before output format change");
                        if (info.size > 0 && (info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                            muxer.writeSampleData(trackIndex, buffer, info);
                            result.outputPackets++;
                            result.outputBytes += info.size;
                        }
                        Map<String, Object> fields = TraceLogger.fields();
                        fields.put("outputIndex", result.outputPackets);
                        fields.put("ptsUs", info.presentationTimeUs);
                        fields.put("outputBytes", info.size);
                        fields.put("flags", info.flags);
                        fields.put("outputWaitNs", waitNs);
                        logger.event("output", fields);
                        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputEos = true;
                    } finally {
                        codec.releaseOutputBuffer(index, false);
                    }
                    lastProgressNs = System.nanoTime();
                } else if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    if (muxerStarted) throw new IOException("Encoder output format changed twice");
                    MediaFormat outputFormat = codec.getOutputFormat();
                    trackIndex = muxer.addTrack(outputFormat);
                    muxer.start();
                    muxerStarted = true;
                    Map<String, Object> fields = TraceLogger.fields();
                    fields.put("format", outputFormat.toString());
                    logger.event("format_changed", fields);
                    lastProgressNs = System.nanoTime();
                } else if (index == MediaCodec.INFO_TRY_AGAIN_LATER &&
                        System.nanoTime() - lastProgressNs > 120_000_000_000L) {
                    throw new IOException("Encoder stalled for 120 s");
                }
            }
            logger.event("finished");
            return result;
        } finally {
            if (codec != null) {
                if (codecStarted) {
                    try { codec.stop(); } catch (RuntimeException ignored) {}
                }
                codec.release();
            }
            if (muxer != null) {
                if (muxerStarted) {
                    try { muxer.stop(); } catch (RuntimeException ignored) {}
                }
                muxer.release();
            }
        }
    }

    /** Fill a frame-aligned chunk unless EOF; avoids splitting interleaved PCM across reads. */
    private static int readChunk(InputStream input, byte[] bytes) throws IOException {
        int total = 0;
        while (total < bytes.length) {
            int read = input.read(bytes, total, bytes.length - total);
            if (read < 0) return total == 0 ? -1 : total;
            total += read;
        }
        return total;
    }
}
