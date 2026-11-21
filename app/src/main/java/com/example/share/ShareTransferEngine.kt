package com.example.share

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.core.net.toFile
import com.example.core.security.SecurityManager
import com.example.data.local.entity.PairedDeviceEntity
import com.example.data.local.entity.TransferRecordEntity
import com.example.data.repository.MediaLibraryRepository
import com.example.data.repository.ShareAndDownloadRepository
import com.example.domain.model.ActiveTransferState
import com.example.domain.model.DiscoveredPeer
import com.example.domain.model.IncomingTransferPrompt
import com.example.domain.model.SelectedShareFile
import com.example.domain.model.TransferDirection
import com.example.domain.model.TransferStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.UUID
import javax.crypto.SecretKey
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real offline encrypted socket file-transfer engine for AURA Share.
 * Supports:
 * - Mutual pairing PIN verification & session AES-256-GCM encryption
 * - Explicit receiver consent before accepting any bytes
 * - Multi-file transfers (photos, videos, music, documents)
 * - SHA-256 per-file validation & path-traversal confinement
 * - Pause / Resume offset negotiation & safe Cancel handling
 * - Strict Private Space protection
 */
@Singleton
class ShareTransferEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val securityManager: SecurityManager,
    private val shareRepository: ShareAndDownloadRepository,
    private val mediaRepository: MediaLibraryRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _activeTransfer = MutableStateFlow<ActiveTransferState?>(null)
    val activeTransfer: StateFlow<ActiveTransferState?> = _activeTransfer.asStateFlow()

    private val _incomingPrompt = MutableStateFlow<IncomingTransferPrompt?>(null)
    val incomingPrompt: StateFlow<IncomingTransferPrompt?> = _incomingPrompt.asStateFlow()

    private val _serverPort = MutableStateFlow(0)
    val serverPort: StateFlow<Int> = _serverPort.asStateFlow()

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused.asStateFlow()

    @Volatile
    private var isCancelled = false

    @Volatile
    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private var activeClientJob: Job? = null

    private var currentSessionPin: String = securityManager.generatePairingPin()
    private var currentSessionKey: SecretKey = securityManager.generateSessionKey()
    private var consentDeferred: CompletableDeferred<Boolean>? = null

    val deviceId: String by lazy {
        UUID.nameUUIDFromBytes((android.os.Build.MODEL + context.packageName).toByteArray()).toString()
    }

    fun getCurrentPairingInfo(alias: String, localIp: String): PairingPayload {
        return PairingPayload(
            deviceId = deviceId,
            displayName = alias,
            hostAddress = localIp,
            port = _serverPort.value.takeIf { it > 0 } ?: LocalDiscoveryManager.DEFAULT_SHARE_PORT,
            pairingPin = currentSessionPin,
            sessionKeyHex = securityManager.keyToHex(currentSessionKey)
        )
    }

    fun regeneratePairingCredentials(): Pair<String, SecretKey> {
        currentSessionPin = securityManager.generatePairingPin()
        currentSessionKey = securityManager.generateSessionKey()
        return currentSessionPin to currentSessionKey
    }

    fun startReceiverServer(
        saveFolderDir: File = defaultReceiveDir()
    ): Int {
        if (serverSocket != null && serverSocket?.isClosed == false) {
            return _serverPort.value
        }
        val ss = ServerSocket(0)
        serverSocket = ss
        _serverPort.value = ss.localPort
        serverJob = scope.launch {
            while (isActive && !ss.isClosed) {
                val client = runCatching { ss.accept() }.getOrNull() ?: break
                launch {
                    handleIncomingClient(client, saveFolderDir)
                }
            }
        }
        return ss.localPort
    }

    fun stopReceiverServer() {
        serverJob?.cancel()
        runCatching { serverSocket?.close() }
        serverSocket = null
        _serverPort.value = 0
    }

    fun respondToIncomingConsent(accepted: Boolean) {
        consentDeferred?.complete(accepted)
        _incomingPrompt.value = null
    }

    fun pauseActiveTransfer() {
        _isPaused.value = true
        _activeTransfer.value = _activeTransfer.value?.copy(status = TransferStatus.PAUSED)
    }

    fun resumeActiveTransfer() {
        _isPaused.value = false
        _activeTransfer.value = _activeTransfer.value?.copy(status = TransferStatus.IN_PROGRESS)
    }

    fun cancelActiveTransfer() {
        isCancelled = true
        _isPaused.value = false
        activeClientJob?.cancel()
        _activeTransfer.value = _activeTransfer.value?.copy(status = TransferStatus.CANCELLED)
    }

    fun clearActiveTransferBanner() {
        _activeTransfer.value = null
    }

    /**
     * Validates that no Private Space files are sent without explicit user authorization.
     */
    fun validateFilesForSharing(
        files: List<SelectedShareFile>,
        privateSpaceAuthorized: Boolean
    ): Result<List<SelectedShareFile>> {
        val blockedPrivate = files.filter { it.isPrivate }
        if (blockedPrivate.isNotEmpty() && !privateSpaceAuthorized) {
            return Result.failure(
                SecurityException("Private Space items cannot be shared without explicit PIN authorization.")
            )
        }
        if (files.isEmpty()) {
            return Result.failure(IllegalArgumentException("Select at least one file to send."))
        }
        return Result.success(files)
    }

    fun sendFiles(
        peer: DiscoveredPeer,
        files: List<SelectedShareFile>,
        senderAlias: String,
        pairingPin: String,
        sessionKeyHex: String,
        privateSpaceAuthorized: Boolean = false
    ) {
        val check = validateFilesForSharing(files, privateSpaceAuthorized)
        if (check.isFailure) {
            _activeTransfer.value = ActiveTransferState(
                transferId = UUID.randomUUID().toString(),
                peerName = peer.displayName,
                status = TransferStatus.FAILED,
                errorMessage = check.exceptionOrNull()?.message
            )
            return
        }

        isCancelled = false
        _isPaused.value = false
        val transferId = UUID.randomUUID().toString()
        val totalBytes = files.sumOf { it.sizeBytes.coerceAtLeast(1L) }

        activeClientJob = scope.launch {
            val key = runCatching { securityManager.keyFromHex(sessionKeyHex) }
                .getOrElse {
                    _activeTransfer.value = ActiveTransferState(
                        transferId = transferId,
                        peerName = peer.displayName,
                        status = TransferStatus.FAILED,
                        errorMessage = "Invalid encryption key. Pair via QR or 6-digit PIN."
                    )
                    return@launch
                }

            _activeTransfer.value = ActiveTransferState(
                transferId = transferId,
                peerName = peer.displayName,
                currentFileName = files.first().name,
                fileIndex = 1,
                totalFiles = files.size,
                bytesTransferred = 0L,
                totalBytes = totalBytes,
                status = TransferStatus.PENDING_CONSENT
            )

            val socket = Socket()
            try {
                socket.connect(InetSocketAddress(peer.hostAddress, peer.port), 8_000)
                socket.soTimeout = 15_000
                val out = DataOutputStream(socket.getOutputStream())
                val input = DataInputStream(socket.getInputStream())

                // 1. Send authenticated handshake
                out.writeUTF(PROTOCOL_MAGIC)
                out.writeUTF(deviceId)
                out.writeUTF(senderAlias)
                out.writeUTF(securityManager.sha256Bytes(pairingPin.toByteArray()))
                out.writeInt(files.size)
                out.writeLong(totalBytes)
                files.forEach { f ->
                    out.writeUTF(securityManager.sanitizeFileName(f.name))
                    out.writeUTF(f.mimeType)
                    out.writeLong(f.sizeBytes)
                }
                out.flush()

                // 2. Await explicit receiver consent
                val consentCode = input.readUTF()
                if (consentCode != CONSENT_ACCEPT) {
                    val reason = if (consentCode == CONSENT_PIN_INVALID) {
                        "Pairing PIN rejected by peer."
                    } else {
                        "Peer declined transfer request."
                    }
                    _activeTransfer.value = _activeTransfer.value?.copy(
                        status = TransferStatus.CANCELLED,
                        errorMessage = reason
                    )
                    return@launch
                }

                // Persist trusted paired device
                shareRepository.savePairedDevice(
                    PairedDeviceEntity(
                        deviceId = peer.id,
                        displayName = peer.displayName,
                        fingerprint = securityManager.sha256Bytes(key.encoded).take(12),
                        hostAddress = peer.hostAddress,
                        port = peer.port
                    )
                )

                var overallSent = 0L
                val startedAt = System.currentTimeMillis()

                files.forEachIndexed { idx, file ->
                    if (isCancelled) throw InterruptedException("Cancelled by user")

                    val uri = Uri.parse(file.uri)
                    val checksum = computeUriSha256(uri)
                    out.writeUTF(checksum)
                    out.flush()

                    // Negotiate resume offset from receiver if partial file exists
                    val resumeOffset = input.readLong().coerceIn(0L, file.sizeBytes)
                    overallSent += resumeOffset

                    val buffer = ByteArray(CHUNK_SIZE)
                    openInputStreamForUri(uri)?.use { stream ->
                        if (resumeOffset > 0L) {
                            stream.skip(resumeOffset)
                        }
                        var read: Int
                        while (stream.read(buffer).also { read = it } != -1) {
                            while (_isPaused.value && !isCancelled) {
                                delay(200L)
                            }
                            if (isCancelled) throw InterruptedException("Cancelled by user")

                            val encrypted = securityManager.encryptChunk(buffer, read, key)
                            out.writeInt(encrypted.size)
                            out.write(encrypted)
                            overallSent += read

                            val elapsedSec = ((System.currentTimeMillis() - startedAt) / 1000.0).coerceAtLeast(0.2)
                            val speed = (overallSent / elapsedSec).toLong()
                            _activeTransfer.value = ActiveTransferState(
                                transferId = transferId,
                                peerName = peer.displayName,
                                currentFileName = file.name,
                                fileIndex = idx + 1,
                                totalFiles = files.size,
                                bytesTransferred = overallSent.coerceAtMost(totalBytes),
                                totalBytes = totalBytes,
                                speedBytesPerSec = speed,
                                status = if (_isPaused.value) TransferStatus.PAUSED else TransferStatus.IN_PROGRESS
                            )
                        }
                    } ?: throw IllegalStateException("Unable to open ${file.name}")

                    // End-of-file marker
                    out.writeInt(-1)
                    out.flush()

                    val verifyStatus = input.readUTF()
                    if (verifyStatus != VERIFY_OK) {
                        throw IllegalStateException("Integrity check failed for ${file.name}")
                    }

                    shareRepository.logTransfer(
                        TransferRecordEntity(
                            id = UUID.randomUUID().toString(),
                            peerName = peer.displayName,
                            peerDeviceId = peer.id,
                            fileName = file.name,
                            fileSizeBytes = file.sizeBytes,
                            mimeType = file.mimeType,
                            direction = TransferDirection.SENT.name,
                            status = TransferStatus.COMPLETED.name,
                            sha256Checksum = checksum,
                            savedUri = file.uri,
                            encrypted = true
                        )
                    )
                }

                _activeTransfer.value = _activeTransfer.value?.copy(
                    bytesTransferred = totalBytes,
                    status = TransferStatus.COMPLETED
                )
            } catch (e: Exception) {
                val finalStatus = if (isCancelled) TransferStatus.CANCELLED else TransferStatus.FAILED
                _activeTransfer.value = _activeTransfer.value?.copy(
                    status = finalStatus,
                    errorMessage = e.localizedMessage ?: "Transfer interrupted"
                )
                shareRepository.logTransfer(
                    TransferRecordEntity(
                        id = transferId,
                        peerName = peer.displayName,
                        peerDeviceId = peer.id,
                        fileName = files.first().name,
                        fileSizeBytes = totalBytes,
                        mimeType = files.first().mimeType,
                        direction = TransferDirection.SENT.name,
                        status = finalStatus.name,
                        sha256Checksum = "",
                        encrypted = true
                    )
                )
            } finally {
                runCatching { socket.close() }
            }
        }
    }

    private suspend fun handleIncomingClient(socket: Socket, saveFolderDir: File) {
        withContext(Dispatchers.IO) {
            var promptForThisClient: IncomingTransferPrompt? = null
            var deferredForThisClient: CompletableDeferred<Boolean>? = null
            try {
                socket.soTimeout = 30_000
                val input = DataInputStream(socket.getInputStream())
                val out = DataOutputStream(socket.getOutputStream())

                val magic = input.readUTF()
                if (magic != PROTOCOL_MAGIC) return@withContext

                val senderId = input.readUTF()
                val senderName = input.readUTF()
                val pinHash = input.readUTF()
                val fileCount = input.readInt().coerceIn(0, 100)
                val totalBytes = input.readLong().coerceAtLeast(0L)

                val expectedPinHash = securityManager.sha256Bytes(currentSessionPin.toByteArray())
                val incomingMeta = mutableListOf<Triple<String, String, Long>>()
                repeat(fileCount) {
                    val name = securityManager.sanitizeFileName(input.readUTF())
                    val mime = input.readUTF()
                    val size = input.readLong().coerceAtLeast(0L)
                    incomingMeta.add(Triple(name, mime, size))
                }

                if (pinHash != expectedPinHash) {
                    out.writeUTF(CONSENT_PIN_INVALID)
                    out.flush()
                    return@withContext
                }

                // Explicit User Consent Gate — never silently receive files!
                val deferred = CompletableDeferred<Boolean>()
                val prompt = IncomingTransferPrompt(
                    requestId = UUID.randomUUID().toString(),
                    senderName = senderName,
                    senderFingerprint = senderId.take(10),
                    fileNames = incomingMeta.map { it.first },
                    totalBytes = totalBytes,
                    isEncrypted = true
                )
                deferredForThisClient = deferred
                promptForThisClient = prompt
                consentDeferred = deferred
                _incomingPrompt.value = prompt

                val accepted = deferred.await()
                if (_incomingPrompt.value?.requestId == prompt.requestId) {
                    _incomingPrompt.value = null
                }
                if (!accepted) {
                    out.writeUTF(CONSENT_DECLINE)
                    out.flush()
                    return@withContext
                }

                out.writeUTF(CONSENT_ACCEPT)
                out.flush()

                saveFolderDir.mkdirs()
                val transferId = UUID.randomUUID().toString()
                var totalReceived = 0L
                val startedAt = System.currentTimeMillis()
                isCancelled = false

                incomingMeta.forEachIndexed { idx, (safeName, mime, expectedSize) ->
                    val expectedSha = input.readUTF()
                    val finalFile = securityManager.resolveSafeFileInDir(saveFolderDir, safeName)
                    val partFile = securityManager.resolveSafeFileInDir(saveFolderDir, "$safeName.part")
                    val resumeOffset = if (partFile.exists()) partFile.length() else 0L
                    out.writeLong(resumeOffset)
                    out.flush()
                    totalReceived += resumeOffset

                    FileOutputStream(partFile, /* append= */ resumeOffset > 0L).use { fos ->
                        while (true) {
                            val packetLen = input.readInt()
                            if (packetLen == -1) break
                            if (packetLen <= 0 || packetLen > CHUNK_SIZE * 2) {
                                throw SecurityException("Invalid encrypted packet size")
                            }
                            val packet = ByteArray(packetLen)
                            input.readFully(packet)
                            val plain = securityManager.decryptChunk(packet, currentSessionKey)
                            fos.write(plain)
                            totalReceived += plain.size

                            val elapsedSec = ((System.currentTimeMillis() - startedAt) / 1000.0).coerceAtLeast(0.2)
                            _activeTransfer.value = ActiveTransferState(
                                transferId = transferId,
                                peerName = senderName,
                                currentFileName = safeName,
                                fileIndex = idx + 1,
                                totalFiles = fileCount,
                                bytesTransferred = totalReceived.coerceAtMost(totalBytes),
                                totalBytes = totalBytes,
                                speedBytesPerSec = (totalReceived / elapsedSec).toLong(),
                                status = TransferStatus.IN_PROGRESS
                            )
                        }
                    }

                    // Validate SHA-256 integrity
                    val actualSha = FileInputStream(partFile).use { securityManager.sha256(it) }
                    if (!actualSha.equals(expectedSha, ignoreCase = true)) {
                        partFile.delete()
                        out.writeUTF(VERIFY_FAIL)
                        out.flush()
                        throw SecurityException("SHA-256 integrity check failed for $safeName")
                    }

                    if (finalFile.exists()) finalFile.delete()
                    partFile.renameTo(finalFile)

                    val savedUri = Uri.fromFile(finalFile).toString()
                    mediaRepository.importLocalUri(
                        uri = Uri.fromFile(finalFile),
                        isPrivate = false,
                        smartTag = "AURA Share"
                    )
                    shareRepository.logTransfer(
                        TransferRecordEntity(
                            id = UUID.randomUUID().toString(),
                            peerName = senderName,
                            peerDeviceId = senderId,
                            fileName = safeName,
                            fileSizeBytes = expectedSize,
                            mimeType = mime,
                            direction = TransferDirection.RECEIVED.name,
                            status = TransferStatus.COMPLETED.name,
                            sha256Checksum = actualSha,
                            savedUri = savedUri,
                            encrypted = true
                        )
                    )
                    out.writeUTF(VERIFY_OK)
                    out.flush()
                }

                _activeTransfer.value = _activeTransfer.value?.copy(
                    bytesTransferred = totalBytes,
                    status = TransferStatus.COMPLETED
                )
            } catch (e: Exception) {
                _activeTransfer.value = _activeTransfer.value?.copy(
                    status = TransferStatus.FAILED,
                    errorMessage = e.localizedMessage ?: "Incoming transfer interrupted"
                )
            } finally {
                if (promptForThisClient != null && _incomingPrompt.value?.requestId == promptForThisClient.requestId) {
                    _incomingPrompt.value = null
                }
                if (deferredForThisClient != null && consentDeferred === deferredForThisClient) {
                    consentDeferred = null
                }
                runCatching { socket.close() }
            }
        }
    }

    fun defaultReceiveDir(): File = File(context.filesDir, "aura_received").apply { mkdirs() }

    fun querySelectedFileMetadata(uri: Uri, isPrivate: Boolean): SelectedShareFile {
        var name = uri.lastPathSegment?.substringAfterLast('/') ?: "file_${System.currentTimeMillis()}"
        var size = 0L
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val si = c.getColumnIndex(OpenableColumns.SIZE)
                    if (ni >= 0) name = c.getString(ni) ?: name
                    if (si >= 0) size = c.getLong(si)
                }
            }
        }
        if (size == 0L && uri.scheme == "file") {
            size = runCatching { uri.toFile().length() }.getOrDefault(0L)
        }
        val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
        return SelectedShareFile(
            uri = uri.toString(),
            name = securityManager.sanitizeFileName(name),
            mimeType = mime,
            sizeBytes = size,
            isPrivate = isPrivate
        )
    }

    private fun computeUriSha256(uri: Uri): String {
        return openInputStreamForUri(uri)?.use { securityManager.sha256(it) } ?: ""
    }

    private fun openInputStreamForUri(uri: Uri) =
        if (uri.scheme == "file") FileInputStream(uri.toFile())
        else context.contentResolver.openInputStream(uri)

    companion object {
        private const val PROTOCOL_MAGIC = "AURASHARE_V1"
        private const val CONSENT_ACCEPT = "ACCEPT"
        private const val CONSENT_DECLINE = "DECLINE"
        private const val CONSENT_PIN_INVALID = "PIN_INVALID"
        private const val VERIFY_OK = "SHA_OK"
        private const val VERIFY_FAIL = "SHA_FAIL"
        private const val CHUNK_SIZE = 32 * 1024
    }
}
