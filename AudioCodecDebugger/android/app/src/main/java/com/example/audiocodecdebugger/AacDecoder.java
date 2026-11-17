package com.example.audiocodecdebugger;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;

import java.io.Closeable;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class AacDecoder {
    private static final String TAG = "AudioCodecDebugger";
    private static final long TIMEOUT_US = 10_000;
    private static final long STALL_LIMIT_NS = 120_000_000_000L;
    private static final int[] RATES = {96000, 88200, 64000, 48000, 44100, 32000,
            24000, 22050, 16000, 12000, 11025, 8000, 7350};

    public static final class Result {
        public File pcmFile;
        public File traceFile;
        public int inputCount;
        public int outputCount;
        public int outputReleasedCount;
        public long outputBytes;
        public int sampleRate;
        public int channelCount;
        public int flushCount;
        public final List<Long> outputWaitNs = new ArrayList<>();

        @Override public String toString() {
            return String.format(java.util.Locale.US,
                    "AAC decode complete: inputs=%d outputs=%d released=%d bytes=%d format=%d Hz/%d ch flushes=%d\nPCM=%s\nTrace=%s",
                    inputCount, outputCount, outputReleasedCount, outputBytes,
                    sampleRate, channelCount, flushCount, pcmFile, traceFile);
        }
    }

    private interface SampleSource extends Closeable {
        MediaFormat format();
        boolean next(Sample sample) throws IOException;
        void write(ByteBuffer destination, Sample sample) throws IOException;
        void onQueued();
    }

    private static final class Sample {
        byte[] data;
        int size;
        long ptsUs;
    }

    private static final class ExtractorSource implements SampleSource {
        private final MediaExtractor extractor = new MediaExtractor();
        private final MediaFormat format;
        ExtractorSource(File file) throws IOException {
            extractor.setDataSource(file.getAbsolutePath());
            int selected = -1;
            MediaFormat selectedFormat = null;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat candidate = extractor.getTrackFormat(i);
                String mime = candidate.getString(MediaFormat.KEY_MIME);
                if (MediaFormat.MIMETYPE_AUDIO_AAC.equalsIgnoreCase(mime)) {
                    selected = i;
                    selectedFormat = candidate;
                    break;
                }
            }
            if (selected < 0) throw new IOException("No audio/mp4a-latm track in " + file);
            format = selectedFormat;
            extractor.selectTrack(selected);
        }
        @Override public MediaFormat format() { return format; }
        @Override public boolean next(Sample sample) {
            long size = extractor.getSampleSize();
            if (size < 0) return false;
            if (size > Integer.MAX_VALUE) throw new IllegalStateException("Sample too large: " + size);
            sample.size = (int) size;
            sample.ptsUs = extractor.getSampleTime();
            sample.data = null;
            return true;
        }
        @Override public void write(ByteBuffer destination, Sample sample) throws IOException {
            int read = extractor.readSampleData(destination, 0);
            if (read != sample.size) throw new IOException("Extractor size changed: " + read + " != " + sample.size);
        }
        @Override public void onQueued() { extractor.advance(); }
        @Override public void close() { extractor.release(); }
    }

    private static final class AdtsSource implements SampleSource {
        private final PushbackInputStream input;
        private Sample pending;
        private MediaFormat format;
        private long nextPtsUs;
        AdtsSource(File file) throws IOException {
            input = new PushbackInputStream(new FileInputStream(file), 10);
            skipId3();
            pending = readFrame();
            if (pending == null) throw new IOException("Empty ADTS file");
        }
        private void skipId3() throws IOException {
            byte[] first = new byte[10];
            int count = readUpTo(input, first, 0, 10);
            if (count == 10 && first[0] == 'I' && first[1] == 'D' && first[2] == '3') {
                if ((first[6] & 0x80) != 0 || (first[7] & 0x80) != 0 ||
                        (first[8] & 0x80) != 0 || (first[9] & 0x80) != 0) {
                    throw new IOException("Invalid ID3 synchsafe size");
                }
                long size = ((long) first[6] << 21) | ((long) first[7] << 14) |
                        ((long) first[8] << 7) | (first[9] & 0xffL);
                if ((first[5] & 0x10) != 0) size += 10;
                skipFully(input, size);
            } else if (count > 0) {
                input.unread(first, 0, count);
            }
        }
        private Sample readFrame() throws IOException {
            byte[] header = new byte[7];
            int first = input.read();
            if (first < 0) return null;
            header[0] = (byte) first;
            readFully(input, header, 1, 6);
            if ((header[0] & 0xff) != 0xff || (header[1] & 0xf6) != 0xf0) {
                throw new IOException("Invalid ADTS syncword/layer near PTS " + nextPtsUs);
            }
            int rateIndex = (header[2] >> 2) & 0xf;
            if (rateIndex >= RATES.length) throw new IOException("Reserved ADTS sample rate index");
            int channels = ((header[2] & 1) << 2) | ((header[3] & 0xff) >> 6);
            if (channels == 0) throw new IOException("ADTS channel_configuration=0/PCE is not supported by this test");
            int length = ((header[3] & 3) << 11) | ((header[4] & 0xff) << 3) | ((header[5] & 0xff) >> 5);
            int headerLength = (header[1] & 1) != 0 ? 7 : 9;
            if (length < headerLength) throw new IOException("Invalid ADTS frame length " + length);
            byte[] frame = new byte[length];
            System.arraycopy(header, 0, frame, 0, 7);
            readFully(input, frame, 7, length - 7);
            if (format == null) {
                format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, RATES[rateIndex], channels);
                format.setInteger(MediaFormat.KEY_AAC_PROFILE, (header[2] >> 6) + 1);
            }
            Sample sample = new Sample();
            sample.data = frame;
            sample.size = length;
            sample.ptsUs = nextPtsUs;
            int blocks = (header[6] & 3) + 1;
            nextPtsUs += Math.round(1_000_000.0 * 1024 * blocks / RATES[rateIndex]);
            return sample;
        }
        @Override public MediaFormat format() { return format; }
        @Override public boolean next(Sample sample) throws IOException {
            if (pending == null) pending = readFrame();
            if (pending == null) return false;
            sample.data = pending.data;
            sample.size = pending.size;
            sample.ptsUs = pending.ptsUs;
            pending = null;
            return true;
        }
        @Override public void write(ByteBuffer destination, Sample sample) {
            destination.put(sample.data, 0, sample.size);
        }
        @Override public void onQueued() {}
        @Override public void close() throws IOException { input.close(); }
    }

    public static Result decode(File input, File pcm, File trace, String codecName) throws IOException {
        return decode(input, pcm, trace, codecName, -1);
    }

    /** flushAfterInputs is for fault experiments only; -1 disables injection. */
    public static Result decode(File input, File pcm, File trace, String codecName,
                                int flushAfterInputs) throws IOException {
        boolean adts = input.getName().toLowerCase(java.util.Locale.US).endsWith(".aac");
        Result result = new Result();
        result.pcmFile = pcm;
        result.traceFile = trace;
        MediaCodec codec = null;
        boolean started = false;
        try (SampleSource source = adts ? new AdtsSource(input) : new ExtractorSource(input);
             FileOutputStream stream = new FileOutputStream(pcm);
             FileChannel channel = stream.getChannel();
             TraceLogger logger = new TraceLogger(trace, codecName == null ? "default" : codecName)) {
            MediaFormat configuredFormat = source.format();
            result.sampleRate = configuredFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            result.channelCount = configuredFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            codec = codecName == null || codecName.trim().isEmpty()
                    ? MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
                    : MediaCodec.createByCodecName(codecName.trim());
            codec.configure(configuredFormat, null, null, 0);
            codec.start();
            started = true;
            logger.event("started");
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputEos = false, outputEos = false;
            long lastProgressNs = System.nanoTime();
            while (!outputEos) {
                if (!inputEos) {
                    int index = codec.dequeueInputBuffer(TIMEOUT_US);
                    if (index >= 0) {
                        ByteBuffer buffer = codec.getInputBuffer(index);
                        if (buffer == null) throw new IOException("Null input buffer " + index);
                        buffer.clear();
                        Sample sample = new Sample();
                        if (source.next(sample)) {
                            if (sample.size > buffer.remaining()) {
                                throw new IOException("Input capacity " + buffer.remaining() + " < sample " + sample.size);
                            }
                            source.write(buffer, sample);
                            long queueNs = System.nanoTime();
                            codec.queueInputBuffer(index, 0, sample.size, sample.ptsUs, 0);
                            source.onQueued();
                            Map<String, Object> fields = TraceLogger.fields();
                            fields.put("frameIndex", result.inputCount);
                            fields.put("ptsUs", sample.ptsUs);
                            fields.put("inputBytes", sample.size);
                            fields.put("queueTimeNs", queueNs);
                            logger.event("input", fields);
                            result.inputCount++;
                            if (result.inputCount == flushAfterInputs) {
                                codec.flush();
                                result.flushCount++;
                                logger.event("flush");
                            }
                        } else {
                            codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            logger.event("input_eos");
                            inputEos = true;
                        }
                        lastProgressNs = System.nanoTime();
                    }
                }

                long dequeueStartNs = System.nanoTime();
                int index = codec.dequeueOutputBuffer(info, TIMEOUT_US);
                long outputWaitNs = System.nanoTime() - dequeueStartNs;
                if (index >= 0) {
                    try {
                        ByteBuffer buffer = codec.getOutputBuffer(index);
                        if (buffer == null) throw new IOException("Null output buffer " + index);
                        if (info.size > 0) {
                            buffer.position(info.offset);
                            buffer.limit(info.offset + info.size);
                            while (buffer.hasRemaining()) channel.write(buffer);
                        }
                        Map<String, Object> fields = TraceLogger.fields();
                        fields.put("outputIndex", result.outputCount);
                        fields.put("ptsUs", info.presentationTimeUs);
                        fields.put("outputBytes", info.size);
                        fields.put("flags", info.flags);
                        fields.put("outputWaitNs", outputWaitNs);
                        fields.put("dequeueStartNs", dequeueStartNs);
                        logger.event("output", fields);
                        result.outputCount++;
                        result.outputBytes += info.size;
                        result.outputWaitNs.add(outputWaitNs);
                        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputEos = true;
                    } finally {
                        codec.releaseOutputBuffer(index, false);
                        result.outputReleasedCount++;
                    }
                    lastProgressNs = System.nanoTime();
                } else if (index == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat format = codec.getOutputFormat();
                    result.sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    result.channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    if (format.containsKey(MediaFormat.KEY_PCM_ENCODING) &&
                            format.getInteger(MediaFormat.KEY_PCM_ENCODING) != AudioFormat.ENCODING_PCM_16BIT) {
                        throw new IOException("Expected signed 16-bit PCM, output format=" + format);
                    }
                    Map<String, Object> fields = TraceLogger.fields();
                    fields.put("sampleRate", result.sampleRate);
                    fields.put("channelCount", result.channelCount);
                    fields.put("format", format.toString());
                    logger.event("format_changed", fields);
                    lastProgressNs = System.nanoTime();
                } else if (index == MediaCodec.INFO_TRY_AGAIN_LATER &&
                        System.nanoTime() - lastProgressNs > STALL_LIMIT_NS) {
                    throw new IOException("Codec stalled for 120 s; inspect buffer ownership and trace");
                }
            }
            logger.event("finished");
            return result;
        } catch (MediaCodec.CodecException e) {
            Log.e(TAG, "Codec failure recoverable=" + e.isRecoverable() + " transient=" + e.isTransient(), e);
            throw new IOException("MediaCodec failure: " + e.getDiagnosticInfo(), e);
        } finally {
            if (codec != null) {
                if (started) {
                    try { codec.stop(); } catch (RuntimeException e) { Log.w(TAG, "stop after failure", e); }
                }
                codec.release();
            }
        }
    }

    private static int readUpTo(InputStream in, byte[] data, int offset, int count) throws IOException {
        int total = 0;
        while (total < count) {
            int read = in.read(data, offset + total, count - total);
            if (read < 0) break;
            total += read;
        }
        return total;
    }

    private static void readFully(InputStream in, byte[] data, int offset, int count) throws IOException {
        if (readUpTo(in, data, offset, count) != count) throw new EOFException("Truncated AAC input");
    }

    private static void skipFully(InputStream in, long count) throws IOException {
        byte[] scratch = new byte[4096];
        while (count > 0) {
            int read = in.read(scratch, 0, (int) Math.min(scratch.length, count));
            if (read < 0) throw new EOFException("Truncated ID3 tag");
            count -= read;
        }
    }
}
