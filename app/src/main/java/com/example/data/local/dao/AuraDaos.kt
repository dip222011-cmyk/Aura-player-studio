package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.data.local.entity.DownloadTaskEntity
import com.example.data.local.entity.EditorProjectEntity
import com.example.data.local.entity.MediaItemEntity
import com.example.data.local.entity.PairedDeviceEntity
import com.example.data.local.entity.PlaylistEntity
import com.example.data.local.entity.PlaylistTrackCrossRef
import com.example.data.local.entity.TransferRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {
    @Query("SELECT * FROM media_items WHERE isPrivate = 0 AND kind = :kind ORDER BY dateAddedMs DESC")
    fun observePublicByKind(kind: String): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE isPrivate = 0 ORDER BY dateAddedMs DESC")
    fun observeAllPublic(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE isPrivate = 1 ORDER BY dateAddedMs DESC")
    fun observePrivateItems(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE isPrivate = 0 AND isExportedResult = 1 ORDER BY dateAddedMs DESC")
    fun observePublicExports(): Flow<List<MediaItemEntity>>

    @Query("SELECT * FROM media_items WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): MediaItemEntity?

    @Query("SELECT COUNT(*) FROM media_items")
    suspend fun countAll(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: MediaItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<MediaItemEntity>)

    @Query("UPDATE media_items SET isPrivate = :isPrivate WHERE id = :id")
    suspend fun setPrivateFlag(id: Long, isPrivate: Boolean)

    @Query("DELETE FROM media_items WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface PlaylistDao {
    @Query("SELECT * FROM playlists ORDER BY createdAtMs DESC")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Query(
        """
        SELECT m.* FROM media_items m
        INNER JOIN playlist_tracks pt ON m.id = pt.mediaId
        WHERE pt.playlistId = :playlistId AND m.isPrivate = 0
        ORDER BY pt.sortOrder ASC
        """
    )
    fun observePlaylistTracks(playlistId: Long): Flow<List<MediaItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addTrackToPlaylist(ref: PlaylistTrackCrossRef)

    @Query("DELETE FROM playlist_tracks WHERE playlistId = :playlistId AND mediaId = :mediaId")
    suspend fun removeTrackFromPlaylist(playlistId: Long, mediaId: Long)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Query("SELECT COUNT(*) FROM playlists")
    suspend fun countPlaylists(): Int
}

@Dao
interface EditorProjectDao {
    @Query("SELECT * FROM editor_projects ORDER BY updatedAtMs DESC")
    fun observeAllProjects(): Flow<List<EditorProjectEntity>>

    @Query("SELECT * FROM editor_projects WHERE id = :id LIMIT 1")
    suspend fun getProjectById(id: String): EditorProjectEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProject(project: EditorProjectEntity)

    @Query("DELETE FROM editor_projects WHERE id = :id")
    suspend fun deleteProjectById(id: String)

    @Query("SELECT COUNT(*) FROM editor_projects")
    suspend fun countProjects(): Int
}

@Dao
interface TransferDao {
    @Query("SELECT * FROM transfer_records ORDER BY timestampMs DESC")
    fun observeTransferHistory(): Flow<List<TransferRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTransfer(record: TransferRecordEntity)

    @Query("DELETE FROM transfer_records WHERE id = :id")
    suspend fun deleteTransfer(id: String)

    @Query("DELETE FROM transfer_records")
    suspend fun clearHistory()

    @Query("SELECT * FROM paired_devices ORDER BY lastPairedAtMs DESC")
    fun observePairedDevices(): Flow<List<PairedDeviceEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPairedDevice(device: PairedDeviceEntity)

    @Query("DELETE FROM paired_devices WHERE deviceId = :deviceId")
    suspend fun removePairedDevice(deviceId: String)
}

@Dao
interface DownloadDao {
    @Query("SELECT * FROM download_tasks ORDER BY createdAtMs DESC")
    fun observeDownloads(): Flow<List<DownloadTaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDownload(task: DownloadTaskEntity)

    @Update
    suspend fun updateDownload(task: DownloadTaskEntity)

    @Query("DELETE FROM download_tasks WHERE id = :id")
    suspend fun deleteDownload(id: String)

    @Query("SELECT COUNT(*) FROM download_tasks")
    suspend fun countDownloads(): Int
}
