package com.example.audiocodecdebugger;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final String TAG = "AudioCodecDebugger";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private TextView output;
    private EditText codecName;
    private File inputDir;
    private File outputDir;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        File base = getExternalFilesDir(null);
        if (base == null) base = getFilesDir();
        inputDir = new File(base, "input");
        outputDir = new File(base, "output");
        inputDir.mkdirs();
        outputDir.mkdirs();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 32, 32, 32);
        root.setBackgroundColor(Color.rgb(247, 247, 247));
        TextView title = new TextView(this);
        title.setText("AudioCodecDebugger");
        title.setTextSize(24);
        title.setTextColor(Color.rgb(0, 80, 85));
        title.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(title, new LinearLayout.LayoutParams(-1, 90));
        codecName = new EditText(this);
        codecName.setSingleLine(true);
        codecName.setText("c2.android.aac.decoder");
        codecName.setHint("Codec name; blank = platform default");
        root.addView(codecName);
        addButton(root, "枚举 AAC Codec", "enumerate");
        addButton(root, "解码 input.m4a → PCM", "decode_m4a");
        addButton(root, "解码 input.aac（ADTS）→ PCM", "decode_aac");
        addButton(root, "编码 input.pcm → M4A", "encode");
        addButton(root, "解码基准（3 次）", "benchmark");
        addButton(root, "故障与 Buffer 反压测试", "fault");
        output = new TextView(this);
        output.setTextSize(12);
        output.setTextIsSelectable(true);
        output.setPadding(8, 24, 8, 24);
        ScrollView scroll = new ScrollView(this);
        scroll.addView(output);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        append("输入目录: " + inputDir + "\n输出目录: " + outputDir +
                "\n请将测试文件放入输入目录。所有时间指标使用 System.nanoTime()。\n");
        handleIntent(getIntent());
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent != null && intent.getStringExtra("action") != null) {
            execute(intent.getStringExtra("action"));
        }
    }

    private void addButton(LinearLayout root, String label, String action) {
        Button button = new Button(this);
        button.setText(label);
        button.setOnClickListener(v -> execute(action));
        root.addView(button, new LinearLayout.LayoutParams(-1, -2));
    }

    private void execute(String action) {
        append("\n> " + action + "\n");
        String selectedCodec = codecName.getText().toString().trim();
        executor.execute(() -> {
            try {
                String result = runAction(action, selectedCodec);
                append(result + "\n");
            } catch (Throwable error) {
                Log.e(TAG, "Action failed: " + action, error);
                append("FAILED: " + error + "\n" + Log.getStackTraceString(error) + "\n");
            }
        });
    }

    private String runAction(String action, String codec) throws Exception {
        File m4a = new File(inputDir, "input.m4a");
        File aac = new File(inputDir, "input.aac");
        switch (action) {
            case "enumerate": return CodecEnumerator.enumerateAac();
            case "decode_m4a": return AacDecoder.decode(m4a, new File(outputDir, "decoded_m4a_s16le.pcm"),
                    new File(outputDir, "decoded_m4a.jsonl"), codec).toString();
            case "decode_aac": return AacDecoder.decode(aac, new File(outputDir, "decoded_adts_s16le.pcm"),
                    new File(outputDir, "decoded_adts.jsonl"), codec).toString();
            case "encode": return AacEncoder.encode(new File(inputDir, "input.pcm"),
                    new File(outputDir, "encoded.m4a"), new File(outputDir, "encoded.jsonl"),
                    48000, 2, 128000).toString();
            case "benchmark":
                File input = m4a.exists() ? m4a : aac;
                return CodecBenchmark.run(input, new File(outputDir, "benchmark"), codec, 3);
            case "fault":
                File faultInput = m4a.exists() ? m4a : aac;
                return CodecFaultTest.runAll(faultInput, new File(outputDir, "fault"), codec);
            default: return "Unknown action: " + action;
        }
    }

    private void append(String text) {
        Log.i(TAG, text);
        runOnUiThread(() -> output.append(text));
    }

    @Override protected void onDestroy() {
        executor.shutdownNow();
        super.onDestroy();
    }
}
