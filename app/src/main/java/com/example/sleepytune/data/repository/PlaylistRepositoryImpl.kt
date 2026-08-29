package com.example.sleepytune.data.repository

import android.content.Context
import android.util.Log
import com.example.sleepytune.data.datasource.LocalMusicDataSource
import com.example.sleepytune.data.local.dao.AlarmDao
import com.example.sleepytune.data.local.dao.MusicDao
import com.example.sleepytune.data.local.dao.PlaylistDao
import com.example.sleepytune.data.local.entity.Music
import com.example.sleepytune.data.local.entity.Playlist
import com.example.sleepytune.data.local.entity.PlaylistSong
import com.example.sleepytune.data.local.entity.PlaylistWithSongs
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val playlistDao: PlaylistDao,
    private val musicDao: MusicDao,
    private val alarmDao: AlarmDao,
    private val musicDataSource: LocalMusicDataSource
) : PlaylistRepository {

    override fun observeAllPlaylists(): Flow<List<Playlist>> {
        return playlistDao.getAllPlaylists()
    }

    override fun observeAllPlaylistsWithCount(): Flow<List<com.example.sleepytune.data.local.entity.PlaylistWithCount>> {
        return playlistDao.getAllPlaylistsWithCount()
    }

    override suspend fun getPlaylistWithSongs(id: Long): PlaylistWithSongs? {
        return playlistDao.getPlaylistWithSongs(id)
    }

    override suspend fun createPlaylist(name: String): Long {
        val playlist = Playlist(name = name)
        return playlistDao.insertPlaylist(playlist)
    }

    override suspend fun createPlaylistWithSongs(name: String, musicIds: List<Long>): Long {
        return withContext(Dispatchers.IO) {
            try {
                val id = playlistDao.insertPlaylist(Playlist(name = name))
                val distinct = musicIds.distinct()
                if (distinct.isNotEmpty()) {
                    playlistDao.addSongsToPlaylist(distinct.map { PlaylistSong(id, it) })
                }
                Log.i("PlaylistRepo", "已创建歌单 '$name' (id=$id) 并添加 ${distinct.size} 首歌曲")
                id
            } catch (e: Exception) {
                Log.e("PlaylistRepo", "创建歌单 '$name' 失败: ${e.message}", e)
                -1L
            }
        }
    }

    override suspend fun refreshDailyRandomPlaylist(name: String, songCount: Int, force: Boolean): Long? {
        return withContext(Dispatchers.IO) {
            try {
                val prefs = context.getSharedPreferences("playlist_state", Context.MODE_PRIVATE)
                val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                val savedId = prefs.getLong("daily_random_playlist_id", -1L)
                val savedDate = prefs.getString("daily_random_date", "") ?: ""

                // 复用已有歌单（用户删除后自动重建）
                var playlistId =
                    if (savedId > 0 && playlistDao.getPlaylistWithSongs(savedId) != null) savedId else -1L
                if (playlistId <= 0) {
                    playlistId = playlistDao.insertPlaylist(Playlist(name = name))
                    Log.i("PlaylistRepo", "已创建每日随机歌单 id=$playlistId")
                }

                // 今天已生成且非手动刷新 → 直接返回
                if (!force && savedDate == today) return@withContext playlistId

                // 随机挑选歌曲并整批替换
                val allIds = musicDao.getAllIds()
                val picked = allIds.shuffled().take(minOf(songCount, allIds.size))
                playlistDao.deletePlaylistSongs(playlistId)
                if (picked.isNotEmpty()) {
                    playlistDao.addSongsToPlaylist(picked.map { PlaylistSong(playlistId, it) })
                }
                prefs.edit()
                    .putLong("daily_random_playlist_id", playlistId)
                    .putString("daily_random_date", today)
                    .apply()
                Log.i("PlaylistRepo", "每日随机歌单已${if (force) "手动换一批" else "刷新"}: id=$playlistId, ${picked.size}首")
                playlistId
            } catch (e: Exception) {
                Log.e("PlaylistRepo", "刷新每日随机歌单失败: ${e.message}", e)
                null
            }
        }
    }

    override suspend fun getDailyRandomPlaylistId(): Long {
        val prefs = context.getSharedPreferences("playlist_state", Context.MODE_PRIVATE)
        return prefs.getLong("daily_random_playlist_id", -1L)
    }

    override suspend fun ensureDailyRandomPlaylistId(): Long? {
        // 服务端（响铃）不需要本地化名称，用统一名；该歌单在各列表页均被隐藏
        return refreshDailyRandomPlaylist("每日随机")
    }

    override suspend fun deletePlaylist(playlist: Playlist) {
        playlistDao.deletePlaylist(playlist)
        playlistDao.deletePlaylistSongs(playlist.id)
        // 级联：解除所有引用该歌单的闹钟，避免闹钟指向已删除的歌单（触发时静默响默认音）
        val affected = alarmDao.clearPlaylistReference(playlist.id)
        Log.i("PlaylistRepo", "已删除歌单 id=${playlist.id}，并解除引用它的闹钟($affected)")
    }
    
    override suspend fun renamePlaylist(playlistId: Long, newName: String) {
        withContext(Dispatchers.IO) {
            try {
                playlistDao.renamePlaylist(playlistId, newName)
                Log.i("PlaylistRepo", "歌单重命名成功: id=$playlistId, newName=$newName")
            } catch (e: Exception) {
                Log.e("PlaylistRepo", "歌单重命名失败: ${e.message}", e)
            }
        }
    }

    override suspend fun addSongToPlaylist(playlistId: Long, musicId: Long) {
        val song = PlaylistSong(playlistId, musicId)
        try {
            playlistDao.addSongToPlaylist(song)
            Log.i("PlaylistRepo", "已添加歌曲 $musicId 到歌单 $playlistId")
        } catch (e: Exception) {
            Log.w("PlaylistRepo", "歌曲已存在于歌单中: $musicId")
        }
    }

    override suspend fun removeSongFromPlaylist(playlistId: Long, musicId: Long) {
        val song = PlaylistSong(playlistId, musicId)
        playlistDao.deletePlaylistSong(song)
    }
    
    // 音乐持久化核心方法
    
    override suspend fun getSongsInPlaylist(playlistId: Long): List<Long> {
        return withContext(Dispatchers.IO) {
            try {
                val playlist = playlistDao.getPlaylistWithSongs(playlistId)
                playlist?.songIds ?: emptyList()
            } catch (e: Exception) {
                Log.e("PlaylistRepo", "获取歌单歌曲失败: ${e.message}", e)
                emptyList()
            }
        }
    }
    
    override suspend fun getMusicById(musicId: Long): Music? {
        return withContext(Dispatchers.IO) {
            try {
                musicDao.getMusicById(musicId)
            } catch (e: Exception) {
                Log.e("PlaylistRepo", "获取音乐失败: ${e.message}", e)
                null
            }
        }
    }

    override fun observeSongsInPlaylist(playlistId: Long): Flow<List<Music>> {
        return musicDao.getSongsInPlaylist(playlistId)
    }

    /**
     * 扫描音乐并保存到数据库
     * 流程：MediaStore扫描 → 保存/更新到Room → 返回数据库中的完整列表
     */
    override suspend fun scanMusic(): List<Music> {
        return withContext(Dispatchers.IO) {
            try {
                // 1. 从 MediaStore 扫描设备中的音乐文件
                val scannedMusic = musicDataSource.scanMusic()
                Log.i("PlaylistRepo", "从MediaStore扫描到 ${scannedMusic.size} 首音乐")
                
                if (scannedMusic.isNotEmpty()) {
                    // 2. 批量保存到 Room 数据库（REPLACE模式，自动更新已存在的记录）
                    musicDao.insertAll(scannedMusic)
                    Log.i("PlaylistRepo", "已保存 ${scannedMusic.size} 首音乐到数据库")
                    
                    // 2.1 清理僵尸记录：数据库中已不在 MediaStore 的条目（文件被删除/移动/卸载）
                    val scannedIds = scannedMusic.map { it.id }.toSet()
                    val staleIds = musicDao.getAllIds().filter { it !in scannedIds }
                    staleIds.chunked(500).forEach { chunk -> musicDao.deleteByIds(chunk) }
                    if (staleIds.isNotEmpty()) {
                        Log.i("PlaylistRepo", "已清理 ${staleIds.size} 条失效音乐记录")
                    }
                }
                
                // 3. 从数据库返回完整的音乐列表
                val count = musicDao.getCount()
                Log.i("PlaylistRepo", "数据库中共有 $count 首音乐")
                
                // 由于 getAllMusic() 返回的是 Flow，这里需要同步查询
                // 但为了兼容性，直接返回扫描结果（已保存到DB）
                scannedMusic
                
            } catch (e: Exception) {
                Log.e("PlaylistRepo", "扫描音乐失败: ${e.message}", e)
                emptyList()
            }
        }
    }

    override suspend fun refreshMusicCache() {
        scanMusic()
    }
}
