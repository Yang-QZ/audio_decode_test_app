# Android 多媒体音频编解码学习教程

基于 `Z:\work\DP8678GPU_GRP\android\system` 的 MTK8678 实际源码，主线为 **MediaExtractor → MediaCodec → CCodec → Codec2 → C2SoftAacDec → FDK-AAC → PCM**。

## 阅读顺序

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

- `AudioCodecDebugger/android/`：完整 Android Java 工程，支持 AAC/M4A 解码、PCM 编码、组件枚举、基准与故障实验；含 Gradle Wrapper。
- `AudioCodecDebugger/native/AacFrameParser/`：C++17 ADTS 帧解析器。
- `AudioCodecDebugger/scripts/`：测试素材生成、Python ADTS 参考解析、PCM 对比、延迟统计。
- `AudioCodecDebugger/tests/`：主机自动化测试。
- `patches/`：不默认启用的 Native Trace/耗时注入补丁及撤销说明。
- `TEST_REPORT.md`：实际执行、跳过和待设备验证的边界。

## 快速开始

```powershell
python -m unittest discover -s AudioCodecDebugger/tests -v
python AudioCodecDebugger/scripts/generate_test_files.py --output AudioCodecDebugger/test_data
python AudioCodecDebugger/scripts/adts_inspect.py AudioCodecDebugger/test_data/parser_only_5_frames.adts
cd AudioCodecDebugger/android
.\gradlew.bat assembleDebug
```

最后一条需要 JDK 17 和 Android SDK 35。真实设备操作、输出样例与风险说明见 [Android README](AudioCodecDebugger/android/README.md)。

## 证据约定

- **[本地源码]**：已从上述源码树核实路径、签名或行为；行号对应当前工作树。
- **[API 合同]**：Android 公共 API 语义，仍需以设备实现验证。
- **[技术推断]**：依据源码作出的解释，不冒充实测。
- **[示例]**：命令的预期输出或合成数据，不冒充设备日志。
- **[已实测]**：只用于本次确实执行成功的主机测试或 Java 编译。

本教程不把 AAC 都视为 ADTS，不假定 Work 与压缩帧/PCM 输出一一对应，也不把 MediaCodec/Codec2 当作蓝牙 HFP 编解码的唯一入口。
