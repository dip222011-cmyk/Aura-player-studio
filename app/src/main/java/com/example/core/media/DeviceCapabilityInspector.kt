package com.example.core.media

import android.media.MediaCodecInfo
import android.media.MediaCodecList
import androidx.media3.common.MimeTypes
import javax.inject.Inject
import javax.inject.Singleton

enum class ExportResolution(val label: String, val shortSide: Int, val longSide: Int, val recommendedBitrateMbps: Int) {
    HD_720P("720p HD", 720, 1280, 6),
    FHD_1080P("1080p Full HD", 1080, 1920, 12),
    QHD_2K("2K QHD", 1440, 2560, 22),
    UHD_4K("4K Ultra HD", 2160, 3840, 40)
}

enum class ExportCodec(val label: String, val mimeType: String) {
    H264("H.264 (AVC)", MimeTypes.VIDEO_H264),
    H265("H.265 (HEVC)", MimeTypes.VIDEO_H265)
}

enum class ExportFps(val fps: Int, val label: String) {
    FPS_24(24, "24 FPS (Cinema)"),
    FPS_30(30, "30 FPS (Standard)"),
    FPS_60(60, "60 FPS (Smooth)")
}

data class ExportCapabilities(
    val supportsH264: Boolean,
    val supportsH265: Boolean,
    val supportedResolutions: Set<ExportResolution>,
    val supportedFps: Set<ExportFps>,
    val maxSupportedWidth: Int,
    val maxSupportedHeight: Int,
    val maxSupportedFps: Int,
    val encoderSummary: String
)

@Singleton
class DeviceCapabilityInspector @Inject constructor() {

    fun inspect(): ExportCapabilities {
        val encoders = runCatching {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { it.isEncoder }
        }.getOrDefault(emptyList())

        val avcEncoders = encoders.filter { info ->
            info.supportedTypes.any { it.equals(MimeTypes.VIDEO_H264, ignoreCase = true) }
        }
        val hevcEncoders = encoders.filter { info ->
            info.supportedTypes.any { it.equals(MimeTypes.VIDEO_H265, ignoreCase = true) }
        }

        val supportsH264 = avcEncoders.isNotEmpty()
        val supportsH265 = hevcEncoders.isNotEmpty()

        var maxW = 1920
        var maxH = 1080
        var maxFps = 30

        val allVideoEncoders = avcEncoders + hevcEncoders
        if (allVideoEncoders.isNotEmpty()) {
            var foundW = 0
            var foundH = 0
            var foundFps = 0
            allVideoEncoders.forEach { info ->
                val type = info.supportedTypes.firstOrNull {
                    it.equals(MimeTypes.VIDEO_H264, true) || it.equals(MimeTypes.VIDEO_H265, true)
                } ?: return@forEach
                runCatching {
                    val caps = info.getCapabilitiesForType(type).videoCapabilities
                    if (caps != null) {
                        foundW = maxOf(foundW, caps.supportedWidths.upper)
                        foundH = maxOf(foundH, caps.supportedHeights.upper)
                        foundFps = maxOf(foundFps, caps.supportedFrameRates.upper.toInt())
                    }
                }
            }
            if (foundW > 0) maxW = foundW
            if (foundH > 0) maxH = foundH
            if (foundFps > 0) maxFps = foundFps
        }

        val supportedRes = buildSet {
            add(ExportResolution.HD_720P)
            if (maxW >= 1920 && maxH >= 1080) add(ExportResolution.FHD_1080P)
            if (maxW >= 2560 && maxH >= 1440) add(ExportResolution.QHD_2K)
            if (maxW >= 3840 && maxH >= 2160) add(ExportResolution.UHD_4K)
        }

        val supportedFps = buildSet {
            add(ExportFps.FPS_24)
            add(ExportFps.FPS_30)
            if (maxFps >= 60) add(ExportFps.FPS_60)
        }

        val primaryEncoder = (avcEncoders.firstOrNull { !isSoftwareOnly(it) }
            ?: avcEncoders.firstOrNull())?.name ?: "Platform Default"

        return ExportCapabilities(
            supportsH264 = supportsH264 || encoders.isEmpty(),
            supportsH265 = supportsH265,
            supportedResolutions = supportedRes,
            supportedFps = supportedFps,
            maxSupportedWidth = maxW,
            maxSupportedHeight = maxH,
            maxSupportedFps = maxFps,
            encoderSummary = "Encoder: $primaryEncoder (${maxW}x${maxH} @ ${maxFps}fps)"
        )
    }

    private fun isSoftwareOnly(info: MediaCodecInfo): Boolean {
        return info.name.contains("OMX.google", ignoreCase = true) ||
            info.name.contains("c2.android", ignoreCase = true)
    }
}
