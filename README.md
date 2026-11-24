# Android 多媒体音频编解码学习

本仓库包含两个**独立的** Android 工程：

| 位置 | 工程 | 说明 |
| --- | --- | --- |
| **仓库根目录** | `AudioDecodeTestApp` | 多格式音频测试应用（界面框架阶段）。Android Studio **直接打开仓库根目录**即可识别本工程 |
| `AudioCodecDebugger/android/` | `AudioCodecDebugger` | AAC 编解码专项实验工程。独立 Gradle 工程，需**单独打开该子目录** |

> 注意：两个工程不能合并成一个 Gradle 工程（一个启用 AndroidX，一个禁用），所以分别独立构建。

## 快速开始（根目录：AudioDecodeTestApp）

### 环境

- JDK 17+
- Android SDK Platform 35、Android SDK Build Tools

clone 后需指定 Android SDK 路径（任选其一）：

1. 设置环境变量 `ANDROID_HOME`（或 `ANDROID_SDK_ROOT`）指向 SDK 目录；
2. 在**仓库根目录**新建 `local.properties`，写入 `sdk.dir=<你的SDK路径>`（本机示例：`C:\Users\WTCL\AppData\Local\Android\Sdk`）。

> `local.properties` 已被 `.gitignore` 排除，不会提交到仓库。

### 构建

```powershell
# 在仓库根目录执行
.\gradlew.bat assembleDebug
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

> `settings.gradle` 已配置阿里云 Maven 镜像，国内网络可直接解析 Google Maven 依赖；官方源保留作回退。

### Android Studio

用 Android Studio 打开**仓库根目录**（`decoding/` 或 clone 后的仓库根），会自动识别为 Gradle 工程。

## AudioCodecDebugger（独立工程）

位于 `AudioCodecDebugger/android/`，是独立的 Gradle 工程，需单独打开该目录，或命令行构建：

```powershell
cd AudioCodecDebugger/android
.\gradlew.bat assembleDebug
```

该工程的素材准备、设备操作与故障说明见 [AudioCodecDebugger/android/README.md](AudioCodecDebugger/android/README.md)。

## 学习教程

基于 `Z:\work\DP8678GPU_GRP\android\system` 的 MTK8678 实际源码，主线为 **MediaExtractor → MediaCodec → CCodec → Codec2 → C2SoftAacDec → FDK-AAC → PCM**。

1. [课程总览、源码索引与版本证据](docs/00_课程总览与源码索引.md)
2. [第 1 周：AAC 基础与码流](docs/01_AAC_基础与码流.md)
3. [第 2 周：MediaCodec API 及解码程序](docs/02_MediaCodec_API_及解码程序.md)
4. [第 3 周：MediaCodec Java/JNI/Native 源码](docs/03_MediaCodec_Java_JNI_Native_源码解析.md)
5. [第 4 周：CCodec 与 Codec2 对象和数据流](docs/04_CCodec_Codec2_对象与数据流.md)
6. [第 5 周：SimpleC2Component 队列、线程与生命周期](docs/05_SimpleC2Component_队列线程与生命周期.md)
7. [第 6 周：C2SoftAacDec 与 FDK-AAC](docs/06_C2SoftAacDec_FDK_AAC_逐函数解析.md)
8. [第 7 周：AAC 编码器与异常恢复](docs/07_AAC_编码器与异常恢复.md)
9. [第 8 周：综合实验与故障诊断](docs/08_综合实验与故障诊断.md)
10. [全链路时序图与术语表](docs/09_全链路时序图与术语表.md)
11. [常见故障排查手册](docs/10_常见故障排查手册.md)

## 可运行交付

- 根目录 `AudioDecodeTestApp`：Kotlin + Material 3 的多格式音频测试界面框架。
- `AudioCodecDebugger/android/`：完整 Android Java 工程，支持 AAC/M4A 解码、PCM 编码、组件枚举、基准与故障实验；含 Gradle Wrapper。
- `AudioCodecDebugger/native/AacFrameParser/`：C++17 ADTS 帧解析器。
- `AudioCodecDebugger/scripts/`：测试素材生成、Python ADTS 参考解析、PCM 对比、延迟统计。
- `AudioCodecDebugger/tests/`：主机自动化测试。
- `patches/`：不默认启用的 Native Trace/耗时注入补丁及撤销说明。
- `docs/开源播放器调研.md`：开源播放器/解码库调研（Media3、VLC、FFmpeg）。
- `TEST_REPORT.md`：实际执行、跳过和待设备验证的边界。

## 主机工具快速开始

```powershell
python -m unittest discover -s AudioCodecDebugger/tests -v
python AudioCodecDebugger/scripts/generate_test_files.py --output AudioCodecDebugger/test_data
python AudioCodecDebugger/scripts/adts_inspect.py AudioCodecDebugger/test_data/parser_only_5_frames.adts
```

## 证据约定

- **[本地源码]**：已从上述源码树核实路径、签名或行为；行号对应当前工作树。
- **[API 合同]**：Android 公共 API 语义，仍需以设备实现验证。
- **[技术推断]**：依据源码作出的解释，不冒充实测。
- **[示例]**：命令的预期输出或合成数据，不冒充设备日志。
- **[已实测]**：只用于本次确实执行成功的主机测试或 Java 编译。

本教程不把 AAC 都视为 ADTS，不假定 Work 与压缩帧/PCM 输出一一对应，也不把 MediaCodec/Codec2 当作蓝牙 HFP 编解码的唯一入口。
