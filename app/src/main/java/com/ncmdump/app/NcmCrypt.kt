package com.ncmdump.app

/**
 * JNI 绑定类，对应 ncm_jni.cpp 中的 native 方法
 */
class NcmCrypt {

    companion object {
        init {
            System.loadLibrary("ncmdump")
        }

        /**
         * 解密单个 ncm 文件
         * @param inputPath .ncm 文件绝对路径
         * @param outputDir 输出目录，空字符串则输出到同目录
         * @return 解密后文件绝对路径，失败返回空字符串
         */
        @JvmStatic
        external fun decrypt(inputPath: String, outputDir: String): String

        /**
         * 批量解密
         */
        @JvmStatic
        external fun decryptBatch(inputPaths: Array<String>, outputDir: String): Int

        /**
         * 获取 ncm 文件的元数据 JSON
         * 包含 name/artist/album/format/duration/bitrate
         */
        @JvmStatic
        external fun getMetadata(inputPath: String): String

        /**
         * 获取 ncm 文件的封面图片字节数组
         */
        @JvmStatic
        external fun getCoverImage(inputPath: String): ByteArray

        /**
         * 获取封面 MIME 类型（image/png 或 image/jpeg）
         */
        @JvmStatic
        external fun getCoverMime(inputPath: String): String
    }
}
