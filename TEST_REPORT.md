# 测试与交付报告

日期：2026-11-04  
工作区：`C:\Users\WTCL\Desktop\DOC\decoding`  
源码：`Z:\work\DP8678GPU_GRP\android\system`

## 1. 版本证据

- `frameworks/base`：`ee42aee9b1774e6c8cf8e19c9fd12946f93cb31b`，detached HEAD。
- `frameworks/av`：`8fa836e6a6f09d4f1d3fe4e72e68596d7fddd7b3`，`mtk8678_gpu_main_dev`。
- `external/aac`：`c25fba6e77788e3ec2c2cba15bacbd0baab5b675`，detached HEAD。
- FDK decoder API 宏：3.2.0。
- 整体 Android 版本和设备运行库版本：未确认；API 35 桩存在不代表设备版本。

## 2. 已实际执行并通过

| 项目 | 命令/方式 | 结果 |
|---|---|---|
| 本地源码扫描 | 只读扫描 Java/JNI/Native/CCodec/Codec2/AAC/FDK | 确认真实路径、签名、行号、组件注册和厂商扩展 |
| 主机单元测试 | `python -m unittest discover -s AudioCodecDebugger/tests -v` | Ran 6；5 passed，1 skipped；最终 OK |
| Python 语法 | `python -m compileall -q AudioCodecDebugger/scripts` | 通过 |
| 素材生成 | `generate_test_files.py --seconds 5` | 生成 5 秒 PCM/WAV、5 帧 ADTS、截断/坏 sync 样例 |
| ADTS 工具 | `adts_inspect.py` | 5 帧、48 kHz、AAC-LC profile、双声道配置、变长帧解析成功 |
| PCM 工具 | 合成 PCM 与自身比较 | bit-exact 自比较通过；不是 AAC 质量测试 |
| Trace 转换 | 合成 AAC_TRACE → JSONL → latency | queue=1 ms、decode=2 ms、callback=1 ms，数值来自合成样例 |
| Android Java 编译 | `javac` + 本地 `prebuilts/sdk/35/public/android.jar` | 全部 7 个 Java 文件通过，`ANDROID_JAVA_COMPILE_OK` |
| Gradle Wrapper | `gradlew.bat --version` | Gradle 8.9 启动成功 |
| 补丁应用检查 | `git apply --check --ignore-space-change --ignore-whitespace` | 在真实 `frameworks/av` 工作树通过 |
| 补丁撤销检查 | 在修改副本 `git apply --check -R ...` | 通过 |
| 文档结构 | 内容检索 | 01–08 均含 12 个要求章节、至少 5 项验收和 6 道含答案思考题 |

主机测试唯一跳过项：`test_native_if_provided`。需要通过 `AAC_FRAME_PARSER` 环境变量提供已编译 C++ 程序后执行。

## 3. 未执行或受环境限制

| 项目 | 原因/状态 |
|---|---|
| C++ ADTS 编译与运行 | 本机无 CMake、clang++/g++/MSVC；源码已交付，未声称编译通过 |
| FFmpeg 编解码与 ffplay | 本机未安装；命令和脚本支持已提供，未生成可解码 AAC/M4A |
| APK `assembleDebug` | 无完整 Android SDK 环境；仅完成 API 35 Java 编译和 Wrapper 启动 |
| ADB 设备实验 | `adb devices -l` 无设备；未安装/运行 App |
| Native 编译/刷机 | 未配置项目 lunch 目标、构建权限和开发设备；未修改原源码 |
| `C2SoftAacDec::process` 设备证明 | 需 Native trace/断点；当前只有本地源码证据 |
| 5 分钟连续解码、P95/P99 设备基准 | 待设备执行 |
| PCM 设备输出、故障矩阵、Priming/Padding | 待设备/FFmpeg 执行 |

## 4. 补丁说明

`patches/0001-aac-codec2-trace.patch` 没有应用到原源码。普通 `apply --check` 因网络源码树行尾/空白差异失败，使用两个 ignore 选项后通过；撤销检查也通过。补丁默认关闭业务日志与阻塞：

- `debug.audio.codec.trace=0`
- `debug.audio.codec.delay.ms=0`

它仅用于独立实验分支和开发设备，部署需处理 APEX/AVB/签名/SELinux，不得直接覆盖生产库。

## 5. 交付物

- `README.md`：入口。
- `docs/00`–`10`：11 份教程、索引、时序、术语和排障文档。
- `AudioCodecDebugger/android/`：完整 Java App、Gradle Wrapper、README。
- `AudioCodecDebugger/native/AacFrameParser/`：C++17 解析器与 CMake。
- `AudioCodecDebugger/scripts/`：5 个主机工具。
- `AudioCodecDebugger/tests/`：自动化测试。
- `AudioCodecDebugger/test_data/`：合成 PCM/WAV/ADTS 样例。
- `AudioCodecDebugger/samples/`：明确标记的合成/主机输出样例。
- `patches/`：可撤销 Trace/耗时补丁和构建风险说明。

## 6. 后续真实设备验收顺序

1. 记录设备 fingerprint 与源码/运行库一致性。
2. 构建安装 APK，枚举并显式选择实际软件 AAC 组件。
3. 运行 M4A/ADTS 解码，检查输出 EOS、格式、释放计数和 PCM。
4. 在开发构建应用补丁，证明进入 `C2SoftAacDec::process` 和 FDK。
5. 执行 5 分钟稳定性、benchmark、fault 和编码往返。
6. 保存真实 JSONL/logcat/PCM/报告，恢复属性并撤销补丁。
