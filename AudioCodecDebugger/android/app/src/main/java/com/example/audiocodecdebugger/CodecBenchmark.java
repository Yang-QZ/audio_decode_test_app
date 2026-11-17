package com.example.audiocodecdebugger;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class CodecBenchmark {
    private CodecBenchmark() {}

    public static String run(File input, File outputDir, String codecName, int iterations) throws IOException {
        if (iterations <= 0 || iterations > 100) throw new IllegalArgumentException("iterations must be 1..100");
        if (!outputDir.exists() && !outputDir.mkdirs()) throw new IOException("Cannot create " + outputDir);
        List<Long> elapsed = new ArrayList<>();
        List<Long> outputWait = new ArrayList<>();
        long totalPcmBytes = 0;
        for (int i = 0; i < iterations; i++) {
            File pcm = new File(outputDir, "benchmark_" + i + ".pcm");
            File trace = new File(outputDir, "benchmark_" + i + ".jsonl");
            long start = System.nanoTime();
            AacDecoder.Result result = AacDecoder.decode(input, pcm, trace, codecName);
            elapsed.add(System.nanoTime() - start);
            outputWait.addAll(result.outputWaitNs);
            totalPcmBytes += result.outputBytes;
            if (!pcm.delete()) throw new IOException("Cannot delete temporary " + pcm);
        }
        JSONObject report = new JSONObject();
        try {
            report.put("input", input.getAbsolutePath());
            report.put("codec", codecName == null ? "default" : codecName);
            report.put("iterations", iterations);
            report.put("wallDecodeMs", stats(elapsed));
            report.put("appOutputWaitMs", stats(outputWait));
            report.put("totalPcmBytes", totalPcmBytes);
            report.put("scope", "App wall time and dequeueOutputBuffer wait; not native decode CPU time");
        } catch (Exception e) {
            throw new IOException("Cannot build benchmark report", e);
        }
        File file = new File(outputDir, "benchmark_summary.json");
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write((report.toString(2) + "\n").getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) {
            throw new IOException("Cannot write benchmark report", e);
        }
        return report.toString() + "\nReport=" + file;
    }

    private static JSONObject stats(List<Long> values) throws Exception {
        JSONObject object = new JSONObject();
        object.put("count", values.size());
        if (values.isEmpty()) return object;
        List<Long> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        long sum = 0;
        for (long value : values) sum += value;
        object.put("mean", sum / values.size() / 1e6);
        object.put("min", sorted.get(0) / 1e6);
        object.put("p95", percentile(sorted, .95) / 1e6);
        object.put("p99", percentile(sorted, .99) / 1e6);
        object.put("max", sorted.get(sorted.size() - 1) / 1e6);
        object.put("samplesNs", new JSONArray(values));
        return object;
    }

    private static long percentile(List<Long> sorted, double fraction) {
        return sorted.get(Math.max(0, (int) Math.ceil(fraction * sorted.size()) - 1));
    }
}
