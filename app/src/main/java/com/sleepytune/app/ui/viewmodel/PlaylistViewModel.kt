package com.sleepytune.app.ui.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sleepytune.app.R
import com.sleepytune.app.data.local.entity.Music
import com.sleepytune.app.data.local.entity.Playlist
import com.sleepytune.app.data.repository.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val playlists: StateFlow<List<Playlist>> = playlistRepository.observeAllPlaylists()
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())

    /** 歌单列表（含歌曲数），管理页卡片展示用 */
    val playlistsWithCount: StateFlow<List<com.sleepytune.app.data.local.entity.PlaylistWithCount>> =
        playlistRepository.observeAllPlaylistsWithCount()
            .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // 每日随机歌单
    private val _dailyRandomPlaylistId = MutableStateFlow(0L)
    val dailyRandomPlaylistId: StateFlow<Long> = _dailyRandomPlaylistId.asStateFlow()

    private val _dailyRandomSongCount = MutableStateFlow(0)
    val dailyRandomSongCount: StateFlow<Int> = _dailyRandomSongCount.asStateFlow()

    init {
        // 读取已存在的每日随机歌单 id（用于各列表隐藏它；不存在则保持 0）
        viewModelScope.launch {
            _dailyRandomPlaylistId.value = playlistRepository.getDailyRandomPlaylistId().coerceAtLeast(0L)
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            playlistRepository.createPlaylist(name)
        }
    }

    /**
     * 创建歌单并一次性加入多首歌曲（专辑整辑添加）
     */
    fun createPlaylistWithSongs(name: String, musicIds: List<Long>) {
        viewModelScope.launch {
            playlistRepository.createPlaylistWithSongs(name, musicIds)
        }
    }

    /**
     * 刷新每日随机歌单：首次/跨天自动换一批；force=true 手动换一批
     */
    fun refreshDailyRandomPlaylist(force: Boolean = false) {
        viewModelScope.launch {
            val name = context.getString(R.string.playlist_daily_random)
            val id = playlistRepository.refreshDailyRandomPlaylist(name, DAILY_SONG_COUNT, force)
            if (id != null && id > 0) {
                _dailyRandomPlaylistId.value = id
                val songs = playlistRepository.getSongsInPlaylist(id)
                _dailyRandomSongCount.value = songs.size
            }
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        viewModelScope.launch {
            playlistRepository.deletePlaylist(playlist)
        }
    }
    
    fun renamePlaylist(playlistId: Long, newName: String) {
        viewModelScope.launch {
            playlistRepository.renamePlaylist(playlistId, newName)
            android.util.Log.i("PlaylistVM", "歌单重命名: id=$playlistId, newName=$newName")
        }
    }
    
    fun getPlaylist(playlistId: Long): Flow<Playlist?> {
        return playlists.map { it.find { playlist -> playlist.id == playlistId } }
    }

    fun getPlaylistSongs(playlistId: Long): Flow<List<Music>> {
        _isLoading.value = true
        return playlistRepository.observeSongsInPlaylist(playlistId).map { songs ->
            _isLoading.value = false
            android.util.Log.i("PlaylistVM", "歌单 $playlistId 查询到 ${songs.size} 首歌曲")
            songs
        }
    }

    fun removeSongFromPlaylist(playlistId: Long, songId: Long) {
        viewModelScope.launch {
            playlistRepository.removeSongFromPlaylist(playlistId, songId)
            android.util.Log.i("PlaylistVM", "已从歌单删除歌曲, playlist=$playlistId, song=$songId")
        }
    }

    companion object {
        const val DAILY_SONG_COUNT = 20
    }
}
