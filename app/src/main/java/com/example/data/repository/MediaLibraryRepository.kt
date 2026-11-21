package com.example.data.repository

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import com.example.core.datastore.AuraPreferences
import com.example.core.security.SecurityManager
import com.example.data.local.dao.MediaDao
import com.example.data.local.dao.PlaylistDao
import com.example.data.local.entity.MediaItemEntity
import com.example.data.local.entity.PlaylistEntity
import com.example.data.local.entity.PlaylistTrackCrossRef
import com.example.domain.model.AuraMediaItem
import com.example.domain.model.MediaKind
import com.example.domain.model.SmartAlbumSummary
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaLibraryRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaDao: MediaDao,
    private val playlistDao: PlaylistDao,
    private val securityManager: SecurityManager,
    private val preferences: AuraPreferences
) {
    private val _privateSpaceUnlocked = MutableStateFlow(false)
    val privateSpaceUnlocked: StateFlow<Boolean> = _privateSpaceUnlocked.asStateFlow()

    val publicAudioItems: Flow<List<AuraMediaItem>> =
        mediaDao.observePublicByKind(MediaKind.AUDIO.name).map { it.map(MediaItemEntity::toDomain) }

    val publicVideoItems: Flow<List<AuraMediaItem>> =
        mediaDao.observePublicByKind(MediaKind.VIDEO.name).map { it.map(MediaItemEntity::toDomain) }

    val publicPhotoItems: Flow<List<AuraMediaItem>> =
        mediaDao.observePublicByKind(MediaKind.PHOTO.name).map { it.map(MediaItemEntity::toDomain) }

    val allPublicItems: Flow<List<AuraMediaItem>> =
        mediaDao.observeAllPublic().map { it.map(MediaItemEntity::toDomain) }

    val privateItems: Flow<List<AuraMediaItem>> =
        mediaDao.observePrivateItems().map { it.map(MediaItemEntity::toDomain) }

    val smartAlbums: Flow<List<SmartAlbumSummary>> = allPublicItems.map { items ->
        val exports = items.count { it.isExportedResult }
        val highResAudio = items.count { it.kind == MediaKind.AUDIO }
        val videos = items.count { it.kind == MediaKind.VIDEO }
        val received = items.count { it.smartTag.contains("Share", ignoreCase = true) }
        val voiceOvers = items.count { it.smartTag.contains("Voice", ignoreCase = true) }
        listOf(
            SmartAlbumSummary("exports", "Studio Exports", "Rendered in Media3", exports, 0xFF38BDF8),
            SmartAlbumSummary("audio", "High-Res Audio", "Lossless & Ambient Stems", highResAudio, 0xFFA855F7),
            SmartAlbumSummary("cinema", "Cinema Reels", "Video Clips & Edits", videos, 0xFFF59E0B),
            SmartAlbumSummary("voice", "Voice-Overs", "Studio Mic Captures", voiceOvers, 0xFF10B981),
            SmartAlbumSummary("received", "AURA Share Inbox", "Encrypted Transfers", received, 0xFF06B6D4)
        )
    }

    val playlists: Flow<List<PlaylistEntity>> = playlistDao.observePlaylists()

    fun observePlaylistTracks(playlistId: Long): Flow<List<AuraMediaItem>> =
        playlistDao.observePlaylistTracks(playlistId).map { it.map(MediaItemEntity::toDomain) }

    suspend fun getMediaById(id: Long): AuraMediaItem? = mediaDao.getById(id)?.toDomain()

    suspend fun importLocalUri(
        uri: Uri,
        forcedKind: MediaKind? = null,
        isPrivate: Boolean = false,
        smartTag: String = "Imported",
        isExportedResult: Boolean = false
    ): AuraMediaItem = withContext(Dispatchers.IO) {
        // Persist read permission if supported by SAF/Picker URI
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
        val meta = extractMetadata(uri, forcedKind)
        val entity = MediaItemEntity(
            uri = uri.toString(),
            title = meta.title,
            artistOrCreator = meta.artist,
            kind = meta.kind.name,
            mimeType = meta.mimeType,
            durationMs = meta.durationMs,
            sizeBytes = meta.sizeBytes,
            dateAddedMs = System.currentTimeMillis(),
            artworkUri = if (meta.kind == MediaKind.PHOTO || meta.kind == MediaKind.VIDEO) uri.toString() else null,
            isPrivate = isPrivate,
            smartTag = smartTag,
            isExportedResult = isExportedResult,
            resolutionLabel = meta.resolution
        )
        val id = mediaDao.insert(entity)
        entity.copy(id = id).toDomain()
    }

    suspend fun registerExportedVideo(
        filePath: String,
        title: String,
        durationMs: Long,
        sizeBytes: Long,
        resolutionLabel: String
    ): AuraMediaItem = withContext(Dispatchers.IO) {
        val uriString = Uri.fromFile(java.io.File(filePath)).toString()
        val entity = MediaItemEntity(
            uri = uriString,
            title = title,
            artistOrCreator = "Aura Studio Export",
            kind = MediaKind.VIDEO.name,
            mimeType = "video/mp4",
            durationMs = durationMs,
            sizeBytes = sizeBytes,
            dateAddedMs = System.currentTimeMillis(),
            artworkUri = uriString,
            isPrivate = false,
            smartTag = "Studio Exports",
            isExportedResult = true,
            resolutionLabel = resolutionLabel
        )
        val id = mediaDao.insert(entity)
        entity.copy(id = id).toDomain()
    }

    suspend fun moveToPrivateSpace(mediaId: Long, makePrivate: Boolean) {
        mediaDao.setPrivateFlag(mediaId, makePrivate)
    }

    suspend fun deleteMedia(mediaId: Long) {
        mediaDao.deleteById(mediaId)
    }

    suspend fun setupPrivatePin(pin: String): Boolean {
        if (pin.length < 4) return false
        val salt = securityManager.generateSaltHex()
        val hash = securityManager.hashPin(pin, salt)
        preferences.savePinCredentials(salt, hash)
        _privateSpaceUnlocked.value = true
        return true
    }

    suspend fun unlockPrivateSpace(pin: String): Boolean {
        val creds = preferences.getPinCredentials() ?: return false
        val ok = securityManager.verifyPin(pin, creds.first, creds.second)
        if (ok) _privateSpaceUnlocked.value = true
        return ok
    }

    fun lockPrivateSpace() {
        _privateSpaceUnlocked.value = false
    }

    suspend fun createPlaylist(name: String, description: String): Long {
        return playlistDao.insertPlaylist(
            PlaylistEntity(name = name.trim(), description = description.trim())
        )
    }

    suspend fun addTrackToPlaylist(playlistId: Long, mediaId: Long, sortOrder: Int = 0) {
        playlistDao.addTrackToPlaylist(PlaylistTrackCrossRef(playlistId, mediaId, sortOrder))
    }

    suspend fun removeTrackFromPlaylist(playlistId: Long, mediaId: Long) {
        playlistDao.removeTrackFromPlaylist(playlistId, mediaId)
    }

    suspend fun deletePlaylist(playlistId: Long) {
        playlistDao.deletePlaylist(playlistId)
    }

    private data class ExtractedMeta(
        val title: String,
        val artist: String,
        val kind: MediaKind,
        val mimeType: String,
        val durationMs: Long,
        val sizeBytes: Long,
        val resolution: String?
    )

    private fun extractMetadata(uri: Uri, forcedKind: MediaKind?): ExtractedMeta {
        var displayName = uri.lastPathSegment?.substringAfterLast('/') ?: "Local Media"
        var sizeBytes = 0L
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx >= 0) displayName = cursor.getString(nameIdx) ?: displayName
                    if (sizeIdx >= 0) sizeBytes = cursor.getLong(sizeIdx)
                }
            }
        }
        if (sizeBytes == 0L && uri.scheme == "file") {
            sizeBytes = uri.path?.let { java.io.File(it).length() } ?: 0L
        }

        val mime = context.contentResolver.getType(uri) ?: guessMimeFromName(displayName)
        val kind = forcedKind ?: when {
            mime.startsWith("audio/") -> MediaKind.AUDIO
            mime.startsWith("video/") -> MediaKind.VIDEO
            mime.startsWith("image/") -> MediaKind.PHOTO
            else -> MediaKind.DOCUMENT
        }

        var durationMs = if (kind == MediaKind.PHOTO) 4_000L else 0L
        var artist = "Local Library"
        var title = displayName.substringBeforeLast('.')
        var resolution: String? = null

        if (kind == MediaKind.AUDIO || kind == MediaKind.VIDEO) {
            val retriever = MediaMetadataRetriever()
            runCatching {
                retriever.setDataSource(context, uri)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let {
                    durationMs = it
                }
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }?.let {
                    title = it
                }
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() }?.let {
                    artist = it
                }
                val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
                val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
                if (w != null && h != null) resolution = "${w}x${h}"
            }
            runCatching { retriever.release() }
        }

        return ExtractedMeta(
            title = title.ifBlank { displayName },
            artist = artist,
            kind = kind,
            mimeType = mime,
            durationMs = durationMs.coerceAtLeast(1_000L),
            sizeBytes = sizeBytes,
            resolution = resolution
        )
    }

    private fun guessMimeFromName(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "m4a", "aac" -> "audio/mp4"
            "flac" -> "audio/flac"
            "mp4", "mkv", "mov" -> "video/mp4"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> "application/octet-stream"
        }
    }
}

private fun MediaItemEntity.toDomain(): AuraMediaItem = AuraMediaItem(
    id = id,
    uri = uri,
    title = title,
    artistOrCreator = artistOrCreator,
    kind = runCatching { MediaKind.valueOf(kind) }.getOrDefault(MediaKind.DOCUMENT),
    mimeType = mimeType,
    durationMs = durationMs,
    sizeBytes = sizeBytes,
    dateAddedMs = dateAddedMs,
    artworkUri = artworkUri,
    isPrivate = isPrivate,
    smartTag = smartTag,
    isExportedResult = isExportedResult,
    resolutionLabel = resolutionLabel
)
