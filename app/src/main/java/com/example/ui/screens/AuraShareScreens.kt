package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QrCode2
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.WifiFind
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.example.data.local.entity.TransferRecordEntity
import com.example.domain.model.ActiveTransferState
import com.example.domain.model.DiscoveredPeer
import com.example.domain.model.IncomingTransferPrompt
import com.example.domain.model.SelectedShareFile
import com.example.domain.model.TransferDirection
import com.example.domain.model.TransferStatus
import com.example.ui.components.AuraTopHeader
import com.example.ui.components.GlassStudioCard
import com.example.ui.components.StudioActionPill
import com.example.ui.components.formatBytes
import com.example.ui.theme.CreatorAmber
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.LuminousCyan
import com.example.ui.theme.SignalEmerald
import com.example.ui.viewmodel.AuraShareViewModel
import java.text.DateFormat
import java.util.Date

@Composable
fun AuraShareScreen(
    viewModel: AuraShareViewModel,
    selectedFiles: List<SelectedShareFile>,
    selectedPeer: DiscoveredPeer?,
    qrBitmap: android.graphics.Bitmap?,
    pairingUriText: String,
    pairingPinDisplay: String,
    serverPort: Int,
    shareError: String?,
    onOpenDiscovery: () -> Unit,
    onOpenTransferScreen: () -> Unit,
    onOpenHistory: () -> Unit
) {
    val clipboard = LocalClipboardManager.current

    val mediaPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.addSelectedUris(uris)
    }

    val docPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.addSelectedUris(uris)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("aura_share_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            AuraTopHeader(
                title = "AURA Share",
                subtitle = "Offline AES-256-GCM Encrypted Transfer",
                trailing = {
                    IconButton(onClick = onOpenHistory, modifier = Modifier.testTag("open_transfer_history_btn")) {
                        Icon(Icons.Rounded.History, contentDescription = "Transfer History")
                    }
                }
            )
        }

        // Security Guarantee Banner
        item {
            GlassStudioCard(accentColor = SignalEmerald, modifier = Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Security, null, tint = SignalEmerald)
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("Zero-Cloud Local Vault Security", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Explicit receiver consent, SHA-256 verification, and Private Space isolation enabled.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Send Queue & File Pickers
        item {
            GlassStudioCard(accentColor = LuminousCyan, modifier = Modifier.fillMaxWidth()) {
                Text("1. Select Files to Send (${selectedFiles.size})", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StudioActionPill(
                        label = "Photos/Videos",
                        icon = Icons.Rounded.Add,
                        accent = LuminousCyan,
                        onClick = {
                            mediaPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                            )
                        },
                        modifier = Modifier.weight(1f),
                        testTag = "share_pick_media_btn"
                    )
                    StudioActionPill(
                        label = "Music/Docs",
                        icon = Icons.Rounded.FolderOpen,
                        accent = ElectricViolet,
                        onClick = { docPicker.launch(arrayOf("*/*")) },
                        modifier = Modifier.weight(1f),
                        testTag = "share_pick_docs_btn"
                    )
                }

                if (selectedFiles.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(10.dp))
                    selectedFiles.forEach { f ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${f.name} (${formatBytes(f.sizeBytes)})",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { viewModel.removeSelectedFile(f.uri) }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Remove file", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }

        // Discovery & Send Action
        item {
            GlassStudioCard(accentColor = ElectricViolet, modifier = Modifier.fillMaxWidth()) {
                Text("2. Pair & Send to Device", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = selectedPeer?.let { "Target: ${it.displayName} (${it.hostAddress}:${it.port})" }
                        ?: "No receiving device paired yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (selectedPeer != null) LuminousCyan else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = onOpenDiscovery,
                        modifier = Modifier.weight(1f).testTag("open_discovery_btn")
                    ) {
                        Icon(Icons.Rounded.Devices, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Discover / Pair")
                    }
                    Button(
                        onClick = {
                            viewModel.sendSelectedFiles()
                            onOpenTransferScreen()
                        },
                        enabled = selectedFiles.isNotEmpty() && selectedPeer != null,
                        modifier = Modifier.weight(1f).testTag("share_send_now_btn")
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Send, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Send Encrypted")
                    }
                }
            }
        }

        // Receive Mode & QR Pairing Card
        item {
            GlassStudioCard(accentColor = CreatorAmber, modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Receive Files (QR & Local Wi-Fi)", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (serverPort > 0) "Listening on port $serverPort • PIN: $pairingPinDisplay"
                            else "Start Receive Mode to generate QR pairing code",
                            style = MaterialTheme.typography.labelMedium,
                            color = CreatorAmber
                        )
                    }
                    IconButton(onClick = viewModel::regeneratePairingQr) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Regenerate QR")
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                if (serverPort == 0) {
                    Button(
                        onClick = viewModel::startReceiveMode,
                        modifier = Modifier.fillMaxWidth().testTag("start_receive_mode_btn")
                    ) {
                        Icon(Icons.Rounded.QrCode2, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Enable Receive Mode & Show QR")
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        qrBitmap?.let { bmp ->
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "AURA Share QR pairing code",
                                modifier = Modifier
                                    .size(184.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.White)
                                    .padding(8.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { clipboard.setText(AnnotatedString(pairingUriText)) },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Copy Pairing URI")
                            }
                            OutlinedButton(
                                onClick = viewModel::stopReceiveMode,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Stop Listening")
                            }
                        }
                    }
                }
            }
        }

        shareError?.let { err ->
            item {
                GlassStudioCard(accentColor = CrimsonAlert, modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Lock, null, tint = CrimsonAlert)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(err, color = CrimsonAlert, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
fun DeviceDiscoveryScreen(
    viewModel: AuraShareViewModel,
    peers: List<DiscoveredPeer>,
    isDiscovering: Boolean,
    onPeerSelected: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val caps = viewModel.discoveryCapabilities
    var qrTextInput by remember { mutableStateOf("") }
    var hostInput by remember { mutableStateOf("") }
    var portInput by remember { mutableStateOf("48721") }
    var pinInput by remember { mutableStateOf("") }
    var keyInput by remember { mutableStateOf("") }

    val wifiPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ ->
        viewModel.startDiscovery()
    }

    LaunchedEffect(Unit) {
        viewModel.startDiscovery()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("device_discovery_screen")
    ) {
        AuraTopHeader(
            title = "Device Discovery",
            subtitle = "Local IP: ${caps.localIpAddress}",
            onBack = onBack,
            trailing = {
                Button(
                    onClick = {
                        if (!viewModel.hasWifiDirectPermission()) {
                            val perm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                Manifest.permission.NEARBY_WIFI_DEVICES
                            } else {
                                Manifest.permission.ACCESS_FINE_LOCATION
                            }
                            wifiPermissionLauncher.launch(perm)
                        } else {
                            viewModel.startDiscovery()
                        }
                    }
                ) {
                    Icon(Icons.Rounded.WifiFind, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isDiscovering) "Rescan" else "Scan")
                }
            }
        )

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                GlassStudioCard(accentColor = LuminousCyan, modifier = Modifier.fillMaxWidth()) {
                    Text("Discovery Transports", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "NSD Wi-Fi: ${if (caps.nsdSupported) "Ready" else "N/A"} • " +
                            "Wi-Fi Direct: ${if (caps.wifiDirectSupported) "Supported" else "Unavailable"} • " +
                            "Nearby: ${if (caps.nearbySupported) "Ready" else "Unavailable"}",
                        style = MaterialTheme.typography.labelSmall,
                        color = LuminousCyan
                    )
                }
            }

            if (peers.isNotEmpty()) {
                item { Text("Discovered Peers", style = MaterialTheme.typography.titleLarge) }
                items(peers, key = { it.id }) { peer ->
                    GlassStudioCard(
                        accentColor = SignalEmerald,
                        onClick = {
                            viewModel.selectPeer(peer)
                            onPeerSelected()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(peer.displayName, style = MaterialTheme.typography.titleMedium)
                        Text(
                            "${peer.hostAddress}:${peer.port} • ${peer.source.name}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            item {
                GlassStudioCard(accentColor = ElectricViolet, modifier = Modifier.fillMaxWidth()) {
                    Text("Pair via QR Pairing URI", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = qrTextInput,
                        onValueChange = { qrTextInput = it },
                        label = { Text("Paste aura-share://pair?... URI") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (viewModel.pairFromQrOrLink(qrTextInput)) {
                                onPeerSelected()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Pair from URI")
                    }
                }
            }

            item {
                GlassStudioCard(accentColor = CreatorAmber, modifier = Modifier.fillMaxWidth()) {
                    Text("Manual Encrypted Pairing", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = hostInput,
                        onValueChange = { hostInput = it },
                        label = { Text("Receiver IP (e.g. 192.168.1.42)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = portInput,
                            onValueChange = { portInput = it.filter(Char::isDigit) },
                            label = { Text("Port") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = pinInput,
                            onValueChange = { pinInput = it.filter(Char::isDigit) },
                            label = { Text("6-Digit PIN") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = keyInput,
                        onValueChange = { keyInput = it },
                        label = { Text("64-Char AES-256 Session Key Hex") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val p = portInput.toIntOrNull() ?: 48721
                            if (viewModel.pairManually(hostInput, p, pinInput, keyInput)) {
                                onPeerSelected()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Connect & Pair")
                    }
                }
            }
        }
    }
}

@Composable
fun FileTransferScreen(
    activeTransfer: ActiveTransferState?,
    isPaused: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onOpenHistory: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("file_transfer_screen")
    ) {
        AuraTopHeader("File Transfer", "Live encrypted stream telemetry", onBack = onBack)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (activeTransfer == null) {
                GlassStudioCard(accentColor = LuminousCyan, modifier = Modifier.fillMaxWidth()) {
                    Text("No Active Transfer", style = MaterialTheme.typography.titleLarge)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Select files on the AURA Share hub or enable Receive Mode to start an encrypted local transfer.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedButton(onClick = onOpenHistory) {
                        Text("View Transfer History")
                    }
                }
            } else {
                GlassStudioCard(
                    accentColor = when (activeTransfer.status) {
                        TransferStatus.COMPLETED -> SignalEmerald
                        TransferStatus.FAILED, TransferStatus.CANCELLED -> CrimsonAlert
                        else -> LuminousCyan
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Peer: ${activeTransfer.peerName}", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "File ${activeTransfer.fileIndex}/${activeTransfer.totalFiles}: ${activeTransfer.currentFileName}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = LuminousCyan
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    LinearProgressIndicator(
                        progress = { activeTransfer.progressFraction },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "${formatBytes(activeTransfer.bytesTransferred)} / ${formatBytes(activeTransfer.totalBytes)}",
                            style = MaterialTheme.typography.labelMedium
                        )
                        Text(
                            "${formatBytes(activeTransfer.speedBytesPerSec)}/s • ${activeTransfer.status.name}",
                            style = MaterialTheme.typography.labelMedium,
                            color = SignalEmerald
                        )
                    }
                    activeTransfer.errorMessage?.let { err ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(err, color = CrimsonAlert, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (activeTransfer.status == TransferStatus.IN_PROGRESS || activeTransfer.status == TransferStatus.PAUSED) {
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = { if (isPaused) onResume() else onPause() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(if (isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isPaused) "Resume" else "Pause")
                            }
                            Button(
                                onClick = onCancel,
                                colors = ButtonDefaults.buttonColors(containerColor = CrimsonAlert),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Rounded.Cancel, null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Cancel")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TransferHistoryScreen(
    records: List<TransferRecordEntity>,
    onDeleteRecord: (String) -> Unit,
    onClearAll: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("transfer_history_screen")
    ) {
        AuraTopHeader(
            title = "Transfer History",
            subtitle = "${records.size} encrypted local transfers",
            onBack = onBack,
            trailing = {
                if (records.isNotEmpty()) {
                    TextButton(onClick = onClearAll) { Text("Clear") }
                }
            }
        )

        if (records.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No local transfer history yet.", style = MaterialTheme.typography.bodyLarge)
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(records, key = { it.id }) { rec ->
                    val isSent = rec.direction == TransferDirection.SENT.name
                    val dateStr = remember(rec.timestampMs) {
                        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                            .format(Date(rec.timestampMs))
                    }
                    GlassStudioCard(
                        accentColor = if (isSent) LuminousCyan else SignalEmerald,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(rec.fileName, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "${rec.direction} • ${rec.peerName} • ${formatBytes(rec.fileSizeBytes)} • ${rec.status}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSent) LuminousCyan else SignalEmerald
                                )
                                Text(
                                    "$dateStr • AES-256-GCM • SHA: ${rec.sha256Checksum.take(10)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { onDeleteRecord(rec.id) }) {
                                Icon(Icons.Rounded.Delete, contentDescription = "Delete record")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun IncomingTransferConsentDialog(
    prompt: IncomingTransferPrompt,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDecline,
        icon = { Icon(Icons.Rounded.Security, contentDescription = null, tint = SignalEmerald) },
        title = { Text("Incoming Encrypted Transfer") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    "${prompt.senderName} (ID: ${prompt.senderFingerprint}) wants to send ${prompt.fileNames.size} file(s) totaling ${formatBytes(prompt.totalBytes)}.",
                    style = MaterialTheme.typography.bodyLarge
                )
                prompt.fileNames.take(5).forEach { name ->
                    Text("• $name", style = MaterialTheme.typography.bodyMedium, color = LuminousCyan)
                }
                Text(
                    "Files are encrypted in transit and verified with SHA-256 before saving.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            Button(onClick = onAccept, modifier = Modifier.testTag("accept_incoming_transfer_btn")) {
                Text("Accept & Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDecline, modifier = Modifier.testTag("decline_incoming_transfer_btn")) {
                Text("Decline", color = CrimsonAlert)
            }
        }
    )
}
