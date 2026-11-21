package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MovieCreation
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.example.domain.model.AuraMediaItem
import com.example.ui.components.AuraTopHeader
import com.example.ui.components.GlassStudioCard
import com.example.ui.components.StudioActionPill
import com.example.ui.components.formatBytes
import com.example.ui.components.formatTimeMs
import com.example.ui.theme.CreatorAmber
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.LuminousCyan
import com.example.ui.theme.SignalEmerald

@Composable
fun VideoLibraryScreen(
    videos: List<AuraMediaItem>,
    onImportVideo: (Uri) -> Unit,
    onEditInStudio: (AuraMediaItem) -> Unit,
    onShareVideo: (AuraMediaItem) -> Unit,
    onMoveToPrivate: (Long) -> Unit
) {
    var playingVideo by remember { mutableStateOf<AuraMediaItem?>(null) }

    // Google Play Policy compliant zero-permission Android Photo Picker
    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let(onImportVideo)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("video_library_screen")
    ) {
        AuraTopHeader(
            title = "Video Library",
            subtitle = "${videos.size} clips & studio exports",
            trailing = {
                StudioActionPill(
                    label = "Import",
                    icon = Icons.Rounded.Add,
                    accent = CreatorAmber,
                    onClick = {
                        videoPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                        )
                    },
                    testTag = "import_video_button"
                )
            }
        )

        if (videos.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.VideoLibrary,
                        contentDescription = null,
                        tint = CreatorAmber,
                        modifier = Modifier.size(56.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Your Video Library is Empty", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Import local videos via Photo Picker or export a timeline in Pro Video Editor.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            videoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        }
                    ) {
                        Text("Import Video")
                    }
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(videos, key = { it.id }) { video ->
                    GlassStudioCard(
                        accentColor = if (video.isExportedResult) SignalEmerald else CreatorAmber,
                        onClick = { playingVideo = video },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFF0A1120)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.PlayCircle,
                                    contentDescription = "Play video",
                                    tint = LuminousCyan,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    video.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    buildString {
                                        append(formatTimeMs(video.durationMs))
                                        if (!video.resolutionLabel.isNullOrBlank()) {
                                            append(" • ${video.resolutionLabel}")
                                        }
                                        append(" • ${formatBytes(video.sizeBytes)}")
                                    },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (video.isExportedResult) {
                                    Text(
                                        "STUDIO EXPORT",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = SignalEmerald
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onEditInStudio(video) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.MovieCreation, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Edit", maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = { onShareVideo(video) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.Share, null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Share", maxLines = 1)
                            }
                            IconButton(onClick = { onMoveToPrivate(video.id) }) {
                                Icon(
                                    Icons.Rounded.Lock,
                                    contentDescription = "Move to Private Space",
                                    tint = ElectricViolet
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    playingVideo?.let { video ->
        VideoPreviewDialog(
            video = video,
            onDismiss = { playingVideo = null }
        )
    }
}

@Composable
private fun VideoPreviewDialog(
    video: AuraMediaItem,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val player = remember(video.uri) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(video.uri)))
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color(0xFF070A12)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        video.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Rounded.Close, contentDescription = "Close player", tint = Color.White)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            this.player = player
                            useController = true
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(16.dp))
                )
            }
        }
    }
}
