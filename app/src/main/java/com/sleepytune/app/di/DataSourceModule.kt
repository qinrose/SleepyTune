package com.sleepytune.app.di

import android.content.Context
import com.sleepytune.app.data.datasource.LocalMusicDataSource
import com.sleepytune.app.data.datasource.MediaStoreMusicDataSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataSourceModule {
    @Provides
    @Singleton
    fun provideLocalMusicDataSource(@ApplicationContext context: Context): LocalMusicDataSource = MediaStoreMusicDataSource(context)
}
