package com.ncmdump.app

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.regex.Pattern

/**
 * 哔哩哔哩视频解析工具
 * playurl API 无需 wbi 签名，带 Referer+Origin 头即可
 */
object BiliParser {

    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/136.0.0.0 Safari/537.36"
    private const val REFERER = "https://www.bilibili.com/"
    private const val ORIGIN = "https://www.bilibili.com"
    private const val VIEW_API = "https://api.bilibili.com/x/web-interface/view?"
    private const val PLAY_API = "https://api.bilibili.com/x/player/playurl?fnval=16&fnver=0&fourk=1"

    private val BV_PATTERN = Pattern.compile("(?i)(BV[0-9A-Za-z]{10})")
    private val AV_PATTERN = Pattern.compile("(?i)(?<![0-9A-Za-z])av(\\d+)(?![0-9A-Za-z])")
    private val B23_PATTERN = Pattern.compile("https?://b23\\.tv/[0-9A-Za-z]+", Pattern.CASE_INSENSITIVE)
    private val BILI_URL_PATTERN = Pattern.compile("https?://(?:www\\.)?bilibili\\.com/video/[^\\s]+", Pattern.CASE_INSENSITIVE)

    var lastError: String = ""
        private set

    data class VideoInfo(
        val bvid: String,
        val aid: Long,
        val title: String,
        val desc: String,
        val owner: String,
        val cover: String,
        val duration: Int,
        val cid: Long,
        val pages: List<PageInfo>
    )

    data class PageInfo(
        val cid: Long,
        val page: Int,
        val part: String,
        val duration: Int
    )

    data class PlayUrl(
        val videoUrl: String,
        val audioUrl: String,
        val quality: Int,
        val qualityDesc: String
    )

    fun extractBvid(input: String): String? {
        val bvMatcher = BV_PATTERN.matcher(input)
        if (bvMatcher.find()) return bvMatcher.group(1)

        val avMatcher = AV_PATTERN.matcher(input)
        if (avMatcher.find()) return "av" + avMatcher.group(1)

        val b23Matcher = B23_PATTERN.matcher(input)
        if (b23Matcher.find()) {
            val redirect = getRedirectUrl(b23Matcher.group())
            extractBvid(redirect)?.let { return it }
        }

        val biliMatcher = BILI_URL_PATTERN.matcher(input)
        if (biliMatcher.find()) return extractBvid(biliMatcher.group())

        return null
    }

    fun getVideoInfo(input: String): VideoInfo? {
        return try {
            val videoId = extractBvid(input) ?: run {
                lastError = "无法识别BV/AV号"
                return null
            }

            val query = if (videoId.startsWith("BV")) {
                "bvid=" + URLEncoder.encode(videoId, "UTF-8")
            } else {
                "aid=" + URLEncoder.encode(videoId.substring(2), "UTF-8")
            }

            val response = httpGet(VIEW_API + query) ?: run {
                lastError = "网络请求失败"
                return null
            }
            val json = JSONObject(response)

            if (json.optInt("code", -1) != 0) {
                lastError = json.optString("message", "code=${json.optInt("code")}")
                return null
            }

            val data = json.optJSONObject("data") ?: run {
                lastError = "无 data 字段"
                return null
            }

            val owner = data.optJSONObject("owner")
            val pagesArray = data.optJSONArray("pages")
            val pages = mutableListOf<PageInfo>()

            if (pagesArray != null) {
                for (i in 0 until pagesArray.length()) {
                    val p = pagesArray.optJSONObject(i) ?: continue
                    pages.add(
                        PageInfo(
                            cid = p.optLong("cid", 0),
                            page = p.optInt("page", 0),
                            part = p.optString("part", ""),
                            duration = p.optInt("duration", 0)
                        )
                    )
                }
            }

            var cover = data.optString("pic", "")
            if (cover.startsWith("http://")) cover = "https://" + cover.substring(7)

            VideoInfo(
                bvid = data.optString("bvid", videoId),
                aid = data.optLong("aid", 0),
                title = data.optString("title", ""),
                desc = data.optString("desc", ""),
                owner = owner?.optString("name", "") ?: "",
                cover = cover,
                duration = data.optInt("duration", 0),
                cid = data.optLong("cid", 0),
                pages = pages
            )
        } catch (e: Exception) {
            lastError = e.message ?: "未知错误"
            e.printStackTrace()
            null
        }
    }

    fun getPlayUrl(bvid: String, cid: Long, qn: Int = 64): PlayUrl? {
        return try {
            val api = "$PLAY_API&bvid=${URLEncoder.encode(bvid, "UTF-8")}&cid=$cid&qn=$qn"
            val response = httpGet(api) ?: run {
                lastError = "播放地址请求失败"
                return null
            }
            val json = JSONObject(response)

            if (json.optInt("code", -1) != 0) {
                lastError = json.optString("message", "playurl code=${json.optInt("code")}")
                return null
            }

            val data = json.optJSONObject("data") ?: return null
            val dash = data.optJSONObject("dash")

            if (dash != null) {
                val videoUrl = pickBestStreamUrl(dash.optJSONArray("video"))
                val audioUrl = pickBestStreamUrl(dash.optJSONArray("audio"))

                if (videoUrl.isNotEmpty()) {
                    return PlayUrl(
                        videoUrl = videoUrl,
                        audioUrl = audioUrl,
                        quality = data.optInt("quality", qn),
                        qualityDesc = data.optString("quality_desc", "")
                    )
                }
            }

            val durl = data.optJSONArray("durl")
            if (durl != null && durl.length() > 0) {
                val videoUrl = durl.getJSONObject(0).optString("url", "")
                if (videoUrl.isNotEmpty()) {
                    return PlayUrl(
                        videoUrl = videoUrl,
                        audioUrl = "",
                        quality = data.optInt("quality", qn),
                        qualityDesc = data.optString("quality_desc", "")
                    )
                }
            }

            lastError = "无可用播放流"
            null
        } catch (e: Exception) {
            lastError = e.message ?: "未知错误"
            e.printStackTrace()
            null
        }
    }

    fun getDirectPlayUrl(bvid: String, cid: Long, qn: Int = 64): PlayUrl? {
        return try {
            val api = "https://api.bilibili.com/x/player/playurl?fnval=0&fnver=0&fourk=1" +
                    "&bvid=${URLEncoder.encode(bvid, "UTF-8")}&cid=$cid&qn=$qn"
            val response = httpGet(api) ?: return null
            val json = JSONObject(response)
            if (json.optInt("code", -1) != 0) return null

            val data = json.optJSONObject("data") ?: return null
            val durl = data.optJSONArray("durl")
            if (durl != null && durl.length() > 0) {
                val videoUrl = durl.getJSONObject(0).optString("url", "")
                if (videoUrl.isNotEmpty()) {
                    return PlayUrl(
                        videoUrl = videoUrl,
                        audioUrl = "",
                        quality = data.optInt("quality", qn),
                        qualityDesc = data.optString("quality_desc", "")
                    )
                }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun pickBestStreamUrl(array: org.json.JSONArray?): String {
        if (array == null || array.length() == 0) return ""

        var bestUrl = ""
        var maxBandwidth = -1

        for (i in 0 until array.length()) {
            val stream = array.optJSONObject(i) ?: continue
            val bandwidth = stream.optInt("bandwidth", 0)
            var url = stream.optString("base_url", "")
            if (url.isEmpty()) url = stream.optString("baseUrl", "")

            if (url.isNotEmpty() && (bestUrl.isEmpty() || bandwidth > maxBandwidth)) {
                bestUrl = url
                maxBandwidth = bandwidth
            }
        }

        if (bestUrl.isEmpty()) {
            for (i in 0 until array.length()) {
                val stream = array.optJSONObject(i) ?: continue
                val backup = stream.optJSONArray("backup_url")
                if (backup != null && backup.length() > 0) return backup.optString(0, "")
            }
        }

        return bestUrl
    }

    private fun getRedirectUrl(urlStr: String): String {
        return try {
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.setRequestProperty("User-Agent", UA)
            val code = conn.responseCode
            if (code == 301 || code == 302 || code == 307 || code == 308) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                location ?: urlStr
            } else {
                conn.disconnect()
                urlStr
            }
        } catch (_: Exception) {
            urlStr
        }
    }

    private fun httpGet(urlStr: String): String? {
        return try {
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 15000
            conn.readTimeout = 20000
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", UA)
            conn.setRequestProperty("Referer", REFERER)
            conn.setRequestProperty("Origin", ORIGIN)
            conn.setRequestProperty("Accept", "application/json, text/plain, */*")

            val code = conn.responseCode
            val inputStream = if (code in 200..399) conn.inputStream else conn.errorStream
            val response = inputStream?.bufferedReader()?.use { it.readText() } ?: ""
            conn.disconnect()
            if (code in 200..399) response else null
        } catch (_: Exception) {
            null
        }
    }

    fun formatDuration(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return "%02d:%02d".format(m, s)
    }
}
