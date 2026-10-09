package com.ncmdump.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AlertDialog
import kotlin.concurrent.thread
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object UpdateChecker {

    private const val RELEASE_URL = "https://api.github.com/repos/muling0721/MuLing-Tool/releases/latest"
    private const val DOWNLOAD_URL = "https://github.com/muling0721/MuLing-Tool/releases/latest"

    data class ReleaseInfo(
        val tagName: String,
        val name: String,
        val body: String,
        val htmlUrl: String
    )

    fun checkUpdate(context: Context, showNoUpdate: Boolean = false) {
        thread {
            try {
                val release = fetchLatestRelease()
                if (release != null) {
                    val latestVersion = release.tagName.removePrefix("v")
                    val currentVersion = getCurrentVersion(context)
                    if (isNewerVersion(latestVersion, currentVersion)) {
                        (context as? android.app.Activity)?.runOnUiThread {
                            showUpdateDialog(context, release)
                        }
                    } else if (showNoUpdate) {
                        (context as? android.app.Activity)?.runOnUiThread {
                            AlertDialog.Builder(context)
                                .setTitle("检查更新")
                                .setMessage("当前已是最新版本\n当前版本: v$currentVersion")
                                .setPositiveButton("确定", null)
                                .show()
                        }
                    }
                } else if (showNoUpdate) {
                    (context as? android.app.Activity)?.runOnUiThread {
                        AlertDialog.Builder(context)
                            .setTitle("检查更新")
                            .setMessage("无法获取更新信息，请检查网络")
                            .setPositiveButton("确定", null)
                            .show()
                    }
                }
            } catch (e: Exception) {
                if (showNoUpdate) {
                    (context as? android.app.Activity)?.runOnUiThread {
                        AlertDialog.Builder(context)
                            .setTitle("检查更新")
                            .setMessage("检查更新失败: ${e.message}")
                            .setPositiveButton("确定", null)
                            .show()
                    }
                }
            }
        }
    }

    private fun fetchLatestRelease(): ReleaseInfo? {
        return try {
            val url = URL(RELEASE_URL)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
            conn.setRequestProperty("User-Agent", "Analysis-Tool-App")

            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(response)
                ReleaseInfo(
                    tagName = json.optString("tag_name", ""),
                    name = json.optString("name", ""),
                    body = json.optString("body", ""),
                    htmlUrl = json.optString("html_url", DOWNLOAD_URL)
                )
            } else null
        } catch (e: Exception) {
            null
        }
    }

    private fun showUpdateDialog(context: Context, release: ReleaseInfo) {
        AlertDialog.Builder(context)
            .setTitle("最新版本已发布是否更新")
            .setMessage("当前版本: ${getCurrentVersion(context)}\n最新版本: ${release.tagName}")
            .setPositiveButton("立即更新") { _, _ ->
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(release.htmlUrl))
                context.startActivity(intent)
            }
            .setNegativeButton("暂不更新", null)
            .setCancelable(false)
            .show()
    }

    private fun getCurrentVersion(context: Context): String {
        return try {
            val pInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            pInfo.versionName ?: "1.0"
        } catch (e: Exception) {
            "1.0"
        }
    }

    private fun isNewerVersion(latest: String, current: String): Boolean {
        return try {
            val latestParts = latest.split(".", "-").map { it.toIntOrNull() ?: 0 }
            val currentParts = current.split(".", "-").map { it.toIntOrNull() ?: 0 }
            val maxLen = maxOf(latestParts.size, currentParts.size)
            for (i in 0 until maxLen) {
                val l = latestParts.getOrElse(i) { 0 }
                val c = currentParts.getOrElse(i) { 0 }
                if (l > c) return true
                if (l < c) return false
            }
            false
        } catch (e: Exception) {
            latest != current
        }
    }
}
