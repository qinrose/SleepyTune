package com.example.sleepytune.data.datasource

import com.example.sleepytune.data.local.entity.Music

interface LocalMusicDataSource {
    suspend fun scanMusic(): List<Music>   // 可能挂起（使用协程）
}
