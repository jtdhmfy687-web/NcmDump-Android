package com.ncmdump.app

import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.images.ArtworkFactory
import java.io.File

/**
 * 音频元数据写入工具
 * 支持 MP3(ID3v2) 和 FLAC 的标题/艺术家/专辑/封面/歌词写入
 */
object MetadataWriter {

    /**
     * 写入元数据、封面和歌词
     * 所有操作都有独立异常保护，单项失败不影响其他
     */
    fun write(
        audioPath: String,
        title: String,
        artist: String,
        album: String,
        coverBytes: ByteArray?,
        coverMime: String,
        lyrics: String? = null
    ): Boolean {
        var anySuccess = false
        return try {
            val audioFile = AudioFileIO.read(File(audioPath))
            val tag = audioFile.tagOrCreateAndSetDefault

            // 写入文本元数据
            try {
                if (title.isNotEmpty()) tag.setField(FieldKey.TITLE, title)
                if (artist.isNotEmpty()) tag.setField(FieldKey.ARTIST, artist)
                if (album.isNotEmpty()) tag.setField(FieldKey.ALBUM, album)
                anySuccess = true
            } catch (_: Exception) {}

            // 写入封面
            if (coverBytes != null && coverBytes.isNotEmpty()) {
                try {
                    val artwork = ArtworkFactory.getNew()
                    artwork.setBinaryData(coverBytes)
                    artwork.setMimeType(coverMime.ifEmpty { "image/jpeg" })
                    artwork.setPictureType(3) // Front cover
                    tag.deleteArtworkField()
                    tag.addField(artwork)
                    anySuccess = true
                } catch (_: Exception) {}
            }

            // 写入歌词
            if (!lyrics.isNullOrEmpty()) {
                try {
                    tag.setField(FieldKey.LYRICS, lyrics)
                    anySuccess = true
                } catch (_: Exception) {
                    // 某些格式不支持 LYRICS 字段，忽略
                }
            }

            audioFile.commit()
            anySuccess
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
