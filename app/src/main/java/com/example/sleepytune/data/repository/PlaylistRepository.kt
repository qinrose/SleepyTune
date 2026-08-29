package com.example.sleepytune.data.repository

import com.example.sleepytune.data.local.entity.Music
import com.example.sleepytune.data.local.entity.Playlist
import com.example.sleepytune.data.local.entity.PlaylistWithCount
import com.example.sleepytune.data.local.entity.PlaylistWithSongs
import kotlinx.coroutines.flow.Flow
import java.io.File

interface PlaylistRepository {
    fun observeAllPlaylists(): Flow<List<Playlist>>

    /** 歌单列表（含歌曲数），管理页卡片展示用 */
    fun observeAllPlaylistsWithCount(): Flow<List<PlaylistWithCount>>
    suspend fun getPlaylistWithSongs(id: Long): PlaylistWithSongs?
    suspend fun createPlaylist(name: String): Long

    /**
     * 创建歌单并一次性加入多首歌曲（专辑整辑添加）
     */
    suspend fun createPlaylistWithSongs(name: String, musicIds: List<Long>): Long

    /**
     * 刷新"每日随机"歌单：每天自动换一批随机歌曲（当天重复调用直接返回已有歌单）
     * @param force 手动"换一批"时传 true，忽略日期限制
     * @return 歌单 id（失败返回 null）
     */
    suspend fun refreshDailyRandomPlaylist(name: String, songCount: Int = 20, force: Boolean = false): Long?

    /** 读取已存在的每日随机歌单 id（不存在返回 -1） */
    suspend fun getDailyRandomPlaylistId(): Long

    /** 确保每日随机歌单存在（不存在则创建），返回其 id；失败返回 null */
    suspend fun ensureDailyRandomPlaylistId(): Long?

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
