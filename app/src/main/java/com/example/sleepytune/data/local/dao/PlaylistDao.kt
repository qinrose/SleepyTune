package com.example.sleepytune.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import com.example.sleepytune.data.local.entity.Playlist
import com.example.sleepytune.data.local.entity.PlaylistSong
import com.example.sleepytune.data.local.entity.PlaylistWithCount
import com.example.sleepytune.data.local.entity.PlaylistWithSongs
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Insert
    suspend fun insertPlaylist(playlist: Playlist): Long

    @Query("SELECT * FROM playlists ORDER BY name ASC")
    fun getAllPlaylists(): Flow<List<Playlist>>

    @Transaction
    @Query(
        "SELECT p.*, (SELECT COUNT(*) FROM playlist_songs ps WHERE ps.playlistId = p.id) AS songCount " +
        "FROM playlists p ORDER BY p.name ASC"
    )
    fun getAllPlaylistsWithCount(): Flow<List<PlaylistWithCount>>

    @Transaction
    @Query("SELECT * FROM playlists WHERE id = :playlistId")
    suspend fun getPlaylistWithSongs(playlistId: Long): PlaylistWithSongs?

    @Insert
    suspend fun addSongToPlaylist(playlistSong: PlaylistSong)

    @Insert
    suspend fun addSongsToPlaylist(playlistSongs: List<PlaylistSong>)

    @Delete
    suspend fun deletePlaylistSong(playlistSong: PlaylistSong)

    @Delete
    suspend fun deletePlaylist(playlist: Playlist)
    
    @Query("UPDATE playlists SET name = :newName WHERE id = :playlistId")
    suspend fun renamePlaylist(playlistId: Long, newName: String)
    
    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun deletePlaylistSongs(playlistId: Long)
}
