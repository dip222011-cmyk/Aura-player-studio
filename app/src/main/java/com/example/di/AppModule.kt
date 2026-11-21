package com.example.di

import android.content.Context
import androidx.room.Room
import com.example.data.local.AuraDatabase
import com.example.data.local.dao.DownloadDao
import com.example.data.local.dao.EditorProjectDao
import com.example.data.local.dao.MediaDao
import com.example.data.local.dao.PlaylistDao
import com.example.data.local.dao.TransferDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AuraDatabase {
        return Room.databaseBuilder(
            context,
            AuraDatabase::class.java,
            "aura_player_creator.db"
        ).fallbackToDestructiveMigration(dropAllTables = false)
            .build()
    }

    @Provides
    fun provideMediaDao(db: AuraDatabase): MediaDao = db.mediaDao()

    @Provides
    fun providePlaylistDao(db: AuraDatabase): PlaylistDao = db.playlistDao()

    @Provides
    fun provideEditorProjectDao(db: AuraDatabase): EditorProjectDao = db.editorProjectDao()

    @Provides
    fun provideTransferDao(db: AuraDatabase): TransferDao = db.transferDao()

    @Provides
    fun provideDownloadDao(db: AuraDatabase): DownloadDao = db.downloadDao()
}
