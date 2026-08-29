package com.example.sleepytune.di

import com.example.sleepytune.data.datasource.LocalMusicDataSource
import com.example.sleepytune.data.local.database.AppDatabase
import com.example.sleepytune.data.local.dao.MusicDao
import com.example.sleepytune.data.repository.AlarmRepository
import com.example.sleepytune.data.repository.AlarmRepositoryImpl
import com.example.sleepytune.data.repository.PlaylistRepository
import com.example.sleepytune.data.repository.PlaylistRepositoryImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    @Provides
    @Singleton
    fun providePlaylistRepository(
        @dagger.hilt.android.qualifiers.ApplicationContext context: android.content.Context,
        appDatabase: AppDatabase,
        dataSource: LocalMusicDataSource
    ): PlaylistRepository = PlaylistRepositoryImpl(
        context = context,
        playlistDao = appDatabase.playlistDao(),
        musicDao = appDatabase.musicDao(),
        alarmDao = appDatabase.alarmDao(),
        musicDataSource = dataSource
    )

    @Provides
    @Singleton
    fun provideAlarmRepository(appDatabase: AppDatabase): AlarmRepository = AlarmRepositoryImpl(
        alarmDao = appDatabase.alarmDao()
    )
    
    @Provides
    @Singleton
    fun provideMusicDao(appDatabase: AppDatabase): MusicDao = appDatabase.musicDao()
}
