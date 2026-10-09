package com.ncmdump.app

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

object FileUtils {

    fun getPathFromUri(context: Context, uri: Uri): String? {
        // 先尝试复制到缓存目录
        return try {
            val fileName = getFileName(context, uri)
            val cacheFile = File(context.cacheDir, "ffmpeg_${System.currentTimeMillis()}_$fileName")
            context.contentResolver.openInputStream(uri)?.use { input ->
                cacheFile.outputStream().use { output -> input.copyTo(output) }
            }
            if (cacheFile.exists() && cacheFile.length() > 0) cacheFile.absolutePath else null
        } catch (e: Exception) {
            null
        }
    }

    fun getFileName(context: Context, uri: Uri): String {
        return try {
            var name = ""
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex >= 0) name = cursor.getString(nameIndex)
            }
            name.ifEmpty { uri.lastPathSegment ?: "unknown" }
        } catch (e: Exception) {
            uri.lastPathSegment ?: "unknown"
        }
    }
}
