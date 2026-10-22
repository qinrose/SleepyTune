package com.sleepytune.app.service

import android.content.Context
import android.util.Log
import com.sleepytune.app.data.local.entity.Music
import com.sleepytune.app.data.repository.PlaylistRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

// 歌单管理器（加载、切换、恢复）
@Singleton
class PlaylistManager @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val alarmStateManager: AlarmStateManager
) {
    companion object {
        private const val TAG = "PlaylistManager"
    }

    private var musicList: List<Music> = emptyList()
    private var currentIndex: Int = 0
    private var playlistId: Long = 0L
    private var shuffleMode: Boolean = false
    private var onSongChanged: ((Music, Int, Int) -> Unit)? = null

    // 设置歌曲切换回调
    fun setOnSongChanged(callback: (Music, Int, Int) -> Unit) {
        onSongChanged = callback
    }

    // 加载歌单
    suspend fun loadPlaylist(config: AlarmConfig): Boolean {
        playlistId = config.playlistId
        shuffleMode = config.shuffleMode
        
        if (config.ringtonePath != null) {
            // 单铃声模式，不需要歌单
            return false
        }
        
        // 每日随机：解析为当日随机歌单（跨天自动换一批）
        val resolvedId = resolvePlaylistId(playlistId) ?: return false
        if (resolvedId <= 0) return false
        
        val songIds = playlistRepository.getSongsInPlaylist(resolvedId)
        if (songIds.isEmpty()) {
            Log.w(TAG, "歌单为空: playlistId=$playlistId")
            return false
        }
        
        musicList = songIds.mapNotNull { playlistRepository.getMusicById(it) }
        if (musicList.isEmpty()) return false
        
        if (shuffleMode) musicList = musicList.shuffled()
        currentIndex = 0
        
        persistState()
        Log.i(TAG, "加载歌单: ${musicList.size}首, shuffle=$shuffleMode")
        return true
    }

    // 每日随机哨兵 id → 真实歌单 id；普通歌单原样返回
    private suspend fun resolvePlaylistId(id: Long): Long? {
        if (id != com.sleepytune.app.data.local.entity.Alarm.DAILY_RANDOM_PLAYLIST_ID) return id
        return playlistRepository.ensureDailyRandomPlaylistId()
    }

    // 获取当前歌曲
    fun getCurrentSong(): Music? {
        if (musicList.isEmpty() || currentIndex >= musicList.size) return null
        return musicList[currentIndex]
    }

    // 播放下一首
    fun nextSong(): Music? {
        if (musicList.isEmpty()) return null
        currentIndex = (currentIndex + 1) % musicList.size
        persistState()
        notifySongChanged()
        return getCurrentSong()
    }

    // 剔除无法播放的歌曲（按路径匹配），返回下一首可播歌曲；全部失败时返回 null
    fun removeFailedSong(failedPath: String?): Music? {
        if (musicList.isEmpty()) return null
        if (failedPath.isNullOrEmpty()) return nextSong()

        val removedIndex = musicList.indexOfFirst { it.path == failedPath }
        if (removedIndex >= 0) {
            val newList = musicList.toMutableList().apply { removeAt(removedIndex) }
            musicList = newList
            // 移除的歌曲在当前索引之前时，索引需要前移一位，保持指向"下一首"
            if (removedIndex < currentIndex) currentIndex--
            if (musicList.isEmpty()) {
                currentIndex = 0
                Log.w(TAG, "歌单中所有歌曲均已失败，无可用歌曲")
                persistState()
                return null
            }
            currentIndex = currentIndex.coerceIn(0, musicList.size - 1)
            Log.w(TAG, "已剔除失效歌曲: $failedPath")
            persistState()
        }
        return getCurrentSong()
    }

    // 播放上一首
    fun previousSong(): Music? {
        if (musicList.isEmpty()) return null
        currentIndex = if (currentIndex == 0) musicList.size - 1 else currentIndex - 1
        persistState()
        notifySongChanged()
        return getCurrentSong()
    }

    // 从持久化恢复
    fun restoreFromPersistence(context: Context): Boolean {
        val (savedList, savedIndex) = alarmStateManager.loadPlaylist(context)
        if (savedList.isNotEmpty()) {
            musicList = savedList
            currentIndex = savedIndex
            playlistId = alarmStateManager.loadPlaylistId(context)
            Log.i(TAG, "恢复歌单: ${savedList.size}首")
            return true
        }
        return false
    }

    // 从数据库恢复
    suspend fun restoreFromDatabase(): Boolean {
        val resolvedId = resolvePlaylistId(playlistId) ?: return false
        if (resolvedId <= 0) return false
        
        val songIds = playlistRepository.getSongsInPlaylist(resolvedId)
        musicList = songIds.mapNotNull { playlistRepository.getMusicById(it) }
        if (musicList.isNotEmpty()) {
            currentIndex = 0
            persistState()
            Log.i(TAG, "从数据库恢复: ${musicList.size}首")
            return true
        }
        return false
    }

    // 持久化状态
    private fun persistState() {
        // 需要 Context，由调用者处理
    }

    fun persistState(context: Context) {
        alarmStateManager.persistPlaylist(context, musicList, currentIndex)
    }

    // 通知歌曲切换
    private fun notifySongChanged() {
        getCurrentSong()?.let { song ->
            onSongChanged?.invoke(song, currentIndex + 1, musicList.size)
        }
    }

    // 获取歌单信息
    fun getPlaylistInfo(): Pair<Int, Int> = Pair(currentIndex + 1, musicList.size)
    
    fun isEmpty(): Boolean = musicList.isEmpty()
    
    fun getSize(): Int = musicList.size
    
    fun getCurrentIndex(): Int = currentIndex

    // 清空
    fun clear() {
        musicList = emptyList()
        currentIndex = 0
    }
}