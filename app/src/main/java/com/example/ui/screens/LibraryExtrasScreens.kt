package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.core.datastore.AppSettingsState
import com.example.core.media.ExportCapabilities
import com.example.data.local.entity.DownloadTaskEntity
import com.example.data.local.entity.PlaylistEntity
import com.example.domain.model.AuraMediaItem
import com.example.domain.model.MediaKind
import com.example.domain.model.SmartAlbumSummary
import com.example.ui.components.AuraTopHeader
import com.example.ui.components.GlassStudioCard
import com.example.ui.components.StudioActionPill
import com.example.ui.components.formatBytes
import com.example.ui.components.formatTimeMs
import com.example.ui.theme.CreatorAmber
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.LuminousCyan
import com.example.ui.theme.SignalEmerald
import com.example.ui.theme.ThemeMode

@Composable
fun PrivateSpaceScreen(
    hasPin: Boolean,
    isUnlocked: Boolean,
    pinError: String?,
    privateItems: List<AuraMediaItem>,
    onSetupPin: (String) -> Unit,
    onUnlock: (String) -> Unit,
    onLock: () -> Unit,
    onImportPrivateMedia: (Uri) -> Unit,
    onRemoveFromPrivate: (Long) -> Unit,
    onPlayAudio: (AuraMediaItem) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    var pinInput by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let(onImportPrivateMedia) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("private_space_screen")
    ) {
        AuraTopHeader(
            title = "Private Space",
            subtitle = if (isUnlocked) "Vault Unlocked • Isolated from AURA Share" else "PIN-Protected Media Vault",
            onBack = onBack,
            trailing = {
                if (isUnlocked) {
                    IconButton(onClick = onLock, modifier = Modifier.testTag("lock_private_space_button")) {
                        Icon(Icons.Rounded.Lock, contentDescription = "Lock Private Space", tint = CreatorAmber)
                    }
                }
            }
        )

        if (!isUnlocked) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Rounded.Security,
                    contentDescription = null,
                    tint = CreatorAmber,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = if (hasPin) "Enter Vault PIN" else "Create Private Space PIN",
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Files stored here are hidden from libraries and blocked from AURA Share unless explicitly unlocked.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(18.dp))
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = { if (it.length <= 8) pinInput = it.filter(Char::isDigit) },
                    label = { Text("4–8 Digit PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("private_pin_input")
                )
                if (!pinError.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(pinError, color = CrimsonAlert, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = {
                        if (hasPin) onUnlock(pinInput) else onSetupPin(pinInput)
                        pinInput = ""
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("private_pin_submit")
                ) {
                    Icon(Icons.Rounded.LockOpen, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(if (hasPin) "Unlock Vault" else "Set PIN & Enter")
                }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        StudioActionPill(
                            label = "Add Private Photo/Video",
                            icon = Icons.Rounded.Add,
                            accent = CreatorAmber,
                            onClick = {
                                picker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
                if (privateItems.isEmpty()) {
                    item {
                        GlassStudioCard(accentColor = CreatorAmber, modifier = Modifier.fillMaxWidth()) {
                            Text("Private Space is Empty", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Move tracks or videos here from Music/Video libraries, or import directly.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(privateItems, key = { it.id }) { item ->
                        GlassStudioCard(accentColor = CreatorAmber, modifier = Modifier.fillMaxWidth()) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(item.title, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "${item.kind.name} • ${formatBytes(item.sizeBytes)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                if (item.kind == MediaKind.AUDIO) {
                                    IconButton(onClick = { onPlayAudio(item) }) {
                                        Icon(Icons.Rounded.PlayArrow, contentDescription = "Play private audio")
                                    }
                                }
                                TextButton(onClick = { onRemoveFromPrivate(item.id) }) {
                                    Text("Restore")
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
fun SmartAlbumsScreen(
    albums: List<SmartAlbumSummary>,
    allPublicItems: List<AuraMediaItem>,
    onPlayAudio: (AuraMediaItem) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    var selectedAlbumId by remember { mutableStateOf("exports") }

    val matchingItems = remember(selectedAlbumId, allPublicItems) {
        when (selectedAlbumId) {
            "exports" -> allPublicItems.filter { it.isExportedResult }
            "audio" -> allPublicItems.filter { it.kind == MediaKind.AUDIO }
            "cinema" -> allPublicItems.filter { it.kind == MediaKind.VIDEO }
            "voice" -> allPublicItems.filter { it.smartTag.contains("Voice", ignoreCase = true) }
            "received" -> allPublicItems.filter { it.smartTag.contains("Share", ignoreCase = true) }
            else -> allPublicItems
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("smart_albums_screen")
    ) {
        AuraTopHeader("Smart Albums", "Auto-organized studio collections", onBack = onBack)
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(albums, key = { it.id }) { album ->
                val accent = Color(album.accentColorHex)
                GlassStudioCard(
                    accentColor = accent,
                    onClick = { selectedAlbumId = album.id },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(album.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                album.subtitle,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text("${album.count}", style = MaterialTheme.typography.headlineMedium, color = accent)
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(6.dp))
                Text("Items in Selected Album", style = MaterialTheme.typography.titleLarge)
            }

            items(matchingItems, key = { it.id }) { item ->
                GlassStudioCard(accentColor = LuminousCyan, modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${item.kind.name} • ${formatTimeMs(item.durationMs)} • ${item.smartTag}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (item.kind == MediaKind.AUDIO) {
                            IconButton(onClick = { onPlayAudio(item) }) {
                                Icon(Icons.Rounded.PlayArrow, contentDescription = "Play")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlaylistsScreen(
    playlists: List<PlaylistEntity>,
    selectedPlaylistId: Long?,
    selectedTracks: List<AuraMediaItem>,
    onSelectPlaylist: (Long) -> Unit,
    onCreatePlaylist: (String, String) -> Unit,
    onDeletePlaylist: (Long) -> Unit,
    onPlayTrack: (AuraMediaItem, List<AuraMediaItem>) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    var showCreate by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }
    var newDesc by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("playlists_screen")
    ) {
        AuraTopHeader(
            title = "Playlists",
            subtitle = "${playlists.size} collections",
            onBack = onBack,
            trailing = {
                StudioActionPill(
                    label = "New",
                    icon = Icons.Rounded.Add,
                    accent = ElectricViolet,
                    onClick = { showCreate = true }
                )
            }
        )

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(playlists, key = { it.id }) { pl ->
                GlassStudioCard(
                    accentColor = if (selectedPlaylistId == pl.id) LuminousCyan else ElectricViolet,
                    onClick = { onSelectPlaylist(pl.id) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, null, tint = ElectricViolet, modifier = Modifier.size(30.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(pl.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                pl.description.ifBlank { "Custom Aura Playlist" },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { onDeletePlaylist(pl.id) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Delete playlist")
                        }
                    }
                }
            }

            if (selectedPlaylistId != null && selectedTracks.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Playlist Tracks", style = MaterialTheme.typography.titleLarge)
                }
                items(selectedTracks, key = { it.id }) { track ->
                    GlassStudioCard(
                        accentColor = LuminousCyan,
                        onClick = { onPlayTrack(track, selectedTracks) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = LuminousCyan)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(track.title, style = MaterialTheme.typography.titleMedium)
                                Text(track.artistOrCreator, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        AlertDialog(
            onDismissRequest = { showCreate = false },
            title = { Text("Create Playlist") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Playlist Name") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = newDesc,
                        onValueChange = { newDesc = it },
                        label = { Text("Description") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    onCreatePlaylist(newName, newDesc)
                    newName = ""
                    newDesc = ""
                    showCreate = false
                }) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { showCreate = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun DownloadCenterScreen(
    downloads: List<DownloadTaskEntity>,
    onEnqueuePack: (String, String) -> Unit,
    onTogglePause: (DownloadTaskEntity) -> Unit,
    onDelete: (String) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("download_center_screen")
    ) {
        AuraTopHeader(
            title = "Download Center",
            subtitle = "${downloads.size} asset tasks",
            onBack = onBack,
            trailing = {
                StudioActionPill(
                    label = "Get LUT Pack",
                    icon = Icons.Rounded.Download,
                    accent = SignalEmerald,
                    onClick = { onEnqueuePack("Studio Analog Film Pack", "LUT & Stems") }
                )
            }
        )
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(downloads, key = { it.id }) { task ->
                val progress = if (task.totalBytes > 0L) {
                    (task.downloadedBytes.toFloat() / task.totalBytes.toFloat()).coerceIn(0f, 1f)
                } else 0f
                GlassStudioCard(accentColor = SignalEmerald, modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(task.title, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${task.category} • ${task.status} • ${formatBytes(task.totalBytes)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (task.status != "COMPLETED") {
                            IconButton(onClick = { onTogglePause(task) }) {
                                Icon(
                                    if (task.status == "PAUSED") Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                                    contentDescription = "Pause or resume download"
                                )
                            }
                        }
                        IconButton(onClick = { onDelete(task.id) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Remove task")
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(
    settings: AppSettingsState,
    capabilities: ExportCapabilities,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetDynamicColor: (Boolean) -> Unit,
    onSetAutosave: (Boolean) -> Unit,
    onSetMagneticSnap: (Boolean) -> Unit,
    onSetPauseOnNoisy: (Boolean) -> Unit,
    onSetDeviceAlias: (String) -> Unit,
    onSelectShareFolder: (Uri) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    var aliasInput by remember(settings.deviceAlias) { mutableStateOf(settings.deviceAlias) }
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri -> uri?.let(onSelectShareFolder) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("settings_screen")
    ) {
        AuraTopHeader("Settings", "Studio, Audio Engine & AURA Share", onBack = onBack)

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                GlassStudioCard(accentColor = LuminousCyan, modifier = Modifier.fillMaxWidth()) {
                    Text("Appearance", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = settings.themeMode == mode,
                                onClick = { onSetThemeMode(mode) },
                                label = {
                                    Text(
                                        when (mode) {
                                            ThemeMode.STUDIO_DARK -> "Studio Dark"
                                            ThemeMode.SYSTEM -> "System"
                                            ThemeMode.LIGHT -> "Light"
                                        }
                                    )
                                }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Material You Dynamic Color", style = MaterialTheme.typography.bodyLarge)
                        Switch(checked = settings.dynamicColor, onCheckedChange = onSetDynamicColor)
                    }
                }
            }

            item {
                GlassStudioCard(accentColor = ElectricViolet, modifier = Modifier.fillMaxWidth()) {
                    Text("Audio & Video Studio", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Autosave Video Projects", style = MaterialTheme.typography.bodyLarge)
                        Switch(checked = settings.autosaveEditor, onCheckedChange = onSetAutosave)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Magnetic Timeline Snapping", style = MaterialTheme.typography.bodyLarge)
                        Switch(checked = settings.magneticSnapDefault, onCheckedChange = onSetMagneticSnap)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Pause on Headphone Disconnect", style = MaterialTheme.typography.bodyLarge)
                        Switch(checked = settings.pauseOnHeadphoneDisconnect, onCheckedChange = onSetPauseOnNoisy)
                    }
                }
            }

            item {
                GlassStudioCard(accentColor = SignalEmerald, modifier = Modifier.fillMaxWidth()) {
                    Text("AURA Share Identity & Storage", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = aliasInput,
                        onValueChange = { aliasInput = it },
                        label = { Text("Device Broadcast Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onSetDeviceAlias(aliasInput) }) {
                            Text("Save Alias")
                        }
                        OutlinedButton(onClick = { folderPicker.launch(null) }) {
                            Icon(Icons.Rounded.FolderOpen, null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Receive Folder")
                        }
                    }
                    if (!settings.shareDownloadFolderUri.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Folder: ${settings.shareDownloadFolderUri}",
                            style = MaterialTheme.typography.labelSmall,
                            color = SignalEmerald
                        )
                    }
                }
            }

            item {
                GlassStudioCard(accentColor = CreatorAmber, modifier = Modifier.fillMaxWidth()) {
                    Text("Hardware Codec Diagnostics", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(capabilities.encoderSummary, style = MaterialTheme.typography.labelMedium)
                    Text(
                        "H.264: ${if (capabilities.supportsH264) "Supported" else "No"} • " +
                            "H.265: ${if (capabilities.supportsH265) "Supported" else "Unsupported"}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
