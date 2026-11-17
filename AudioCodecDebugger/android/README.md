# AudioCodecDebugger Android 实验

一个无 AndroidX、无运行时存储权限的 Java 测试应用。模块位于 `app/src/main/java/com/example/audiocodecdebugger/`：

- `CodecEnumerator`：AAC 编解码器和能力枚举。
- `AacDecoder`：M4A/MP4（MediaExtractor）或 ADTS 裸流到交错 PCM16；处理 CSD、PTS、格式变化、EOS、Flush 和 Buffer 释放。
- `AacEncoder`：48 kHz、双声道、s16le PCM 到 AAC-LC/M4A。
- `CodecBenchmark`：应用墙钟时间、输出等待及 P95/P99；不是 Native 解码 CPU 时间。
- `CodecFaultTest`：截断、损坏、Flush、非法状态、快速 Stop、持有输出 Buffer。
- `TraceLogger`：进程内 `System.nanoTime()` JSONL，含 PID/TID、PTS、输入/输出字节。

## 构建

依赖：JDK 17、Android SDK Platform 35、Android SDK Build Tools。Gradle Wrapper 已包含，无需预装 Gradle。

```powershell
cd AudioCodecDebugger/android
.\gradlew.bat assembleDebug
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

也可用本源码树 API 桩做纯 Java 编译检查；这不能代替 APK 构建或设备验证：

```powershell
$files = Get-ChildItem app/src/main/java -Recurse -Filter *.java | ForEach-Object FullName
javac -encoding UTF-8 -source 8 -target 8 -cp Z:/work/DP8678GPU_GRP/android/system/prebuilts/sdk/35/public/android.jar -d build/javac-check $files
```

## 准备素材

主机安装 FFmpeg 后，在仓库根目录执行：

```powershell
python AudioCodecDebugger/scripts/generate_test_files.py --output AudioCodecDebugger/test_data --encode
adb shell mkdir -p /sdcard/Android/data/com.example.audiocodecdebugger/files/input
adb push AudioCodecDebugger/test_data/sine.m4a /sdcard/Android/data/com.example.audiocodecdebugger/files/input/input.m4a
adb push AudioCodecDebugger/test_data/sine.aac /sdcard/Android/data/com.example.audiocodecdebugger/files/input/input.aac
adb push AudioCodecDebugger/test_data/sine_48k_stereo_s16le.pcm /sdcard/Android/data/com.example.audiocodecdebugger/files/input/input.pcm
```

应用专属目录不需要 `READ/WRITE_EXTERNAL_STORAGE`。部分设备的 USB/MTP 策略可能限制访问 `Android/data`，但 `adb push/pull` 通常可用；失败时使用应用 UI 显示的实际目录并检查设备策略。

## 运行

启动应用后点击按钮，或用 adb action：

```powershell
adb shell am start -n com.example.audiocodecdebugger/.MainActivity --es action enumerate
adb shell am start -n com.example.audiocodecdebugger/.MainActivity --es action decode_m4a
adb shell am start -n com.example.audiocodecdebugger/.MainActivity --es action decode_aac
adb shell am start -n com.example.audiocodecdebugger/.MainActivity --es action encode
adb shell am start -n com.example.audiocodecdebugger/.MainActivity --es action benchmark
adb shell am start -n com.example.audiocodecdebugger/.MainActivity --es action fault
adb logcat -s AudioCodecDebugger:I C2SoftAacDec:V
```

默认显式选择 `c2.android.aac.decoder`。若设备枚举结果没有该名称，将 UI 输入框清空以使用平台默认，或填入实际软件 AAC 解码器名称；不要仅凭名称推断当前设备一定使用软件路径。

拉取输出：

```powershell
adb pull /sdcard/Android/data/com.example.audiocodecdebugger/files/output AudioCodecDebugger/device_output
ffplay -f s16le -ar 48000 -ac 2 AudioCodecDebugger/device_output/decoded_m4a_s16le.pcm
python AudioCodecDebugger/scripts/analyze_latency.py AudioCodecDebugger/device_output/decoded_m4a.jsonl
python AudioCodecDebugger/scripts/compare_pcm.py AudioCodecDebugger/test_data/sine_48k_stereo_s16le.pcm AudioCodecDebugger/device_output/decoded_m4a_s16le.pcm --sample-rate 48000 --channels 2 --scan-delay 4096
```

`ffplay` 的采样率和声道必须按实际 `format_changed` 日志调整。AAC 是有损编码，往返不追求 bit-exact；容器中的 priming/padding 与原始 PCM 样本数应单独核对。

## 故障排查

- `createByCodecName` 失败：先运行 `enumerate`，确认设备名称与源码组件名称一致。
- M4A 无 AAC track：用 `ffprobe` 检查实际 MIME/编码，不要改扩展名伪装。
- ADTS `PCE not supported`：本实验不解析 `channel_configuration=0`，换常规声道配置样例。
- 输出等待或 120 s 超时：检查是否每个输出 index 都调用了 `releaseOutputBuffer`，以及输入/EOS 是否只提交一次。
- PCM 爆音：先核对 s16le、交错布局、采样率、声道及 PTS，不要把 ADTS 字节当 PCM。
- 故障测试的“接受损坏输入”不等于解码正确；需同时检查返回码、静音、输出连续性和恢复行为。

当前交付只完成了 API 35 Java 编译检查；APK 构建和真实设备运行必须在具备 SDK/设备的环境中执行并记录结果。
