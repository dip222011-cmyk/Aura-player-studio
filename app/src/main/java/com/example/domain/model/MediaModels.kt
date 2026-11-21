package com.example.domain.model

import kotlinx.serialization.Serializable

enum class MediaKind {
    AUDIO,
    VIDEO,
    PHOTO,
    DOCUMENT
}

data class AuraMediaItem(
    val id: Long = 0L,
    val uri: String,
    val title: String,
    val artistOrCreator: String,
    val kind: MediaKind,
    val mimeType: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val dateAddedMs: Long,
    val artworkUri: String? = null,
    val isPrivate: Boolean = false,
    val smartTag: String = "Library",
    val isExportedResult: Boolean = false,
    val resolutionLabel: String? = null
)

data class SmartAlbumSummary(
    val id: String,
    val title: String,
    val subtitle: String,
    val count: Int,
    val accentColorHex: Long
)

data class PlaybackUiState(
    val currentMediaId: Long = -1L,
    val title: String = "No Track Playing",
    val artist: String = "Aura Audio Engine",
    val artworkUri: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackSpeed: Float = 1f,
    val repeatMode: Int = 0,
    val shuffleEnabled: Boolean = false,
    val queue: List<AuraMediaItem> = emptyList(),
    val isConnected: Boolean = false
)

enum class ExportPhase {
    IDLE,
    PREPARING,
    RENDERING,
    COMPLETED,
    CANCELLED,
    ERROR
}

data class ExportUiState(
    val phase: ExportPhase = ExportPhase.IDLE,
    val progressPercent: Int = 0,
    val estimatedSizeMb: Double = 0.0,
    val outputPath: String? = null,
    val savedDisplayUri: String? = null,
    val actualSizeBytes: Long = 0L,
    val errorMessage: String? = null
)

@Serializable
enum class PeerDiscoverySource {
    NSD_LOCAL_NETWORK,
    WIFI_DIRECT,
    NEARBY_CONNECTIONS,
    QR_PAIRING
}

data class DiscoveredPeer(
    val id: String,
    val displayName: String,
    val hostAddress: String,
    val port: Int,
    val pairingPin: String? = null,
    val sessionKeyHex: String? = null,
    val source: PeerDiscoverySource,
    val isTrusted: Boolean = false
)

enum class TransferDirection {
    SENT,
    RECEIVED
}

enum class TransferStatus {
    PENDING_CONSENT,
    IN_PROGRESS,
    PAUSED,
    COMPLETED,
    CANCELLED,
    FAILED
}

data class SelectedShareFile(
    val uri: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val isPrivate: Boolean = false
)

data class IncomingTransferPrompt(
    val requestId: String,
    val senderName: String,
    val senderFingerprint: String,
    val fileNames: List<String>,
    val totalBytes: Long,
    val isEncrypted: Boolean = true
)

data class ActiveTransferState(
    val transferId: String = "",
    val peerName: String = "",
    val currentFileName: String = "",
    val fileIndex: Int = 0,
    val totalFiles: Int = 0,
    val bytesTransferred: Long = 0L,
    val totalBytes: Long = 0L,
    val speedBytesPerSec: Long = 0L,
    val status: TransferStatus = TransferStatus.IN_PROGRESS,
    val isEncrypted: Boolean = true,
    val errorMessage: String? = null
) {
    val progressFraction: Float
        get() = if (totalBytes > 0L) (bytesTransferred.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
}
