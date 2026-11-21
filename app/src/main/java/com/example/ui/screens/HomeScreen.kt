package com.example.ui.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MovieCreation
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.local.entity.EditorProjectEntity
import com.example.domain.model.PlaybackUiState
import com.example.domain.model.SmartAlbumSummary
import com.example.ui.components.GlassStudioCard
import com.example.ui.components.RotatingAlbumArt
import com.example.ui.components.formatTimeMs
import com.example.ui.theme.CreatorAmber
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.LuminousCyan
import com.example.ui.theme.SignalEmerald

@Composable
fun HomeScreen(
    playbackState: PlaybackUiState,
    audioCount: Int,
    videoCount: Int,
    recentProjects: List<EditorProjectEntity>,
    smartAlbums: List<SmartAlbumSummary>,
    onTogglePlayPause: () -> Unit,
    onOpenNowPlaying: () -> Unit,
    onOpenVideoEditor: () -> Unit,
    onOpenProject: (String) -> Unit,
    onOpenShare: () -> Unit,
    onOpenPrivateSpace: () -> Unit,
    onOpenSmartAlbums: () -> Unit,
    onOpenPlaylists: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenMusicLibrary: () -> Unit,
    onOpenVideoLibrary: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("home_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "AURA PLAYER",
                        style = MaterialTheme.typography.headlineLarge,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "Creator Edition • Studio & Offline Share",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LuminousCyan
                    )
                }
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .testTag("home_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Settings,
                        contentDescription = "Settings"
                    )
                }
            }
        }

        // Hero Studio Banner + Rotating Vinyl Player Preview
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 720.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .clickable(onClick = onOpenNowPlaying)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_studio_banner_1795241855159),
                    contentDescription = "Aura Creator Studio Banner",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(188.dp)
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(188.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    Color(0xEE070A12),
                                    Color(0xBB0F1524),
                                    Color(0x66070A12)
                                )
                            )
                        )
                        .padding(18.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "NOW SPINNING",
                                style = MaterialTheme.typography.labelSmall,
                                color = LuminousCyan
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = playbackState.title,
                                style = MaterialTheme.typography.headlineMedium,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = playbackState.artist,
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.78f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Button(
                                    onClick = onTogglePlayPause,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = LuminousCyan,
                                        contentColor = Color(0xFF070A12)
                                    ),
                                    modifier = Modifier.testTag("home_play_pause_button")
                                ) {
                                    Icon(
                                        imageVector = if (playbackState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                        contentDescription = null
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (playbackState.isPlaying) "Pause" else "Play")
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = formatTimeMs(playbackState.positionMs),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        RotatingAlbumArt(
                            artworkUri = playbackState.artworkUri,
                            trackKey = playbackState.currentMediaId,
                            isPlaying = playbackState.isPlaying,
                            size = 122.dp
                        )
                    }
                }
            }
        }

        // Creator Modules Quick Launch Grid
        item {
            Text(
                text = "Creator Modules",
                style = MaterialTheme.typography.titleLarge
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ModuleQuickCard(
                    title = "Pro Video Editor",
                    subtitle = "Multi-track & Media3",
                    icon = Icons.Rounded.MovieCreation,
                    accent = LuminousCyan,
                    onClick = onOpenVideoEditor,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("home_open_editor_card")
                )
                ModuleQuickCard(
                    title = "AURA Share",
                    subtitle = "Encrypted P2P & QR",
                    icon = Icons.Rounded.Share,
                    accent = ElectricViolet,
                    onClick = onOpenShare,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("home_open_share_card")
                )
            }
        }

        // Media Libraries & Vault
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ModuleQuickCard(
                    title = "Music ($audioCount)",
                    subtitle = "Background Engine",
                    icon = Icons.Rounded.MusicNote,
                    accent = SignalEmerald,
                    onClick = onOpenMusicLibrary,
                    modifier = Modifier.weight(1f)
                )
                ModuleQuickCard(
                    title = "Videos ($videoCount)",
                    subtitle = "4K & Exports",
                    icon = Icons.Rounded.VideoLibrary,
                    accent = CreatorAmber,
                    onClick = onOpenVideoLibrary,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Vault, Smart Albums, Playlists, Download Center
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    UtilityChipCard(
                        title = "Private Space",
                        icon = Icons.Rounded.Lock,
                        accent = CreatorAmber,
                        onClick = onOpenPrivateSpace,
                        testTag = "home_private_space_card"
                    )
                }
                item {
                    UtilityChipCard(
                        title = "Smart Albums",
                        icon = Icons.Rounded.AutoAwesome,
                        accent = LuminousCyan,
                        onClick = onOpenSmartAlbums,
                        testTag = "home_smart_albums_card"
                    )
                }
                item {
                    UtilityChipCard(
                        title = "Playlists",
                        icon = Icons.AutoMirrored.Rounded.PlaylistPlay,
                        accent = ElectricViolet,
                        onClick = onOpenPlaylists,
                        testTag = "home_playlists_card"
                    )
                }
                item {
                    UtilityChipCard(
                        title = "Downloads",
                        icon = Icons.Rounded.Download,
                        accent = SignalEmerald,
                        onClick = onOpenDownloads,
                        testTag = "home_downloads_card"
                    )
                }
            }
        }

        // Recent Video Editor Projects
        if (recentProjects.isNotEmpty()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Recent Studio Projects",
                        style = MaterialTheme.typography.titleLarge
                    )
                    Text(
                        text = "View All",
                        style = MaterialTheme.typography.labelLarge,
                        color = LuminousCyan,
                        modifier = Modifier
                            .clickable(onClick = onOpenVideoEditor)
                            .padding(8.dp)
                    )
                }
            }
            items(recentProjects.take(4), key = { it.id }) { project ->
                GlassStudioCard(
                    accentColor = ElectricViolet,
                    onClick = { onOpenProject(project.id) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MovieCreation,
                            contentDescription = null,
                            tint = LuminousCyan,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(project.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${project.aspectRatio.removePrefix("RATIO_").replace("_", ":")} • ${project.clipCount} items • ${formatTimeMs(project.durationMs)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Smart Albums Snapshot
        if (smartAlbums.isNotEmpty()) {
            item {
                Text("Smart Albums", style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(smartAlbums, key = { it.id }) { album ->
                        GlassStudioCard(
                            accentColor = Color(album.accentColorHex),
                            onClick = onOpenSmartAlbums,
                            modifier = Modifier.width(164.dp)
                        ) {
                            Text(
                                album.title,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                album.subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "${album.count} items",
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(album.accentColorHex)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModuleQuickCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    GlassStudioCard(
        accentColor = accent,
        onClick = onClick,
        modifier = modifier
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun UtilityChipCard(
    title: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit,
    testTag: String
) {
    GlassStudioCard(
        accentColor = accent,
        onClick = onClick,
        modifier = Modifier.testTag(testTag)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(title, style = MaterialTheme.typography.labelLarge)
        }
    }
}
