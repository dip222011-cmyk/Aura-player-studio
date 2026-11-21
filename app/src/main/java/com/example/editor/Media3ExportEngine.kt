package com.example.editor

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import com.example.core.media.ExportCodec
import com.example.core.media.ExportFps
import com.example.core.media.ExportResolution
import com.example.data.repository.MediaLibraryRepository
import com.example.domain.model.ClipMediaType
import com.example.domain.model.ExportPhase
import com.example.domain.model.ExportUiState
import com.example.domain.model.SpeedRampPreset
import com.example.domain.model.TimelineProjectState
import com.example.domain.model.TransitionType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileInputStream
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
@OptIn(UnstableApi::class)
class Media3ExportEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaRepository: MediaLibraryRepository,
    private val reverseProcessor: VideoReverseProcessor
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _exportState = MutableStateFlow(ExportUiState())
    val exportState: StateFlow<ExportUiState> = _exportState.asStateFlow()

    private var activeTransformer: Transformer? = null
    private var exportPrepJob: Job? = null
    private var progressRunnable: Runnable? = null

    @OptIn(UnstableApi::class)
    fun startExport(
        project: TimelineProjectState,
        resolution: ExportResolution,
        codec: ExportCodec,
        fps: ExportFps,
        userDestinationUri: Uri? = null
    ) {
        if (project.videoClips.isEmpty() && project.overlayClips.isEmpty()) {
            _exportState.value = ExportUiState(
                phase = ExportPhase.ERROR,
                errorMessage = "Add at least one video clip or photo before exporting."
            )
            return
        }

        cancelExportInternal(resetState = false)

        val estimatedMb = Media3EffectFactory.estimateOutputSizeMb(project, resolution)
        val exportDir = File(context.filesDir, "aura_exports").apply { mkdirs() }
        val safeTitle = project.name.replace(Regex("[^A-Za-z0-9_\\- ]"), "_").ifBlank { "aura_export" }
        val outFile = File(exportDir, "${safeTitle}_${System.currentTimeMillis()}.mp4")

        _exportState.value = ExportUiState(
            phase = ExportPhase.PREPARING,
            progressPercent = 0,
            estimatedSizeMb = estimatedMb,
            outputPath = outFile.absolutePath
        )

        exportPrepJob = scope.launch {
            try {
                val activeOverlays = if (project.trackControls.overlayVisible) {
                    project.overlayClips
                } else emptyList()
                val activeTexts = if (project.trackControls.textVisible) {
                    project.textLayers
                } else emptyList()

                val videoSequenceItems = project.videoClips.mapIndexed { index, clip ->
                    val prevClip = project.videoClips.getOrNull(index - 1)
                    val incomingTrans = prevClip?.transitionToNext ?: TransitionType.NONE
                    val incomingDur = prevClip?.transitionDurationMs ?: 0L

                    val mediaUri = if (clip.isReversed && clip.mediaType == ClipMediaType.VIDEO) {
                        reverseProcessor.reverseTrimmedClip(
                            context = context,
                            sourceUri = Uri.parse(clip.sourceUri),
                            trimStartMs = clip.trimStartMs,
                            trimEndMs = clip.trimEndMs,
                            fps = fps.fps.coerceAtMost(30)
                        )
                    } else {
                        Uri.parse(clip.sourceUri)
                    }

                    val mediaItemBuilder = MediaItem.Builder().setUri(mediaUri)
                    if (clip.mediaType == ClipMediaType.PHOTO || clip.mediaType == ClipMediaType.FREEZE_FRAME) {
                        mediaItemBuilder.setImageDurationMs(clip.effectiveDurationMs)
                    } else if (!clip.isReversed) {
                        mediaItemBuilder.setClippingConfiguration(
                            MediaItem.ClippingConfiguration.Builder()
                                .setStartPositionMs(clip.trimStartMs)
                                .setEndPositionMs(clip.trimEndMs)
                                .build()
                        )
                    }

                    val videoEffects = Media3EffectFactory.buildClipVideoEffects(
                        clip = clip,
                        textLayers = activeTexts,
                        overlayClips = activeOverlays,
                        context = context,
                        incomingTransition = incomingTrans,
                        incomingTransitionDurationMs = incomingDur
                    ).toMutableList<Effect>()

                    val audioProcessors = mutableListOf<AudioProcessor>()
                    val clipSourceDurationMs = (clip.trimEndMs - clip.trimStartMs).coerceAtLeast(250L)
                    if (!clip.isMuted && !project.trackControls.videoMuted) {
                        if (clip.volume != 1f || clip.fadeInMs > 0L || clip.fadeOutMs > 0L) {
                            audioProcessors.add(
                                Media3EffectFactory.buildVolumeAudioProcessor(
                                    volume = clip.volume,
                                    fadeInMs = clip.fadeInMs,
                                    fadeOutMs = clip.fadeOutMs,
                                    clipDurationMs = clip.effectiveDurationMs
                                )
                            )
                        }
                    }

                    val editedBuilder = EditedMediaItem.Builder(mediaItemBuilder.build())
                        .setRemoveAudio(clip.isMuted || project.trackControls.videoMuted || clip.isReversed)
                        .setEffects(Effects(audioProcessors, videoEffects))

                    if (clip.speed != 1f || clip.speedRampPreset != SpeedRampPreset.NONE) {
                        val speedProvider = Media3EffectFactory.buildSpeedProvider(
                            baseSpeed = clip.speed,
                            rampPreset = clip.speedRampPreset,
                            clipDurationUs = clipSourceDurationMs * 1000L
                        )
                        editedBuilder.setSpeed(speedProvider)
                    }

                    if (clip.mediaType == ClipMediaType.PHOTO || clip.mediaType == ClipMediaType.FREEZE_FRAME) {
                        editedBuilder.setFrameRate(fps.fps)
                    }
                    editedBuilder.build()
                }

                val sequences = mutableListOf<EditedMediaItemSequence>()
                sequences.add(EditedMediaItemSequence.withAudioAndVideoFrom(videoSequenceItems))

                // Add music (trackIndex=1) and voice-over (trackIndex=2) sequences honoring sync offsets & fades
                listOf(1, 2).forEach { trackIdx ->
                    val muted = when (trackIdx) {
                        1 -> project.trackControls.musicMuted
                        2 -> project.trackControls.voiceOverMuted
                        else -> false
                    }
                    if (!muted) {
                        val trackClips = project.audioClips
                            .filter { it.trackIndex == trackIdx }
                            .sortedBy { it.timelineStartMs + it.syncOffsetMs }
                        if (trackClips.isNotEmpty()) {
                            val seqBuilder = EditedMediaItemSequence.Builder(setOf(C.TRACK_TYPE_AUDIO))
                            var cursorMs = 0L
                            trackClips.forEach { ac ->
                                val desiredStartMs = (ac.timelineStartMs + ac.syncOffsetMs).coerceAtLeast(cursorMs)
                                val gapMs = desiredStartMs - cursorMs
                                if (gapMs > 10L) {
                                    seqBuilder.addGap(gapMs * 1000L)
                                    cursorMs += gapMs
                                }
                                val item = MediaItem.Builder()
                                    .setUri(Uri.parse(ac.sourceUri))
                                    .setClippingConfiguration(
                                        MediaItem.ClippingConfiguration.Builder()
                                            .setStartPositionMs(ac.trimStartMs)
                                            .setEndPositionMs(ac.trimEndMs)
                                            .build()
                                    )
                                    .build()
                                val proc = Media3EffectFactory.buildVolumeAudioProcessor(
                                    volume = ac.volume,
                                    fadeInMs = ac.fadeInMs,
                                    fadeOutMs = ac.fadeOutMs,
                                    clipDurationMs = ac.effectiveDurationMs
                                )
                                seqBuilder.addItem(
                                    EditedMediaItem.Builder(item)
                                        .setEffects(Effects(listOf(proc), emptyList()))
                                        .build()
                                )
                                cursorMs += ac.effectiveDurationMs
                            }
                            sequences.add(seqBuilder.build())
                        }
                    }
                }

                val presentation = Media3EffectFactory.buildCompositionPresentation(project.aspectRatio, resolution)
                val composition = Composition.Builder(sequences)
                    .setEffects(Effects(emptyList(), listOf(presentation)))
                    .build()

                val encoderFactory = DefaultEncoderFactory.Builder(context)
                    .setRequestedVideoEncoderSettings(
                        VideoEncoderSettings.Builder()
                            .setBitrate(resolution.recommendedBitrateMbps * 1_000_000)
                            .build()
                    )
                    .build()

                val transformer = Transformer.Builder(context)
                    .setVideoMimeType(codec.mimeType)
                    .setAudioMimeType(MimeTypes.AUDIO_AAC)
                    .setEncoderFactory(encoderFactory)
                    .addListener(object : Transformer.Listener {
                        override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                            stopProgressPolling()
                            val fileSize = outFile.length()
                            scope.launch {
                                var displayUri = Uri.fromFile(outFile).toString()
                                if (userDestinationUri != null) {
                                    runCatching {
                                        context.contentResolver.openOutputStream(userDestinationUri)?.use { destOut ->
                                            FileInputStream(outFile).use { srcIn ->
                                                srcIn.copyTo(destOut)
                                            }
                                        }
                                        displayUri = userDestinationUri.toString()
                                    }
                                }
                                mediaRepository.registerExportedVideo(
                                    filePath = outFile.absolutePath,
                                    title = project.name,
                                    durationMs = project.totalDurationMs,
                                    sizeBytes = fileSize,
                                    resolutionLabel = resolution.label
                                )
                                _exportState.value = ExportUiState(
                                    phase = ExportPhase.COMPLETED,
                                    progressPercent = 100,
                                    estimatedSizeMb = estimatedMb,
                                    outputPath = outFile.absolutePath,
                                    savedDisplayUri = displayUri,
                                    actualSizeBytes = fileSize
                                )
                            }
                        }

                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException
                        ) {
                            stopProgressPolling()
                            _exportState.value = _exportState.value.copy(
                                phase = ExportPhase.ERROR,
                                errorMessage = exportException.localizedMessage
                                    ?: "Hardware encoder rejected parameters (${exportException.errorCodeName}). Try H.264 1080p."
                            )
                        }
                    })
                    .build()

                activeTransformer = transformer
                _exportState.value = _exportState.value.copy(phase = ExportPhase.RENDERING)
                transformer.start(composition, outFile.absolutePath)
                startProgressPolling(transformer)
            } catch (err: Exception) {
                stopProgressPolling()
                _exportState.value = _exportState.value.copy(
                    phase = ExportPhase.ERROR,
                    errorMessage = err.localizedMessage ?: "Failed to prepare Media3 export pipeline"
                )
            }
        }
    }

    @OptIn(UnstableApi::class)
    private fun startProgressPolling(transformer: Transformer) {
        stopProgressPolling()
        val holder = ProgressHolder()
        val runnable = object : Runnable {
            override fun run() {
                val state = transformer.getProgress(holder)
                if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                    _exportState.value = _exportState.value.copy(
                        progressPercent = holder.progress.coerceIn(0, 99)
                    )
                }
                if (_exportState.value.phase == ExportPhase.RENDERING ||
                    _exportState.value.phase == ExportPhase.PREPARING
                ) {
                    mainHandler.postDelayed(this, 250L)
                }
            }
        }
        progressRunnable = runnable
        mainHandler.post(runnable)
    }

    private fun stopProgressPolling() {
        progressRunnable?.let { mainHandler.removeCallbacks(it) }
        progressRunnable = null
    }

    @OptIn(UnstableApi::class)
    fun cancelExport() {
        cancelExportInternal(resetState = false)
        _exportState.value = _exportState.value.copy(
            phase = ExportPhase.CANCELLED,
            errorMessage = "Export cancelled cleanly. Hardware codecs released."
        )
    }

    fun resetExportState() {
        _exportState.value = ExportUiState()
    }

    @OptIn(UnstableApi::class)
    private fun cancelExportInternal(resetState: Boolean) {
        exportPrepJob?.cancel()
        exportPrepJob = null
        stopProgressPolling()
        runCatching { activeTransformer?.cancel() }
        activeTransformer = null
        if (resetState) {
            _exportState.value = ExportUiState()
        }
    }
}
