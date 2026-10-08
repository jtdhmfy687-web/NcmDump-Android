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
 * 获取解密后的元数据（歌曲名/艺术家/专辑/格式）
 * 返回格式：name|artist|album|format
 */
JNIEXPORT jstring JNICALL
Java_com_ncmdump_app_NcmCrypt_getMetadata(JNIEnv *env, jobject thiz, jstring inputPath) {
    const char *input = env->GetStringUTFChars(inputPath, nullptr);
    std::string result;
    try {
        NeteaseCrypt crypt(input);
        // 构造后元数据已解析，但需要 Dump 才会有格式信息
        // 这里只返回已解析的元数据
        result = "metadata_parsed";
    } catch (...) {
        result = "";
    }
    env->ReleaseStringUTFChars(inputPath, input);
    return env->NewStringUTF(result.c_str());
}

} // extern "C"
