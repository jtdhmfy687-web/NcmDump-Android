package com.ncmdump.app

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest

/**
 * 哔哩哔哩视频解析工具
 */
object BiliParser {

    private const val UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
    private const val REFERER = "https://www.bilibili.com"

    // wbi 混淆表
    private val MIXIN_KEY_ENC_TAB = intArrayOf(
        46,47,18,2,53,8,23,32,15,50,10,31,58,3,45,35,27,43,5,49,33,9,42,19,29,28,14,39,12,38,41,13,37,48,7,16,24,55,40,61,26,17,0,1,60,51,30,4,22,25,54,21,56,59,6,63,57,62,11,36,20,34,44,52
    )

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

    /**
     * 从输入中提取 BV 号
     */
    fun extractBvid(input: String): String? {
        val bvPattern = Regex("BV[0-9A-Za-z]{10}")
        return bvPattern.find(input)?.value
    }

    /**
     * 获取视频信息
     */
    fun getVideoInfo(bvid: String): VideoInfo? {
        return try {
            val url = "https://api.bilibili.com/x/web-interface/view?bvid=$bvid"
            val response = httpGet(url) ?: return null
            val json = JSONObject(response)

            if (json.optInt("code", -1) != 0) return null

            val data = json.getJSONObject("data")
            val owner = data.getJSONObject("owner")
            val pagesArray = data.getJSONArray("pages")
            val pages = mutableListOf<PageInfo>()

            for (i in 0 until pagesArray.length()) {
                val p = pagesArray.getJSONObject(i)
                pages.add(
                    PageInfo(
                        cid = p.optLong("cid", 0),
                        page = p.optInt("page", 0),
                        part = p.optString("part", ""),
                        duration = p.optInt("duration", 0)
                    )
                )
            }

            VideoInfo(
                bvid = data.optString("bvid", bvid),
                aid = data.optLong("aid", 0),
                title = data.optString("title", ""),
                desc = data.optString("desc", ""),
                owner = owner.optString("name", ""),
                cover = data.optString("pic", ""),
                duration = data.optInt("duration", 0),
                cid = data.optLong("cid", 0),
                pages = pages
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * 获取播放地址（DASH格式，视频和音频分离）
     * 需要 wbi 签名
     */
    fun getPlayUrl(bvid: String, cid: Long, qn: Int = 64): PlayUrl? {
        return try {
            val params = mutableMapOf(
                "bvid" to bvid,
                "cid" to cid.toString(),
                "qn" to qn.toString(),
                "fnval" to "16",
                "fnver" to "0",
                "fourk" to "1"
            )

            val signedParams = signWbi(params)
            val query = signedParams.entries.joinToString("&") {
                "${URLEncoder.encode(it.key, "UTF-8")}=${URLEncoder.encode(it.value, "UTF-8")}"
            }
            val url = "https://api.bilibili.com/x/player/playurl?$query"
            val response = httpGet(url) ?: return null
            val json = JSONObject(response)

            if (json.optInt("code", -1) != 0) return null

            val data = json.getJSONObject("data")
            val dash = data.optJSONObject("dash")

            if (dash != null) {
                // DASH 格式
                val videoArray = dash.optJSONArray("video")
                val audioArray = dash.optJSONArray("audio")

                var videoUrl = ""
                var audioUrl = ""

                if (videoArray != null && videoArray.length() > 0) {
                    // 取第一个（最高清晰度）
                    videoUrl = videoArray.getJSONObject(0).optString("baseUrl", "")
                }
                if (audioArray != null && audioArray.length() > 0) {
                    audioUrl = audioArray.getJSONObject(0).optString("baseUrl", "")
                }

                if (videoUrl.isNotEmpty()) {
                    return PlayUrl(
                        videoUrl = videoUrl,
                        audioUrl = audioUrl,
                        quality = data.optInt("quality", qn),
                        qualityDesc = data.optString("quality_desc", "")
                    )
                }
            }

            // 回退：durl 格式（旧版）
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
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * wbi 签名
     */
    private fun signWbi(params: Map<String, String>): Map<String, String> {
        return try {
            val mixinKey = getMixinKey()
            val wts = (System.currentTimeMillis() / 1000).toString()

            val signedParams = params.toMutableMap()
            signedParams["wts"] = wts

            // 排序并拼接
            val query = signedParams.entries.sortedBy { it.key }.joinToString("&") {
                "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}"
            }

            val wRid = md5(query + mixinKey)
            signedParams["w_rid"] = wRid
            signedParams
        } catch (_: Exception) {
            params
        }
    }

    /**
     * 获取 wbi mixin key
     */
    private fun getMixinKey(): String {
        return try {
            val response = httpGet("https://api.bilibili.com/x/web-interface/nav") ?: return ""
            val json = JSONObject(response)
            val wbiImg = json.optJSONObject("data")?.optJSONObject("wbi_img") ?: return ""

            val imgUrl = wbiImg.optString("img_url", "")
            val subUrl = wbiImg.optString("sub_url", "")

            val imgKey = imgUrl.substringAfterLast("/").substringBefore(".")
            val subKey = subUrl.substringAfterLast("/").substringBefore(".")

            val orig = imgKey + subKey
            val mixinKey = MIXIN_KEY_ENC_TAB.map { orig[it] }.joinToString("").take(32)
            mixinKey
        } catch (_: Exception) {
            ""
        }
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        val digest = md.digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }

    private fun httpGet(urlStr: String): String? {
        return try {
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", UA)
            conn.setRequestProperty("Referer", REFERER)
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            if (conn.responseCode != 200) {
                conn.disconnect()
                return null
            }

            val response = conn.inputStream.bufferedReader().use { it.readText() }
            conn.disconnect()
            response
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 格式化时长（秒 -> mm:ss）
     */
    fun formatDuration(seconds: Int): String {
        val m = seconds / 60
        val s = seconds % 60
        return "%02d:%02d".format(m, s)
    }
}
