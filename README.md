# NcmDump Android

网易云音乐 ncm 加密格式解密工具的 Android 图形化移植版。

基于 [taurusxin/ncmdump](https://github.com/taurusxin/ncmdump) C++ 核心，通过 NDK/JNI 编译为原生库，Kotlin 实现 UI。

## 功能

- 选择单个或多个 .ncm 文件解密
- 自定义输出目录
- 批量解密进度显示
- 自动识别输出格式（mp3 / flac）
- 支持 armeabi-v7a / arm64-v8a / x86 / x86_64 全架构

## 项目结构

```
NcmDumpAndroid/
├── app/
│   ├── build.gradle
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/ncmdump/app/
│       │   ├── MainActivity.kt      # 主界面：文件选择/解密/进度
│       │   └── NcmCrypt.kt          # JNI 绑定类
│       ├── jni/
│       │   ├── CMakeLists.txt       # NDK 构建配置
│       │   ├── ncm_jni.cpp          # JNI 绑定层（C++ ↔ Kotlin）
│       │   ├── ncmcrypt.cpp         # ncm 解密核心（已剥离 taglib）
│       │   ├── ncmcrypt.h
│       │   ├── aes.cpp / aes.h      # AES-128-ECB 解密
│       │   ├── cJSON.cpp / cJSON.h  # 元数据 JSON 解析
│       │   └── base64.h             # Base64 解码
│       └── res/
│           ├── layout/activity_main.xml
│           └── values/
├── build.gradle
├── settings.gradle
└── gradle.properties
```

## 解密原理

ncm 文件采用两层加密：
1. **密钥层**：文件头内置经 RSA 加密的核心密钥，用固定 `sCoreKey` 经 AES-128-ECB 解密后构建 RC4 风格的 keybox
2. **音频层**：原始音频流用 keybox 逐字节异或解密，输出标准 mp3 或 flac
3. **元数据层**：歌曲名/艺术家/专辑经 `sModifyKey` AES 解密 + Base64 解码后解析

> 本移植已剥离 taglib 依赖（封面/元数据写入），解密后的音频文件可正常播放；如需写入封面可在 Java 层用 `MediaMetadataRetriever` 扩展。

## 构建

### 环境要求
- Android Studio Hedgehog (2023.1.1) 或更高
- Android NDK 25+（项目已指定 CMake 3.22.1）
- Android SDK Platform 34

### 命令行构建
```bash
# 生成 gradle wrapper（首次）
gradle wrapper

# 构建 Debug APK
./gradlew assembleDebug

# 构建 Release APK
./gradlew assembleRelease
```

APK 输出路径：`app/build/outputs/apk/debug/app-debug.apk`

### Android Studio 构建
1. 用 Android Studio 打开 `NcmDumpAndroid` 目录
2. 等待 Gradle 同步完成（自动下载 NDK/CMake）
3. 点击 Build → Build Bundle(s) / APK(s) → Build APK(s)

## 使用

1. 安装 APK 后打开应用
2. 点击「选择 ncm 文件」，从文件管理器中选择 .ncm 文件（可多选）
3. （可选）点击「选择输出目录」指定保存位置
4. 点击「开始解密」，等待进度完成
5. 解密后的 mp3/flac 文件保存在输出目录（默认：`Android/data/com.ncmdump.app/files/`）

## 技术要点

### SAF 路径处理
Android 10+ 采用 Scoped Storage，通过 SAF 选择的文件只有 `content://` Uri，无法直接传给 JNI 层的 `std::ifstream`。解决方案：
- 先将 Uri 对应的文件流拷贝到应用私有缓存目录 `cacheDir`
- 获取缓存文件的绝对路径传给 native 层解密
- 解密完成后删除缓存文件

### 输出目录
- 用户通过 SAF 选择的目录，尝试解析为真实文件路径（primary 存储可直接映射）
- 无法解析时默认输出到 `getExternalFilesDir(null)`

### 已知限制
- 不写入专辑封面和 ID3 元数据（已剥离 taglib）
- 网易云音乐 3.0+ 某些版本下载的 ncm 不内置封面，需网络获取（本工具不处理）
- 超大文件解密在主线程执行可能 ANR（已用子线程处理）

## 版权声明

本工具仅供个人已购买/下载歌曲的本地备份使用。未经授权传播、商用解密后的音频文件，会侵犯音乐作品著作权。
