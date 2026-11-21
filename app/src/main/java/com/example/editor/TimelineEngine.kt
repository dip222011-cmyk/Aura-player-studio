package com.example.editor

import com.example.domain.model.AspectRatioPreset
import com.example.domain.model.AudioClip
import com.example.domain.model.ClipMediaType
import com.example.domain.model.ColorGradingConfig
import com.example.domain.model.CropRect
import com.example.domain.model.EditorFontStyle
import com.example.domain.model.KeyframePoint
import com.example.domain.model.OverlayClip
import com.example.domain.model.SpeedRampPreset
import com.example.domain.model.TextAnimationPreset
import com.example.domain.model.TextLayerClip
import com.example.domain.model.TimelineProjectState
import com.example.domain.model.TrackControls
import com.example.domain.model.TransitionType
import com.example.domain.model.VideoClip
import com.example.domain.model.VideoEffectPreset
import java.util.UUID
import kotlin.math.abs

class TimelineHistoryManager(private val maxHistory: Int = 40) {
    private val undoStack = ArrayDeque<TimelineProjectState>()
    private val redoStack = ArrayDeque<TimelineProjectState>()

    val canUndo: Boolean get() = undoStack.isNotEmpty()
    val canRedo: Boolean get() = redoStack.isNotEmpty()

    fun pushSnapshot(previousState: TimelineProjectState) {
        if (undoStack.size >= maxHistory) {
            undoStack.removeFirst()
        }
        undoStack.addLast(previousState)
        redoStack.clear()
    }

    fun undo(currentState: TimelineProjectState): TimelineProjectState? {
        if (undoStack.isEmpty()) return null
        redoStack.addLast(currentState)
        return undoStack.removeLast()
    }

    fun redo(currentState: TimelineProjectState): TimelineProjectState? {
        if (redoStack.isEmpty()) return null
        undoStack.addLast(currentState)
        return redoStack.removeLast()
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }
}

object TimelineEngine {

    private const val MAGNETIC_SNAP_THRESHOLD_MS = 220L

    fun reflowVideoTrack(clips: List<VideoClip>): List<VideoClip> {
        var cursorMs = 0L
        return clips.map { clip ->
            val updated = clip.copy(timelineStartMs = cursorMs)
            cursorMs += clip.effectiveDurationMs
            updated
        }
    }

    fun magneticSnap(candidateMs: Long, state: TimelineProjectState): Long {
        if (!state.magneticSnapEnabled) return candidateMs.coerceAtLeast(0L)
        val boundaries = buildList {
            add(0L)
            state.videoClips.forEach {
                add(it.timelineStartMs)
                add(it.timelineStartMs + it.effectiveDurationMs)
            }
            state.audioClips.forEach {
                add(it.timelineStartMs)
                add(it.timelineStartMs + it.effectiveDurationMs)
            }
            state.overlayClips.forEach {
                add(it.timelineStartMs)
                add(it.timelineStartMs + it.durationMs)
            }
            state.textLayers.forEach {
                add(it.startMs)
                add(it.endMs)
            }
        }
        val nearest = boundaries.minByOrNull { abs(it - candidateMs) } ?: return candidateMs.coerceAtLeast(0L)
        return if (abs(nearest - candidateMs) <= MAGNETIC_SNAP_THRESHOLD_MS) nearest else candidateMs.coerceAtLeast(0L)
    }

    fun appendVideoOrPhoto(
        state: TimelineProjectState,
        sourceUri: String,
        title: String,
        mediaType: ClipMediaType,
        durationMs: Long
    ): TimelineProjectState {
        val safeDur = if (mediaType == ClipMediaType.PHOTO) 4_000L else durationMs.coerceAtLeast(1_000L)
        val newClip = VideoClip(
            sourceUri = sourceUri,
            title = title,
            mediaType = mediaType,
            sourceDurationMs = safeDur,
            trimStartMs = 0L,
            trimEndMs = safeDur
        )
        return state.copy(videoClips = reflowVideoTrack(state.videoClips + newClip))
    }

    fun addOverlayClip(
        state: TimelineProjectState,
        sourceUri: String,
        title: String,
        mediaType: ClipMediaType,
        startMs: Long
    ): TimelineProjectState {
        val snappedStart = magneticSnap(startMs, state)
        val overlay = OverlayClip(
            sourceUri = sourceUri,
            title = title,
            mediaType = mediaType,
            timelineStartMs = snappedStart,
            durationMs = 3_500L
        )
        return state.copy(overlayClips = state.overlayClips + overlay)
    }

    fun appendAudioClip(
        state: TimelineProjectState,
        sourceUri: String,
        title: String,
        trackIndex: Int,
        durationMs: Long,
        startMs: Long,
        waveformPeaks: List<Float>
    ): TimelineProjectState {
        val safeDur = durationMs.coerceAtLeast(1_000L)
        val snappedStart = magneticSnap(startMs, state)
        val clip = AudioClip(
            sourceUri = sourceUri,
            title = title,
            trackIndex = trackIndex,
            sourceDurationMs = safeDur,
            trimStartMs = 0L,
            trimEndMs = safeDur,
            timelineStartMs = snappedStart,
            waveformPeaks = waveformPeaks
        )
        return state.copy(audioClips = state.audioClips + clip)
    }

    fun trimVideoClip(
        state: TimelineProjectState,
        clipId: String,
        newTrimStartMs: Long,
        newTrimEndMs: Long
    ): TimelineProjectState {
        val updated = state.videoClips.map { clip ->
            if (clip.id != clipId) clip
            else {
                val clampedStart = newTrimStartMs.coerceIn(0L, clip.sourceDurationMs - 300L)
                val clampedEnd = newTrimEndMs.coerceIn(clampedStart + 300L, clip.sourceDurationMs)
                clip.copy(trimStartMs = clampedStart, trimEndMs = clampedEnd)
            }
        }
        return state.copy(videoClips = reflowVideoTrack(updated))
    }

    fun splitVideoClipAt(
        state: TimelineProjectState,
        clipId: String,
        playheadMs: Long
    ): TimelineProjectState {
        val idx = state.videoClips.indexOfFirst { it.id == clipId }
        if (idx == -1) return state
        val target = state.videoClips[idx]
        val localOffsetTimeline = playheadMs - target.timelineStartMs
        if (localOffsetTimeline <= 250L || localOffsetTimeline >= target.effectiveDurationMs - 250L) {
            return state
        }
        val sourceSplitOffset = (localOffsetTimeline * target.speed).toLong()
        val splitPointSourceMs = (target.trimStartMs + sourceSplitOffset)
            .coerceIn(target.trimStartMs + 250L, target.trimEndMs - 250L)

        val firstHalf = target.copy(
            trimEndMs = splitPointSourceMs,
            transitionToNext = TransitionType.NONE
        )
        val secondHalf = target.copy(
            id = UUID.randomUUID().toString(),
            title = "${target.title} (Part 2)",
            trimStartMs = splitPointSourceMs
        )
        val mutable = state.videoClips.toMutableList()
        mutable.removeAt(idx)
        mutable.add(idx, firstHalf)
        mutable.add(idx + 1, secondHalf)
        return state.copy(videoClips = reflowVideoTrack(mutable))
    }

    fun mergeWithNextClip(state: TimelineProjectState, clipId: String): TimelineProjectState {
        val idx = state.videoClips.indexOfFirst { it.id == clipId }
        if (idx == -1 || idx >= state.videoClips.lastIndex) return state
        val first = state.videoClips[idx]
        val second = state.videoClips[idx + 1]
        if (first.sourceUri != second.sourceUri || first.mediaType != second.mediaType) {
            return state
        }
        val merged = first.copy(
            trimStartMs = minOf(first.trimStartMs, second.trimStartMs),
            trimEndMs = maxOf(first.trimEndMs, second.trimEndMs),
            transitionToNext = second.transitionToNext
        )
        val mutable = state.videoClips.toMutableList()
        mutable[idx] = merged
        mutable.removeAt(idx + 1)
        return state.copy(videoClips = reflowVideoTrack(mutable))
    }

    fun reorderVideoClip(state: TimelineProjectState, clipId: String, delta: Int): TimelineProjectState {
        val idx = state.videoClips.indexOfFirst { it.id == clipId }
        if (idx == -1) return state
        val targetIdx = (idx + delta).coerceIn(0, state.videoClips.lastIndex)
        if (idx == targetIdx) return state
        val mutable = state.videoClips.toMutableList()
        val item = mutable.removeAt(idx)
        mutable.add(targetIdx, item)
        return state.copy(videoClips = reflowVideoTrack(mutable))
    }

    fun duplicateVideoClip(state: TimelineProjectState, clipId: String): TimelineProjectState {
        val idx = state.videoClips.indexOfFirst { it.id == clipId }
        if (idx == -1) return state
        val copy = state.videoClips[idx].copy(
            id = UUID.randomUUID().toString(),
            title = "${state.videoClips[idx].title} (Copy)"
        )
        val mutable = state.videoClips.toMutableList()
        mutable.add(idx + 1, copy)
        return state.copy(videoClips = reflowVideoTrack(mutable))
    }

    fun deleteVideoClip(state: TimelineProjectState, clipId: String): TimelineProjectState {
        val filtered = state.videoClips.filterNot { it.id == clipId }
        return state.copy(videoClips = reflowVideoTrack(filtered))
    }

    fun insertFreezeFrame(state: TimelineProjectState, clipId: String, playheadMs: Long): TimelineProjectState {
        val idx = state.videoClips.indexOfFirst { it.id == clipId }
        if (idx == -1) return state
        val target = state.videoClips[idx]
        val freeze = VideoClip(
            sourceUri = target.sourceUri,
            title = "${target.title} [Freeze]",
            mediaType = ClipMediaType.FREEZE_FRAME,
            sourceDurationMs = 1_500L,
            trimStartMs = 0L,
            trimEndMs = 1_500L,
            colorGrading = target.colorGrading,
            rotationDegrees = target.rotationDegrees,
            flipHorizontal = target.flipHorizontal,
            flipVertical = target.flipVertical
        )
        val mutable = state.videoClips.toMutableList()
        mutable.add(idx + 1, freeze)
        return state.copy(videoClips = reflowVideoTrack(mutable))
    }

    fun updateVideoClip(
        state: TimelineProjectState,
        clipId: String,
        transform: (VideoClip) -> VideoClip
    ): TimelineProjectState {
        val updated = state.videoClips.map { if (it.id == clipId) transform(it) else it }
        return state.copy(videoClips = reflowVideoTrack(updated))
    }

    fun trimAudioClip(
        state: TimelineProjectState,
        audioId: String,
        newTrimStartMs: Long,
        newTrimEndMs: Long
    ): TimelineProjectState {
        val updated = state.audioClips.map { clip ->
            if (clip.id != audioId) clip
            else {
                val start = newTrimStartMs.coerceIn(0L, clip.sourceDurationMs - 300L)
                val end = newTrimEndMs.coerceIn(start + 300L, clip.sourceDurationMs)
                clip.copy(trimStartMs = start, trimEndMs = end)
            }
        }
        return state.copy(audioClips = updated)
    }

    fun splitAudioClipAt(
        state: TimelineProjectState,
        audioId: String,
        playheadMs: Long
    ): TimelineProjectState {
        val idx = state.audioClips.indexOfFirst { it.id == audioId }
        if (idx == -1) return state
        val target = state.audioClips[idx]
        val localOffset = playheadMs - target.timelineStartMs
        if (localOffset <= 250L || localOffset >= target.effectiveDurationMs - 250L) return state
        val splitPoint = target.trimStartMs + localOffset
        val first = target.copy(trimEndMs = splitPoint)
        val second = target.copy(
            id = UUID.randomUUID().toString(),
            title = "${target.title} (Split)",
            trimStartMs = splitPoint,
            timelineStartMs = playheadMs
        )
        val mutable = state.audioClips.toMutableList()
        mutable[idx] = first
        mutable.add(idx + 1, second)
        return state.copy(audioClips = mutable)
    }

    fun updateAudioClip(
        state: TimelineProjectState,
        audioId: String,
        transform: (AudioClip) -> AudioClip
    ): TimelineProjectState {
        return state.copy(
            audioClips = state.audioClips.map { if (it.id == audioId) transform(it) else it }
        )
    }

    fun deleteAudioClip(state: TimelineProjectState, audioId: String): TimelineProjectState {
        return state.copy(audioClips = state.audioClips.filterNot { it.id == audioId })
    }

    fun addTextLayer(
        state: TimelineProjectState,
        text: String,
        fontStyle: EditorFontStyle,
        startMs: Long,
        durationMs: Long = 3_000L,
        animation: TextAnimationPreset = TextAnimationPreset.FADE,
        hasBackground: Boolean = true,
        isSubtitle: Boolean = false
    ): TimelineProjectState {
        val snapped = magneticSnap(startMs, state)
        val layer = TextLayerClip(
            text = text.ifBlank { "Aura Title" },
            fontStyle = fontStyle,
            startMs = snapped,
            endMs = snapped + durationMs.coerceAtLeast(500L),
            animation = animation,
            hasBackground = hasBackground,
            isSubtitle = isSubtitle
        )
        return state.copy(textLayers = state.textLayers + layer)
    }

    fun parseSrtSubtitles(srtContent: String): List<TextLayerClip> {
        val blocks = srtContent.trim().split(Regex("\\r?\\n\\r?\\n"))
        val result = mutableListOf<TextLayerClip>()
        val timeRegex = Regex("""(\d{2}):(\d{2}):(\d{2})[,.](\d{3})\s*-->\s*(\d{2}):(\d{2}):(\d{2})[,.](\d{3})""")
        for (block in blocks) {
            val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
            if (lines.size < 2) continue
            val timeLineIdx = lines.indexOfFirst { timeRegex.containsMatchIn(it) }
            if (timeLineIdx == -1) continue
            val match = timeRegex.find(lines[timeLineIdx]) ?: continue
            val g = match.groupValues
            val startMs = ((g[1].toLong() * 3600 + g[2].toLong() * 60 + g[3].toLong()) * 1000L) + g[4].toLong()
            val endMs = ((g[5].toLong() * 3600 + g[6].toLong() * 60 + g[7].toLong()) * 1000L) + g[8].toLong()
            val captionText = lines.drop(timeLineIdx + 1).joinToString("\n")
            if (captionText.isNotBlank() && endMs > startMs) {
                result.add(
                    TextLayerClip(
                        text = captionText,
                        fontStyle = EditorFontStyle.PLUS_JAKARTA,
                        fontSizeSp = 18f,
                        posX = 0.5f,
                        posY = 0.86f,
                        startMs = startMs,
                        endMs = endMs,
                        animation = TextAnimationPreset.FADE,
                        isSubtitle = true
                    )
                )
            }
        }
        return result
    }

    fun parseCubeLutPreset(lutContent: String, name: String): ColorGradingConfig {
        // Extracts dominant warm/cool/contrast bias from a .cube LUT text file safely
        val lines = lutContent.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("TITLE") && !it.startsWith("LUT_3D_SIZE") && !it.startsWith("DOMAIN") }
            .take(64)
            .toList()
        var rSum = 0f
        var gSum = 0f
        var bSum = 0f
        var count = 0
        for (line in lines) {
            val parts = line.split(Regex("\\s+"))
            if (parts.size >= 3) {
                val r = parts[0].toFloatOrNull()
                val g = parts[1].toFloatOrNull()
                val b = parts[2].toFloatOrNull()
                if (r != null && g != null && b != null) {
                    rSum += r; gSum += g; bSum += b; count++
                }
            }
        }
        if (count == 0) {
            return ColorGradingConfig(contrast = 0.2f, saturation = 0.15f, lutName = name)
        }
        val rAvg = rSum / count
        val gAvg = gSum / count
        val bAvg = bSum / count
        val tempShift = ((rAvg - bAvg) * 0.6f).coerceIn(-0.6f, 0.6f)
        val tintShift = ((gAvg - (rAvg + bAvg) / 2f) * 0.5f).coerceIn(-0.5f, 0.5f)
        return ColorGradingConfig(
            contrast = 0.18f,
            saturation = 0.12f,
            temperature = tempShift,
            tint = tintShift,
            vignette = 0.22f,
            lutName = name
        )
    }

    fun addKeyframeToClip(
        state: TimelineProjectState,
        clipId: String,
        playheadMs: Long
    ): TimelineProjectState {
        return updateVideoClip(state, clipId) { clip ->
            val offset = (playheadMs - clip.timelineStartMs).coerceIn(0L, clip.effectiveDurationMs)
            val kf = KeyframePoint(
                timeOffsetMs = offset,
                scale = clip.scale,
                rotationDeg = clip.rotationDegrees,
                offsetX = clip.offsetX,
                offsetY = clip.offsetY
            )
            val updated = (clip.keyframes.filterNot { abs(it.timeOffsetMs - offset) < 150L } + kf)
                .sortedBy { it.timeOffsetMs }
            clip.copy(keyframes = updated)
        }
    }

    fun setAspectRatio(state: TimelineProjectState, ratio: AspectRatioPreset): TimelineProjectState {
        return state.copy(aspectRatio = ratio)
    }

    fun updateTrackControls(state: TimelineProjectState, controls: TrackControls): TimelineProjectState {
        return state.copy(trackControls = controls)
    }
}
