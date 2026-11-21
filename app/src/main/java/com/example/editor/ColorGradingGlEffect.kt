package com.example.editor

import android.content.Context
import android.opengl.GLES20
import androidx.annotation.OptIn
import androidx.media3.common.VideoFrameProcessingException
import androidx.media3.common.util.GlProgram
import androidx.media3.common.util.GlUtil
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.GlShaderProgram
import com.example.domain.model.ColorGradingConfig
import com.example.domain.model.VideoEffectPreset

/**
 * Custom OpenGL ES 2.0 Media3 GlEffect implementing professional Color Grading
 * (Exposure, Contrast, Brightness, Saturation, Temperature, Tint, Highlights,
 * Shadows, Vignette, Sharpening) + Glitch / Cinematic / Noir / Blur-style effects
 * for both real-time ExoPlayer preview and Media3 Transformer export.
 */
@OptIn(UnstableApi::class)
class AuraColorGradingGlEffect(
    private val grading: ColorGradingConfig,
    private val effectPreset: VideoEffectPreset,
    private val effectIntensity: Float,
    private val fadeInMs: Long,
    private val fadeOutMs: Long,
    private val clipDurationMs: Long
) : GlEffect {

    override fun toGlShaderProgram(context: Context, useHdr: Boolean): GlShaderProgram {
        return AuraColorGradingShaderProgram(
            useHdr = useHdr,
            grading = grading,
            effectPreset = effectPreset,
            effectIntensity = effectIntensity,
            fadeInMs = fadeInMs,
            fadeOutMs = fadeOutMs,
            clipDurationMs = clipDurationMs
        )
    }

    override fun isNoOp(inputWidth: Int, inputHeight: Int): Boolean {
        return grading == ColorGradingConfig() &&
            effectPreset == VideoEffectPreset.NONE &&
            fadeInMs <= 0L &&
            fadeOutMs <= 0L
    }
}

@OptIn(UnstableApi::class)
private class AuraColorGradingShaderProgram(
    useHdr: Boolean,
    private val grading: ColorGradingConfig,
    private val effectPreset: VideoEffectPreset,
    private val effectIntensity: Float,
    private val fadeInMs: Long,
    private val fadeOutMs: Long,
    private val clipDurationMs: Long
) : BaseGlShaderProgram(useHdr, /* texturePoolCapacity= */ 1) {

    private var glProgram: GlProgram? = null
    private var texWidth = 1
    private var texHeight = 1

    init {
        try {
            val program = GlProgram(VERTEX_SHADER, FRAGMENT_SHADER)
            program.setBufferAttribute(
                "aFramePosition",
                GlUtil.getNormalizedCoordinateBounds(),
                GlUtil.HOMOGENEOUS_COORDINATE_VECTOR_SIZE
            )
            glProgram = program
        } catch (e: Exception) {
            throw VideoFrameProcessingException(e)
        }
    }

    override fun configure(inputWidth: Int, inputHeight: Int): Size {
        texWidth = inputWidth.coerceAtLeast(1)
        texHeight = inputHeight.coerceAtLeast(1)
        return Size(texWidth, texHeight)
    }

    override fun drawFrame(inputTexId: Int, presentationTimeUs: Long) {
        val program = glProgram ?: return
        try {
            program.use()
            program.setSamplerTexIdUniform("uTexSampler", inputTexId, 0)
            program.setFloatsUniform("uTexelSize", floatArrayOf(1f / texWidth, 1f / texHeight))
            program.setFloatUniform("uExposure", grading.exposure)
            program.setFloatUniform("uContrast", 1f + grading.contrast * 0.65f)
            program.setFloatUniform("uBrightness", grading.brightness * 0.45f)
            program.setFloatUniform("uSaturation", 1f + grading.saturation)
            program.setFloatUniform("uTemperature", grading.temperature * 0.25f)
            program.setFloatUniform("uTint", grading.tint * 0.20f)
            program.setFloatUniform("uHighlights", grading.highlights * 0.35f)
            program.setFloatUniform("uShadows", grading.shadows * 0.35f)
            program.setFloatUniform("uVignette", grading.vignette)
            program.setFloatUniform("uSharpen", grading.sharpening)
            program.setFloatUniform("uTimeSec", presentationTimeUs / 1_000_000f)

            val presetMode = when (effectPreset) {
                VideoEffectPreset.NONE -> 0f
                VideoEffectPreset.BLUR -> 1f
                VideoEffectPreset.GLITCH -> 2f
                VideoEffectPreset.CINEMATIC -> 3f
                VideoEffectPreset.NOIR -> 4f
                VideoEffectPreset.ANIMATED_GLOW -> 5f
            }
            program.setFloatUniform("uPresetMode", presetMode)
            program.setFloatUniform("uPresetIntensity", effectIntensity)

            val timeMs = presentationTimeUs / 1000L
            var alpha = 1f
            if (fadeInMs > 0L && timeMs < fadeInMs) {
                alpha *= (timeMs.toFloat() / fadeInMs.toFloat()).coerceIn(0f, 1f)
            }
            if (fadeOutMs > 0L && clipDurationMs > fadeOutMs && timeMs > clipDurationMs - fadeOutMs) {
                alpha *= ((clipDurationMs - timeMs).toFloat() / fadeOutMs.toFloat()).coerceIn(0f, 1f)
            }
            program.setFloatUniform("uFadeAlpha", alpha)

            program.bindAttributesAndUniforms()
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        } catch (e: Exception) {
            throw VideoFrameProcessingException(e)
        }
    }

    override fun release() {
        super.release()
        runCatching { glProgram?.delete() }
        glProgram = null
    }

    companion object {
        private const val VERTEX_SHADER = """
            attribute vec4 aFramePosition;
            varying vec2 vTexSamplingCoord;
            void main() {
                gl_Position = aFramePosition;
                vTexSamplingCoord = aFramePosition.xy * 0.5 + 0.5;
            }
        """

        private const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform sampler2D uTexSampler;
            uniform vec2 uTexelSize;
            uniform float uExposure;
            uniform float uContrast;
            uniform float uBrightness;
            uniform float uSaturation;
            uniform float uTemperature;
            uniform float uTint;
            uniform float uHighlights;
            uniform float uShadows;
            uniform float uVignette;
            uniform float uSharpen;
            uniform float uTimeSec;
            uniform float uPresetMode;
            uniform float uPresetIntensity;
            uniform float uFadeAlpha;
            varying vec2 vTexSamplingCoord;

            void main() {
                vec2 uv = vTexSamplingCoord;

                // Glitch horizontal RGB shift
                if (uPresetMode > 1.5 && uPresetMode < 2.5) {
                    float jitter = sin(uv.y * 42.0 + uTimeSec * 18.0) * 0.012 * uPresetIntensity;
                    float r = texture2D(uTexSampler, clamp(uv + vec2(jitter, 0.0), 0.0, 1.0)).r;
                    float g = texture2D(uTexSampler, uv).g;
                    float b = texture2D(uTexSampler, clamp(uv - vec2(jitter, 0.0), 0.0, 1.0)).b;
                    gl_FragColor = vec4(vec3(r, g, b) * uFadeAlpha, 1.0);
                    return;
                }

                vec4 base = texture2D(uTexSampler, uv);
                vec3 color = base.rgb;

                // Soft box blur preset
                if (uPresetMode > 0.5 && uPresetMode < 1.5) {
                    vec2 off = uTexelSize * (2.5 * uPresetIntensity + 0.5);
                    vec3 sum = color;
                    sum += texture2D(uTexSampler, clamp(uv + vec2(off.x, 0.0), 0.0, 1.0)).rgb;
                    sum += texture2D(uTexSampler, clamp(uv - vec2(off.x, 0.0), 0.0, 1.0)).rgb;
                    sum += texture2D(uTexSampler, clamp(uv + vec2(0.0, off.y), 0.0, 1.0)).rgb;
                    sum += texture2D(uTexSampler, clamp(uv - vec2(0.0, off.y), 0.0, 1.0)).rgb;
                    color = sum / 5.0;
                }

                // Sharpening unsharp mask
                if (uSharpen > 0.01) {
                    vec3 n = texture2D(uTexSampler, clamp(uv + vec2(0.0, uTexelSize.y), 0.0, 1.0)).rgb;
                    vec3 s = texture2D(uTexSampler, clamp(uv - vec2(0.0, uTexelSize.y), 0.0, 1.0)).rgb;
                    vec3 e = texture2D(uTexSampler, clamp(uv + vec2(uTexelSize.x, 0.0), 0.0, 1.0)).rgb;
                    vec3 w = texture2D(uTexSampler, clamp(uv - vec2(uTexelSize.x, 0.0), 0.0, 1.0)).rgb;
                    vec3 lap = (4.0 * color - (n + s + e + w));
                    color = clamp(color + lap * (uSharpen * 0.75), 0.0, 1.0);
                }

                // Exposure & Brightness
                color *= pow(2.0, uExposure);
                color += uBrightness;

                // Contrast around 0.5 midpoint
                color = (color - 0.5) * uContrast + 0.5;

                // Highlights & Shadows
                float luma = dot(color, vec3(0.299, 0.587, 0.114));
                float shadowMask = 1.0 - smoothstep(0.0, 0.55, luma);
                float highlightMask = smoothstep(0.45, 1.0, luma);
                color += vec3(uShadows) * shadowMask;
                color += vec3(uHighlights) * highlightMask;

                // Temperature (Warm/Cool) & Tint (Green/Magenta)
                color.r += uTemperature;
                color.b -= uTemperature;
                color.g += uTint;

                // Saturation
                float gLuma = dot(color, vec3(0.299, 0.587, 0.114));
                color = mix(vec3(gLuma), color, uSaturation);

                // Cinematic Teal & Orange
                if (uPresetMode > 2.5 && uPresetMode < 3.5) {
                    vec3 shadowsTone = vec3(0.06, 0.20, 0.28);
                    vec3 highTone = vec3(1.05, 0.90, 0.76);
                    vec3 cine = mix(color * (1.0 - 0.25) + shadowsTone * 0.25, color * highTone, smoothstep(0.2, 0.85, gLuma));
                    color = mix(color, cine, uPresetIntensity);
                }

                // Obsidian Noir
                if (uPresetMode > 3.5 && uPresetMode < 4.5) {
                    vec3 bw = vec3(pow(clamp(gLuma, 0.0, 1.0), 1.15));
                    color = mix(color, bw, uPresetIntensity);
                }

                // Animated Liquid Glow
                if (uPresetMode > 4.5) {
                    float pulse = 0.5 + 0.5 * sin(uTimeSec * 2.5);
                    color += vec3(0.08, 0.16, 0.25) * pulse * uPresetIntensity;
                }

                // Vignette
                if (uVignette > 0.01) {
                    float dist = distance(uv, vec2(0.5, 0.5));
                    float vig = smoothstep(0.85, 0.30, dist * (0.85 + uVignette * 0.7));
                    color *= mix(1.0, vig, uVignette);
                }

                color = clamp(color * uFadeAlpha, 0.0, 1.0);
                gl_FragColor = vec4(color, base.a);
            }
        """
    }
}
