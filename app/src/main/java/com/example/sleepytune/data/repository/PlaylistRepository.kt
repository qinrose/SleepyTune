package com.example.sleepytune.data.repository

import com.example.sleepytune.data.local.entity.Music
import com.example.sleepytune.data.local.entity.Playlist
import com.example.sleepytune.data.local.entity.PlaylistWithSongs
import kotlinx.coroutines.flow.Flow
import java.io.File

interface PlaylistRepository {
    fun observeAllPlaylists(): Flow<List<Playlist>>
    suspend fun getPlaylistWithSongs(id: Long): PlaylistWithSongs?
    suspend fun createPlaylist(name: String): Long
    suspend fun deletePlaylist(playlist: Playlist)
    suspend fun renamePlaylist(playlistId: Long, newName: String)
    suspend fun addSongToPlaylist(playlistId: Long, musicId: Long)
    suspend fun removeSongFromPlaylist(playlistId: Long, musicId: Long)
    
    // 新增：供AlarmPlaybackService使用
    suspend fun getSongsInPlaylist(playlistId: Long): List<Long>
    suspend fun getMusicById(musicId: Long): Music?
    
    // 响应式歌单歌曲查询（Room Flow JOIN）
    fun observeSongsInPlaylist(playlistId: Long): Flow<List<Music>>
    
    // 音乐库相关
    suspend fun scanMusic(): List<Music>
    suspend fun refreshMusicCache()
}
