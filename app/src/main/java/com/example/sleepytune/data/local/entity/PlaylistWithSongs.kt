package com.example.sleepytune.data.local.entity

import androidx.room.Embedded
import androidx.room.Relation

/**
 * 歌单及其包含的歌曲ID列表
 * 
 * Room @Relation 说明：
 * - parentColumn: 父实体(Playlist)的关联列 → playlists.id
 * - entityColumn: 子实体(PlaylistSong)的外键列 → playlist_songs.playlistId (不是musicId!)
 * - projection: 从子实体中选取的列 → musicId (我们只需要歌曲ID)
 */
data class PlaylistWithSongs(
    @Embedded val playlist: Playlist,
    @Relation(
        parentColumn = "id",           // playlists.id (父表主键)
        entityColumn = "playlistId",   // playlist_songs.playlistId (子表外键) ← 修复！
        entity = PlaylistSong::class,
        projection = ["musicId"]       // 返回 musicId 列表
    )
    val songIds: List<Long>
)
