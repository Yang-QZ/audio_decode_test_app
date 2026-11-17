# AacFrameParser

只解析 ADTS 封装，不解码 AAC payload。支持 ID3v2 前缀、7/9 字节头、VBR 帧长、多个 `raw_data_block`、截断与错误 syncword；`channel_configuration=0` 时不会猜声道数。

## 构建

需要 CMake 3.16+ 和支持 C++17 的编译器：

```sh
cmake -S AudioCodecDebugger/native/AacFrameParser -B AudioCodecDebugger/native/AacFrameParser/build
cmake --build AudioCodecDebugger/native/AacFrameParser/build --config Release
```

Windows 多配置生成器的程序通常位于 `build/Release/aac_frame_parser.exe`；单配置生成器位于 `build/aac_frame_parser`。

## 运行

```sh
python AudioCodecDebugger/scripts/generate_test_files.py --output AudioCodecDebugger/test_data
python AudioCodecDebugger/scripts/adts_inspect.py AudioCodecDebugger/test_data/parser_only_5_frames.adts
aac_frame_parser --json AudioCodecDebugger/test_data/parser_only_5_frames.adts
aac_frame_parser --csv input.aac > frames.csv
```

正常退出码为 0；输入结构错误为 1；命令或文件错误为 2。`--recover` 只用于调查 sync 丢失，会扫描下一候选头，不保证恢复出的 payload 正确。样例 `parser_only_5_frames.adts` 的 payload 是合成数据，只能测试解析器，不能送解码器。
