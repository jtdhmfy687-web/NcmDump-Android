package com.ncmdump.app

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 网易云音乐歌词爬取工具
 */
object LyricFetcher {

    private const val SEARCH_URL = "https://music.163.com/api/search/get"
    private const val LYRIC_URL = "https://music.163.com/api/song/lyric"
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36"

    /**
     * 根据歌曲名和艺术家搜索并获取歌词
     * @return LRC 格式歌词，失败返回 null
     */
    fun fetchLyrics(title: String, artist: String): String? {
        return try {
            val songId = searchSong(title, artist) ?: return null
            getLyric(songId)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 搜索歌曲 ID
     */
    private fun searchSong(title: String, artist: String): String? {
        return try {
            val keyword = if (artist.isNotEmpty()) "$title $artist" else title
            val encodedKeyword = URLEncoder.encode(keyword, "UTF-8")
            val url = "$SEARCH_URL?s=$encodedKeyword&type=1&limit=3&offset=0"

            val response = httpGet(url) ?: return null
            val json = JSONObject(response)

            val result = json.optJSONObject("result") ?: return null
            val songs = result.optJSONArray("songs") ?: return null

            if (songs.length() == 0) return null

            // 优先匹配艺术家
            for (i in 0 until songs.length()) {
                val song = songs.getJSONObject(i)
                val songName = song.optString("name", "")
                val artists = song.optJSONArray("artists")
                var artistName = ""
                if (artists != null && artists.length() > 0) {
                    artistName = artists.getJSONObject(0).optString("name", "")
                }
                if (songName.contains(title, ignoreCase = true) ||
                    (artist.isNotEmpty() && artistName.contains(artist, ignoreCase = true))) {
                    return song.optString("id", null)
                }
            }

            // 没有精确匹配就返回第一个
            songs.getJSONObject(0).optString("id", null)
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 获取歌词
     */
    private fun getLyric(songId: String): String? {
        return try {
            val url = "$LYRIC_URL?id=$songId&lv=1&kv=1&tv=-1"
            val response = httpGet(url) ?: return null
            val json = JSONObject(response)

            val lrc = json.optJSONObject("lrc") ?: return null
            val lyric = lrc.optString("lyric", "")

            if (lyric.isEmpty()) null else lyric
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 通用 HTTP GET 请求
     */
    private fun httpGet(urlStr: String): String? {
        return try {
            val url = URL(urlStr)
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Referer", "https://music.163.com/")
            conn.connectTimeout = 8000
            conn.readTimeout = 8000

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
}
