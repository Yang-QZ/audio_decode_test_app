package com.example.audiocodecdebugger;

import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/** Destructive inputs are copies under output/fault; source media is never modified. */
public final class CodecFaultTest {
    private CodecFaultTest() {}

    private interface CheckedRunnable { void run() throws Exception; }

    public static String runAll(File input, File outputDir, String codecName) {
        if (!outputDir.exists()) outputDir.mkdirs();
        StringBuilder report = new StringBuilder();
        runCase(report, "flush_after_20_inputs", () -> {
            AacDecoder.Result result = AacDecoder.decode(input, new File(outputDir, "flush.pcm"),
                    new File(outputDir, "flush.jsonl"), codecName, 20);
            if (result.flushCount != 1) throw new IOException("Flush was not injected");
            report.append("  ").append(result).append('\n');
        });
        runCase(report, "truncated_copy", () -> {
            File copy = new File(outputDir, "truncated_" + input.getName());
            byte[] data = readAll(input);
            int length = Math.max(1, data.length * 3 / 5);
            try (FileOutputStream out = new FileOutputStream(copy)) { out.write(data, 0, length); }
            attemptDecode(copy, outputDir, codecName, "truncated");
        });
        runCase(report, "corrupted_copy", () -> {
            File copy = new File(outputDir, "corrupted_" + input.getName());
            byte[] data = readAll(input);
            for (int i = data.length / 2; i < data.length; i += Math.max(1, data.length / 97)) data[i] ^= 0x55;
            try (FileOutputStream out = new FileOutputStream(copy)) { out.write(data); }
            attemptDecode(copy, outputDir, codecName, "corrupted");
        });
        runCase(report, "start_before_configure", () -> {
            MediaCodec codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
            try {
                codec.start();
                throw new IOException("Unexpectedly accepted start before configure");
            } catch (IllegalStateException expected) {
                report.append("  expected IllegalStateException: ").append(expected.getMessage()).append('\n');
            } finally { codec.release(); }
        });
        runCase(report, "configured_start_stop_without_input", () -> {
            MediaCodec codec = MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
            boolean started = false;
            try {
                MediaFormat format = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, 48000, 2);
                codec.configure(format, null, null, 0);
                codec.start();
                started = true;
                codec.stop();
                started = false;
                report.append("  stop completed without input\n");
            } finally {
                if (started) codec.stop();
                codec.release();
            }
        });
        if (!input.getName().toLowerCase(java.util.Locale.US).endsWith(".aac")) {
            runCase(report, "hold_four_output_buffers", () ->
                    report.append(holdOutputBuffers(input, new File(outputDir, "hold_buffers.jsonl"), codecName)));
        } else {
            report.append("hold_four_output_buffers: skipped for ADTS; use M4A input\n");
        }
        return report.toString();
    }

    private static void attemptDecode(File input, File outputDir, String codecName, String label) throws Exception {
        try {
            AacDecoder.Result result = AacDecoder.decode(input, new File(outputDir, label + ".pcm"),
                    new File(outputDir, label + ".jsonl"), codecName);
            android.util.Log.i("AudioCodecDebugger", label + " accepted: " + result);
        } catch (RuntimeException | IOException expected) {
            android.util.Log.i("AudioCodecDebugger", label + " rejected", expected);
        }
    }

    private static String holdOutputBuffers(File input, File trace, String codecName) throws Exception {
        MediaExtractor extractor = new MediaExtractor();
        MediaCodec codec = null;
        boolean started = false;
        List<Integer> held = new ArrayList<>();
        int stalls = 0, immediateOutputs = 0;
        try (TraceLogger logger = new TraceLogger(trace, codecName == null ? "default" : codecName)) {
            extractor.setDataSource(input.getAbsolutePath());
            int track = -1;
            MediaFormat format = null;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat candidate = extractor.getTrackFormat(i);
                if (MediaFormat.MIMETYPE_AUDIO_AAC.equalsIgnoreCase(candidate.getString(MediaFormat.KEY_MIME))) {
                    track = i;
                    format = candidate;
                    break;
                }
            }
            if (track < 0) throw new IOException("No AAC track");
            extractor.selectTrack(track);
            codec = codecName == null || codecName.trim().isEmpty()
                    ? MediaCodec.createDecoderByType(MediaFormat.MIMETYPE_AUDIO_AAC)
                    : MediaCodec.createByCodecName(codecName.trim());
            codec.configure(format, null, null, 0);
            codec.start();
            started = true;
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputEos = false, outputEos = false;
            long loopStartNs = System.nanoTime();
            while (!outputEos) {
                if (!inputEos) {
                    int index = codec.dequeueInputBuffer(0);
                    if (index >= 0) {
                        ByteBuffer buffer = codec.getInputBuffer(index);
                        buffer.clear();
                        int size = extractor.readSampleData(buffer, 0);
                        if (size < 0) {
                            codec.queueInputBuffer(index, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputEos = true;
                        } else {
                            long pts = extractor.getSampleTime();
                            codec.queueInputBuffer(index, 0, size, pts, 0);
                            extractor.advance();
                        }
                    }
                }
                int index = codec.dequeueOutputBuffer(info, 20_000);
                if (index >= 0) {
                    if (held.size() < 4) {
                        held.add(index);
                        logger.event("output_held");
                    } else {
                        codec.releaseOutputBuffer(index, false);
                        immediateOutputs++;
                    }
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputEos = true;
                } else if (index == MediaCodec.INFO_TRY_AGAIN_LATER && held.size() == 4) {
                    stalls++;
                    if (stalls == 3) {
                        for (int heldIndex : held) codec.releaseOutputBuffer(heldIndex, false);
                        held.clear();
                        logger.event("held_outputs_released");
                    }
                }
                if (System.nanoTime() - loopStartNs > 120_000_000_000L) {
                    throw new IOException("Hold-output test timed out; buffers are released in finally");
                }
            }
            return "hold test: backpressureStalls=" + stalls + " immediateOutputs=" + immediateOutputs +
                    "; stalls are observations, not a universal pool-size assertion\n";
        } finally {
            if (codec != null && started) {
                for (int index : held) {
                    try { codec.releaseOutputBuffer(index, false); } catch (RuntimeException ignored) {}
                }
            }
            if (codec != null) {
                if (started) { try { codec.stop(); } catch (RuntimeException ignored) {} }
                codec.release();
            }
            extractor.release();
        }
    }

    private static byte[] readAll(File file) throws IOException {
        try (FileInputStream input = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[16384];
            int count;
            while ((count = input.read(buffer)) >= 0) output.write(buffer, 0, count);
            return output.toByteArray();
        }
    }

    private static void runCase(StringBuilder report, String name, CheckedRunnable test) {
        report.append(name).append(":\n");
        try { test.run(); }
        catch (Exception e) { report.append("  observed failure: ").append(e).append('\n'); }
    }
}
