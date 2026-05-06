package com.example.sleepytune.data.local.entity

import androidx.room.TypeConverter
import java.util.*

class Converters {
    @TypeConverter
    fun fromIntSet(days: Set<Int>?): String? {
        return days?.joinToString(",")
    }

    @TypeConverter
    fun toIntSet(value: String?): Set<Int>? {
        return value?.split(",")?.mapNotNull { it.toIntOrNull() }?.toSet()
    }
}
