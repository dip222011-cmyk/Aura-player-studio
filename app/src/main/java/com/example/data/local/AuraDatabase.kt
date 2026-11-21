package com.example.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.example.data.local.dao.DownloadDao
import com.example.data.local.dao.EditorProjectDao
import com.example.data.local.dao.MediaDao
import com.example.data.local.dao.PlaylistDao
import com.example.data.local.dao.TransferDao
import com.example.data.local.entity.DownloadTaskEntity
import com.example.data.local.entity.EditorProjectEntity
import com.example.data.local.entity.MediaItemEntity
import com.example.data.local.entity.PairedDeviceEntity
import com.example.data.local.entity.PlaylistEntity
import com.example.data.local.entity.PlaylistTrackCrossRef
import com.example.data.local.entity.TransferRecordEntity

@Database(
    entities = [
        MediaItemEntity::class,
        PlaylistEntity::class,
        PlaylistTrackCrossRef::class,
        EditorProjectEntity::class,
        TransferRecordEntity::class,
        PairedDeviceEntity::class,
        DownloadTaskEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AuraDatabase : RoomDatabase() {
    abstract fun mediaDao(): MediaDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun editorProjectDao(): EditorProjectDao
    abstract fun transferDao(): TransferDao
    abstract fun downloadDao(): DownloadDao
}
