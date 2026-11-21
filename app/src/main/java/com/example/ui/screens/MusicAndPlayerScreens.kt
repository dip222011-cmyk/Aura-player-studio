package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.example.data.local.entity.PlaylistEntity
import com.example.domain.model.AuraMediaItem
import com.example.domain.model.MediaKind
import com.example.domain.model.PlaybackUiState
import com.example.ui.components.AuraTopHeader
import com.example.ui.components.GlassStudioCard
import com.example.ui.components.RotatingAlbumArt
import com.example.ui.components.StudioActionPill
import com.example.ui.components.formatBytes
import com.example.ui.components.formatTimeMs
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.LuminousCyan
import com.example.ui.theme.SignalEmerald

@Composable
fun MusicLibraryScreen(
    tracks: List<AuraMediaItem>,
    playlists: List<PlaylistEntity>,
    playbackState: PlaybackUiState,
    onPlayTrack: (AuraMediaItem) -> Unit,
    onImportAudio: (Uri) -> Unit,
    onMoveToPrivate: (Long) -> Unit,
    onAddToPlaylist: (Long, Long) -> Unit,
    onShareItem: (AuraMediaItem) -> Unit,
    onOpenNowPlaying: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(tracks, query) {
        if (query.isBlank()) tracks
        else tracks.filter {
            it.title.contains(query, ignoreCase = true) ||
                it.artistOrCreator.contains(query, ignoreCase = true)
        }
    }

    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let(onImportAudio)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("music_library_screen")
    ) {
        AuraTopHeader(
            title = "Music Library",
            subtitle = "${tracks.size} offline tracks • MediaSessionService ready",
            trailing = {
                StudioActionPill(
                    label = "Import",
                    icon = Icons.Rounded.Add,
                    accent = LuminousCyan,
                    onClick = { audioPicker.launch(arrayOf("audio/*")) },
                    testTag = "import_audio_button"
                )
            }
        )

        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search tracks or artists...") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))

        if (filtered.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = LuminousCyan,
                        modifier = Modifier.size(52.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("No audio tracks match your filter", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Import local MP3, WAV, FLAC, or M4A tracks",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filtered, key = { it.id }) { track ->
                    val isCurrent = playbackState.currentMediaId == track.id
                    var showMenu by remember { mutableStateOf(false) }

                    GlassStudioCard(
                        accentColor = if (isCurrent) LuminousCyan else ElectricViolet,
                        onClick = {
                            onPlayTrack(track)
                            onOpenNowPlaying()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("track_card_${track.id}")
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isCurrent) LuminousCyan.copy(alpha = 0.22f)
                                        else MaterialTheme.colorScheme.surface
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isCurrent && playbackState.isPlaying) {
                                        Icons.Rounded.GraphicEq
                                    } else {
                                        Icons.Rounded.MusicNote
                                    },
                                    contentDescription = null,
                                    tint = if (isCurrent) LuminousCyan else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = track.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (isCurrent) LuminousCyan else MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${track.artistOrCreator} • ${formatTimeMs(track.durationMs)} • ${formatBytes(track.sizeBytes)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Box {
                                IconButton(
                                    onClick = { showMenu = true },
                                    modifier = Modifier.minimumInteractiveComponentSize()
                                ) {
                                    Icon(Icons.Rounded.MoreVert, contentDescription = "Track options")
                                }
                                DropdownMenu(
                                    expanded = showMenu,
                                    onDismissRequest = { showMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Send via AURA Share") },
                                        leadingIcon = { Icon(Icons.Rounded.Share, null) },
                                        onClick = {
                                            showMenu = false
                                            onShareItem(track)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Move to Private Space") },
                                        leadingIcon = { Icon(Icons.Rounded.Lock, null) },
                                        onClick = {
                                            showMenu = false
                                            onMoveToPrivate(track.id)
                                        }
                                    )
                                    playlists.forEach { pl ->
                                        DropdownMenuItem(
                                            text = { Text("Add to ${pl.name}") },
                                            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null) },
                                            onClick = {
                                                showMenu = false
                                                onAddToPlaylist(pl.id, track.id)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun NowPlayingScreen(
    playbackState: PlaybackUiState,
    onBack: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekTo: (Long) -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleShuffle: () -> Unit,
    onSetSpeed: (Float) -> Unit
) {
    BackHandler(onBack = onBack)

    val duration = playbackState.durationMs.coerceAtLeast(1_000L)
    var draggingPos by remember(playbackState.currentMediaId) { mutableStateOf<Float?>(null) }
    val sliderPos = draggingPos ?: playbackState.positionMs.toFloat().coerceIn(0f, duration.toFloat())

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF0A1224),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .testTag("now_playing_screen"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AuraTopHeader(
            title = "Now Playing",
            subtitle = if (playbackState.isPlaying) "Background Engine Active" else "Paused",
            onBack = onBack
        )

        Spacer(modifier = Modifier.height(12.dp))

        RotatingAlbumArt(
            artworkUri = playbackState.artworkUri,
            trackKey = playbackState.currentMediaId,
            isPlaying = playbackState.isPlaying,
            size = 248.dp
        )

        Spacer(modifier = Modifier.height(22.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = playbackState.title,
                style = MaterialTheme.typography.headlineMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = playbackState.artist,
                style = MaterialTheme.typography.bodyLarge,
                color = LuminousCyan,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(18.dp))

            Slider(
                value = sliderPos,
                onValueChange = { draggingPos = it },
                onValueChangeFinished = {
                    draggingPos?.let { onSeekTo(it.toLong()) }
                    draggingPos = null
                },
                valueRange = 0f..duration.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = LuminousCyan,
                    activeTrackColor = LuminousCyan
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(formatTimeMs(sliderPos.toLong()), style = MaterialTheme.typography.labelMedium)
                Text(formatTimeMs(duration), style = MaterialTheme.typography.labelMedium)
            }

            Spacer(modifier = Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onToggleShuffle,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (playbackState.shuffleEnabled) LuminousCyan else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onPrevious,
                    modifier = Modifier
                        .size(52.dp)
                        .testTag("now_playing_prev")
                ) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous track", modifier = Modifier.size(34.dp))
                }
                FilledIconButton(
                    onClick = onTogglePlayPause,
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = LuminousCyan,
                        contentColor = Color(0xFF070A12)
                    ),
                    modifier = Modifier
                        .size(72.dp)
                        .testTag("now_playing_play_pause")
                ) {
                    Icon(
                        imageVector = if (playbackState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (playbackState.isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(38.dp)
                    )
                }
                IconButton(
                    onClick = onNext,
                    modifier = Modifier
                        .size(52.dp)
                        .testTag("now_playing_next")
                ) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "Next track", modifier = Modifier.size(34.dp))
                }
                IconButton(
                    onClick = onCycleRepeat,
                    modifier = Modifier.minimumInteractiveComponentSize()
                ) {
                    val icon = if (playbackState.repeatMode == Player.REPEAT_MODE_ONE) {
                        Icons.Rounded.RepeatOne
                    } else Icons.Rounded.Repeat
                    Icon(
                        imageVector = icon,
                        contentDescription = "Repeat mode",
                        tint = if (playbackState.repeatMode != Player.REPEAT_MODE_OFF) {
                            SignalEmerald
                        } else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(0.75f, 1.0f, 1.25f, 1.5f).forEach { spd ->
                    FilterChip(
                        selected = playbackState.playbackSpeed == spd,
                        onClick = { onSetSpeed(spd) },
                        label = { Text("${spd}x", style = MaterialTheme.typography.labelMedium) }
                    )
                }
            }
        }
    }
}
