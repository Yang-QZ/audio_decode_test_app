package com.example.audiocodecdebugger;

import android.os.Process;

import org.json.JSONObject;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** JSONL trace using one process-local monotonic clock. No wall-clock latency math. */
public final class TraceLogger implements Closeable {
    private final BufferedWriter writer;
    private final String component;

    public TraceLogger(File file, String component) throws IOException {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Cannot create " + parent);
        }
        writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(file, false), StandardCharsets.UTF_8));
        this.component = component;
    }

    public synchronized void event(String name, Map<String, ?> fields) throws IOException {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("event", name);
        event.put("component", component);
        event.put("pid", Process.myPid());
        event.put("tid", Process.myTid());
        event.put("monotonicNs", System.nanoTime());
        if (fields != null) event.putAll(fields);
        writer.write(new JSONObject(event).toString());
        writer.newLine();
        writer.flush();
    }

    public synchronized void event(String name) throws IOException {
        event(name, null);
    }

    public static Map<String, Object> fields() {
        return new LinkedHashMap<>();
    }

    @Override
    public synchronized void close() throws IOException {
        writer.close();
    }
}
