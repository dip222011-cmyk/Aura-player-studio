package com.example.data.local

import android.content.Context
import android.net.Uri
import com.example.R
import com.example.data.local.entity.DownloadTaskEntity
import com.example.data.local.entity.EditorProjectEntity
import com.example.data.local.entity.MediaItemEntity
import com.example.data.local.entity.PlaylistEntity
import com.example.data.local.entity.PlaylistTrackCrossRef
import com.example.domain.model.AspectRatioPreset
import com.example.domain.model.AudioClip
import com.example.domain.model.EditorFontStyle
import com.example.domain.model.MediaKind
import com.example.domain.model.TextAnimationPreset
import com.example.domain.model.TextLayerClip
import com.example.domain.model.TimelineProjectState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.PI
import kotlin.math.sin

@Singleton
class AuraSeedData @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: AuraDatabase,
    private val json: Json
) {
    suspend fun ensureSeeded() = withContext(Dispatchers.IO) {
        val mediaDao = database.mediaDao()
        if (mediaDao.countAll() > 0) return@withContext

        val mediaDir = File(context.filesDir, "aura_samples").apply { mkdirs() }
        val artUri = "android.resource://${context.packageName}/${R.drawable.img_aura_cover_midnight_1795241854682}"
        val bannerUri = "android.resource://${context.packageName}/${R.drawable.img_studio_banner_1795241855159}"

        val track1File = createAmbientWavFile(
            file = File(mediaDir, "midnight_resonance.wav"),
            durationSec = 18,
            baseFreqHz = 220.0,
            harmonicHz = 329.63
        )
        val track2File = createAmbientWavFile(
            file = File(mediaDir, "obsidian_horizon.wav"),
            durationSec = 16,
            baseFreqHz = 196.0,
            harmonicHz = 293.66
        )
        val track3File = createAmbientWavFile(
            file = File(mediaDir, "liquid_prism_stem.wav"),
            durationSec = 14,
            baseFreqHz = 261.63,
            harmonicHz = 392.0
        )

        val now = System.currentTimeMillis()
        val id1 = mediaDao.insert(
            MediaItemEntity(
                uri = Uri.fromFile(track1File).toString(),
                title = "Midnight Resonance",
                artistOrCreator = "Aura Studio Collective",
                kind = MediaKind.AUDIO.name,
                mimeType = "audio/wav",
                durationMs = 18_000L,
                sizeBytes = track1File.length(),
                dateAddedMs = now - 3_000L,
                artworkUri = artUri,
                isPrivate = false,
                smartTag = "High-Res Audio"
            )
        )
        val id2 = mediaDao.insert(
            MediaItemEntity(
                uri = Uri.fromFile(track2File).toString(),
                title = "Obsidian Horizon",
                artistOrCreator = "Kairo Vane",
                kind = MediaKind.AUDIO.name,
                mimeType = "audio/wav",
                durationMs = 16_000L,
                sizeBytes = track2File.length(),
                dateAddedMs = now - 2_000L,
                artworkUri = bannerUri,
                isPrivate = false,
                smartTag = "Creator Stems"
            )
        )
        val id3 = mediaDao.insert(
            MediaItemEntity(
                uri = Uri.fromFile(track3File).toString(),
                title = "Liquid Prism Stem",
                artistOrCreator = "Aura Mastering Lab",
                kind = MediaKind.AUDIO.name,
                mimeType = "audio/wav",
                durationMs = 14_000L,
                sizeBytes = track3File.length(),
                dateAddedMs = now - 1_000L,
                artworkUri = artUri,
                isPrivate = false,
                smartTag = "High-Res Audio"
            )
        )

        val playlistDao = database.playlistDao()
        if (playlistDao.countPlaylists() == 0) {
            val pid = playlistDao.insertPlaylist(
                PlaylistEntity(
                    name = "Late Night Mastering",
                    description = "Warm analog ambient stems & lossless masters"
                )
            )
            playlistDao.addTrackToPlaylist(PlaylistTrackCrossRef(pid, id1, 0))
            playlistDao.addTrackToPlaylist(PlaylistTrackCrossRef(pid, id2, 1))
            playlistDao.addTrackToPlaylist(PlaylistTrackCrossRef(pid, id3, 2))
        }

        val projectDao = database.editorProjectDao()
        if (projectDao.countProjects() == 0) {
            val sampleProjectId = "aura_starter_reel"
            val sampleState = TimelineProjectState(
                projectId = sampleProjectId,
                name = "Neon Studio Reel",
                aspectRatio = AspectRatioPreset.RATIO_16_9,
                audioClips = listOf(
                    AudioClip(
                        sourceUri = Uri.fromFile(track1File).toString(),
                        title = "Midnight Resonance",
                        trackIndex = 1,
                        sourceDurationMs = 18_000L,
                        trimStartMs = 0L,
                        trimEndMs = 12_000L,
                        timelineStartMs = 0L,
                        volume = 0.85f
                    )
                ),
                textLayers = listOf(
                    TextLayerClip(
                        text = "AURA CREATOR STUDIO",
                        fontStyle = EditorFontStyle.OUTFIT,
                        fontSizeSp = 28f,
                        startMs = 0L,
                        endMs = 4_000L,
                        animation = TextAnimationPreset.FADE
                    )
                )
            )
            projectDao.upsertProject(
                EditorProjectEntity(
                    id = sampleProjectId,
                    name = sampleState.name,
                    aspectRatio = sampleState.aspectRatio.name,
                    durationMs = sampleState.totalDurationMs,
                    clipCount = sampleState.videoClips.size + sampleState.audioClips.size,
                    thumbnailUri = bannerUri,
                    timelineJson = json.encodeToString(sampleState),
                    createdAtMs = now,
                    updatedAtMs = now,
                    autosavedAtMs = now
                )
            )
        }

        val downloadDao = database.downloadDao()
        if (downloadDao.countDownloads() == 0) {
            downloadDao.upsertDownload(
                DownloadTaskEntity(
                    id = "pack_ambient_stems",
                    title = "Aura Ambient Master Pack (WAV)",
                    category = "Audio Stems",
                    mimeType = "audio/wav",
                    totalBytes = track1File.length() + track2File.length(),
                    downloadedBytes = track1File.length() + track2File.length(),
                    status = "COMPLETED",
                    localUri = Uri.fromFile(track1File).toString()
                )
            )
            downloadDao.upsertDownload(
                DownloadTaskEntity(
                    id = "lut_pack_cinema",
                    title = "Obsidian Cinema LUT Presets",
                    category = "Color Grading",
                    mimeType = "application/octet-stream",
                    totalBytes = 256_000L,
                    downloadedBytes = 256_000L,
                    status = "COMPLETED",
                    localUri = null
                )
            )
        }
    }

    /**
     * Generates a clean, playable 16-bit PCM WAV ambient chord file locally
     * so offline audio playback, MediaSessionService, and waveforms work out of the box.
     */
    private fun createAmbientWavFile(
        file: File,
        durationSec: Int,
        baseFreqHz: Double,
        harmonicHz: Double
    ): File {
        if (file.exists() && file.length() > 44) return file
        val sampleRate = 22_050
        val numSamples = sampleRate * durationSec
        val dataSize = numSamples * 2
        val pcm = ByteArray(dataSize)
        val bb = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN)

        for (i in 0 until numSamples) {
            val t = i.toDouble() / sampleRate
            val env = when {
                t < 0.8 -> t / 0.8
                t > durationSec - 1.0 -> ((durationSec - t) / 1.0).coerceAtLeast(0.0)
                else -> 1.0
            }
            val lfo = 0.7 + 0.3 * sin(2.0 * PI * 0.25 * t)
            val s1 = sin(2.0 * PI * baseFreqHz * t)
            val s2 = 0.55 * sin(2.0 * PI * harmonicHz * t)
            val s3 = 0.25 * sin(2.0 * PI * (baseFreqHz * 1.5) * t)
            val sample = ((s1 + s2 + s3) / 1.8 * env * lfo * 14_000.0).toInt().toShort()
            bb.putShort(sample)
        }

        FileOutputStream(file).use { out ->
            val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
            header.put("RIFF".toByteArray())
            header.putInt(36 + dataSize)
            header.put("WAVE".toByteArray())
            header.put("fmt ".toByteArray())
            header.putInt(16) // PCM chunk size
            header.putShort(1) // PCM format
            header.putShort(1) // Mono
            header.putInt(sampleRate)
            header.putInt(sampleRate * 2)
            header.putShort(2)
            header.putShort(16)
            header.put("data".toByteArray())
            header.putInt(dataSize)
            out.write(header.array())
            out.write(pcm)
        }
        return file
    }
}
