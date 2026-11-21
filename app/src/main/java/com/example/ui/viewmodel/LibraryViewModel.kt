package com.example.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.datastore.AppSettingsState
import com.example.core.datastore.AuraPreferences
import com.example.core.media.DeviceCapabilityInspector
import com.example.core.media.ExportCapabilities
import com.example.data.local.entity.DownloadTaskEntity
import com.example.data.local.entity.PlaylistEntity
import com.example.data.repository.MediaLibraryRepository
import com.example.data.repository.ShareAndDownloadRepository
import com.example.domain.model.AuraMediaItem
import com.example.domain.model.MediaKind
import com.example.domain.model.PlaybackUiState
import com.example.domain.model.SmartAlbumSummary
import com.example.playback.PlaybackController
import com.example.ui.theme.ThemeMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val mediaRepository: MediaLibraryRepository,
    private val shareAndDownloadRepository: ShareAndDownloadRepository,
    private val playbackController: PlaybackController,
    private val preferences: AuraPreferences,
    private val capabilityInspector: DeviceCapabilityInspector
) : ViewModel() {

    val playbackState: StateFlow<PlaybackUiState> = playbackController.uiState

    val settings: StateFlow<AppSettingsState> = preferences.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettingsState())

    val audioItems: StateFlow<List<AuraMediaItem>> = mediaRepository.publicAudioItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val videoItems: StateFlow<List<AuraMediaItem>> = mediaRepository.publicVideoItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val photoItems: StateFlow<List<AuraMediaItem>> = mediaRepository.publicPhotoItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val allPublicItems: StateFlow<List<AuraMediaItem>> = mediaRepository.allPublicItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val privateItems: StateFlow<List<AuraMediaItem>> = mediaRepository.privateItems
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val isPrivateUnlocked: StateFlow<Boolean> = mediaRepository.privateSpaceUnlocked

    val smartAlbums: StateFlow<List<SmartAlbumSummary>> = mediaRepository.smartAlbums
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val playlists: StateFlow<List<PlaylistEntity>> = mediaRepository.playlists
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val downloads: StateFlow<List<DownloadTaskEntity>> = shareAndDownloadRepository.downloads
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedPlaylistId = MutableStateFlow<Long?>(null)
    val selectedPlaylistId: StateFlow<Long?> = _selectedPlaylistId.asStateFlow()

    val selectedPlaylistTracks: StateFlow<List<AuraMediaItem>> = combine(
        _selectedPlaylistId,
        mediaRepository.allPublicItems
    ) { pid, _ -> pid }
        .let { pidFlow ->
            val out = MutableStateFlow<List<AuraMediaItem>>(emptyList())
            viewModelScope.launch {
                pidFlow.collect { pid ->
                    if (pid == null) {
                        out.value = emptyList()
                    } else {
                        out.value = mediaRepository.observePlaylistTracks(pid).first()
                    }
                }
            }
            out.asStateFlow()
        }

    private val _pinError = MutableStateFlow<String?>(null)
    val pinError: StateFlow<String?> = _pinError.asStateFlow()

    val exportCapabilities: ExportCapabilities by lazy { capabilityInspector.inspect() }

    init {
        playbackController.connectIfNeeded()
        viewModelScope.launch {
            audioItems.collect { tracks ->
                if (tracks.isNotEmpty()) {
                    playbackController.restoreLastStateIfIdle(tracks)
                }
            }
        }
    }

    fun playTrack(track: AuraMediaItem, queue: List<AuraMediaItem> = audioItems.value) {
        val list = if (queue.isEmpty()) listOf(track) else queue
        val idx = list.indexOfFirst { it.id == track.id }.coerceAtLeast(0)
        playbackController.playQueue(list, idx)
    }

    fun togglePlayPause() = playbackController.togglePlayPause()
    fun seekTo(ms: Long) = playbackController.seekTo(ms)
    fun nextTrack() = playbackController.next()
    fun previousTrack() = playbackController.previous()
    fun setSpeed(speed: Float) = playbackController.setPlaybackSpeed(speed)
    fun cycleRepeat() = playbackController.cycleRepeatMode()
    fun toggleShuffle() = playbackController.toggleShuffle()

    fun importMediaUri(uri: Uri, forcedKind: MediaKind? = null, isPrivate: Boolean = false) {
        viewModelScope.launch {
            mediaRepository.importLocalUri(uri, forcedKind, isPrivate)
        }
    }

    fun moveToPrivateSpace(mediaId: Long, makePrivate: Boolean) {
        viewModelScope.launch {
            mediaRepository.moveToPrivateSpace(mediaId, makePrivate)
        }
    }

    fun deleteMedia(mediaId: Long) {
        viewModelScope.launch {
            mediaRepository.deleteMedia(mediaId)
        }
    }

    fun setupPrivatePin(pin: String) {
        viewModelScope.launch {
            val ok = mediaRepository.setupPrivatePin(pin)
            _pinError.value = if (ok) null else "Enter at least 4 digits"
        }
    }

    fun unlockPrivateSpace(pin: String) {
        viewModelScope.launch {
            val ok = mediaRepository.unlockPrivateSpace(pin)
            _pinError.value = if (ok) null else "Incorrect PIN"
        }
    }

    fun lockPrivateSpace() {
        mediaRepository.lockPrivateSpace()
        _pinError.value = null
    }

    fun clearPinError() {
        _pinError.value = null
    }

    fun createPlaylist(name: String, description: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val id = mediaRepository.createPlaylist(name, description)
            _selectedPlaylistId.value = id
        }
    }

    fun selectPlaylist(playlistId: Long?) {
        _selectedPlaylistId.value = playlistId
    }

    fun addTrackToPlaylist(playlistId: Long, mediaId: Long) {
        viewModelScope.launch {
            mediaRepository.addTrackToPlaylist(playlistId, mediaId)
            if (_selectedPlaylistId.value == playlistId) {
                // Trigger refresh by re-emitting
                _selectedPlaylistId.value = null
                _selectedPlaylistId.value = playlistId
            }
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            mediaRepository.deletePlaylist(playlistId)
            if (_selectedPlaylistId.value == playlistId) _selectedPlaylistId.value = null
        }
    }

    fun enqueueSampleDownload(title: String, category: String) {
        viewModelScope.launch {
            val id = UUID.randomUUID().toString()
            val total = 4_200_000L
            val initial = DownloadTaskEntity(
                id = id,
                title = title,
                category = category,
                mimeType = "audio/wav",
                totalBytes = total,
                downloadedBytes = 0L,
                status = "DOWNLOADING"
            )
            shareAndDownloadRepository.upsertDownload(initial)
            for (step in 1..5) {
                delay(280L)
                val current = downloads.value.firstOrNull { it.id == id }
                if (current?.status == "PAUSED" || current?.status == "CANCELLED") return@launch
                shareAndDownloadRepository.upsertDownload(
                    initial.copy(
                        downloadedBytes = (total * step) / 5,
                        status = if (step == 5) "COMPLETED" else "DOWNLOADING"
                    )
                )
            }
        }
    }

    fun togglePauseDownload(task: DownloadTaskEntity) {
        viewModelScope.launch {
            val nextStatus = if (task.status == "PAUSED") "COMPLETED" else "PAUSED"
            val nextBytes = if (nextStatus == "COMPLETED") task.totalBytes else task.downloadedBytes
            shareAndDownloadRepository.upsertDownload(
                task.copy(status = nextStatus, downloadedBytes = nextBytes)
            )
        }
    }

    fun deleteDownload(id: String) {
        viewModelScope.launch {
            shareAndDownloadRepository.deleteDownload(id)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { preferences.setDynamicColor(enabled) }
    }

    fun setAutosaveEditor(enabled: Boolean) {
        viewModelScope.launch { preferences.setAutosaveEditor(enabled) }
    }

    fun setMagneticSnapDefault(enabled: Boolean) {
        viewModelScope.launch { preferences.setMagneticSnapDefault(enabled) }
    }

    fun setDeviceAlias(alias: String) {
        viewModelScope.launch { preferences.setDeviceAlias(alias) }
    }

    fun setShareDownloadFolderUri(uri: String?) {
        viewModelScope.launch { preferences.setShareDownloadFolderUri(uri) }
    }

    fun setPauseOnNoisy(enabled: Boolean) {
        viewModelScope.launch { preferences.setPauseOnNoisy(enabled) }
    }
}
