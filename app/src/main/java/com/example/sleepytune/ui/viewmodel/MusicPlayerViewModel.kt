package com.example.sleepytune.ui.viewmodel

import androidx.lifecycle.ViewModel
import com.example.sleepytune.service.MusicPlayerManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MusicPlayerViewModel @Inject constructor(
    val musicPlayerManager: MusicPlayerManager
) : ViewModel() {
    override fun onCleared() {
        super.onCleared()
        // 不在这里释放，因为MusicPlayerManager是单例
    }
}
