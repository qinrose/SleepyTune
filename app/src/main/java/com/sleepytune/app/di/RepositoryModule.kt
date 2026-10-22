package com.sleepytune.app.di

import com.sleepytune.app.data.datasource.LocalMusicDataSource
import com.sleepytune.app.data.local.database.AppDatabase
import com.sleepytune.app.data.local.dao.MusicDao
import com.sleepytune.app.data.repository.AlarmRepository
import com.sleepytune.app.data.repository.AlarmRepositoryImpl
import com.sleepytune.app.data.repository.PlaylistRepository
import com.sleepytune.app.data.repository.PlaylistRepositoryImpl
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
