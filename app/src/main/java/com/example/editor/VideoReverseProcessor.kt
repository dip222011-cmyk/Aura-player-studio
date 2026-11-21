package com.example.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reverses a trimmed video segment into an intermediate H.264 MP4 file
 * so Media3 Transformer can compose reversed clips seamlessly on the timeline.
 */
@Singleton
class VideoReverseProcessor @Inject constructor() {

    suspend fun reverseTrimmedClip(
        context: Context,
        sourceUri: Uri,
        trimStartMs: Long,
        trimEndMs: Long,
        fps: Int = 24
    ): Uri = withContext(Dispatchers.IO) {
        val reverseDir = File(context.cacheDir, "aura_reverse").apply { mkdirs() }
        val outFile = File(reverseDir, "rev_${System.currentTimeMillis()}.mp4")

        val retriever = MediaMetadataRetriever()
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var inputSurface: android.view.Surface? = null

        try {
            retriever.setDataSource(context, sourceUri)
            val srcW = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 1280
            val srcH = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 720
            // Cap reverse buffer resolution for mid-range hardware memory & speed
            val maxDim = 960
            val scale = minOf(1f, maxDim.toFloat() / maxOf(srcW, srcH).toFloat())
            val outW = (((srcW * scale).toInt() / 2) * 2).coerceAtLeast(320)
            val outH = (((srcH * scale).toInt() / 2) * 2).coerceAtLeast(240)

            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, outW, outH).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, 4_000_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, fps)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            }

            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
                configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            }
            inputSurface = encoder.createInputSurface()
            encoder.start()
            muxer = MediaMuxer(outFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val safeStart = trimStartMs.coerceAtLeast(0L)
            val safeEnd = trimEndMs.coerceAtLeast(safeStart + 250L)
            val stepMs = (1000L / fps.coerceIn(12, 30)).coerceAtLeast(33L)
            val bufferInfo = MediaCodec.BufferInfo()
            var trackIndex = -1
            var muxerStarted = false

            val timestamps = mutableListOf<Long>()
            var cur = safeEnd
            while (cur >= safeStart && timestamps.size < 360) {
                timestamps.add(cur)
                cur -= stepMs
            }

            val paint = Paint(Paint.FILTER_BITMAP_FLAG)
            val destRect = Rect(0, 0, outW, outH)

            timestamps.forEach { tMs ->
                val frameBmp: Bitmap? = retriever.getFrameAtTime(
                    tMs * 1000L,
                    MediaMetadataRetriever.OPTION_CLOSEST_SYNC
                )
                val canvas: Canvas = inputSurface.lockCanvas(null)
                try {
                    canvas.drawColor(Color.BLACK)
                    if (frameBmp != null) {
                        canvas.drawBitmap(frameBmp, null, destRect, paint)
                        frameBmp.recycle()
                    }
                } finally {
                    inputSurface.unlockCanvasAndPost(canvas)
                }

                val pair = drainEncoder(encoder, muxer, bufferInfo, trackIndex, muxerStarted, endOfStream = false)
                trackIndex = pair.first
                muxerStarted = pair.second
            }

            encoder.signalEndOfInputStream()
            drainEncoder(encoder, muxer, bufferInfo, trackIndex, muxerStarted, endOfStream = true)
            Uri.fromFile(outFile)
        } finally {
            runCatching { retriever.release() }
            runCatching { encoder?.stop() }
            runCatching { encoder?.release() }
            runCatching { inputSurface?.release() }
            runCatching { muxer?.stop() }
            runCatching { muxer?.release() }
        }
    }

    private fun drainEncoder(
        encoder: MediaCodec,
        muxer: MediaMuxer,
        bufferInfo: MediaCodec.BufferInfo,
        initialTrackIndex: Int,
        initialMuxerStarted: Boolean,
        endOfStream: Boolean
    ): Pair<Int, Boolean> {
        var trackIndex = initialTrackIndex
        var muxerStarted = initialMuxerStarted
        while (true) {
            val status = encoder.dequeueOutputBuffer(bufferInfo, if (endOfStream) 10_000L else 0L)
            if (status == MediaCodec.INFO_TRY_AGAIN_LATER) {
                if (!endOfStream) break
            } else if (status == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                if (!muxerStarted) {
                    trackIndex = muxer.addTrack(encoder.outputFormat)
                    muxer.start()
                    muxerStarted = true
                }
            } else if (status >= 0) {
                val encodedData = encoder.getOutputBuffer(status)
                if (encodedData != null && (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && bufferInfo.size > 0 && muxerStarted) {
                    encodedData.position(bufferInfo.offset)
                    encodedData.limit(bufferInfo.offset + bufferInfo.size)
                    muxer.writeSampleData(trackIndex, encodedData, bufferInfo)
                }
                encoder.releaseOutputBuffer(status, false)
                if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break
            }
        }
        return trackIndex to muxerStarted
    }
}
