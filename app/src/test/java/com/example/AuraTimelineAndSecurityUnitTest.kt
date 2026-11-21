package com.example

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import com.example.core.media.ExportResolution
import com.example.core.security.SecurityManager
import com.example.domain.model.AspectRatioPreset
import com.example.domain.model.ClipMediaType
import com.example.domain.model.ColorGradingConfig
import com.example.domain.model.EditorFontStyle
import com.example.domain.model.SpeedRampPreset
import com.example.domain.model.TimelineProjectState
import com.example.domain.model.TrackControls
import com.example.domain.model.TransitionType
import com.example.domain.model.VideoEffectPreset
import com.example.editor.FadeVolumeAudioProcessor
import com.example.editor.Media3EffectFactory
import com.example.editor.TimelineEngine
import com.example.editor.TimelineHistoryManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

class AuraTimelineAndSecurityUnitTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `timeline edits import trim split merge reorder freeze and undo redo`() {
        val history = TimelineHistoryManager()
        var state = TimelineProjectState(name = "Test Edit", aspectRatio = AspectRatioPreset.RATIO_16_9)

        // 1. Import video
        val s1 = TimelineEngine.appendVideoOrPhoto(
            state = state,
            sourceUri = "file:///tmp/sample.mp4",
            title = "Clip A",
            mediaType = ClipMediaType.VIDEO,
            durationMs = 10_000L
        )
        history.pushSnapshot(state)
        state = s1
        assertEquals(1, state.videoClips.size)
        assertEquals(10_000L, state.totalDurationMs)

        // 2. Trim clip to 8_000ms
        val clipId = state.videoClips.first().id
        val s2 = TimelineEngine.trimVideoClip(state, clipId, 1_000L, 9_000L)
        history.pushSnapshot(state)
        state = s2
        assertEquals(8_000L, state.totalDurationMs)

        // 3. Split at 4_000ms
        val s3 = TimelineEngine.splitVideoClipAt(state, clipId, 4_000L)
        history.pushSnapshot(state)
        state = s3
        assertEquals(2, state.videoClips.size)
        assertEquals(8_000L, state.totalDurationMs)

        // 4. Reorder clips
        val secondId = state.videoClips[1].id
        val reordered = TimelineEngine.reorderVideoClip(state, secondId, -1)
        assertEquals(secondId, reordered.videoClips[0].id)

        // 5. Merge compatible clips back
        val s4 = TimelineEngine.mergeWithNextClip(state, state.videoClips.first().id)
        history.pushSnapshot(state)
        state = s4
        assertEquals(1, state.videoClips.size)

        // 6. Undo back to split state, then Redo
        val undone = history.undo(state)
        assertNotNull(undone)
        assertEquals(2, undone!!.videoClips.size)
        val redone = history.redo(undone)
        assertNotNull(redone)
        assertEquals(1, redone!!.videoClips.size)

        // 7. Insert freeze frame & keyframe
        val frozen = TimelineEngine.insertFreezeFrame(redone, redone.videoClips.first().id, 2_000L)
        assertEquals(2, frozen.videoClips.size)
        assertEquals(ClipMediaType.FREEZE_FRAME, frozen.videoClips[1].mediaType)

        val keyed = TimelineEngine.addKeyframeToClip(frozen, frozen.videoClips.first().id, 1_500L)
        assertEquals(1, keyed.videoClips.first().keyframes.size)
    }

    @Test
    fun `magnetic snapping and track controls behave deterministically`() {
        var state = TimelineEngine.appendVideoOrPhoto(
            TimelineProjectState(magneticSnapEnabled = true),
            "file:///tmp/a.mp4",
            "Main",
            ClipMediaType.VIDEO,
            5_000L
        )
        // Within 220ms of clip end (5000ms) -> snaps to 5000ms
        assertEquals(5_000L, TimelineEngine.magneticSnap(5_140L, state))
        // Outside snap threshold -> preserves exact position
        assertEquals(5_400L, TimelineEngine.magneticSnap(5_400L, state))

        state = TimelineEngine.updateTrackControls(
            state,
            TrackControls(videoVisible = false, musicMuted = true)
        )
        assertFalse(state.trackControls.videoVisible)
        assertTrue(state.trackControls.musicMuted)
    }

    @Test
    fun `srt subtitle parser and cube lut parser produce valid layers and color grading`() {
        val srt = """
            1
            00:00:01,000 --> 00:00:03,500
            Welcome to Aura Studio

            2
            00:00:04,000 --> 00:00:06,000
            Multi-track timeline
        """.trimIndent()
        val cues = TimelineEngine.parseSrtSubtitles(srt)
        assertEquals(2, cues.size)
        assertEquals(1_000L, cues[0].startMs)
        assertEquals(3_500L, cues[0].endMs)
        assertEquals("Welcome to Aura Studio", cues[0].text)

        val cube = """
            TITLE "Warm Cinema"
            LUT_3D_SIZE 2
            0.10 0.05 0.02
            0.95 0.85 0.70
        """.trimIndent()
        val grading = TimelineEngine.parseCubeLutPreset(cube, "WarmCinema.cube")
        assertEquals("WarmCinema.cube", grading.lutName)
        assertTrue(grading.temperature > 0f)
    }

    @OptIn(UnstableApi::class)
    @Test
    fun `media3 speed ramp audio fade and size estimator work on pure jvm`() {
        var state = TimelineEngine.appendVideoOrPhoto(
            TimelineProjectState(),
            "file:///tmp/a.mp4",
            "Main",
            ClipMediaType.VIDEO,
            12_000L
        )
        state = TimelineEngine.updateVideoClip(state, state.videoClips.first().id) {
            it.copy(
                rotationDegrees = 90f,
                flipHorizontal = true,
                effectPreset = VideoEffectPreset.CINEMATIC,
                colorGrading = ColorGradingConfig(exposure = 0.25f, contrast = 0.3f),
                transitionToNext = TransitionType.ZOOM_IN
            )
        }
        state = TimelineEngine.addTextLayer(state, "Aura", EditorFontStyle.OUTFIT, 0L)
        val estMb = Media3EffectFactory.estimateOutputSizeMb(state, ExportResolution.FHD_1080P)
        assertTrue(estMb > 5.0)

        val rampProvider = Media3EffectFactory.buildSpeedProvider(1.0f, SpeedRampPreset.HERO_DIP, 4_000_000L)
        assertTrue(rampProvider.getSpeed(2_000_000L) < 0.6f)
        assertTrue(rampProvider.getSpeed(100_000L) > 1.1f)
        assertTrue(rampProvider.getNextSpeedChangeTimeUs(0L) != C.TIME_UNSET)

        val audioProc = FadeVolumeAudioProcessor(
            targetGain = 0.5f,
            fadeInMs = 0L,
            fadeOutMs = 0L,
            clipDurationMs = 1_000L
        )
        audioProc.configure(AudioProcessor.AudioFormat(44_100, 1, C.ENCODING_PCM_16BIT))
        audioProc.flush(AudioProcessor.StreamMetadata.DEFAULT)
        val pcmIn = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())
        pcmIn.putShort(2000.toShort())
        pcmIn.putShort((-1000).toShort())
        pcmIn.flip()
        audioProc.queueInput(pcmIn)
        val pcmOut = audioProc.output
        assertEquals(4, pcmOut.remaining())
        assertEquals(1000, pcmOut.short.toInt())
        assertEquals(-500, pcmOut.short.toInt())
    }

    @Test
    fun `security manager encrypts decrypts verifies pin and prevents path traversal`() {
        val sec = SecurityManager()
        val salt = sec.generateSaltHex()
        val hash = sec.hashPin("4829", salt)
        assertTrue(sec.verifyPin("4829", salt, hash))
        assertFalse(sec.verifyPin("0000", salt, hash))

        val key = sec.generateSessionKey()
        val payload = "Aura Share Encrypted Payload Bytes".toByteArray()
        val encrypted = sec.encryptChunk(payload, payload.size, key)
        val decrypted = sec.decryptChunk(encrypted, key)
        assertEquals(String(payload), String(decrypted))

        val expectedSha = sec.sha256(ByteArrayInputStream(payload))
        assertEquals(64, expectedSha.length)

        val baseDir = tempFolder.newFolder("received")
        val safeFile = sec.resolveSafeFileInDir(baseDir, "../../etc/passwd")
        assertTrue(safeFile.canonicalPath.startsWith(baseDir.canonicalPath))
        assertFalse(safeFile.name.contains(".."))
    }
}
