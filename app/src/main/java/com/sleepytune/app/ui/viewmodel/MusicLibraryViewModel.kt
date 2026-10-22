package com.sleepytune.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sleepytune.app.data.datasource.LocalMusicDataSource
import com.sleepytune.app.data.local.dao.MusicDao
import com.sleepytune.app.data.local.entity.Music
import com.sleepytune.app.data.repository.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MusicLibraryViewModel @Inject constructor(
    private val musicDataSource: LocalMusicDataSource,
    private val musicDao: MusicDao,
    private val playlistRepository: PlaylistRepository
) : ViewModel() {

    private val _musicList = MutableStateFlow<List<Music>>(emptyList())
    val musicList: StateFlow<List<Music>> = _musicList.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    // 从数据库观察的音乐流（持久化数据）
    val musicFromDb = musicDao.getAllMusic()

    init {
        // 启动时自动加载已保存的音乐
        loadSavedMusic()
    }
    
    /**
     * 从 Room 数据库加载已保存的音乐
     */
    private fun loadSavedMusic() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val count = musicDao.getCount()
                if (count > 0) {
                    // 数据库有数据，直接使用（通过 Flow 自动更新）
                    _isLoading.value = false
                } else {
                    // 数据库为空，需要扫描
                    _isLoading.value = false
                }
            } catch (e: Exception) {
                _isLoading.value = false
            }
        }
    }

    /**
     * 扫描设备音乐并保存到数据库
     */
    fun scanMusic() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // 扫描 MediaStore 并保存到数据库
                val result = playlistRepository.scanMusic()
                _musicList.value = result
                
                android.util.Log.i("MusicLibVM", "扫描完成: ${result.size} 首歌曲")
            } catch (e: Exception) {
                android.util.Log.e("MusicLibVM", "扫描失败: ${e.message}", e)
                _musicList.value = emptyList()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun addToPlaylist(playlistId: Long, musicId: Long) {
        viewModelScope.launch {
            playlistRepository.addSongToPlaylist(playlistId, musicId)
            android.util.Log.i("MusicLibVM", "已添加歌曲 $musicId 到歌单 $playlistId")
        }
    }
}
