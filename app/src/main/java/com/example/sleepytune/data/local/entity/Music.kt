package com.example.sleepytune.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "music")
data class Music(
    @PrimaryKey val id: Long,           // MediaStore ID (作为主键)
    val title: String,
    val artist: String,
    val album: String = "",
    val duration: Long,     // 毫秒
    val size: Long,         // 字节
    val path: String,
    val mimeType: String,
    val albumArtUri: String? = null
)
