package com.example.sleepytune.data.local.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.sleepytune.data.local.dao.AlarmDao
import com.example.sleepytune.data.local.dao.MusicDao
import com.example.sleepytune.data.local.dao.PlaylistDao
import com.example.sleepytune.data.local.entity.Alarm
import com.example.sleepytune.data.local.entity.Converters
import com.example.sleepytune.data.local.entity.Music
import com.example.sleepytune.data.local.entity.Playlist
import com.example.sleepytune.data.local.entity.PlaylistSong

@Database(
    entities = [Playlist::class, PlaylistSong::class, Alarm::class, Music::class],
    version = 7,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun alarmDao(): AlarmDao
    abstract fun musicDao(): MusicDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE alarms ADD COLUMN volume REAL NOT NULL DEFAULT 1.0")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("""
                    CREATE TABLE IF NOT EXISTS music (
                        id INTEGER PRIMARY KEY NOT NULL,
                        title TEXT NOT NULL,
                        artist TEXT NOT NULL,
                        album TEXT NOT NULL DEFAULT '',
                        duration INTEGER NOT NULL,
                        size INTEGER NOT NULL,
                        path TEXT NOT NULL,
                        mimeType TEXT NOT NULL,
                        albumArtUri TEXT
                    )
                """)
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE alarms ADD COLUMN duration INTEGER NOT NULL DEFAULT 5")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                try { database.execSQL("ALTER TABLE alarms ADD COLUMN vibrate INTEGER NOT NULL DEFAULT 1") } catch (_: Exception) {}
                try { database.execSQL("ALTER TABLE alarms ADD COLUMN snoozeEnabled INTEGER NOT NULL DEFAULT 1") } catch (_: Exception) {}
                try { database.execSQL("ALTER TABLE alarms ADD COLUMN playlistId INTEGER NOT NULL DEFAULT 0") } catch (_: Exception) {}
                try { database.execSQL("ALTER TABLE alarms ADD COLUMN ringtone TEXT") } catch (_: Exception) {}
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE alarms ADD COLUMN shuffleMode INTEGER NOT NULL DEFAULT 0")
                database.execSQL("ALTER TABLE alarms ADD COLUMN fadeInEnabled INTEGER NOT NULL DEFAULT 1")
                database.execSQL("ALTER TABLE alarms ADD COLUMN fadeInDuration INTEGER NOT NULL DEFAULT 30")
            }
        }

        // Migration 6 -> 7: 添加延迟震动字段
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE alarms ADD COLUMN vibrateDelayMinutes INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun getInstance(context: Context): AppDatabase = INSTANCE ?: synchronized(this) {
            Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "alarm.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                .fallbackToDestructiveMigration()
                .build().also { INSTANCE = it }
        }
    }
}
