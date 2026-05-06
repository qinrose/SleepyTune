package com.example.sleepytune.data.datasource

import android.content.Context
import android.provider.MediaStore
import com.example.sleepytune.data.local.entity.Music
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject

class MediaStoreMusicDataSource @Inject constructor(
    private val context: Context
) : LocalMusicDataSource {
    
    private val MIN_DURATION_MS = 30000 // 30秒
    private val MIN_SIZE_BYTES = 50000 // 50KB

    override suspend fun scanMusic(): List<Music> = withContext(Dispatchers.IO) {
        val musicList = mutableListOf<Music>()
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.ALBUM_ID
        )
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        // 可选：添加过滤条件（时长、大小）
        val durationFilter = "${MediaStore.Audio.Media.DURATION} >= ?"
        val sizeFilter = "${MediaStore.Audio.Media.SIZE} >= ?"
        // 组合条件，可配置过滤阈值
        val selectionWithFilters = "$selection AND $durationFilter AND $sizeFilter"
        val selectionArgs = arrayOf(MIN_DURATION_MS.toString(), MIN_SIZE_BYTES.toString())

        // 使用try-with-resources确保cursor正确关闭
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selectionWithFilters,
            selectionArgs,
            null
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
            val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
            val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)

            while (cursor.moveToNext()) {
                val albumId = cursor.getLong(albumIdCol)
                val albumArtUri = getAlbumArtUri(albumId)
                
                val music = Music(
                    id = cursor.getLong(idCol),
                    title = cursor.getString(titleCol),
                    artist = cursor.getString(artistCol) ?: "未知艺术家",
                    album = cursor.getString(albumCol) ?: "",
                    duration = cursor.getLong(durationCol),
                    size = cursor.getLong(sizeCol),
                    path = cursor.getString(pathCol),
                    mimeType = cursor.getString(mimeCol) ?: "audio/*",
                    albumArtUri = albumArtUri
                )
                musicList.add(music)
            }
        }
        return@withContext musicList
    }

    private fun getAlbumArtUri(albumId: Long): String? {
        val projection = arrayOf(MediaStore.Audio.Albums.ALBUM_ART)
        val selection = "${MediaStore.Audio.Albums._ID} = ?"
        val selectionArgs = arrayOf(albumId.toString())

        context.contentResolver.query(
            MediaStore.Audio.Albums.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val albumArtCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Albums.ALBUM_ART)
                return cursor.getString(albumArtCol)
            }
        }
        return null
    }
}
