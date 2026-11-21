package com.example.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.automirrored.rounded.VolumeOff
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.ZoomIn
import androidx.compose.material.icons.rounded.ZoomOut
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.example.domain.model.ClipMediaType
import com.example.domain.model.TimelineProjectState
import com.example.editor.Media3EffectFactory
import com.example.editor.VoiceOverState
import com.example.ui.components.AuraTopHeader
import com.example.ui.components.formatTimecodeMs
import com.example.ui.theme.CreatorAmber
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.LuminousCyan
import com.example.ui.theme.SignalEmerald
import com.example.ui.viewmodel.VideoEditorViewModel
import kotlinx.coroutines.delay

@OptIn(UnstableApi::class)
@Composable
fun TimelineEditorScreen(
    project: TimelineProjectState,
    selectedClipId: String?,
    selectedAudioId: String?,
    selectedTextId: String?,
    canUndo: Boolean,
    canRedo: Boolean,
    lastSavedLabel: String,
    voiceOverState: VoiceOverState,
    viewModel: VideoEditorViewModel,
    onOpenExport: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    var activeTab by remember { mutableStateOf(EditorToolTab.IMPORT) }
    var isPreviewPlaying by remember { mutableStateOf(false) }

    val selectedClip = remember(project.videoClips, selectedClipId) {
        project.videoClips.firstOrNull { it.id == selectedClipId } ?: project.videoClips.firstOrNull()
    }
    val selectedAudio = remember(project.audioClips, selectedAudioId) {
        project.audioClips.firstOrNull { it.id == selectedAudioId } ?: project.audioClips.firstOrNull()
    }
    val selectedText = remember(project.textLayers, selectedTextId) {
        project.textLayers.firstOrNull { it.id == selectedTextId } ?: project.textLayers.firstOrNull()
    }

    // Active clip under playhead for live ExoPlayer preview
    val activeClipAtPlayhead = remember(project.videoClips, project.playheadMs) {
        project.videoClips.firstOrNull {
            project.playheadMs >= it.timelineStartMs && project.playheadMs < it.timelineStartMs + it.effectiveDurationMs
        } ?: project.videoClips.firstOrNull()
    }

    val previewPlayer = remember {
        ExoPlayer.Builder(context).build()
    }
    DisposableEffect(previewPlayer) {
        onDispose { previewPlayer.release() }
    }

    // Sync active video clip & Media3 OpenGL effects + playback speed to preview player
    LaunchedEffect(
        activeClipAtPlayhead?.id,
        activeClipAtPlayhead?.colorGrading,
        activeClipAtPlayhead?.effectPreset,
        activeClipAtPlayhead?.rotationDegrees,
        activeClipAtPlayhead?.flipHorizontal,
        activeClipAtPlayhead?.flipVertical,
        activeClipAtPlayhead?.speed,
        project.trackControls.textVisible,
        project.trackControls.overlayVisible
    ) {
        val clip = activeClipAtPlayhead
        if (clip != null && clip.mediaType == ClipMediaType.VIDEO) {
            val item = MediaItem.Builder()
                .setUri(Uri.parse(clip.sourceUri))
                .setClippingConfiguration(
                    MediaItem.ClippingConfiguration.Builder()
                        .setStartPositionMs(clip.trimStartMs)
                        .setEndPositionMs(clip.trimEndMs)
                        .build()
                )
                .build()
            previewPlayer.setMediaItem(item)
            previewPlayer.setVideoEffects(
                Media3EffectFactory.buildClipVideoEffects(
                    clip = clip,
                    textLayers = if (project.trackControls.textVisible) project.textLayers else emptyList(),
                    overlayClips = if (project.trackControls.overlayVisible) project.overlayClips else emptyList(),
                    context = context
                )
            )
            previewPlayer.setPlaybackSpeed(clip.speed.coerceIn(0.25f, 4f))
            previewPlayer.volume = if (clip.isMuted || project.trackControls.videoMuted) 0f else clip.volume
            previewPlayer.prepare()
        }
    }

    // Playhead ticker during preview playback
    LaunchedEffect(isPreviewPlaying, project.totalDurationMs) {
        if (isPreviewPlaying) {
            previewPlayer.play()
            while (isPreviewPlaying) {
                delay(50L)
                val next = project.playheadMs + 50L
                if (next >= project.totalDurationMs.coerceAtLeast(1_000L)) {
                    viewModel.setPlayhead(0L)
                    isPreviewPlaying = false
                } else {
                    viewModel.setPlayhead(next)
                }
            }
        } else {
            previewPlayer.pause()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("timeline_editor_screen")
    ) {
        AuraTopHeader(
            title = project.name,
            subtitle = "${project.aspectRatio.label} • $lastSavedLabel",
            onBack = onBack,
            trailing = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = viewModel::saveProjectNow) {
                        Icon(Icons.Rounded.Save, contentDescription = "Save project")
                    }
                    Button(
                        onClick = onOpenExport,
                        colors = ButtonDefaults.buttonColors(containerColor = LuminousCyan, contentColor = Color(0xFF070A12)),
                        modifier = Modifier.testTag("editor_open_export_btn")
                    ) {
                        Icon(Icons.Rounded.Upload, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Export")
                    }
                }
            }
        )

        // Live Video / Composite Preview Viewport
        val ratio = project.aspectRatio.widthRatio.toFloat() / project.aspectRatio.heightRatio.toFloat()
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(196.dp)
                .background(Color(0xFF05070D)),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(6.dp)
                    .aspectRatio(ratio.coerceIn(0.45f, 2.4f))
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0B111E))
                    .border(1.dp, LuminousCyan.copy(alpha = 0.35f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (!project.trackControls.videoVisible || activeClipAtPlayhead == null) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.Movie, null, tint = LuminousCyan, modifier = Modifier.size(34.dp))
                        Text(
                            if (activeClipAtPlayhead == null) "Import Video or Photo" else "Video Track Hidden",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                } else if (activeClipAtPlayhead.mediaType == ClipMediaType.VIDEO) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                player = previewPlayer
                                useController = false
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    AsyncImage(
                        model = activeClipAtPlayhead.sourceUri,
                        contentDescription = activeClipAtPlayhead.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Active V2 Picture-in-Picture Overlays at playhead
                if (project.trackControls.overlayVisible) {
                    val activeOverlays = project.overlayClips.filter { ov ->
                        project.playheadMs in ov.timelineStartMs..(ov.timelineStartMs + ov.durationMs)
                    }
                    activeOverlays.forEach { ov ->
                        AsyncImage(
                            model = ov.sourceUri,
                            contentDescription = ov.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(10.dp)
                                .size((84f * ov.pipScale.coerceIn(0.25f, 1.2f) * 2f).dp)
                                .clip(RoundedCornerShape(8.dp))
                                .border(1.dp, CreatorAmber, RoundedCornerShape(8.dp))
                        )
                    }
                }

                // Active Text / Subtitle Overlays at playhead
                if (project.trackControls.textVisible) {
                    val activeTexts = project.textLayers.filter {
                        project.playheadMs in it.startMs..it.endMs
                    }
                    activeTexts.forEach { layer ->
                        Surface(
                            color = if (layer.hasBackground) Color(layer.backgroundColorHex) else Color.Transparent,
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 12.dp)
                        ) {
                            Text(
                                text = layer.text,
                                color = Color(layer.colorHex),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
        }

        // Transport, Timecode, Frame Step, Undo/Redo, Snap & Zoom Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { viewModel.stepFrame(-1) }) {
                    Icon(Icons.Rounded.ChevronLeft, contentDescription = "Step back 1 frame")
                }
                IconButton(
                    onClick = { isPreviewPlaying = !isPreviewPlaying },
                    modifier = Modifier.testTag("editor_play_pause_btn")
                ) {
                    Icon(
                        if (isPreviewPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = "Play or pause preview",
                        tint = LuminousCyan
                    )
                }
                IconButton(onClick = { viewModel.stepFrame(1) }) {
                    Icon(Icons.Rounded.ChevronRight, contentDescription = "Step forward 1 frame")
                }
                Text(
                    text = formatTimecodeMs(project.playheadMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = LuminousCyan
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = viewModel::undo, enabled = canUndo, modifier = Modifier.testTag("editor_undo_btn")) {
                    Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = "Undo")
                }
                IconButton(onClick = viewModel::redo, enabled = canRedo, modifier = Modifier.testTag("editor_redo_btn")) {
                    Icon(Icons.AutoMirrored.Rounded.Redo, contentDescription = "Redo")
                }
                IconButton(onClick = viewModel::toggleMagneticSnap) {
                    Icon(
                        Icons.Rounded.Straighten,
                        contentDescription = "Toggle magnetic snapping",
                        tint = if (project.magneticSnapEnabled) CreatorAmber else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { viewModel.setTimelineZoom(project.timelineZoom - 0.25f) }) {
                    Icon(Icons.Rounded.ZoomOut, contentDescription = "Zoom out timeline")
                }
                IconButton(onClick = { viewModel.setTimelineZoom(project.timelineZoom + 0.25f) }) {
                    Icon(Icons.Rounded.ZoomIn, contentDescription = "Zoom in timeline")
                }
            }
        }

        // Playhead Scrubber
        val totalDur = project.totalDurationMs.coerceAtLeast(1_000L).toFloat()
        Slider(
            value = project.playheadMs.toFloat().coerceIn(0f, totalDur),
            onValueChange = { viewModel.setPlayhead(it.toLong()) },
            valueRange = 0f..totalDur,
            modifier = Modifier.padding(horizontal = 14.dp)
        )

        // Multi-Track Timeline View (V1, V2, A1, A2, T1)
        val pxPerSec = 36.dp * project.timelineZoom
        val scrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Track V1 (Primary Video/Photo)
            TimelineTrackRow(
                label = "V1",
                isMuted = project.trackControls.videoMuted,
                isVisible = project.trackControls.videoVisible,
                onToggleMute = { viewModel.toggleTrackMute("V1") },
                onToggleVisible = { viewModel.toggleTrackVisibility("V1") }
            ) {
                Row(
                    modifier = Modifier.horizontalScroll(scrollState),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (project.videoClips.isEmpty()) {
                        Text(
                            "Empty Video Track — tap Import",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp)
                        )
                    } else {
                        project.videoClips.forEach { clip ->
                            val w = ((clip.effectiveDurationMs / 1000f) * pxPerSec.value).dp.coerceAtLeast(56.dp)
                            val isSelected = clip.id == selectedClip?.id
                            Box(
                                modifier = Modifier
                                    .width(w)
                                    .height(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) LuminousCyan.copy(alpha = 0.28f) else Color(0xFF1A2538))
                                    .border(
                                        width = if (isSelected) 2.dp else 1.dp,
                                        color = if (isSelected) LuminousCyan else Color(0xFF334155),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        viewModel.selectClip(clip.id)
                                        viewModel.setPlayhead(clip.timelineStartMs)
                                    }
                                    .padding(horizontal = 6.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    clip.title,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // Track V2 (PiP / Image Overlays)
            if (project.overlayClips.isNotEmpty()) {
                TimelineTrackRow(
                    label = "V2",
                    isMuted = false,
                    isVisible = project.trackControls.overlayVisible,
                    onToggleMute = null,
                    onToggleVisible = { viewModel.toggleTrackVisibility("V2") }
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        project.overlayClips.forEach { ov ->
                            Box(
                                modifier = Modifier
                                    .width(84.dp)
                                    .height(26.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(CreatorAmber.copy(alpha = 0.22f))
                                    .border(1.dp, CreatorAmber, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(ov.title, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            }
                        }
                    }
                }
            }

            // Track A1/A2 (Audio & Voice-Over with waveform)
            TimelineTrackRow(
                label = "A1",
                isMuted = project.trackControls.musicMuted,
                isVisible = true,
                onToggleMute = { viewModel.toggleTrackMute("A1") },
                onToggleVisible = null
            ) {
                Row(
                    modifier = Modifier.horizontalScroll(scrollState),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (project.audioClips.isEmpty()) {
                        Text(
                            "No Audio Tracks",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(6.dp)
                        )
                    } else {
                        project.audioClips.forEach { ac ->
                            val w = ((ac.effectiveDurationMs / 1000f) * pxPerSec.value).dp.coerceAtLeast(64.dp)
                            val isSelected = ac.id == selectedAudio?.id
                            Box(
                                modifier = Modifier
                                    .width(w)
                                    .height(30.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(SignalEmerald.copy(alpha = if (isSelected) 0.28f else 0.14f))
                                    .border(1.dp, if (isSelected) SignalEmerald else Color(0xFF1E4A3B), RoundedCornerShape(6.dp))
                                    .clickable { viewModel.selectAudio(ac.id) }
                                    .padding(horizontal = 4.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                val peaks = ac.waveformPeaks.ifEmpty { List(20) { 0.45f } }
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val step = size.width / peaks.size
                                    peaks.forEachIndexed { idx, p ->
                                        val h = size.height * p * 0.8f
                                        drawLine(
                                            color = SignalEmerald.copy(alpha = 0.65f),
                                            start = Offset(idx * step, (size.height - h) / 2f),
                                            end = Offset(idx * step, (size.height + h) / 2f),
                                            strokeWidth = 2f
                                        )
                                    }
                                }
                                Text(ac.title, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }

            // Track T1 (Text & Subtitles)
            if (project.textLayers.isNotEmpty()) {
                TimelineTrackRow(
                    label = "T1",
                    isMuted = false,
                    isVisible = project.trackControls.textVisible,
                    onToggleMute = null,
                    onToggleVisible = { viewModel.toggleTrackVisibility("T1") }
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        project.textLayers.forEach { layer ->
                            val isSelected = layer.id == selectedText?.id
                            Box(
                                modifier = Modifier
                                    .width(88.dp)
                                    .height(26.dp)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(ElectricViolet.copy(alpha = if (isSelected) 0.32f else 0.18f))
                                    .border(1.dp, ElectricViolet, RoundedCornerShape(6.dp))
                                    .clickable { viewModel.selectText(layer.id) }
                                    .padding(horizontal = 6.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Text(layer.text, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }

        // Contextual Tool Tabs + Tool Panel
        ScrollableTabRow(
            selectedTabIndex = activeTab.ordinal,
            edgePadding = 8.dp
        ) {
            EditorToolTab.entries.forEach { tab ->
                Tab(
                    selected = activeTab == tab,
                    onClick = { activeTab = tab },
                    text = { Text(tab.label, style = MaterialTheme.typography.labelLarge) }
                )
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            TimelineToolPanelContent(
                tab = activeTab,
                project = project,
                selectedClip = selectedClip,
                selectedAudio = selectedAudio,
                selectedText = selectedText,
                voiceOverState = voiceOverState,
                viewModel = viewModel
            )
        }
    }
}

@Composable
private fun TimelineTrackRow(
    label: String,
    isMuted: Boolean,
    isVisible: Boolean,
    onToggleMute: (() -> Unit)?,
    onToggleVisible: (() -> Unit)?,
    content: @Composable () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.width(72.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = LuminousCyan)
            if (onToggleVisible != null) {
                IconButton(onClick = onToggleVisible, modifier = Modifier.size(26.dp)) {
                    Icon(
                        if (isVisible) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff,
                        contentDescription = "Toggle track visibility",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
            if (onToggleMute != null) {
                IconButton(onClick = onToggleMute, modifier = Modifier.size(26.dp)) {
                    Icon(
                        if (isMuted) Icons.AutoMirrored.Rounded.VolumeOff else Icons.AutoMirrored.Rounded.VolumeUp,
                        contentDescription = "Toggle track mute",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        Box(modifier = Modifier.weight(1f)) {
            content()
        }
    }
}
