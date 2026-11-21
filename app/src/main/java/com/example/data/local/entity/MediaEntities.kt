package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.MediaKind
import com.example.domain.model.TransferDirection
import com.example.domain.model.TransferStatus

@Entity(tableName = "media_items")
data class MediaItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val uri: String,
    val title: String,
    val artistOrCreator: String,
    val kind: String, // MediaKind.name
    val mimeType: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val dateAddedMs: Long,
    val artworkUri: String? = null,
    val isPrivate: Boolean = false,
    val smartTag: String = "Library",
    val isExportedResult: Boolean = false,
    val resolutionLabel: String? = null
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val description: String,
    val createdAtMs: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "mediaId"],
    indices = [Index("mediaId")]
)
data class PlaylistTrackCrossRef(
    val playlistId: Long,
    val mediaId: Long,
    val sortOrder: Int
)

@Entity(tableName = "editor_projects")
data class EditorProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val aspectRatio: String,
    val durationMs: Long,
    val clipCount: Int,
    val thumbnailUri: String? = null,
    val timelineJson: String,
    val createdAtMs: Long,
    val updatedAtMs: Long,
    val autosavedAtMs: Long
)

@Entity(tableName = "transfer_records")
data class TransferRecordEntity(
    @PrimaryKey val id: String,
    val peerName: String,
    val peerDeviceId: String,
    val fileName: String,
    val fileSizeBytes: Long,
    val mimeType: String,
    val direction: String, // TransferDirection.name
    val status: String,    // TransferStatus.name
    val sha256Checksum: String,
    val savedUri: String? = null,
    val encrypted: Boolean = true,
    val timestampMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "paired_devices")
data class PairedDeviceEntity(
    @PrimaryKey val deviceId: String,
    val displayName: String,
    val fingerprint: String,
    val hostAddress: String,
    val port: Int,
    val lastPairedAtMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "download_tasks")
data class DownloadTaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val category: String,
    val mimeType: String,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val status: String, // QUEUED, DOWNLOADING, PAUSED, COMPLETED, CANCELLED
    val localUri: String? = null,
    val createdAtMs: Long = System.currentTimeMillis()
)
