# 解析tool - Analysis Tool

NCM 解密 + 哔哩哔哩视频解析的 Android 多媒体工具箱。

基于 ncmdump C++ 核心的 Android 图形化解密工具，附带哔哩哔哩视频解析功能。

## 功能

### NCM 解密
- 选择单个或多个 .ncm 文件批量解密
- 自定义输出目录（自动记忆上次选择）
- 解密后自动写入专辑封面 + ID3 元数据（歌曲名/艺术家/专辑）
- 自动爬取网易云歌词，输出同名 .lrc 文件
- 批量解密进度显示
- 自动识别输出格式（mp3 / flac）
- 支持 armeabi-v7a / arm64-v8a / x86 / x86_64 全架构

### 哔哩哔哩视频解析
- 输入 BV 号或视频链接，解析视频信息
- 显示封面、标题、UP主、时长、简介、分P列表
- 获取 DASH 播放地址（视频流 + 音频流），支持复制
- wbi 签名自动处理

## 项目结构

```
NcmDumpAndroid/
├── app/
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/ncmdump/app/
│       │   ├── MainActivity.kt       # 主界面：NCM解密
│       │   ├── BiliActivity.kt       # B站视频解析界面
│       │   ├── NcmCrypt.kt           # JNI 绑定类
│       │   ├── MetadataWriter.kt     # ID3元数据/封面写入
│       │   ├── LyricFetcher.kt       # 网易云歌词爬取
│       │   └── BiliParser.kt         # B站API解析（含wbi签名）
│       ├── jni/
│       │   ├── CMakeLists.txt        # NDK 构建配置
│       │   ├── ncm_jni.cpp           # JNI 绑定层
│       │   ├── ncmcrypt.cpp          # ncm 解密核心
│       │   ├── ncmcrypt.h
│       │   ├── aes.cpp / aes.h       # AES-128-ECB
│       │   ├── cJSON.cpp / cJSON.h   # JSON 解析
│       │   └── base64.h              # Base64
│       └── res/
│           ├── layout/
│           │   ├── activity_main.xml
│           │   └── activity_bili.xml
│           └── values/
├── build.gradle
├── settings.gradle
└── gradle.properties
```

## 解密原理

ncm 文件采用两层加密：
1. **密钥层**：文件头内置经异或混淆的核心密钥，用固定 `sCoreKey` 经 AES-128-ECB 解密后构建 RC4 风格的 keybox
2. **音频层**：原始音频流用 keybox 逐字节异或解密，输出标准 mp3 或 flac
3. **元数据层**：歌曲名/艺术家/专辑/封面经 `sModifyKey` AES 解密 + Base64 解码后解析

## 构建

### 环境要求
- JDK 17
- Android SDK Platform 34
- Android NDK 25.2.9519653
- CMake 3.22.1
- Gradle 8.2

### 命令行构建
```bash
export JAVA_HOME=/path/to/jdk17
export ANDROID_HOME=/path/to/android-sdk
gradle assembleRelease
```

APK 输出路径：`app/build/outputs/apk/release/`

## 使用

1. 安装 APK 后打开应用
2. 首次使用需授予「所有文件访问权限」
3. 点击「选择 ncm 文件」，选择 .ncm 文件（可多选）
4. （可选）点击「选择输出目录」指定保存位置（自动记忆）
5. 点击「开始解密」，等待进度完成
6. 解密后的 mp3/flac + .lrc 歌词保存在输出目录
7. 首页「更多工具」可进入 B站视频解析

## 技术要点

### SAF 路径处理
Android 10+ 采用 Scoped Storage，通过 SAF 选择的文件只有 `content://` Uri。解决方案：
- 先将 Uri 对应的文件流拷贝到应用私有缓存目录
- 获取缓存文件的绝对路径传给 native 层解密
- 解密完成后删除缓存文件

### 输出目录记忆
- 同时持久化 SAF Tree Uri 和解析后的真实路径
- 启动时恢复 Uri，路径失效时自动重新解析
- 支持内置存储（primary:）和 SD 卡（xxxx-xxxx:）路径映射

### 大文件保护
- `android:largeHeap="true"` 增加内存配额
- 后处理流程全部使用 `Throwable` 捕获，防止 OOM 导致闪退
- 封面超过 10MB 时自动跳过写入

### jaudiotagger 元数据写入
- 使用 `net.jthink:jaudiotagger:3.0.1` 纯 Java 库
- 支持 MP3 ID3v2 和 FLAC 格式
- 封面写入：`deleteArtworkField()` + `addField(artwork)` 避免冲突
- 每项操作独立 try-catch，单项失败不影响整体

## 版权声明

本工具仅供个人已购买/下载歌曲的本地备份使用。未经授权传播、商用解密后的音频文件，会侵犯音乐作品著作权。B站解析功能仅供学习研究使用。

by.muling
