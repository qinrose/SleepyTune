package com.sleepytune.app.data.datasource

import com.sleepytune.app.data.local.entity.Music

interface LocalMusicDataSource {
    suspend fun scanMusic(): List<Music>   // 可能挂起（使用协程）
}
