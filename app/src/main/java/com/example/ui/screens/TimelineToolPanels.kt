package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.CallSplit
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Flip
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Merge
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Rotate90DegreesCw
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.VideoCall
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.domain.model.AspectRatioPreset
import com.example.domain.model.AudioClip
import com.example.domain.model.ColorGradingConfig
import com.example.domain.model.CropRect
import com.example.domain.model.EditorFontStyle
import com.example.domain.model.SpeedRampPreset
import com.example.domain.model.TextAnimationPreset
import com.example.domain.model.TextLayerClip
import com.example.domain.model.TimelineProjectState
import com.example.domain.model.TransitionType
import com.example.domain.model.VideoClip
import com.example.domain.model.VideoEffectPreset
import com.example.editor.VoiceOverState
import com.example.ui.components.formatTimeMs
import com.example.ui.theme.CreatorAmber
import com.example.ui.theme.CrimsonAlert
import com.example.ui.theme.ElectricViolet
import com.example.ui.theme.LuminousCyan
import com.example.ui.theme.SignalEmerald
import com.example.ui.viewmodel.VideoEditorViewModel
import java.util.Locale

enum class EditorToolTab(val label: String) {
    IMPORT("Import"),
    EDIT("Edit"),
    EFFECTS("FX & Trans"),
    COLOR("Color"),
    TEXT("Text"),
    AUDIO("Audio")
}

@Composable
fun TimelineToolPanelContent(
    tab: EditorToolTab,
    project: TimelineProjectState,
    selectedClip: VideoClip?,
    selectedAudio: AudioClip?,
    selectedText: TextLayerClip?,
    voiceOverState: VoiceOverState,
    viewModel: VideoEditorViewModel
) {
    val context = LocalContext.current
    var micPermissionError by remember { mutableStateOf<String?>(null) }

    val videoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { viewModel.importVideoOrPhoto(it, isPhoto = false) } }

    val photoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { viewModel.importVideoOrPhoto(it, isPhoto = true) } }

    val pipPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri -> uri?.let { viewModel.importPipOverlay(it, isPhoto = true) } }

    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importAudioTrack(it, trackIndex = 1) } }

    val srtPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::importSrtSubtitles) }

    val lutPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let(viewModel::importCubeLut) }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            micPermissionError = null
            viewModel.startVoiceOverRecording()
        } else {
            micPermissionError = "Microphone permission is required for Voice-Over recording."
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        when (tab) {
            EditorToolTab.IMPORT -> {
                Text("Import Media & Capture", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            videoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                            )
                        },
                        modifier = Modifier.weight(1f).testTag("editor_import_video_btn")
                    ) {
                        Icon(Icons.Rounded.VideoCall, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Video")
                    }
                    OutlinedButton(
                        onClick = {
                            photoPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.weight(1f).testTag("editor_import_photo_btn")
                    ) {
                        Icon(Icons.Rounded.AddPhotoAlternate, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Photo")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = {
                            pipPicker.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Layers, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("PiP Overlay")
                    }
                    OutlinedButton(
                        onClick = { audioPicker.launch(arrayOf("audio/*")) },
                        modifier = Modifier.weight(1f).testTag("editor_import_audio_btn")
                    ) {
                        Icon(Icons.Rounded.AudioFile, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Music")
                    }
                }

                // Voice-over recording with explicit RECORD_AUDIO permission check
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (voiceOverState.isRecording) {
                                viewModel.stopVoiceOverRecording()
                            } else {
                                val granted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == PackageManager.PERMISSION_GRANTED
                                if (granted) {
                                    viewModel.startVoiceOverRecording()
                                } else {
                                    micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (voiceOverState.isRecording) CrimsonAlert else ElectricViolet
                        ),
                        modifier = Modifier.weight(1f).testTag("voiceover_record_btn")
                    ) {
                        Icon(
                            if (voiceOverState.isRecording) Icons.Rounded.Stop else Icons.Rounded.Mic,
                            null
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (voiceOverState.isRecording) {
                                "Stop Voice-Over (${formatTimeMs(voiceOverState.elapsedMs)})"
                            } else "Record Voice-Over"
                        )
                    }
                }
                (micPermissionError ?: voiceOverState.error)?.let { err ->
                    Text(err, color = CrimsonAlert, style = MaterialTheme.typography.bodyMedium)
                }

                Text("Project Aspect Ratio", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AspectRatioPreset.entries.forEach { ratio ->
                        FilterChip(
                            selected = project.aspectRatio == ratio,
                            onClick = { viewModel.setAspectRatio(ratio) },
                            label = { Text(ratio.label) }
                        )
                    }
                }
            }

            EditorToolTab.EDIT -> {
                if (selectedClip == null) {
                    Text(
                        "Import or select a video/photo clip on Track V1 to edit.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text("Clip: ${selectedClip.title}", style = MaterialTheme.typography.titleMedium, color = LuminousCyan)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = viewModel::splitSelectedClip, modifier = Modifier.weight(1f)) {
                            Icon(Icons.AutoMirrored.Rounded.CallSplit, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Split")
                        }
                        OutlinedButton(onClick = viewModel::mergeSelectedWithNext, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.Merge, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Merge")
                        }
                        OutlinedButton(onClick = viewModel::duplicateSelectedClip, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.ContentCopy, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Dup")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = { viewModel.reorderSelectedClip(-1) }, modifier = Modifier.weight(1f)) {
                            Text("Move <")
                        }
                        OutlinedButton(onClick = { viewModel.reorderSelectedClip(1) }, modifier = Modifier.weight(1f)) {
                            Text("Move >")
                        }
                        OutlinedButton(onClick = viewModel::insertFreezeFrame, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.PhotoCamera, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Freeze")
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(onClick = viewModel::rotateSelectedClip90, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.Rotate90DegreesCw, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("${selectedClip.rotationDegrees.toInt()}°")
                        }
                        OutlinedButton(onClick = viewModel::flipSelectedClipHorizontal, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.Flip, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Flip H")
                        }
                        OutlinedButton(onClick = viewModel::flipSelectedClipVertical, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.SwapHoriz, null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Flip V")
                        }
                    }

                    // Trim sliders
                    val maxDur = selectedClip.sourceDurationMs.coerceAtLeast(1_000L).toFloat()
                    Text(
                        "Trim In: ${formatTimeMs(selectedClip.trimStartMs)}  •  Out: ${formatTimeMs(selectedClip.trimEndMs)}",
                        style = MaterialTheme.typography.labelMedium
                    )
                    Slider(
                        value = selectedClip.trimStartMs.toFloat(),
                        onValueChange = { viewModel.trimSelectedClip(it.toLong(), selectedClip.trimEndMs) },
                        valueRange = 0f..(selectedClip.trimEndMs - 300L).coerceAtLeast(1L).toFloat()
                    )
                    Slider(
                        value = selectedClip.trimEndMs.toFloat(),
                        onValueChange = { viewModel.trimSelectedClip(selectedClip.trimStartMs, it.toLong()) },
                        valueRange = (selectedClip.trimStartMs + 300L).coerceAtMost(maxDur.toLong()).toFloat()..maxDur
                    )

                    // Speed & Speed Ramp
                    Text(
                        String.format(Locale.US, "Speed: %.2fx  •  Scale: %.2fx", selectedClip.speed, selectedClip.scale),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Slider(
                        value = selectedClip.speed,
                        onValueChange = { viewModel.setSelectedClipSpeed(it, selectedClip.speedRampPreset) },
                        valueRange = 0.25f..4.0f
                    )
                    Slider(
                        value = selectedClip.scale,
                        onValueChange = viewModel::setSelectedClipScale,
                        valueRange = 0.5f..2.0f
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Reverse Clip Playback", style = MaterialTheme.typography.bodyLarge)
                        Switch(
                            checked = selectedClip.isReversed,
                            onCheckedChange = { viewModel.toggleSelectedClipReverse() }
                        )
                    }

                    Text("Crop Framing", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = selectedClip.crop == CropRect(),
                            onClick = { viewModel.setSelectedClipCrop(CropRect()) },
                            label = { Text("Full") }
                        )
                        FilterChip(
                            selected = selectedClip.crop.left > 0.1f,
                            onClick = { viewModel.setSelectedClipCrop(CropRect(0.15f, 0.15f, 0.85f, 0.85f)) },
                            label = { Text("Center Zoom") }
                        )
                        FilterChip(
                            selected = selectedClip.crop.top > 0.1f && selectedClip.crop.left == 0f,
                            onClick = { viewModel.setSelectedClipCrop(CropRect(0f, 0.12f, 1f, 0.88f)) },
                            label = { Text("Cinema 21:9") }
                        )
                    }

                    Text("Speed Ramp Curve", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SpeedRampPreset.entries.take(3).forEach { ramp ->
                            FilterChip(
                                selected = selectedClip.speedRampPreset == ramp,
                                onClick = { viewModel.setSelectedClipSpeed(selectedClip.speed, ramp) },
                                label = { Text(ramp.label) }
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = viewModel::deleteSelectedClip,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Delete, null, tint = CrimsonAlert)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Remove Selected Clip", color = CrimsonAlert)
                    }
                }
            }

            EditorToolTab.EFFECTS -> {
                if (selectedClip == null) {
                    Text("Select a video clip on V1 to apply effects, transitions, and keyframes.")
                } else {
                    Text("Visual Effects (OpenGL ES)", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        VideoEffectPreset.entries.take(3).forEach { preset ->
                            FilterChip(
                                selected = selectedClip.effectPreset == preset,
                                onClick = { viewModel.setSelectedClipEffect(preset, selectedClip.effectIntensity) },
                                label = { Text(preset.label) }
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        VideoEffectPreset.entries.drop(3).forEach { preset ->
                            FilterChip(
                                selected = selectedClip.effectPreset == preset,
                                onClick = { viewModel.setSelectedClipEffect(preset, selectedClip.effectIntensity) },
                                label = { Text(preset.label) }
                            )
                        }
                    }

                    Text("Transition to Next Clip (${selectedClip.transitionDurationMs} ms)", style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TransitionType.entries.take(3).forEach { tr ->
                            FilterChip(
                                selected = selectedClip.transitionToNext == tr,
                                onClick = { viewModel.setSelectedClipTransition(tr, selectedClip.transitionDurationMs) },
                                label = { Text(tr.label) }
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TransitionType.entries.drop(3).forEach { tr ->
                            FilterChip(
                                selected = selectedClip.transitionToNext == tr,
                                onClick = { viewModel.setSelectedClipTransition(tr, selectedClip.transitionDurationMs) },
                                label = { Text(tr.label) }
                            )
                        }
                    }
                    Slider(
                        value = selectedClip.transitionDurationMs.toFloat(),
                        onValueChange = { viewModel.setSelectedClipTransition(selectedClip.transitionToNext, it.toLong()) },
                        valueRange = 200f..2_000f
                    )

                    Text("Fade In (${selectedClip.fadeInMs}ms) & Fade Out (${selectedClip.fadeOutMs}ms)", style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = selectedClip.fadeInMs.toFloat(),
                        onValueChange = { viewModel.setSelectedClipFades(it.toLong(), selectedClip.fadeOutMs) },
                        valueRange = 0f..2_000f
                    )
                    Slider(
                        value = selectedClip.fadeOutMs.toFloat(),
                        onValueChange = { viewModel.setSelectedClipFades(selectedClip.fadeInMs, it.toLong()) },
                        valueRange = 0f..2_000f
                    )

                    Button(
                        onClick = viewModel::addKeyframeAtPlayhead,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.BookmarkAdd, null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Keyframe at Playhead (${selectedClip.keyframes.size} saved)")
                    }
                }
            }

            EditorToolTab.COLOR -> {
                if (selectedClip == null) {
                    Text("Select a video clip on V1 to adjust 10-point Color Grading or apply a LUT.")
                } else {
                    val g = selectedClip.colorGrading
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            g.lutName?.let { "LUT: $it" } ?: "10-Point Color Grading",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(onClick = { lutPicker.launch(arrayOf("*/*")) }) {
                                Text("Import .cube")
                            }
                            OutlinedButton(onClick = { viewModel.updateColorGrading(ColorGradingConfig()) }) {
                                Text("Reset")
                            }
                        }
                    }
                    GradingSlider("Exposure", g.exposure, -1f..1f) { viewModel.updateColorGrading(g.copy(exposure = it)) }
                    GradingSlider("Contrast", g.contrast, -1f..1f) { viewModel.updateColorGrading(g.copy(contrast = it)) }
                    GradingSlider("Brightness", g.brightness, -1f..1f) { viewModel.updateColorGrading(g.copy(brightness = it)) }
                    GradingSlider("Saturation", g.saturation, -1f..1f) { viewModel.updateColorGrading(g.copy(saturation = it)) }
                    GradingSlider("Temperature", g.temperature, -1f..1f) { viewModel.updateColorGrading(g.copy(temperature = it)) }
                    GradingSlider("Tint", g.tint, -1f..1f) { viewModel.updateColorGrading(g.copy(tint = it)) }
                    GradingSlider("Highlights", g.highlights, -1f..1f) { viewModel.updateColorGrading(g.copy(highlights = it)) }
                    GradingSlider("Shadows", g.shadows, -1f..1f) { viewModel.updateColorGrading(g.copy(shadows = it)) }
                    GradingSlider("Vignette", g.vignette, 0f..1f) { viewModel.updateColorGrading(g.copy(vignette = it)) }
                    GradingSlider("Sharpening", g.sharpening, 0f..1f) { viewModel.updateColorGrading(g.copy(sharpening = it)) }
                }
            }

            EditorToolTab.TEXT -> {
                var textDraft by remember { mutableStateOf("AURA STUDIO") }
                var fontChoice by remember { mutableStateOf(EditorFontStyle.OUTFIT) }
                var animChoice by remember { mutableStateOf(TextAnimationPreset.FADE) }
                var hasBg by remember { mutableStateOf(true) }

                OutlinedTextField(
                    value = textDraft,
                    onValueChange = { textDraft = it },
                    label = { Text("Title / Caption Text") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    EditorFontStyle.entries.forEach { f ->
                        FilterChip(
                            selected = fontChoice == f,
                            onClick = { fontChoice = f },
                            label = { Text(f.label) }
                        )
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextAnimationPreset.entries.take(3).forEach { anim ->
                        FilterChip(
                            selected = animChoice == anim,
                            onClick = { animChoice = anim },
                            label = { Text(anim.label) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Liquid Glass Text Background")
                    Switch(checked = hasBg, onCheckedChange = { hasBg = it })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { viewModel.addTextLayer(textDraft, fontChoice, animChoice, hasBg) },
                        modifier = Modifier.weight(1f).testTag("add_text_layer_btn")
                    ) {
                        Text("Add Text Layer")
                    }
                    OutlinedButton(
                        onClick = { srtPicker.launch(arrayOf("*/*")) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Rounded.Subtitles, null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Import .SRT")
                    }
                }
                selectedText?.let { layer ->
                    Text("Position Selected Text: ${layer.text}", style = MaterialTheme.typography.labelMedium, color = LuminousCyan)
                    GradingSlider("Pos X", layer.posX, 0.1f..0.9f) { viewModel.moveSelectedTextLayer(it, layer.posY) }
                    GradingSlider("Pos Y", layer.posY, 0.1f..0.9f) { viewModel.moveSelectedTextLayer(layer.posX, it) }
                    OutlinedButton(onClick = viewModel::deleteSelectedTextLayer) {
                        Text("Remove Text Layer", color = CrimsonAlert)
                    }
                }
            }

            EditorToolTab.AUDIO -> {
                if (selectedAudio == null) {
                    Text(
                        "Import Music or record a Voice-Over on the Import tab to edit audio tracks.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text("Audio Clip: ${selectedAudio.title}", style = MaterialTheme.typography.titleMedium, color = SignalEmerald)
                    // Waveform preview canvas
                    val peaks = selectedAudio.waveformPeaks.ifEmpty { List(36) { 0.4f } }
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        val barW = (size.width / peaks.size).coerceAtLeast(3f)
                        peaks.forEachIndexed { idx, p ->
                            val h = size.height * p.coerceIn(0.1f, 1f)
                            val x = idx * barW + barW / 2f
                            drawLine(
                                color = SignalEmerald,
                                start = Offset(x, (size.height - h) / 2f),
                                end = Offset(x, (size.height + h) / 2f),
                                strokeWidth = (barW * 0.6f).coerceAtLeast(2f)
                            )
                        }
                    }

                    GradingSlider("Volume", selectedAudio.volume, 0f..2f) {
                        viewModel.updateSelectedAudio(it, selectedAudio.fadeInMs, selectedAudio.fadeOutMs, selectedAudio.syncOffsetMs)
                    }
                    GradingSlider("Fade In (ms)", selectedAudio.fadeInMs.toFloat(), 0f..3_000f) {
                        viewModel.updateSelectedAudio(selectedAudio.volume, it.toLong(), selectedAudio.fadeOutMs, selectedAudio.syncOffsetMs)
                    }
                    GradingSlider("Fade Out (ms)", selectedAudio.fadeOutMs.toFloat(), 0f..3_000f) {
                        viewModel.updateSelectedAudio(selectedAudio.volume, selectedAudio.fadeInMs, it.toLong(), selectedAudio.syncOffsetMs)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = viewModel::splitSelectedAudio, modifier = Modifier.weight(1f)) {
                            Text("Split Audio")
                        }
                        OutlinedButton(onClick = viewModel::deleteSelectedAudio, modifier = Modifier.weight(1f)) {
                            Text("Delete", color = CrimsonAlert)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GradingSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(String.format(Locale.US, "%.2f", value), style = MaterialTheme.typography.labelMedium, color = LuminousCyan)
        }
        Slider(
            value = value.coerceIn(range.start, range.endInclusive),
            onValueChange = onValueChange,
            valueRange = range
        )
    }
}
