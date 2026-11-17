# AudioDecodeTestApp

用于学习 Android 音频解码的测试应用（从零搭建）。目标是支持对 AAC / MP3 / FLAC / Opus / Vorbis / ALAC / AC-3 等多种编码格式的音频文件进行解码与验证。

## 当前进度

- 已完成：**界面框架**（底部导航 4 个页面：测试 / 文件 / 编解码器 / 关于）。
- 尚未接入：音频文件选择、解封装、解码、播放与 PCM 校验。

## 技术栈

- Kotlin + AndroidX + Material 3
- 后续接入：`MediaExtractor` → `MediaCodec`（默认）→（可选）FFmpeg 软解兜底

## 目录结构

```
app/src/main/java/com/example/audiodecodetestapp/
├── MainActivity.kt            # 单 Activity + 底部导航，负责 Fragment 切换
├── model/CodecFormat.kt       # 音频格式占位数据模型
├── adapter/CodecFormatAdapter.kt  # 格式卡片列表适配器（测试页与编解码器页复用）
└── ui/
    ├── home/HomeFragment.kt       # 测试页：格式卡片网格
    ├── files/FilesFragment.kt     # 文件页：空状态占位
    ├── codecs/CodecsFragment.kt   # 编解码器页：列表占位
    └── about/AboutFragment.kt     # 关于页：技术栈与参考说明
```

## 构建

依赖：JDK 17+、Android SDK Platform 35、Android SDK Build Tools。

> clone 后需指定 Android SDK 路径（任选其一）：
> 1. 设置环境变量 `ANDROID_HOME`（或 `ANDROID_SDK_ROOT`）指向 SDK 目录；
> 2. 在项目根目录新建 `local.properties`，写入 `sdk.dir=<你的SDK路径>`（本机示例：`C:\Users\WTCL\AppData\Local\Android\Sdk`）。
>
> 本机的 `local.properties` 已被 `.gitignore` 排除，不会提交到仓库。

> 仓库源已配置阿里云镜像（`settings.gradle`），以便在国内网络下解析 Google Maven 依赖；官方源保留作回退。

```powershell
cd AudioDecodeTestApp
.\gradlew.bat assembleDebug
adb install -r .\app\build\outputs\apk\debug\app-debug.apk
```

已验证：`assembleDebug` 构建通过，产物 `app/build/outputs/apk/debug/app-debug.apk`。

启动：

```powershell
adb shell am start -n com.example.audiodecodetestapp/.MainActivity
```

## 开源播放器调研

见 [docs/开源播放器调研.md](docs/开源播放器调研.md)。

## 后续规划

1. 文件选择（SAF / 存储访问框架）。
2. `MediaExtractor` 探测容器与音轨信息。
3. `MediaCodec` 解码各格式 → PCM，并做正确性/时延校验。
4. 需要时引入 Media3 FFmpeg 扩展或 libVLC 覆盖冷门格式。
