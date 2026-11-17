# 可撤销 AAC / Codec2 调试补丁

`0001-aac-codec2-trace.patch` 基于以下本地工作树生成：

- 根：`Z:\work\DP8678GPU_GRP\android\system\frameworks\av`
- HEAD：`8fa836e6a6f09d4f1d3fe4e72e68596d7fddd7b3`
- 分支：`mtk8678_gpu_main_dev`

**补丁未应用到原源码树。** 它只应在独立实验分支和开发设备使用。

## 修改范围

1. `CCodecBufferChannel.cpp`：Work 创建时记录 component、frameIndex、PTS、输入字节、单调 queueTimeNs。
2. `SimpleC2Component.cpp`：记录 `queue_nb`、`processQueue/process`、立即完成、`finish/cloneAndSend` 回调。
3. `C2SoftAacDec.cpp`：记录 process start/end、FDK Fill 返回/bytesValid、DecodeFrame 返回/消费/StreamInfo、实际 PCM 输出。

`AAC_TRACE` 使用 logcat `INFO`，属性关闭时没有业务行为变化；仍保留属性查询和极小日志判断开销，因此不要默认合入产品。

耗时注入只存在于 AAC 软件解码 `process()`：`debug.audio.codec.delay.ms` 支持 0–100 ms，超过 100 被截断，负值/0 不阻塞。默认 0。

## 应用与检查

```powershell
cd Z:/work/DP8678GPU_GRP/android/system/frameworks/av
# 先创建独立实验分支并保存现有工作；不要用本说明覆盖未提交修改。
git -c safe.directory='*' apply --check --ignore-space-change --ignore-whitespace C:/Users/WTCL/Desktop/DOC/decoding/patches/0001-aac-codec2-trace.patch
git -c safe.directory='*' apply --ignore-space-change --ignore-whitespace C:/Users/WTCL/Desktop/DOC/decoding/patches/0001-aac-codec2-trace.patch
git -c safe.directory='*' diff --check
```

网络源码树存在行尾/空白差异，普通 `apply --check` 已验证会失败；上述两个 ignore 选项已验证通过。若上下文仍失败，不要强制应用，应基于新工作树重新生成补丁。

撤销：

```powershell
git -c safe.directory='*' apply -R --ignore-space-change --ignore-whitespace C:/Users/WTCL/Desktop/DOC/decoding/patches/0001-aac-codec2-trace.patch
```

## 编译与部署风险

已核实的相关 Soong 目标：

- `libsfplugin_ccodec`（`sfplugin/Android.bp:23-47`）
- `libcodec2_soft_common`
- `libcodec2_soft_aacdec`

在项目已配置的 Linux 构建环境中：

```sh
source build/envsetup.sh
lunch <项目实际目标>
m libsfplugin_ccodec libcodec2_soft_common libcodec2_soft_aacdec
```

`lunch` 目标、ABI、输出路径和是否需整镜像待项目确认。编译成功不等于部署成功。软件 AAC 可能在 `com.android.media.swcodec` APEX；必须遵守项目的 APEX 签名、AVB、动态分区和 SELinux 流程。先备份原库/镜像，记录 fingerprint 与库 hash，不在生产设备直接 push。

## 开关与采集

```sh
adb shell setprop debug.audio.codec.trace 1
adb shell setprop debug.audio.codec.delay.ms 0
adb logcat -c
# 运行一次 AudioCodecDebugger decode_m4a
adb logcat -d -v threadtime -s CCodecBufferChannel SimpleC2Component C2SoftAacDec AudioCodecDebugger > aac_trace.log
adb shell setprop debug.audio.codec.trace 0
```

耗时实验依次使用 5/15/30 ms，每个用例隔离进程和输入状态，结束恢复 0。注入会故意影响实时性，不得用于正常播放评估。

转换与统计：

```powershell
python AudioCodecDebugger/scripts/logcat_to_jsonl.py aac_trace.log --output aac_trace.jsonl
python AudioCodecDebugger/scripts/analyze_latency.py aac_trace.jsonl --output aac_latency.json
```

## 指标边界

- 同一 frameIndex 的 queue/decode/callback 可计算 queueDelay、decodeCost、callbackLag。
- `work_create` 与组件 `queue` 都有 queueTimeNs，转换器优先组件 queue。
- Flush/取消/重排/多输出可能使 Work 不一一对应；转换器遇到同 key 的新 decode_start 会结束上一记录，并保留 `observedEvents` 供检查。
- 缺 decode start/end 的记录会警告，不能用 0 补齐。
- 日志含 threadtime PID/TID；跨进程必须保留组件和时钟来源。
- 本补丁不伪造 `C2SoftAacDec::process()` 的 FDK CPU 时间：decodeCost 包含整个 process 适配、DRC、ring buffer 和可选注入，不是纯算法指令时间。
