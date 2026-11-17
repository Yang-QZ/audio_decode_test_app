# 输出样例

这些文件是**主机合成数据/工具实测输出**，不是 Android 设备日志：

- `native_trace_example.log`：手工构造的一帧 AAC_TRACE，数值仅验证格式。
- `native_trace_example.jsonl`、`native_latency_example.json`：由上述合成日志实际运行转换/统计脚本生成。
- `adts_parser_output.json`：解析 `test_data/parser_only_5_frames.adts` 的真实主机输出；该 ADTS payload 不可解码。
- `pcm_compare_identical.json`：同一合成 PCM 与自身比较，验证工具，不代表 AAC 往返质量。

真实设备结果应另存为 `device_output/`，并附 fingerprint、组件名、源码 HEAD 和 logcat。
