package com.example.domain.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class AspectRatioPreset(val label: String, val widthRatio: Int, val heightRatio: Int) {
    RATIO_16_9("16:9", 16, 9),
    RATIO_9_16("9:16", 9, 16),
    RATIO_1_1("1:1", 1, 1),
    RATIO_4_5("4:5", 4, 5),
    RATIO_21_9("21:9", 21, 9)
}

@Serializable
enum class ClipMediaType {
    VIDEO,
    PHOTO,
    FREEZE_FRAME
}

@Serializable
enum class TransitionType(val label: String) {
    NONE("Cut"),
    CROSS_DISSOLVE("Cross Dissolve"),
    ZOOM_IN("Zoom In"),
    ZOOM_OUT("Zoom Out"),
    FADE_BLACK("Fade Black")
}

@Serializable
enum class VideoEffectPreset(val label: String) {
    NONE("Original"),
    BLUR("Gaussian Blur"),
    GLITCH("Cyber Glitch"),
    CINEMATIC("Cinema Teal/Orange"),
    NOIR("Obsidian Noir"),
    ANIMATED_GLOW("Liquid Glow")
}

@Serializable
enum class SpeedRampPreset(val label: String) {
    NONE("Constant"),
    RAMP_IN("Ramp In"),
    RAMP_OUT("Ramp Out"),
    HERO_DIP("Hero Slow-Mo Dip"),
    PULSE("Beat Pulse")
}

@Serializable
enum class EditorFontStyle(val label: String) {
    OUTFIT("Outfit Display"),
    PLUS_JAKARTA("Jakarta Sans"),
    JETBRAINS_MONO("JetBrains Mono")
}

@Serializable
enum class TextAnimationPreset(val label: String) {
    NONE("Static"),
    FADE("Fade In/Out"),
    SLIDE_UP("Slide Up"),
    SCALE_POP("Scale Pop"),
    TYPEWRITER("Typewriter")
}

@Serializable
data class CropRect(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f
)

@Serializable
data class KeyframePoint(
    val id: String = UUID.randomUUID().toString(),
    val timeOffsetMs: Long,
    val scale: Float = 1f,
    val rotationDeg: Float = 0f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val opacity: Float = 1f
)

@Serializable
data class ColorGradingConfig(
    val exposure: Float = 0f,       // -1f..1f
    val contrast: Float = 0f,       // -1f..1f
    val brightness: Float = 0f,     // -1f..1f
    val saturation: Float = 0f,     // -1f..1f
    val temperature: Float = 0f,    // -1f..1f
    val tint: Float = 0f,           // -1f..1f
    val highlights: Float = 0f,     // -1f..1f
    val shadows: Float = 0f,        // -1f..1f
    val vignette: Float = 0f,       // 0f..1f
    val sharpening: Float = 0f,     // 0f..1f
    val lutName: String? = null,
    val lutUri: String? = null
)

@Serializable
data class VideoClip(
    val id: String = UUID.randomUUID().toString(),
    val sourceUri: String,
    val title: String,
    val mediaType: ClipMediaType = ClipMediaType.VIDEO,
    val sourceDurationMs: Long = 5_000L,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 5_000L,
    val timelineStartMs: Long = 0L,
    val crop: CropRect = CropRect(),
    val rotationDegrees: Float = 0f,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val isReversed: Boolean = false,
    val speed: Float = 1f,
    val speedRampPreset: SpeedRampPreset = SpeedRampPreset.NONE,
    val isMuted: Boolean = false,
    val volume: Float = 1f,
    val fadeInMs: Long = 0L,
    val fadeOutMs: Long = 0L,
    val effectPreset: VideoEffectPreset = VideoEffectPreset.NONE,
    val effectIntensity: Float = 0.65f,
    val colorGrading: ColorGradingConfig = ColorGradingConfig(),
    val transitionToNext: TransitionType = TransitionType.NONE,
    val transitionDurationMs: Long = 600L,
    val keyframes: List<KeyframePoint> = emptyList()
) {
    val effectiveDurationMs: Long
        get() = (((trimEndMs - trimStartMs).coerceAtLeast(250L)) / speed.coerceIn(0.25f, 4f)).toLong()
}

@Serializable
data class OverlayClip(
    val id: String = UUID.randomUUID().toString(),
    val sourceUri: String,
    val title: String,
    val mediaType: ClipMediaType = ClipMediaType.PHOTO,
    val timelineStartMs: Long = 0L,
    val durationMs: Long = 3_000L,
    val posX: Float = 0.72f,
    val posY: Float = 0.18f,
    val pipScale: Float = 0.30f,
    val opacity: Float = 1f,
    val rotationDegrees: Float = 0f,
    val fadeInMs: Long = 250L,
    val fadeOutMs: Long = 250L,
    val keyframes: List<KeyframePoint> = emptyList()
)

@Serializable
data class AudioClip(
    val id: String = UUID.randomUUID().toString(),
    val sourceUri: String,
    val title: String,
    val trackIndex: Int = 1, // 1 = Music, 2 = Voice-Over
    val sourceDurationMs: Long = 10_000L,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 10_000L,
    val timelineStartMs: Long = 0L,
    val syncOffsetMs: Long = 0L,
    val volume: Float = 0.85f,
    val fadeInMs: Long = 300L,
    val fadeOutMs: Long = 500L,
    val waveformPeaks: List<Float> = emptyList()
) {
    val effectiveDurationMs: Long
        get() = (trimEndMs - trimStartMs).coerceAtLeast(250L)
}

@Serializable
data class TextLayerClip(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val fontStyle: EditorFontStyle = EditorFontStyle.OUTFIT,
    val fontSizeSp: Float = 26f,
    val colorHex: Long = 0xFFFFFFFF,
    val backgroundColorHex: Long = 0xAA0F1524,
    val hasBackground: Boolean = true,
    val posX: Float = 0.5f,
    val posY: Float = 0.80f,
    val startMs: Long = 0L,
    val endMs: Long = 3_000L,
    val animation: TextAnimationPreset = TextAnimationPreset.FADE,
    val isSubtitle: Boolean = false
)

@Serializable
data class TrackControls(
    val videoVisible: Boolean = true,
    val videoMuted: Boolean = false,
    val overlayVisible: Boolean = true,
    val musicMuted: Boolean = false,
    val voiceOverMuted: Boolean = false,
    val textVisible: Boolean = true
)

@Serializable
data class TimelineProjectState(
    val projectId: String = UUID.randomUUID().toString(),
    val name: String = "Untitled Studio Project",
    val aspectRatio: AspectRatioPreset = AspectRatioPreset.RATIO_16_9,
    val videoClips: List<VideoClip> = emptyList(),
    val overlayClips: List<OverlayClip> = emptyList(),
    val audioClips: List<AudioClip> = emptyList(),
    val textLayers: List<TextLayerClip> = emptyList(),
    val trackControls: TrackControls = TrackControls(),
    val magneticSnapEnabled: Boolean = true,
    val timelineZoom: Float = 1f,
    val playheadMs: Long = 0L
) {
    val totalDurationMs: Long
        get() {
            val videoEnd = videoClips.maxOfOrNull { it.timelineStartMs + it.effectiveDurationMs } ?: 0L
            val overlayEnd = overlayClips.maxOfOrNull { it.timelineStartMs + it.durationMs } ?: 0L
            val audioEnd = audioClips.maxOfOrNull { it.timelineStartMs + it.effectiveDurationMs } ?: 0L
            val textEnd = textLayers.maxOfOrNull { it.endMs } ?: 0L
            return maxOf(videoEnd, overlayEnd, audioEnd, textEnd)
        }
}
