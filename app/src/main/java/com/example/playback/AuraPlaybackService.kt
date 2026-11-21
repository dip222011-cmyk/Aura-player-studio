package com.example.playback

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.AuraApplication
import com.example.MainActivity
import com.example.R
import com.example.core.datastore.AuraPreferences
import com.example.data.repository.MediaLibraryRepository
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class AuraPlaybackService : MediaSessionService() {

    @Inject
    lateinit var preferences: AuraPreferences

    @Inject
    lateinit var mediaRepository: MediaLibraryRepository

    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        player = ExoPlayer.Builder(this)
            .setAudioAttributes(audioAttributes, /* handleAudioFocus= */ true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                persistPlaybackSnapshot()
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                persistPlaybackSnapshot()
            }
        })

        val sessionActivityIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityIntent)
            .setCallback(AuraSessionCallback())
            .build()

        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(AuraApplication.CHANNEL_PLAYBACK)
            .setChannelName(R.string.channel_playback_name)
            .build()
        setMediaNotificationProvider(notificationProvider)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val sessionPlayer = mediaSession?.player
        if (sessionPlayer == null || !sessionPlayer.playWhenReady || sessionPlayer.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        persistPlaybackSnapshot()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun persistPlaybackSnapshot() {
        if (!::player.isInitialized) return
        val currentId = player.currentMediaItem?.mediaId?.toLongOrNull() ?: return
        val pos = player.currentPosition.coerceAtLeast(0L)
        val speed = player.playbackParameters.speed
        val repeatMode = player.repeatMode
        val shuffle = player.shuffleModeEnabled
        serviceScope.launch(Dispatchers.IO) {
            preferences.updatePlaybackResume(currentId, pos, speed, repeatMode, shuffle)
        }
    }

    private inner class AuraSessionCallback : MediaSession.Callback {
        @OptIn(UnstableApi::class)
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            isForPlayback: Boolean
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            serviceScope.launch {
                val resume = preferences.resumeStateFlow.first()
                val tracks = mediaRepository.publicAudioItems.first()
                if (tracks.isEmpty()) {
                    future.set(
                        MediaSession.MediaItemsWithStartPosition(emptyList(), 0, 0L)
                    )
                    return@launch
                }
                val startIdx = tracks.indexOfFirst { it.id == resume.mediaId }.coerceAtLeast(0)
                val items = tracks.map { track ->
                    val extras = Bundle().apply {
                        putLong("aura_media_id", track.id)
                    }
                    MediaItem.Builder()
                        .setMediaId(track.id.toString())
                        .setUri(Uri.parse(track.uri))
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(track.title)
                                .setArtist(track.artistOrCreator)
                                .setArtworkUri(track.artworkUri?.let(Uri::parse))
                                .setExtras(extras)
                                .build()
                        )
                        .build()
                }
                player.repeatMode = resume.repeatMode
                player.shuffleModeEnabled = resume.shuffleEnabled
                player.setPlaybackSpeed(resume.speed)
                future.set(
                    MediaSession.MediaItemsWithStartPosition(
                        items,
                        startIdx,
                        resume.positionMs.coerceAtLeast(0L)
                    )
                )
            }
            return future
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> {
            val updated = mediaItems.map { item ->
                if (item.localConfiguration != null) item
                else item.buildUpon().setUri(item.requestMetadata.mediaUri).build()
            }.toMutableList()
            return Futures.immediateFuture(updated)
        }
    }
}
