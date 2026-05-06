package com.example.sleepytune.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.sleepytune.data.local.entity.Music
import com.example.sleepytune.data.local.entity.Playlist
import com.example.sleepytune.data.repository.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val playlistRepository: PlaylistRepository
) : ViewModel() {

    val playlists: StateFlow<List<Playlist>> = playlistRepository.observeAllPlaylists()
        .stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            playlistRepository.createPlaylist(name)
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

}
