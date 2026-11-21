package com.example.ui.viewmodel

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.datastore.AuraPreferences
import com.example.core.media.DeviceCapabilityInspector
import com.example.core.media.ExportCapabilities
import com.example.core.media.ExportCodec
import com.example.core.media.ExportFps
import com.example.core.media.ExportResolution
import com.example.core.media.WaveformExtractor
import com.example.data.local.entity.EditorProjectEntity
import com.example.data.repository.EditorProjectRepository
import com.example.data.repository.MediaLibraryRepository
import com.example.domain.model.AspectRatioPreset
import com.example.domain.model.ClipMediaType
import com.example.domain.model.ColorGradingConfig
import com.example.domain.model.CropRect
import com.example.domain.model.EditorFontStyle
import com.example.domain.model.ExportUiState
import com.example.domain.model.MediaKind
import com.example.domain.model.SpeedRampPreset
import com.example.domain.model.TextAnimationPreset
import com.example.domain.model.TimelineProjectState
import com.example.domain.model.TransitionType
import com.example.domain.model.VideoEffectPreset
import com.example.editor.Media3EffectFactory
import com.example.editor.Media3ExportEngine
import com.example.editor.TimelineEngine
import com.example.editor.TimelineHistoryManager
import com.example.editor.VoiceOverRecorder
import com.example.editor.VoiceOverState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class VideoEditorViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val projectRepository: EditorProjectRepository,
    private val mediaRepository: MediaLibraryRepository,
    private val preferences: AuraPreferences,
    private val waveformExtractor: WaveformExtractor,
    private val capabilityInspector: DeviceCapabilityInspector,
    private val exportEngine: Media3ExportEngine,
    private val voiceOverRecorder: VoiceOverRecorder
) : ViewModel() {

    val projects: StateFlow<List<EditorProjectEntity>> = projectRepository.allProjects
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _activeProject = MutableStateFlow(TimelineProjectState())
    val activeProject: StateFlow<TimelineProjectState> = _activeProject.asStateFlow()

    private val _selectedClipId = MutableStateFlow<String?>(null)
    val selectedClipId: StateFlow<String?> = _selectedClipId.asStateFlow()

    private val _selectedAudioId = MutableStateFlow<String?>(null)
    val selectedAudioId: StateFlow<String?> = _selectedAudioId.asStateFlow()

    private val _selectedTextId = MutableStateFlow<String?>(null)
    val selectedTextId: StateFlow<String?> = _selectedTextId.asStateFlow()

    private val _canUndo = MutableStateFlow(false)
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    private val _canRedo = MutableStateFlow(false)
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

    private val _lastSavedLabel = MutableStateFlow("Ready")
    val lastSavedLabel: StateFlow<String> = _lastSavedLabel.asStateFlow()

    val exportState: StateFlow<ExportUiState> = exportEngine.exportState
    val voiceOverState: StateFlow<VoiceOverState> = voiceOverRecorder.state
    val exportCapabilities: ExportCapabilities by lazy { capabilityInspector.inspect() }

    private val history = TimelineHistoryManager()
    private var autosaveJob: Job? = null

    fun createAndOpenProject(name: String, aspectRatio: AspectRatioPreset) {
        viewModelScope.launch {
            val created = projectRepository.createNewProject(name, aspectRatio)
            history.clear()
            syncUndoRedo()
            _activeProject.value = created
            _selectedClipId.value = created.videoClips.firstOrNull()?.id
            _selectedAudioId.value = created.audioClips.firstOrNull()?.id
            _lastSavedLabel.value = "Project Created"
        }
    }

    fun openProject(projectId: String) {
        viewModelScope.launch {
            val loaded = projectRepository.loadProject(projectId) ?: return@launch
            history.clear()
            syncUndoRedo()
            _activeProject.value = loaded
            _selectedClipId.value = loaded.videoClips.firstOrNull()?.id
            _selectedAudioId.value = loaded.audioClips.firstOrNull()?.id
            _selectedTextId.value = loaded.textLayers.firstOrNull()?.id
            _lastSavedLabel.value = "Loaded"
        }
    }

    fun openMediaInNewProject(uriString: String, title: String, isVideo: Boolean) {
        viewModelScope.launch {
            val dur = if (isVideo) queryDurationMs(Uri.parse(uriString)) else 4_000L
            val created = projectRepository.createNewProject(
                name = "$title Edit",
                aspectRatio = AspectRatioPreset.RATIO_16_9
            )
            val withClip = TimelineEngine.appendVideoOrPhoto(
                state = created,
                sourceUri = uriString,
                title = title,
                mediaType = if (isVideo) ClipMediaType.VIDEO else ClipMediaType.PHOTO,
                durationMs = dur
            )
            history.clear()
            syncUndoRedo()
            _activeProject.value = withClip
            _selectedClipId.value = withClip.videoClips.firstOrNull()?.id
            projectRepository.saveProject(withClip, isAutosave = false)
        }
    }

    fun duplicateProject(projectId: String) {
        viewModelScope.launch { projectRepository.duplicateProject(projectId) }
    }

    fun deleteProject(projectId: String) {
        viewModelScope.launch { projectRepository.deleteProject(projectId) }
    }

    fun saveProjectNow() {
        viewModelScope.launch {
            projectRepository.saveProject(_activeProject.value, isAutosave = false)
            _lastSavedLabel.value = "Saved"
        }
    }

    private fun applyMutation(
        recordUndo: Boolean = true,
        transform: (TimelineProjectState) -> TimelineProjectState
    ) {
        val current = _activeProject.value
        val next = transform(current)
        if (next == current) return
        if (recordUndo) {
            history.pushSnapshot(current)
            syncUndoRedo()
        }
        _activeProject.value = next
        scheduleAutosave()
    }

    private fun scheduleAutosave() {
        autosaveJob?.cancel()
        autosaveJob = viewModelScope.launch {
            delay(900L)
            if (preferences.settingsFlow.first().autosaveEditor) {
                projectRepository.saveProject(_activeProject.value, isAutosave = true)
                _lastSavedLabel.value = "Autosaved"
            }
        }
    }

    private fun syncUndoRedo() {
        _canUndo.value = history.canUndo
        _canRedo.value = history.canRedo
    }

    fun undo() {
        val prev = history.undo(_activeProject.value) ?: return
        _activeProject.value = prev
        syncUndoRedo()
        scheduleAutosave()
    }

    fun redo() {
        val next = history.redo(_activeProject.value) ?: return
        _activeProject.value = next
        syncUndoRedo()
        scheduleAutosave()
    }

    fun setPlayhead(ms: Long) {
        val maxDur = _activeProject.value.totalDurationMs.coerceAtLeast(1_000L)
        _activeProject.value = _activeProject.value.copy(playheadMs = ms.coerceIn(0L, maxDur))
    }

    fun stepFrame(direction: Int) {
        val stepMs = 33L * direction // ~30fps frame step
        setPlayhead(_activeProject.value.playheadMs + stepMs)
    }

    fun setTimelineZoom(zoom: Float) {
        _activeProject.value = _activeProject.value.copy(timelineZoom = zoom.coerceIn(0.5f, 3.0f))
    }

    fun toggleMagneticSnap() {
        applyMutation(recordUndo = false) { it.copy(magneticSnapEnabled = !it.magneticSnapEnabled) }
    }

    fun selectClip(id: String?) { _selectedClipId.value = id }
    fun selectAudio(id: String?) { _selectedAudioId.value = id }
    fun selectText(id: String?) { _selectedTextId.value = id }

    fun importVideoOrPhoto(uri: Uri, isPhoto: Boolean) {
        viewModelScope.launch {
            val imported = mediaRepository.importLocalUri(
                uri = uri,
                forcedKind = if (isPhoto) MediaKind.PHOTO else MediaKind.VIDEO
            )
            val dur = if (isPhoto) 4_000L else queryDurationMs(uri).coerceAtLeast(imported.durationMs)
            applyMutation { state ->
                val updated = TimelineEngine.appendVideoOrPhoto(
                    state = state,
                    sourceUri = imported.uri,
                    title = imported.title,
                    mediaType = if (isPhoto) ClipMediaType.PHOTO else ClipMediaType.VIDEO,
                    durationMs = dur
                )
                _selectedClipId.value = updated.videoClips.lastOrNull()?.id
                updated
            }
        }
    }

    fun importPipOverlay(uri: Uri, isPhoto: Boolean) {
        viewModelScope.launch {
            val imported = mediaRepository.importLocalUri(
                uri = uri,
                forcedKind = if (isPhoto) MediaKind.PHOTO else MediaKind.VIDEO
            )
            applyMutation { state ->
                TimelineEngine.addOverlayClip(
                    state = state,
                    sourceUri = imported.uri,
                    title = imported.title,
                    mediaType = if (isPhoto) ClipMediaType.PHOTO else ClipMediaType.VIDEO,
                    startMs = state.playheadMs
                )
            }
        }
    }

    fun importAudioTrack(uri: Uri, trackIndex: Int = 1) {
        viewModelScope.launch {
            val imported = mediaRepository.importLocalUri(uri, forcedKind = MediaKind.AUDIO)
            val peaks = waveformExtractor.extractPeaks(imported.uri)
            applyMutation { state ->
                val updated = TimelineEngine.appendAudioClip(
                    state = state,
                    sourceUri = imported.uri,
                    title = imported.title,
                    trackIndex = trackIndex,
                    durationMs = imported.durationMs,
                    startMs = state.playheadMs,
                    waveformPeaks = peaks
                )
                _selectedAudioId.value = updated.audioClips.lastOrNull()?.id
                updated
            }
        }
    }

    fun startVoiceOverRecording(): Boolean = voiceOverRecorder.startRecording()

    fun stopVoiceOverRecording() {
        val result = voiceOverRecorder.stopRecording() ?: return
        val (uriStr, durMs) = result
        viewModelScope.launch {
            val imported = mediaRepository.importLocalUri(
                uri = Uri.parse(uriStr),
                forcedKind = MediaKind.AUDIO,
                smartTag = "Voice-Overs"
            )
            val peaks = waveformExtractor.extractPeaks(uriStr)
            applyMutation { state ->
                val updated = TimelineEngine.appendAudioClip(
                    state = state,
                    sourceUri = uriStr,
                    title = imported.title,
                    trackIndex = 2,
                    durationMs = durMs,
                    startMs = state.playheadMs,
                    waveformPeaks = peaks
                )
                _selectedAudioId.value = updated.audioClips.lastOrNull()?.id
                updated
            }
        }
    }

    // --- Video Clip Edits ---
    fun trimSelectedClip(startMs: Long, endMs: Long) {
        val id = _selectedClipId.value ?: return
        applyMutation { TimelineEngine.trimVideoClip(it, id, startMs, endMs) }
    }

    fun splitSelectedClip() {
        val id = _selectedClipId.value ?: return
        applyMutation { TimelineEngine.splitVideoClipAt(it, id, it.playheadMs) }
    }

    fun mergeSelectedWithNext() {
        val id = _selectedClipId.value ?: return
        applyMutation { TimelineEngine.mergeWithNextClip(it, id) }
    }

    fun reorderSelectedClip(delta: Int) {
        val id = _selectedClipId.value ?: return
        applyMutation { TimelineEngine.reorderVideoClip(it, id, delta) }
    }

    fun duplicateSelectedClip() {
        val id = _selectedClipId.value ?: return
        applyMutation { TimelineEngine.duplicateVideoClip(it, id) }
    }

    fun deleteSelectedClip() {
        val id = _selectedClipId.value ?: return
        applyMutation {
            val next = TimelineEngine.deleteVideoClip(it, id)
            _selectedClipId.value = next.videoClips.firstOrNull()?.id
            next
        }
    }

    fun insertFreezeFrame() {
        val id = _selectedClipId.value ?: return
        applyMutation { TimelineEngine.insertFreezeFrame(it, id, it.playheadMs) }
    }

    fun rotateSelectedClip90() {
        val id = _selectedClipId.value ?: return
        applyMutation { state ->
            TimelineEngine.updateVideoClip(state, id) { clip ->
                clip.copy(rotationDegrees = (clip.rotationDegrees + 90f) % 360f)
            }
        }
    }

    fun flipSelectedClipHorizontal() {
        val id = _selectedClipId.value ?: return
        applyMutation { state ->
            TimelineEngine.updateVideoClip(state, id) { it.copy(flipHorizontal = !it.flipHorizontal) }
        }
    }

    fun flipSelectedClipVertical() {
        val id = _selectedClipId.value ?: return
        applyMutation { state ->
            TimelineEngine.updateVideoClip(state, id) { it.copy(flipVertical = !it.flipVertical) }
        }
    }

    fun toggleSelectedClipReverse() {
        val id = _selectedClipId.value ?: return
        applyMutation { state ->
            TimelineEngine.updateVideoClip(state, id) { it.copy(isReversed = !it.isReversed) }
        }
    }

    fun setSelectedClipCrop(crop: CropRect) {
        val id = _selectedClipId.value ?: return
        applyMutation { state ->
            TimelineEngine.updateVideoClip(state, id) { it.copy(crop = crop) }
        }
    }

    fun setSelectedClipScale(scale: Float) {
        val id = _selectedClipId.value ?: return
        applyMutation { state ->
            TimelineEngine.updateVideoClip(state, id) { it.copy(scale = scale.coerceIn(0.4f, 2.5f)) }
        }
    }

    fun setSelectedClipSpeed(speed: Float, ramp: SpeedRampPreset = SpeedRampPreset.NONE) {
        val id = _selectedClipId.value ?: return
        applyMutation { state ->
            TimelineEngine.updateVideoClip(state, id) {
                it.copy(speed = speed.coerceIn(0.25f, 4f), speedRampPreset = ramp)
            }
        }
    }

    fun setAspectRatio(ratio: AspectRatioPreset) {
        applyMutation { TimelineEngine.setAspectRatio(it, ratio) }
    }

    fun setSelectedClipEffect(preset: VideoEffectPreset, intensity: Float = 0.7f) {
        val id = _selectedClipId.value ?: return
        applyMutation { state ->
            TimelineEngine.updateVideoClip(state, id) {
                it.copy(effectPreset = preset, effectIntensity = intensity)
            }
        }
    }

    fun setSelectedClipTransition(transition: TransitionType, durationMs: Long) {
        val id = _selectedClipId.value ?: return
        applyMutation { state ->
            TimelineEngine.updateVideoClip(state, id) {
                it.copy(
                    transitionToNext = transition,
                    transitionDurationMs = durationMs.coerceIn(200L, 2_000L)
                )
            }
        }
    }

    fun setSelectedClipFades(fadeInMs: Long, fadeOutMs: Long) {
        val id = _selectedClipId.value ?: return
        applyMutation { state ->
            TimelineEngine.updateVideoClip(state, id) {
                it.copy(fadeInMs = fadeInMs.coerceIn(0L, 2_000L), fadeOutMs = fadeOutMs.coerceIn(0L, 2_000L))
            }
        }
    }

    fun updateColorGrading(grading: ColorGradingConfig) {
        val id = _selectedClipId.value ?: return
        applyMutation { state ->
            TimelineEngine.updateVideoClip(state, id) { it.copy(colorGrading = grading) }
        }
    }

    fun importCubeLut(uri: Uri) {
        val id = _selectedClipId.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: return@launch
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "Custom LUT"
            val grading = TimelineEngine.parseCubeLutPreset(text, name).copy(lutUri = uri.toString())
            withContext(Dispatchers.Main) {
                applyMutation { state ->
                    TimelineEngine.updateVideoClip(state, id) { it.copy(colorGrading = grading) }
                }
            }
        }
    }

    fun addKeyframeAtPlayhead() {
        val id = _selectedClipId.value ?: return
        applyMutation { TimelineEngine.addKeyframeToClip(it, id, it.playheadMs) }
    }

    // --- Text & Subtitles ---
    fun addTextLayer(
        text: String,
        fontStyle: EditorFontStyle,
        animation: TextAnimationPreset,
        hasBackground: Boolean
    ) {
        applyMutation { state ->
            val updated = TimelineEngine.addTextLayer(
                state = state,
                text = text,
                fontStyle = fontStyle,
                startMs = state.playheadMs,
                animation = animation,
                hasBackground = hasBackground
            )
            _selectedTextId.value = updated.textLayers.lastOrNull()?.id
            updated
        }
    }

    fun moveSelectedTextLayer(posX: Float, posY: Float) {
        val tid = _selectedTextId.value ?: return
        applyMutation { state ->
            state.copy(
                textLayers = state.textLayers.map {
                    if (it.id == tid) it.copy(posX = posX.coerceIn(0.1f, 0.9f), posY = posY.coerceIn(0.1f, 0.9f))
                    else it
                }
            )
        }
    }

    fun deleteSelectedTextLayer() {
        val tid = _selectedTextId.value ?: return
        applyMutation { state ->
            val next = state.textLayers.filterNot { it.id == tid }
            _selectedTextId.value = next.firstOrNull()?.id
            state.copy(textLayers = next)
        }
    }

    fun importSrtSubtitles(uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val srt = context.contentResolver.openInputStream(uri)?.bufferedReader()?.readText() ?: return@launch
            val cues = TimelineEngine.parseSrtSubtitles(srt)
            if (cues.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    applyMutation { state ->
                        state.copy(textLayers = state.textLayers + cues)
                    }
                }
            }
        }
    }

    // --- Audio & Track Controls ---
    fun updateSelectedAudio(volume: Float, fadeInMs: Long, fadeOutMs: Long, syncOffsetMs: Long) {
        val aid = _selectedAudioId.value ?: return
        applyMutation { state ->
            TimelineEngine.updateAudioClip(state, aid) {
                it.copy(
                    volume = volume.coerceIn(0f, 2f),
                    fadeInMs = fadeInMs.coerceIn(0L, 3_000L),
                    fadeOutMs = fadeOutMs.coerceIn(0L, 3_000L),
                    syncOffsetMs = syncOffsetMs
                )
            }
        }
    }

    fun splitSelectedAudio() {
        val aid = _selectedAudioId.value ?: return
        applyMutation { TimelineEngine.splitAudioClipAt(it, aid, it.playheadMs) }
    }

    fun trimSelectedAudio(startMs: Long, endMs: Long) {
        val aid = _selectedAudioId.value ?: return
        applyMutation { TimelineEngine.trimAudioClip(it, aid, startMs, endMs) }
    }

    fun deleteSelectedAudio() {
        val aid = _selectedAudioId.value ?: return
        applyMutation {
            val next = TimelineEngine.deleteAudioClip(it, aid)
            _selectedAudioId.value = next.audioClips.firstOrNull()?.id
            next
        }
    }

    fun toggleTrackMute(track: String) {
        applyMutation { state ->
            val tc = state.trackControls
            val next = when (track) {
                "V1" -> tc.copy(videoMuted = !tc.videoMuted)
                "A1" -> tc.copy(musicMuted = !tc.musicMuted)
                "A2" -> tc.copy(voiceOverMuted = !tc.voiceOverMuted)
                else -> tc
            }
            TimelineEngine.updateTrackControls(state, next)
        }
    }

    fun toggleTrackVisibility(track: String) {
        applyMutation { state ->
            val tc = state.trackControls
            val next = when (track) {
                "V1" -> tc.copy(videoVisible = !tc.videoVisible)
                "V2" -> tc.copy(overlayVisible = !tc.overlayVisible)
                "T1" -> tc.copy(textVisible = !tc.textVisible)
                else -> tc
            }
            TimelineEngine.updateTrackControls(state, next)
        }
    }

    // --- Export Engine ---
    fun estimateSizeMb(resolution: ExportResolution): Double {
        return Media3EffectFactory.estimateOutputSizeMb(_activeProject.value, resolution)
    }

    fun startExport(
        resolution: ExportResolution,
        codec: ExportCodec,
        fps: ExportFps,
        userDestinationUri: Uri? = null
    ) {
        exportEngine.startExport(
            project = _activeProject.value,
            resolution = resolution,
            codec = codec,
            fps = fps,
            userDestinationUri = userDestinationUri
        )
    }

    fun cancelExport() = exportEngine.cancelExport()
    fun resetExport() = exportEngine.resetExportState()

    private suspend fun queryDurationMs(uri: Uri): Long = withContext(Dispatchers.IO) {
        var dur = 5_000L
        val retriever = MediaMetadataRetriever()
        runCatching {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let {
                dur = it
            }
        }
        runCatching { retriever.release() }
        dur
    }
}
