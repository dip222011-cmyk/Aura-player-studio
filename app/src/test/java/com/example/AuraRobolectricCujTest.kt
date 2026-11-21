package com.example

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.core.datastore.AuraPreferences
import com.example.core.media.DeviceCapabilityInspector
import com.example.core.media.ExportCodec
import com.example.core.media.ExportResolution
import com.example.core.security.SecurityManager
import com.example.data.local.AuraDatabase
import com.example.data.local.entity.MediaItemEntity
import com.example.data.local.entity.TransferRecordEntity
import com.example.data.repository.EditorProjectRepository
import com.example.data.repository.MediaLibraryRepository
import com.example.data.repository.ShareAndDownloadRepository
import com.example.domain.model.AspectRatioPreset
import com.example.domain.model.ClipMediaType
import com.example.domain.model.DiscoveredPeer
import com.example.domain.model.ExportPhase
import com.example.domain.model.MediaKind
import com.example.domain.model.PeerDiscoverySource
import com.example.domain.model.SelectedShareFile
import com.example.domain.model.TransferDirection
import com.example.domain.model.TransferStatus
import com.example.editor.Media3ExportEngine
import com.example.editor.TimelineEngine
import com.example.editor.VideoReverseProcessor
import com.example.playback.AuraPlaybackService
import com.example.share.PairingPayload
import com.example.share.QrCodeHelper
import com.example.share.ShareTransferEngine
import com.example.ui.components.RotatingAlbumArt
import com.example.ui.theme.AuraPlayerTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AuraRobolectricCujTest {

    @get:Rule
    val composeRule = createComposeRule()

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var db: AuraDatabase

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AuraDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `background audio MediaSessionService initializes session and handles task removal`() {
        val controller = Robolectric.buildService(AuraPlaybackService::class.java)
        val service = controller.create().get()
        assertNotNull(service)
        // Trigger task removal when idle -> cleans up without exception
        service.onTaskRemoved(null)
        controller.destroy()
    }

    @Test
    fun `album artwork rotates only while playing pauses smoothly and transitions cleanly`() {
        var isPlaying by mutableStateOf(true)
        var trackKey by mutableLongStateOf(1L)
        var latestAngle by mutableFloatStateOf(0f)

        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            AuraPlayerTheme {
                RotatingAlbumArt(
                    artworkUri = null,
                    trackKey = trackKey,
                    isPlaying = isPlaying,
                    onRotationChanged = { latestAngle = it }
                )
            }
        }

        // Advance clock while playing -> angle increases
        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule.waitForIdle()
        val angleWhilePlaying = latestAngle
        assertTrue("Expected artwork to rotate while playing, got $angleWhilePlaying", angleWhilePlaying > 5f)

        // Pause playback -> angle must remain frozen
        isPlaying = false
        composeRule.waitForIdle()
        val pausedAngle = latestAngle
        composeRule.mainClock.advanceTimeBy(1_500L)
        composeRule.waitForIdle()
        assertEquals(pausedAngle, latestAngle, 0.5f)

        // Resume playback -> continues from pausedAngle
        isPlaying = true
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(1_000L)
        composeRule.waitForIdle()
        assertTrue("Expected artwork to resume from $pausedAngle, got $latestAngle", latestAngle > pausedAngle + 5f)

        // Change track -> resets cleanly to 0f and resumes rotating smoothly
        trackKey = 2L
        composeRule.waitForIdle()
        composeRule.mainClock.advanceTimeBy(450L)
        composeRule.waitForIdle()
        assertTrue("Expected track change reset below 20deg, got $latestAngle", latestAngle < 20f)
    }

    @Test
    fun `media3 effect factory builds video effects text overlays and pip overlays`() {
        var state = TimelineEngine.appendVideoOrPhoto(
            com.example.domain.model.TimelineProjectState(),
            "file:///tmp/a.mp4",
            "Main",
            ClipMediaType.VIDEO,
            12_000L
        )
        state = TimelineEngine.updateVideoClip(state, state.videoClips.first().id) {
            it.copy(
                rotationDegrees = 90f,
                flipHorizontal = true,
                effectPreset = com.example.domain.model.VideoEffectPreset.CINEMATIC,
                colorGrading = com.example.domain.model.ColorGradingConfig(exposure = 0.25f, contrast = 0.3f),
                transitionToNext = com.example.domain.model.TransitionType.ZOOM_IN
            )
        }
        state = TimelineEngine.addTextLayer(
            state,
            "Aura",
            com.example.domain.model.EditorFontStyle.OUTFIT,
            0L
        )
        val effects = com.example.editor.Media3EffectFactory.buildClipVideoEffects(
            clip = state.videoClips.first(),
            textLayers = state.textLayers,
            context = context
        )
        assertTrue(effects.size >= 4)
    }

    @Test
    fun `editor project repository saves autosaves reloads duplicates and deletes projects`() = runBlocking {
        val repo = EditorProjectRepository(db.editorProjectDao(), Json { ignoreUnknownKeys = true })
        val created = repo.createNewProject("Creator Cut", AspectRatioPreset.RATIO_9_16)
        val withClip = TimelineEngine.appendVideoOrPhoto(
            created,
            "file:///sample/intro.mp4",
            "Intro Clip",
            ClipMediaType.VIDEO,
            6_000L
        )
        repo.saveProject(withClip, isAutosave = true)

        val reloaded = repo.loadProject(withClip.projectId)
        assertNotNull(reloaded)
        assertEquals("Creator Cut", reloaded!!.name)
        assertEquals(AspectRatioPreset.RATIO_9_16, reloaded.aspectRatio)
        assertEquals(1, reloaded.videoClips.size)

        val dup = repo.duplicateProject(withClip.projectId)
        assertNotNull(dup)
        assertEquals(2, repo.allProjects.first().size)

        repo.deleteProject(dup!!.projectId)
        assertEquals(1, repo.allProjects.first().size)
    }

    @Test
    fun `export engine validates empty projects inspects capabilities and handles cancellation`() {
        val sec = SecurityManager()
        val prefs = AuraPreferences(context)
        val mediaRepo = MediaLibraryRepository(context, db.mediaDao(), db.playlistDao(), sec, prefs)
        val engine = Media3ExportEngine(context, mediaRepo, VideoReverseProcessor())
        val caps = DeviceCapabilityInspector().inspect()

        assertTrue(caps.supportsH264)
        assertTrue(ExportResolution.HD_720P in caps.supportedResolutions)

        // Empty project yields recoverable ERROR state
        engine.startExport(
            project = com.example.domain.model.TimelineProjectState(name = "Empty"),
            resolution = ExportResolution.HD_720P,
            codec = ExportCodec.H264,
            fps = com.example.core.media.ExportFps.FPS_30
        )
        assertEquals(ExportPhase.ERROR, engine.exportState.value.phase)

        engine.cancelExport()
        assertEquals(ExportPhase.CANCELLED, engine.exportState.value.phase)

        engine.resetExportState()
        assertEquals(ExportPhase.IDLE, engine.exportState.value.phase)
    }

    @Test
    fun `offline playback preferences persist queue state and resume timestamp`() = runBlocking {
        val prefs = AuraPreferences(context)
        prefs.updatePlaybackResume(
            mediaId = 99L,
            positionMs = 42_500L,
            speed = 1.25f,
            repeatMode = 2,
            shuffleEnabled = true
        )
        val restored = prefs.resumeStateFlow.first()
        assertEquals(99L, restored.mediaId)
        assertEquals(42_500L, restored.positionMs)
        assertEquals(1.25f, restored.speed, 0.01f)
        assertEquals(2, restored.repeatMode)
        assertTrue(restored.shuffleEnabled)
    }

    @Test
    fun `private space items remain strictly isolated from public library queries`() = runBlocking {
        val dao = db.mediaDao()
        dao.insert(
            MediaItemEntity(
                uri = "file:///public.mp3",
                title = "Public Track",
                artistOrCreator = "Artist",
                kind = MediaKind.AUDIO.name,
                mimeType = "audio/mpeg",
                durationMs = 10_000L,
                sizeBytes = 1000L,
                dateAddedMs = 1L,
                isPrivate = false
            )
        )
        dao.insert(
            MediaItemEntity(
                uri = "file:///secret.mp3",
                title = "Secret Vault Audio",
                artistOrCreator = "Private",
                kind = MediaKind.AUDIO.name,
                mimeType = "audio/mpeg",
                durationMs = 10_000L,
                sizeBytes = 1000L,
                dateAddedMs = 2L,
                isPrivate = true
            )
        )

        val publicItems = dao.observeAllPublic().first()
        val privateItems = dao.observePrivateItems().first()
        assertEquals(1, publicItems.size)
        assertEquals("Public Track", publicItems.first().title)
        assertEquals(1, privateItems.size)
        assertEquals("Secret Vault Audio", privateItems.first().title)
    }

    @Test
    fun `qr code helper generates bitmap and parses aura share payload`() {
        val qr = QrCodeHelper()
        val sec = SecurityManager()
        val keyHex = sec.keyToHex(sec.generateSessionKey())
        val payload = PairingPayload(
            deviceId = "dev-123",
            displayName = "Studio Pixel",
            hostAddress = "192.168.1.55",
            port = 48721,
            pairingPin = "654321",
            sessionKeyHex = keyHex
        )
        val uri = qr.encodePayloadUri(payload)
        val parsed = qr.parsePayloadUri(uri)
        assertNotNull(parsed)
        assertEquals("192.168.1.55", parsed!!.hostAddress)
        assertEquals("654321", parsed.pairingPin)

        val bmp = qr.generateQrBitmap(uri, 128)
        assertNotNull(bmp)
        assertEquals(128, bmp!!.width)
    }

    @Test
    fun `share engine blocks private files when locked and completes encrypted loopback transfer with consent`() = runBlocking {
        val sec = SecurityManager()
        val prefs = AuraPreferences(context)
        val mediaRepo = MediaLibraryRepository(context, db.mediaDao(), db.playlistDao(), sec, prefs)
        val shareRepo = ShareAndDownloadRepository(db.transferDao(), db.downloadDao())

        val receiverEngine = ShareTransferEngine(context, sec, shareRepo, mediaRepo)
        val senderEngine = ShareTransferEngine(context, sec, shareRepo, mediaRepo)

        val receiveDir = tempFolder.newFolder("rx")
        val port = receiverEngine.startReceiverServer(receiveDir)
        assertTrue(port > 0)

        val pairing = receiverEngine.getCurrentPairingInfo("Receiver Device", "127.0.0.1")

        // Create real source payload file
        val srcFile = tempFolder.newFile("aura_clip_sample.txt")
        val rawContent = "Aura Player Creator Edition Encrypted Stream Verification Bytes 2026!".repeat(64).toByteArray()
        srcFile.writeBytes(rawContent)

        val shareFile = SelectedShareFile(
            uri = Uri.fromFile(srcFile).toString(),
            name = srcFile.name,
            mimeType = "text/plain",
            sizeBytes = rawContent.size.toLong(),
            isPrivate = false
        )

        // Private Space guard check
        val blocked = senderEngine.validateFilesForSharing(listOf(shareFile.copy(isPrivate = true)), privateSpaceAuthorized = false)
        assertTrue(blocked.isFailure)

        val peer = DiscoveredPeer(
            id = pairing.deviceId,
            displayName = pairing.displayName,
            hostAddress = "127.0.0.1",
            port = port,
            source = PeerDiscoverySource.QR_PAIRING
        )

        // Send via loopback socket with receiver's PIN & AES-256 session key
        senderEngine.sendFiles(
            peer = peer,
            files = listOf(shareFile),
            senderAlias = "Sender Phone",
            pairingPin = pairing.pairingPin,
            sessionKeyHex = pairing.sessionKeyHex
        )

        // Wait for receiver consent prompt, then explicitly approve
        var promptSeen = false
        for (i in 0 until 120) {
            if (receiverEngine.incomingPrompt.value != null) {
                promptSeen = true
                receiverEngine.respondToIncomingConsent(true)
                break
            }
            delay(50L)
        }
        assertTrue("Expected receiver consent prompt", promptSeen)

        // Wait for transfer completion
        for (i in 0 until 120) {
            if (senderEngine.activeTransfer.value?.status == TransferStatus.COMPLETED) break
            delay(50L)
        }
        assertEquals(TransferStatus.COMPLETED, senderEngine.activeTransfer.value?.status)

        val receivedFile = File(receiveDir, "aura_clip_sample.txt")
        assertTrue("Expected received file on disk", receivedFile.exists())
        assertArrayEquals(rawContent, receivedFile.readBytes())

        // Verify declined transfer saves zero bytes
        val declineSrc = tempFolder.newFile("declined.txt").apply { writeText("Should Not Arrive") }
        senderEngine.sendFiles(
            peer = peer,
            files = listOf(
                SelectedShareFile(
                    uri = Uri.fromFile(declineSrc).toString(),
                    name = declineSrc.name,
                    mimeType = "text/plain",
                    sizeBytes = declineSrc.length(),
                    isPrivate = false
                )
            ),
            senderAlias = "Sender Phone",
            pairingPin = pairing.pairingPin,
            sessionKeyHex = pairing.sessionKeyHex
        )
        for (i in 0 until 120) {
            if (receiverEngine.incomingPrompt.value != null) {
                receiverEngine.respondToIncomingConsent(false)
                break
            }
            delay(50L)
        }
        for (i in 0 until 120) {
            if (senderEngine.activeTransfer.value?.status == TransferStatus.CANCELLED) break
            delay(50L)
        }
        assertEquals(TransferStatus.CANCELLED, senderEngine.activeTransfer.value?.status)
        assertFalse(File(receiveDir, "declined.txt").exists())

        receiverEngine.stopReceiverServer()
    }

    @Test
    fun `transfer history logs and persists encrypted transfer records`() = runBlocking {
        val repo = ShareAndDownloadRepository(db.transferDao(), db.downloadDao())
        repo.logTransfer(
            TransferRecordEntity(
                id = "tx-1",
                peerName = "Creator Tablet",
                peerDeviceId = "tab-01",
                fileName = "master_cut.mp4",
                fileSizeBytes = 12_500_000L,
                mimeType = "video/mp4",
                direction = TransferDirection.SENT.name,
                status = TransferStatus.COMPLETED.name,
                sha256Checksum = "abcdef123456",
                encrypted = true
            )
        )
        val records = repo.transferHistory.first()
        assertEquals(1, records.size)
        assertEquals("master_cut.mp4", records.first().fileName)
        assertTrue(records.first().encrypted)
    }
}
