package com.example.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.core.datastore.AuraPreferences
import com.example.domain.model.AuraMediaItem
import com.example.domain.model.PlaybackUiState
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferences: AuraPreferences
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _uiState = MutableStateFlow(PlaybackUiState())
    val uiState: StateFlow<PlaybackUiState> = _uiState.asStateFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var tickerJob: Job? = null
    private var currentQueue: List<AuraMediaItem> = emptyList()

    fun connectIfNeeded() {
        if (controller != null || controllerFuture != null) return
        val token = SessionToken(context, ComponentName(context, AuraPlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        controllerFuture = future
        future.addListener({
            runCatching {
                val mc = future.get()
                controller = mc
                attachPlayerListener(mc)
                _uiState.update { it.copy(isConnected = true) }
                syncFromController(mc)
            }.onFailure {
                controllerFuture = null
            }
        }, ContextCompat.getMainExecutor(context))
    }

    fun playQueue(tracks: List<AuraMediaItem>, startIndex: Int = 0, startPositionMs: Long = 0L) {
        if (tracks.isEmpty()) return
        currentQueue = tracks
        val safeIndex = startIndex.coerceIn(0, tracks.lastIndex)
        val mc = controller
        if (mc == null) {
            connectIfNeeded()
            scope.launch {
                var attempts = 0
                while (controller == null && attempts < 20) {
                    delay(100)
                    attempts++
                }
                controller?.let { ready ->
                    applyQueueAndPlay(ready, tracks, safeIndex, startPositionMs)
                }
            }
            // Optimistically update UI state while service connects
            val selected = tracks[safeIndex]
            _uiState.update {
                it.copy(
                    currentMediaId = selected.id,
                    title = selected.title,
                    artist = selected.artistOrCreator,
                    artworkUri = selected.artworkUri,
                    durationMs = selected.durationMs,
                    positionMs = startPositionMs.coerceAtLeast(0L),
                    queue = tracks
                )
            }
        } else {
            applyQueueAndPlay(mc, tracks, safeIndex, startPositionMs)
        }
    }

    private fun applyQueueAndPlay(
        mc: MediaController,
        tracks: List<AuraMediaItem>,
        startIndex: Int,
        startPositionMs: Long = 0L
    ) {
        val mediaItems = tracks.map { it.toMedia3Item() }
        val state = _uiState.value
        mc.repeatMode = state.repeatMode
        mc.shuffleModeEnabled = state.shuffleEnabled
        mc.setPlaybackSpeed(state.playbackSpeed.coerceIn(0.5f, 2.0f))
        mc.setMediaItems(mediaItems, startIndex, startPositionMs.coerceAtLeast(0L))
        mc.prepare()
        mc.play()
        syncFromController(mc)
    }

    fun togglePlayPause() {
        val mc = controller
        if (mc == null) {
            if (currentQueue.isNotEmpty()) {
                val idx = currentQueue.indexOfFirst { it.id == _uiState.value.currentMediaId }.coerceAtLeast(0)
                playQueue(currentQueue, idx, startPositionMs = _uiState.value.positionMs)
            } else {
                connectIfNeeded()
            }
            return
        }
        if (mc.isPlaying) {
            mc.pause()
        } else {
            if (mc.playbackState == Player.STATE_IDLE && mc.mediaItemCount > 0) {
                mc.prepare()
            }
            mc.play()
        }
    }

    fun seekTo(positionMs: Long) {
        val clamped = positionMs.coerceAtLeast(0L)
        controller?.seekTo(clamped)
        _uiState.update { it.copy(positionMs = clamped) }
    }

    fun next() {
        val mc = controller
        if (mc == null) {
            if (currentQueue.isNotEmpty()) {
                val idx = currentQueue.indexOfFirst { it.id == _uiState.value.currentMediaId }
                val nextIdx = if (idx >= 0) (idx + 1) % currentQueue.size else 0
                playQueue(currentQueue, nextIdx)
            }
            return
        }
        if (mc.hasNextMediaItem()) {
            mc.seekToNextMediaItem()
        } else if (mc.mediaItemCount > 0) {
            mc.seekToDefaultPosition(0)
        }
        mc.play()
    }

    fun previous() {
        val mc = controller
        if (mc == null) {
            if (currentQueue.isNotEmpty()) {
                val idx = currentQueue.indexOfFirst { it.id == _uiState.value.currentMediaId }.coerceAtLeast(0)
                val prevIdx = if (idx > 0) idx - 1 else currentQueue.lastIndex
                playQueue(currentQueue, prevIdx)
            }
            return
        }
        if (mc.currentPosition > 3_000L) {
            mc.seekTo(0L)
        } else if (mc.hasPreviousMediaItem()) {
            mc.seekToPreviousMediaItem()
        } else if (mc.mediaItemCount > 0) {
            mc.seekToDefaultPosition(mc.mediaItemCount - 1)
        }
        mc.play()
    }

    fun setPlaybackSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 2.0f)
        controller?.setPlaybackSpeed(clamped)
        _uiState.update { it.copy(playbackSpeed = clamped) }
    }

    fun cycleRepeatMode() {
        val currentMode = controller?.repeatMode ?: _uiState.value.repeatMode
        val next = when (currentMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        controller?.repeatMode = next
        _uiState.update { it.copy(repeatMode = next) }
    }

    fun toggleShuffle() {
        val currentShuffle = controller?.shuffleModeEnabled ?: _uiState.value.shuffleEnabled
        val next = !currentShuffle
        controller?.shuffleModeEnabled = next
        _uiState.update { it.copy(shuffleEnabled = next) }
    }

    fun restoreLastStateIfIdle(libraryTracks: List<AuraMediaItem>) {
        if (libraryTracks.isEmpty()) return
        currentQueue = libraryTracks
        scope.launch {
            val resume = preferences.resumeStateFlow.first()
            val mc = controller
            if (mc != null && mc.mediaItemCount == 0 && resume.mediaId > 0L) {
                val idx = libraryTracks.indexOfFirst { it.id == resume.mediaId }.coerceAtLeast(0)
                val items = libraryTracks.map { it.toMedia3Item() }
                mc.setMediaItems(items, idx, resume.positionMs)
                mc.repeatMode = resume.repeatMode
                mc.shuffleModeEnabled = resume.shuffleEnabled
                mc.setPlaybackSpeed(resume.speed)
                mc.prepare()
                syncFromController(mc)
            } else if (_uiState.value.currentMediaId == -1L && libraryTracks.isNotEmpty()) {
                val initial = libraryTracks.firstOrNull { it.id == resume.mediaId } ?: libraryTracks.first()
                _uiState.update {
                    it.copy(
                        currentMediaId = initial.id,
                        title = initial.title,
                        artist = initial.artistOrCreator,
                        artworkUri = initial.artworkUri,
                        durationMs = initial.durationMs,
                        positionMs = if (initial.id == resume.mediaId) resume.positionMs.coerceAtLeast(0L) else 0L,
                        playbackSpeed = resume.speed.coerceIn(0.5f, 2.0f),
                        repeatMode = resume.repeatMode,
                        shuffleEnabled = resume.shuffleEnabled,
                        queue = libraryTracks
                    )
                }
            }
        }
    }

    private fun attachPlayerListener(mc: MediaController) {
        mc.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                syncFromController(mc)
                if (isPlaying) startProgressTicker(mc) else stopProgressTicker()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                syncFromController(mc)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                syncFromController(mc)
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                _uiState.update { it.copy(repeatMode = repeatMode) }
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                _uiState.update { it.copy(shuffleEnabled = shuffleModeEnabled) }
            }
        })
        if (mc.isPlaying) startProgressTicker(mc)
    }

    private fun startProgressTicker(mc: MediaController) {
        tickerJob?.cancel()
        tickerJob = scope.launch {
            while (isActive && mc.isPlaying) {
                val pos = mc.currentPosition.coerceAtLeast(0L)
                val dur = mc.duration.takeIf { it > 0L } ?: _uiState.value.durationMs
                _uiState.update { it.copy(positionMs = pos, durationMs = dur) }
                delay(250L)
            }
        }
    }

    private fun stopProgressTicker() {
        tickerJob?.cancel()
        tickerJob = null
    }

    private fun syncFromController(mc: MediaController) {
        val item = mc.currentMediaItem
        val meta = item?.mediaMetadata
        val mediaId = item?.mediaId?.toLongOrNull() ?: _uiState.value.currentMediaId
        val matchedTrack = currentQueue.firstOrNull { it.id == mediaId }
        val duration = mc.duration.takeIf { it > 0L } ?: matchedTrack?.durationMs ?: _uiState.value.durationMs
        _uiState.update {
            it.copy(
                currentMediaId = mediaId,
                title = meta?.title?.toString() ?: matchedTrack?.title ?: it.title,
                artist = meta?.artist?.toString() ?: matchedTrack?.artistOrCreator ?: it.artist,
                artworkUri = meta?.artworkUri?.toString() ?: matchedTrack?.artworkUri ?: it.artworkUri,
                isPlaying = mc.isPlaying,
                positionMs = mc.currentPosition.coerceAtLeast(0L),
                durationMs = duration,
                playbackSpeed = mc.playbackParameters.speed,
                repeatMode = mc.repeatMode,
                shuffleEnabled = mc.shuffleModeEnabled,
                queue = currentQueue.ifEmpty { it.queue }
            )
        }
    }

    private fun AuraMediaItem.toMedia3Item(): MediaItem {
        val extras = Bundle().apply { putLong("aura_media_id", id) }
        return MediaItem.Builder()
            .setMediaId(id.toString())
            .setUri(Uri.parse(uri))
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist(artistOrCreator)
                    .setArtworkUri(artworkUri?.let(Uri::parse))
                    .setExtras(extras)
                    .build()
            )
            .build()
    }
}
