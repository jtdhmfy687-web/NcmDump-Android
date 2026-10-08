package com.ncmdump.app

import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.ArtworkFactory
import java.io.File

/**
 * 音频元数据写入工具
 * 支持 MP3(ID3v2) 和 FLAC 的标题/艺术家/专辑/封面写入
 */
object MetadataWriter {

    /**
     * 写入元数据和封面
     * @param audioPath  音频文件路径（mp3/flac）
     * @param title      歌曲名
     * @param artist     艺术家
     * @param album      专辑
     * @param coverBytes 封面图片字节，为空则不写封面
     * @param coverMime  封面 MIME 类型（image/png 或 image/jpeg）
     * @return true 成功，false 失败
     */
    fun write(
        audioPath: String,
        title: String,
        artist: String,
        album: String,
        coverBytes: ByteArray?,
        coverMime: String
    ): Boolean {
        return try {
            val audioFile = AudioFileIO.read(File(audioPath))
            val tag = audioFile.tagOrCreateAndSetDefault

            // 写入文本元数据
            if (title.isNotEmpty()) {
                tag.setField(FieldKey.TITLE, title)
            }
            if (artist.isNotEmpty()) {
                tag.setField(FieldKey.ARTIST, artist)
            }
            if (album.isNotEmpty()) {
                tag.setField(FieldKey.ALBUM, album)
            }

            // 写入封面
            if (coverBytes != null && coverBytes.isNotEmpty()) {
                try {
                    val artwork = ArtworkFactory.getNew()
                    artwork.setBinaryData(coverBytes)
                    artwork.setMimeType(coverMime.ifEmpty { "image/jpeg" })
                    artwork.setPictureType(3) // Front cover
                    artwork.setImageFromData()
                    tag.setField(artwork)
                } catch (e: Exception) {
                    // 封面写入失败不影响整体
                }
            }

            audioFile.commit()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
