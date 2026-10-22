package com.sleepytune.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.sleepytune.app.data.local.entity.Music
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicDao {

    @Query("SELECT * FROM music ORDER BY title ASC")
    fun getAllMusic(): Flow<List<Music>>

    @Query("SELECT * FROM music WHERE id = :id")
    suspend fun getMusicById(id: Long): Music?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(music: List<Music>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(music: Music)

    @Query("DELETE FROM music")
    suspend fun deleteAll()

    @Query("SELECT id FROM music")
    suspend fun getAllIds(): List<Long>

    @Query("DELETE FROM music WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("SELECT COUNT(*) FROM music")
    suspend fun getCount(): Int

    @Query("""
        SELECT m.* FROM music m 
        INNER JOIN playlist_songs ps ON m.id = ps.musicId 
        WHERE ps.playlistId = :playlistId
        ORDER BY m.title ASC
    """)
    fun getSongsInPlaylist(playlistId: Long): Flow<List<Music>>
}
