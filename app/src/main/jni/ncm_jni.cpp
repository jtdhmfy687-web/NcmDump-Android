#include <jni.h>
#include <string>
#include <stdexcept>
#include "ncmcrypt.h"

extern "C" {

/**
 * 解密单个 ncm 文件
 * @param inputPath  输入 .ncm 文件绝对路径
 * @param outputDir  输出目录（为空则输出到同目录）
 * @return 解密后文件的绝对路径；失败返回空字符串
 */
JNIEXPORT jstring JNICALL
Java_com_ncmdump_app_NcmCrypt_decrypt(JNIEnv *env, jobject thiz,
                                       jstring inputPath, jstring outputDir) {
    const char *input = env->GetStringUTFChars(inputPath, nullptr);
    const char *outDir = outputDir ? env->GetStringUTFChars(outputDir, nullptr) : nullptr;

    std::string result;
    try {
        NeteaseCrypt crypt(input);
        crypt.Dump(outDir ? std::string(outDir) : "");
        crypt.FixMetadata();
        result = crypt.dumpFilepath().string();
    } catch (const std::exception &e) {
        result = "";
    }

    env->ReleaseStringUTFChars(inputPath, input);
    if (outDir) env->ReleaseStringUTFChars(outputDir, outDir);

    return env->NewStringUTF(result.c_str());
}

/**
 * 批量解密
 * @param inputPaths  文件路径数组
 * @param outputDir   输出目录
 * @return 成功解密的文件数
 */
JNIEXPORT jint JNICALL
Java_com_ncmdump_app_NcmCrypt_decryptBatch(JNIEnv *env, jobject thiz,
                                            jobjectArray inputPaths, jstring outputDir) {
    jsize count = env->GetArrayLength(inputPaths);
    const char *outDir = outputDir ? env->GetStringUTFChars(outputDir, nullptr) : nullptr;
    std::string outDirStr = outDir ? std::string(outDir) : "";

    int success = 0;
    for (jsize i = 0; i < count; i++) {
        jstring path = (jstring)env->GetObjectArrayElement(inputPaths, i);
        const char *input = env->GetStringUTFChars(path, nullptr);
        try {
            NeteaseCrypt crypt(input);
            crypt.Dump(outDirStr);
            crypt.FixMetadata();
            success++;
        } catch (...) {
            // 单个失败不中断批量
        }
        env->ReleaseStringUTFChars(path, input);
        env->DeleteLocalRef(path);
    }

    if (outDir) env->ReleaseStringUTFChars(outputDir, outDir);
    return success;
}

/**
 * 获取 ncm 文件的元数据 JSON
 * 包含 name/artist/album/format/duration/bitrate
 */
JNIEXPORT jstring JNICALL
Java_com_ncmdump_app_NcmCrypt_getMetadata(JNIEnv *env, jobject thiz, jstring inputPath) {
    const char *input = env->GetStringUTFChars(inputPath, nullptr);
    std::string result;
    try {
        NeteaseCrypt crypt(input);
        result = crypt.GetMetadataJson();
    } catch (...) {
        result = "";
    }
    env->ReleaseStringUTFChars(inputPath, input);
    return env->NewStringUTF(result.c_str());
}

/**
 * 获取 ncm 文件的封面图片字节数组
 */
JNIEXPORT jbyteArray JNICALL
Java_com_ncmdump_app_NcmCrypt_getCoverImage(JNIEnv *env, jobject thiz, jstring inputPath) {
    const char *input = env->GetStringUTFChars(inputPath, nullptr);
    jbyteArray result = env->NewByteArray(0);
    try {
        NeteaseCrypt crypt(input);
        const std::string& imgData = crypt.GetCoverImage();
        if (!imgData.empty()) {
            result = env->NewByteArray(imgData.length());
            env->SetByteArrayRegion(result, 0, imgData.length(),
                                     reinterpret_cast<const jbyte*>(imgData.data()));
        }
    } catch (...) {
        // 返回空数组
    }
    env->ReleaseStringUTFChars(inputPath, input);
    return result;
}

/**
 * 获取封面 MIME 类型（image/png 或 image/jpeg）
 */
JNIEXPORT jstring JNICALL
Java_com_ncmdump_app_NcmCrypt_getCoverMime(JNIEnv *env, jobject thiz, jstring inputPath) {
    const char *input = env->GetStringUTFChars(inputPath, nullptr);
    std::string result;
    try {
        NeteaseCrypt crypt(input);
        result = crypt.GetCoverMime();
    } catch (...) {
        result = "";
    }
    env->ReleaseStringUTFChars(inputPath, input);
    return env->NewStringUTF(result.c_str());
}

} // extern "C"
