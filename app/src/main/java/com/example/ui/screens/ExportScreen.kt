package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.core.media.ExportCapabilities
import com.example.core.media.ExportCodec
import com.example.core.media.ExportFps
import com.example.core.media.ExportResolution
import com.example.domain.model.ExportPhase
import com.example.domain.model.ExportUiState
import com.example.domain.model.TimelineProjectState
import com.example.ui.components.AuraTopHeader
import com.example.ui.components.GlassStudioCard
import com.example.ui.components.formatBytes
import com.example.ui.components.formatTimeMs
import com.example.ui.theme.CreatorAmber
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.LuminousCyan
import com.example.ui.theme.SignalEmerald
import java.util.Locale

@Composable
fun ExportScreen(
    project: TimelineProjectState,
    capabilities: ExportCapabilities,
    exportState: ExportUiState,
    estimatedSizeMb: (ExportResolution) -> Double,
    onStartExport: (ExportResolution, ExportCodec, ExportFps, Uri?) -> Unit,
    onCancelExport: () -> Unit,
    onResetExport: () -> Unit,
    onOpenVideoLibrary: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    var selectedRes by remember {
        mutableStateOf(
            if (ExportResolution.FHD_1080P in capabilities.supportedResolutions) ExportResolution.FHD_1080P
            else ExportResolution.HD_720P
        )
    }
    var selectedCodec by remember { mutableStateOf(ExportCodec.H264) }
    var selectedFps by remember { mutableStateOf(ExportFps.FPS_30) }
    var customSaveUri by remember { mutableStateOf<Uri?>(null) }

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("video/mp4")
    ) { uri -> customSaveUri = uri }

    val isRunning = exportState.phase == ExportPhase.PREPARING || exportState.phase == ExportPhase.RENDERING

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("export_screen")
    ) {
        AuraTopHeader(
            title = "Export Studio",
            subtitle = "${project.name} • ${project.aspectRatio.label} • ${formatTimeMs(project.totalDurationMs)}",
            onBack = onBack
        )

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                GlassStudioCard(accentColor = LuminousCyan, modifier = Modifier.fillMaxWidth()) {
                    Text("Device Hardware Pipeline", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        capabilities.encoderSummary,
                        style = MaterialTheme.typography.labelMedium,
                        color = LuminousCyan
                    )
                    Text(
                        "Unsupported codecs or frame rates for this hardware are disabled automatically.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            item {
                GlassStudioCard(accentColor = ElectricViolet, modifier = Modifier.fillMaxWidth()) {
                    Text("Resolution", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ExportResolution.entries.take(2).forEach { res ->
                            val supported = res in capabilities.supportedResolutions
                            FilterChip(
                                selected = selectedRes == res,
                                enabled = supported && !isRunning,
                                onClick = { selectedRes = res },
                                label = { Text(if (supported) res.label else "${res.label} (N/A)") }
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ExportResolution.entries.drop(2).forEach { res ->
                            val supported = res in capabilities.supportedResolutions
                            FilterChip(
                                selected = selectedRes == res,
                                enabled = supported && !isRunning,
                                onClick = { selectedRes = res },
                                label = { Text(if (supported) res.label else "${res.label} (N/A)") }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Video Codec (MP4 Container)", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ExportCodec.entries.forEach { codec ->
                            val supported = when (codec) {
                                ExportCodec.H264 -> capabilities.supportsH264
                                ExportCodec.H265 -> capabilities.supportsH265
                            }
                            FilterChip(
                                selected = selectedCodec == codec,
                                enabled = supported && !isRunning,
                                onClick = { selectedCodec = codec },
                                label = { Text(if (supported) codec.label else "${codec.label} (Unsupported)") }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Frame Rate", style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ExportFps.entries.forEach { fps ->
                            val supported = fps in capabilities.supportedFps
                            FilterChip(
                                selected = selectedFps == fps,
                                enabled = supported && !isRunning,
                                onClick = { selectedFps = fps },
                                label = { Text(if (supported) "${fps.fps} FPS" else "${fps.fps} FPS (N/A)") }
                            )
                        }
                    }
                }
            }

            item {
                GlassStudioCard(accentColor = CreatorAmber, modifier = Modifier.fillMaxWidth()) {
                    val est = estimatedSizeMb(selectedRes)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Estimated Output Size", style = MaterialTheme.typography.titleMedium)
                            Text(
                                String.format(Locale.US, "~%.2f MB (MP4 • %s)", est, selectedCodec.label),
                                style = MaterialTheme.typography.labelMedium,
                                color = CreatorAmber
                            )
                        }
                        OutlinedButton(
                            onClick = { saveLauncher.launch("${project.name.replace(' ', '_')}.mp4") },
                            enabled = !isRunning
                        ) {
                            Icon(Icons.Rounded.FolderOpen, null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (customSaveUri == null) "Save As..." else "Folder Chosen")
                        }
                    }
                }
            }

            // Export Progress / Action Card
            item {
                GlassStudioCard(
                    accentColor = when (exportState.phase) {
                        ExportPhase.COMPLETED -> SignalEmerald
                        ExportPhase.ERROR -> CrimsonAlert
                        else -> LuminousCyan
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    when (exportState.phase) {
                        ExportPhase.IDLE -> {
                            Button(
                                onClick = {
                                    onStartExport(selectedRes, selectedCodec, selectedFps, customSaveUri)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("start_export_button")
                            ) {
                                Icon(Icons.Rounded.Movie, null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Render & Export MP4")
                            }
                        }
                        ExportPhase.PREPARING, ExportPhase.RENDERING -> {
                            Text(
                                "Rendering with Media3 Transformer (${exportState.progressPercent}%)",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            LinearProgressIndicator(
                                progress = { exportState.progressPercent / 100f },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = onCancelExport,
                                colors = ButtonDefaults.buttonColors(containerColor = CrimsonAlert),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("cancel_export_button")
                            ) {
                                Icon(Icons.Rounded.Cancel, null)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Cancel Export")
                            }
                        }
                        ExportPhase.COMPLETED -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.CheckCircle, null, tint = SignalEmerald)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Export Completed & Added to Video Library", style = MaterialTheme.typography.titleMedium)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                "Output size: ${formatBytes(exportState.actualSizeBytes)}",
                                style = MaterialTheme.typography.labelMedium,
                                color = SignalEmerald
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = onOpenVideoLibrary, modifier = Modifier.weight(1f)) {
                                    Text("Open Video Library")
                                }
                                OutlinedButton(onClick = onResetExport, modifier = Modifier.weight(1f)) {
                                    Text("Export Another")
                                }
                            }
                        }
                        ExportPhase.CANCELLED, ExportPhase.ERROR -> {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.ErrorOutline, null, tint = CrimsonAlert)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    exportState.errorMessage ?: "Export did not complete",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = CrimsonAlert
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = {
                                        onStartExport(ExportResolution.HD_720P, ExportCodec.H264, ExportFps.FPS_30, customSaveUri)
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Recover (720p H.264)")
                                }
                                OutlinedButton(onClick = onResetExport, modifier = Modifier.weight(1f)) {
                                    Text("Dismiss")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
