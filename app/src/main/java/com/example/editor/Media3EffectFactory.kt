package com.example.editor

import android.content.Context
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import android.text.SpannableString
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Effect
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.Crop
import androidx.media3.effect.MatrixTransformation
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.effect.StaticOverlaySettings
import androidx.media3.effect.TextOverlay
import androidx.media3.effect.TextureOverlay
import com.example.core.media.ExportResolution
import com.example.domain.model.AspectRatioPreset
import com.example.domain.model.OverlayClip
import com.example.domain.model.SpeedRampPreset
import com.example.domain.model.TextLayerClip
import com.example.domain.model.TimelineProjectState
import com.example.domain.model.TransitionType
import com.example.domain.model.VideoClip
import java.nio.ByteBuffer
import kotlin.math.roundToInt

object Media3EffectFactory {

    @OptIn(UnstableApi::class)
    fun buildClipVideoEffects(
        clip: VideoClip,
        textLayers: List<TextLayerClip> = emptyList(),
        overlayClips: List<OverlayClip> = emptyList(),
        context: Context? = null,
        incomingTransition: TransitionType = TransitionType.NONE,
        incomingTransitionDurationMs: Long = 0L
    ): List<Effect> {
        val effects = mutableListOf<Effect>()

        // 1. Crop (convert 0..1 normalized UI crop to Media3 -1..1 NDC bounds)
        val c = clip.crop
        if (c.left > 0.01f || c.top > 0.01f || c.right < 0.99f || c.bottom < 0.99f) {
            val ndcLeft = (c.left * 2f - 1f).coerceIn(-1f, 0.8f)
            val ndcRight = (c.right * 2f - 1f).coerceIn(ndcLeft + 0.1f, 1f)
            val ndcTop = (1f - c.top * 2f).coerceIn(-0.8f, 1f)
            val ndcBottom = (1f - c.bottom * 2f).coerceIn(-1f, ndcTop - 0.1f)
            effects.add(Crop(ndcLeft, ndcRight, ndcBottom, ndcTop))
        }

        // 2. Scale, Flip, and Rotation
        val flipX = if (clip.flipHorizontal) -1f else 1f
        val flipY = if (clip.flipVertical) -1f else 1f
        if (clip.rotationDegrees != 0f || clip.scale != 1f || clip.flipHorizontal || clip.flipVertical) {
            effects.add(
                ScaleAndRotateTransformation.Builder()
                    .setRotationDegrees(clip.rotationDegrees)
                    .setScale(clip.scale * flipX, clip.scale * flipY)
                    .build()
            )
        }

        // 3. Keyframe / Zoom Transition / Offset Matrix Transformation
        if (clip.keyframes.isNotEmpty() ||
            clip.transitionToNext == TransitionType.ZOOM_IN ||
            clip.transitionToNext == TransitionType.ZOOM_OUT ||
            clip.offsetX != 0f || clip.offsetY != 0f
        ) {
            val durationUs = (clip.effectiveDurationMs * 1000L).coerceAtLeast(1_000L)
            val transUs = (clip.transitionDurationMs * 1000L).coerceAtMost(durationUs / 2)
            effects.add(MatrixTransformation { presentationTimeUs ->
                val matrix = Matrix()
                var scale = 1f
                var tx = clip.offsetX
                var ty = clip.offsetY
                var rot = 0f

                if (clip.keyframes.size >= 2) {
                    val tMs = presentationTimeUs / 1000L
                    val sorted = clip.keyframes.sortedBy { it.timeOffsetMs }
                    val afterIdx = sorted.indexOfFirst { it.timeOffsetMs >= tMs }
                    when {
                        afterIdx <= 0 -> {
                            val kf = sorted.first()
                            scale = kf.scale; tx = kf.offsetX; ty = kf.offsetY; rot = kf.rotationDeg
                        }
                        afterIdx > sorted.lastIndex -> {
                            val kf = sorted.last()
                            scale = kf.scale; tx = kf.offsetX; ty = kf.offsetY; rot = kf.rotationDeg
                        }
                        else -> {
                            val a = sorted[afterIdx - 1]
                            val b = sorted[afterIdx]
                            val span = (b.timeOffsetMs - a.timeOffsetMs).coerceAtLeast(1L).toFloat()
                            val f = ((tMs - a.timeOffsetMs) / span).coerceIn(0f, 1f)
                            scale = a.scale + (b.scale - a.scale) * f
                            tx = a.offsetX + (b.offsetX - a.offsetX) * f
                            ty = a.offsetY + (b.offsetY - a.offsetY) * f
                            rot = a.rotationDeg + (b.rotationDeg - a.rotationDeg) * f
                        }
                    }
                }

                if (clip.transitionToNext == TransitionType.ZOOM_IN && presentationTimeUs > durationUs - transUs) {
                    val p = ((presentationTimeUs - (durationUs - transUs)).toFloat() / transUs.toFloat()).coerceIn(0f, 1f)
                    scale *= (1f + 0.35f * p)
                } else if (clip.transitionToNext == TransitionType.ZOOM_OUT && presentationTimeUs > durationUs - transUs) {
                    val p = ((presentationTimeUs - (durationUs - transUs)).toFloat() / transUs.toFloat()).coerceIn(0f, 1f)
                    scale *= (1f - 0.25f * p).coerceAtLeast(0.5f)
                }

                matrix.postScale(scale, scale)
                matrix.postRotate(rot)
                matrix.postTranslate(tx, ty)
                matrix
            })
        }

        // 4. OpenGL ES Color Grading + Video Effect Preset + Fade In/Out + Cross Dissolve / Fade Black
        val effectiveFadeIn = if (incomingTransition == TransitionType.CROSS_DISSOLVE ||
            incomingTransition == TransitionType.FADE_BLACK
        ) {
            maxOf(clip.fadeInMs, incomingTransitionDurationMs / 2L)
        } else clip.fadeInMs

        val effectiveFadeOut = if (clip.transitionToNext == TransitionType.CROSS_DISSOLVE ||
            clip.transitionToNext == TransitionType.FADE_BLACK
        ) {
            maxOf(clip.fadeOutMs, clip.transitionDurationMs / 2L)
        } else clip.fadeOutMs

        val glEffect = AuraColorGradingGlEffect(
            grading = clip.colorGrading,
            effectPreset = clip.effectPreset,
            effectIntensity = clip.effectIntensity,
            fadeInMs = effectiveFadeIn,
            fadeOutMs = effectiveFadeOut,
            clipDurationMs = clip.effectiveDurationMs
        )
        effects.add(glEffect)

        // 5. Picture-in-Picture (Track V2) + Text/Subtitle Overlays (Track T1) overlapping this clip
        val clipEndMs = clip.timelineStartMs + clip.effectiveDurationMs
        val overlays = mutableListOf<TextureOverlay>()

        if (context != null) {
            val overlappingPip = overlayClips.filter { ov ->
                ov.timelineStartMs + ov.durationMs > clip.timelineStartMs && ov.timelineStartMs < clipEndMs
            }
            overlappingPip.forEach { ov ->
                runCatching {
                    val ndcX = (ov.posX * 2f - 1f).coerceIn(-0.85f, 0.85f)
                    val ndcY = (1f - ov.posY * 2f).coerceIn(-0.85f, 0.85f)
                    val settings = StaticOverlaySettings.Builder()
                        .setBackgroundFrameAnchor(ndcX, ndcY)
                        .setOverlayFrameAnchor(0f, 0f)
                        .setScale(ov.pipScale.coerceIn(0.15f, 0.9f), ov.pipScale.coerceIn(0.15f, 0.9f))
                        .setRotationDegrees(ov.rotationDegrees)
                        .setAlphaScale(ov.opacity.coerceIn(0.1f, 1f))
                        .build()
                    BitmapOverlay.createStaticBitmapOverlay(context, Uri.parse(ov.sourceUri), settings)
                }.getOrNull()?.let(overlays::add)
            }
        }

        val overlappingText = textLayers.filter { layer ->
            layer.endMs > clip.timelineStartMs && layer.startMs < clipEndMs
        }
        overlappingText.forEach { layer ->
            val spannable = SpannableString(layer.text).apply {
                setSpan(
                    ForegroundColorSpan(layer.colorHex.toInt()),
                    0,
                    length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                if (layer.hasBackground) {
                    setSpan(
                        BackgroundColorSpan(Color.argb(180, 15, 21, 36)),
                        0,
                        length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }
            val ndcX = (layer.posX * 2f - 1f).coerceIn(-0.9f, 0.9f)
            val ndcY = (1f - layer.posY * 2f).coerceIn(-0.9f, 0.9f)
            val settings = StaticOverlaySettings.Builder()
                .setOverlayFrameAnchor(0f, 0f)
                .setBackgroundFrameAnchor(ndcX, ndcY)
                .build()
            overlays.add(TextOverlay.createStaticTextOverlay(spannable, settings))
        }

        if (overlays.isNotEmpty()) {
            effects.add(OverlayEffect(overlays))
        }

        return effects
    }

    @OptIn(UnstableApi::class)
    fun buildSpeedProvider(
        baseSpeed: Float,
        rampPreset: SpeedRampPreset,
        clipDurationUs: Long
    ): SpeedProvider {
        val safeBase = baseSpeed.coerceIn(0.25f, 4.0f)
        val safeDurUs = clipDurationUs.coerceAtLeast(250_000L)
        return object : SpeedProvider {
            override fun getSpeed(timeUs: Long): Float {
                val ratio = (timeUs.toFloat() / safeDurUs.toFloat()).coerceIn(0f, 1f)
                return when (rampPreset) {
                    SpeedRampPreset.NONE -> safeBase
                    SpeedRampPreset.RAMP_IN -> {
                        val factor = if (ratio < 0.4f) 0.5f + (ratio / 0.4f) * 0.5f else 1f
                        (safeBase * factor).coerceIn(0.25f, 4.0f)
                    }
                    SpeedRampPreset.RAMP_OUT -> {
                        val factor = if (ratio > 0.6f) 1f - ((ratio - 0.6f) / 0.4f) * 0.5f else 1f
                        (safeBase * factor).coerceIn(0.25f, 4.0f)
                    }
                    SpeedRampPreset.HERO_DIP -> {
                        val factor = if (ratio in 0.30f..0.70f) 0.40f else 1.35f
                        (safeBase * factor).coerceIn(0.25f, 4.0f)
                    }
                    SpeedRampPreset.PULSE -> {
                        val stepIndex = (timeUs / 500_000L).toInt()
                        val factor = if (stepIndex % 2 == 0) 1.25f else 0.75f
                        (safeBase * factor).coerceIn(0.25f, 4.0f)
                    }
                }
            }

            override fun getNextSpeedChangeTimeUs(timeUs: Long): Long {
                if (rampPreset == SpeedRampPreset.NONE) return C.TIME_UNSET
                val nextStep = ((timeUs / 100_000L) + 1L) * 100_000L
                return if (nextStep >= safeDurUs) C.TIME_UNSET else nextStep
            }
        }
    }

    @OptIn(UnstableApi::class)
    fun buildCompositionPresentation(
        aspectRatio: AspectRatioPreset,
        resolution: ExportResolution
    ): Presentation {
        val ratio = aspectRatio.widthRatio.toFloat() / aspectRatio.heightRatio.toFloat()
        val (w, h) = when {
            aspectRatio.widthRatio > aspectRatio.heightRatio -> {
                val height = resolution.shortSide
                val width = ((height * ratio).toInt() / 2) * 2
                width to height
            }
            aspectRatio.widthRatio < aspectRatio.heightRatio -> {
                val width = resolution.shortSide
                val height = ((width / ratio).toInt() / 2) * 2
                width to height
            }
            else -> resolution.shortSide to resolution.shortSide
        }
        return Presentation.createForWidthAndHeight(
            w.coerceAtLeast(360),
            h.coerceAtLeast(360),
            Presentation.LAYOUT_SCALE_TO_FIT
        )
    }

    @OptIn(UnstableApi::class)
    fun buildVolumeAudioProcessor(
        volume: Float,
        fadeInMs: Long = 0L,
        fadeOutMs: Long = 0L,
        clipDurationMs: Long = 0L
    ): AudioProcessor {
        return FadeVolumeAudioProcessor(
            targetGain = volume.coerceIn(0f, 2f),
            fadeInMs = fadeInMs,
            fadeOutMs = fadeOutMs,
            clipDurationMs = clipDurationMs
        )
    }

    fun estimateOutputSizeMb(project: TimelineProjectState, resolution: ExportResolution): Double {
        val seconds = (project.totalDurationMs / 1000.0).coerceAtLeast(1.0)
        val totalMbps = resolution.recommendedBitrateMbps + 0.192
        return (seconds * totalMbps) / 8.0
    }
}

/**
 * Custom PCM 16-bit Media3 AudioProcessor supporting clip volume gain + linear fade-in/fade-out ramps.
 */
@OptIn(UnstableApi::class)
class FadeVolumeAudioProcessor(
    private val targetGain: Float,
    private val fadeInMs: Long,
    private val fadeOutMs: Long,
    private val clipDurationMs: Long
) : BaseAudioProcessor() {

    private var processedFrames: Long = 0L

    override fun onConfigure(
        inputAudioFormat: AudioProcessor.AudioFormat
    ): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val outputBuffer = replaceOutputBuffer(remaining)
        val bytesPerFrame = inputAudioFormat.bytesPerFrame.coerceAtLeast(2)
        val sampleRate = inputAudioFormat.sampleRate.coerceAtLeast(1)

        while (inputBuffer.remaining() >= bytesPerFrame) {
            val currentTimeMs = (processedFrames * 1000L) / sampleRate
            var fadeMultiplier = 1f
            if (fadeInMs > 0L && currentTimeMs < fadeInMs) {
                fadeMultiplier *= (currentTimeMs.toFloat() / fadeInMs.toFloat()).coerceIn(0f, 1f)
            }
            if (fadeOutMs > 0L && clipDurationMs > fadeOutMs && currentTimeMs > clipDurationMs - fadeOutMs) {
                fadeMultiplier *= ((clipDurationMs - currentTimeMs).toFloat() / fadeOutMs.toFloat()).coerceIn(0f, 1f)
            }
            val gain = (targetGain * fadeMultiplier).coerceIn(0f, 2f)

            for (ch in 0 until inputAudioFormat.channelCount) {
                if (inputBuffer.remaining() >= 2) {
                    val sample = inputBuffer.short
                    val scaled = (sample * gain).roundToInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                    outputBuffer.putShort(scaled.toShort())
                }
            }
            processedFrames++
        }
        outputBuffer.flip()
    }

    override fun onFlush(streamMetadata: AudioProcessor.StreamMetadata) {
        processedFrames = 0L
    }

    override fun onReset() {
        processedFrames = 0L
    }
}
