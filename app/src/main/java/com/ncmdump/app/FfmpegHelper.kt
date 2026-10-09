package com.ncmdump.app

import android.content.Context
import android.net.Uri
import android.os.Environment
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegSession
import java.io.File

object FfmpegHelper {

    // 音频格式列表
    val audioFormats = listOf("mp3", "wav", "aac", "flac", "ogg", "m4a", "opus", "wma")

    // 获取输出目录
    private fun getOutputDir(context: Context): File {
        val dir = File(Environment.getExternalStorageDirectory(), "AI区/音频处理")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    // 音频格式转换
    fun convertAudio(
        context: Context,
        inputUri: Uri,
        outputFormat: String,
        bitrate: String = "192k",
        callback: (Boolean, String?) -> Unit
    ) {
        val inputPath = FileUtils.getPathFromUri(context, inputUri) ?: run {
            callback(false, "无法获取文件路径")
            return
        }
        val inputFile = File(inputPath)
        val outputName = inputFile.nameWithoutExtension + ".$outputFormat"
        val outputFile = File(getOutputDir(context), outputName)

        val cmd = arrayOf(
            "-y", "-i", inputPath,
            "-b:a", bitrate,
            outputFile.absolutePath
        )

        FFmpegKit.executeAsync(cmd.joinToString(" ")) { session ->
            if (session.returnCode.isValueSuccess) {
                callback(true, outputFile.absolutePath)
            } else {
                callback(false, session.failStackTrace)
            }
        }
    }

    // 音频剪辑
    fun trimAudio(
        context: Context,
        inputUri: Uri,
        startTime: String,
        duration: String,
        outputFormat: String = "mp3",
        callback: (Boolean, String?) -> Unit
    ) {
        val inputPath = FileUtils.getPathFromUri(context, inputUri) ?: run {
            callback(false, "无法获取文件路径")
            return
        }
        val inputFile = File(inputPath)
        val outputName = "${inputFile.nameWithoutExtension}_trim.$outputFormat"
        val outputFile = File(getOutputDir(context), outputName)

        val cmd = arrayOf(
            "-y", "-ss", startTime, "-t", duration,
            "-i", inputPath,
            "-acodec", "copy",
            outputFile.absolutePath
        )

        FFmpegKit.executeAsync(cmd.joinToString(" ")) { session ->
            if (session.returnCode.isValueSuccess) {
                callback(true, outputFile.absolutePath)
            } else {
                // copy 失败时重新编码
                val cmd2 = arrayOf(
                    "-y", "-ss", startTime, "-t", duration,
                    "-i", inputPath,
                    outputFile.absolutePath
                )
                FFmpegKit.executeAsync(cmd2.joinToString(" ")) { session2 ->
                    if (session2.returnCode.isValueSuccess) {
                        callback(true, outputFile.absolutePath)
                    } else {
                        callback(false, session2.failStackTrace)
                    }
                }
            }
        }
    }

    // 音频合并
    fun mergeAudio(
        context: Context,
        inputUris: List<Uri>,
        outputFormat: String = "mp3",
        callback: (Boolean, String?) -> Unit
    ) {
        if (inputUris.size < 2) {
            callback(false, "至少需要两个音频文件")
            return
        }

        // 创建文件列表
        val listFile = File(context.cacheDir, "concat_list.txt")
        val sb = StringBuilder()
        val paths = mutableListOf<String>()
        for (uri in inputUris) {
            val path = FileUtils.getPathFromUri(context, uri)
            if (path != null) {
                paths.add(path)
                sb.append("file '").append(path).append("'\n")
            }
        }
        listFile.writeText(sb.toString())

        if (paths.size < 2) {
            callback(false, "无法获取文件路径")
            return
        }

        val outputName = "merged_${System.currentTimeMillis()}.$outputFormat"
        val outputFile = File(getOutputDir(context), outputName)

        val cmd = arrayOf(
            "-y", "-f", "concat", "-safe", "0",
            "-i", listFile.absolutePath,
            "-acodec", "copy",
            outputFile.absolutePath
        )

        FFmpegKit.executeAsync(cmd.joinToString(" ")) { session ->
            listFile.delete()
            if (session.returnCode.isValueSuccess) {
                callback(true, outputFile.absolutePath)
            } else {
                // copy 失败时重新编码
                val cmd2 = arrayOf(
                    "-y", "-f", "concat", "-safe", "0",
                    "-i", listFile.absolutePath,
                    outputFile.absolutePath
                )
                listFile.writeText(sb.toString())
                FFmpegKit.executeAsync(cmd2.joinToString(" ")) { session2 ->
                    listFile.delete()
                    if (session2.returnCode.isValueSuccess) {
                        callback(true, outputFile.absolutePath)
                    } else {
                        callback(false, session2.failStackTrace)
                    }
                }
            }
        }
    }

    // 取消所有任务
    fun cancelAll() {
        FFmpegKit.cancel()
    }
}
