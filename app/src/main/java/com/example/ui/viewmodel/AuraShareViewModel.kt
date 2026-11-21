package com.example.ui.viewmodel

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.datastore.AuraPreferences
import com.example.data.local.entity.PairedDeviceEntity
import com.example.data.local.entity.TransferRecordEntity
import com.example.data.repository.MediaLibraryRepository
import com.example.data.repository.ShareAndDownloadRepository
import com.example.domain.model.ActiveTransferState
import com.example.domain.model.DiscoveredPeer
import com.example.domain.model.IncomingTransferPrompt
import com.example.domain.model.PeerDiscoverySource
import com.example.domain.model.SelectedShareFile
import com.example.share.DiscoveryCapabilities
import com.example.share.LocalDiscoveryManager
import com.example.share.QrCodeHelper
import com.example.share.ShareTransferEngine
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AuraShareViewModel @Inject constructor(
    private val discoveryManager: LocalDiscoveryManager,
    private val transferEngine: ShareTransferEngine,
    private val qrCodeHelper: QrCodeHelper,
    private val shareRepository: ShareAndDownloadRepository,
    private val mediaRepository: MediaLibraryRepository,
    private val preferences: AuraPreferences
) : ViewModel() {

    val discoveredPeers: StateFlow<List<DiscoveredPeer>> = discoveryManager.discoveredPeers
    val isDiscovering: StateFlow<Boolean> = discoveryManager.isDiscovering
    val isAdvertising: StateFlow<Boolean> = discoveryManager.isAdvertising

    val activeTransfer: StateFlow<ActiveTransferState?> = transferEngine.activeTransfer
    val incomingPrompt: StateFlow<IncomingTransferPrompt?> = transferEngine.incomingPrompt
    val isPaused: StateFlow<Boolean> = transferEngine.isPaused
    val serverPort: StateFlow<Int> = transferEngine.serverPort

    val transferHistory: StateFlow<List<TransferRecordEntity>> = shareRepository.transferHistory
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pairedDevices: StateFlow<List<PairedDeviceEntity>> = shareRepository.pairedDevices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _selectedFiles = MutableStateFlow<List<SelectedShareFile>>(emptyList())
    val selectedFiles: StateFlow<List<SelectedShareFile>> = _selectedFiles.asStateFlow()

    private val _selectedPeer = MutableStateFlow<DiscoveredPeer?>(null)
    val selectedPeer: StateFlow<DiscoveredPeer?> = _selectedPeer.asStateFlow()

    private val _qrBitmap = MutableStateFlow<Bitmap?>(null)
    val qrBitmap: StateFlow<Bitmap?> = _qrBitmap.asStateFlow()

    private val _pairingUriText = MutableStateFlow("")
    val pairingUriText: StateFlow<String> = _pairingUriText.asStateFlow()

    private val _pairingPinDisplay = MutableStateFlow("")
    val pairingPinDisplay: StateFlow<String> = _pairingPinDisplay.asStateFlow()

    private val _shareError = MutableStateFlow<String?>(null)
    val shareError: StateFlow<String?> = _shareError.asStateFlow()

    val discoveryCapabilities: DiscoveryCapabilities by lazy { discoveryManager.inspectCapabilities() }

    fun hasWifiDirectPermission(): Boolean = discoveryManager.hasWifiDirectPermission()

    fun startReceiveMode() {
        viewModelScope.launch {
            val port = transferEngine.startReceiverServer()
            val alias = preferences.settingsFlow.first().deviceAlias
            val localIp = discoveryManager.resolveLocalIpv4Address()
            discoveryManager.startAdvertising(alias, transferEngine.deviceId, port)
            val payload = transferEngine.getCurrentPairingInfo(alias, localIp)
            val uri = qrCodeHelper.encodePayloadUri(payload)
            _pairingUriText.value = uri
            _pairingPinDisplay.value = payload.pairingPin
            _qrBitmap.value = qrCodeHelper.generateQrBitmap(uri, 512)
        }
    }

    fun regeneratePairingQr() {
        transferEngine.regeneratePairingCredentials()
        startReceiveMode()
    }

    fun stopReceiveMode() {
        discoveryManager.stopAdvertising()
        transferEngine.stopReceiverServer()
    }

    fun startDiscovery() {
        discoveryManager.startDiscovery()
    }

    fun stopDiscovery() {
        discoveryManager.stopDiscovery()
    }

    fun selectPeer(peer: DiscoveredPeer) {
        _selectedPeer.value = peer
        _shareError.value = null
    }

    fun pairFromQrOrLink(rawPayload: String): Boolean {
        val parsed = qrCodeHelper.parsePayloadUri(rawPayload) ?: run {
            _shareError.value = "Invalid AURA Share QR or pairing link"
            return false
        }
        val peer = discoveryManager.addQrPairedPeer(parsed)
        _selectedPeer.value = peer
        _shareError.value = null
        return true
    }

    fun pairManually(hostAddress: String, port: Int, pin: String, keyHex: String): Boolean {
        if (hostAddress.isBlank() || pin.length < 4 || keyHex.length != 64) {
            _shareError.value = "Enter valid local IP, 6-digit PIN, and 64-char session key (or scan QR)."
            return false
        }
        val peer = DiscoveredPeer(
            id = "manual_${hostAddress}_$port",
            displayName = "Manual Peer ($hostAddress)",
            hostAddress = hostAddress.trim(),
            port = port,
            pairingPin = pin.trim(),
            sessionKeyHex = keyHex.trim(),
            source = PeerDiscoverySource.NSD_LOCAL_NETWORK,
            isTrusted = true
        )
        _selectedPeer.value = peer
        _shareError.value = null
        return true
    }

    fun addSelectedUris(uris: List<Uri>) {
        val items = uris.map { uri ->
            transferEngine.querySelectedFileMetadata(uri, isPrivate = false)
        }
        _selectedFiles.value = (_selectedFiles.value + items).distinctBy { it.uri }
        _shareError.value = null
    }

    fun addLibraryItemToShare(uri: String, name: String, mimeType: String, sizeBytes: Long, isPrivate: Boolean) {
        if (isPrivate && !mediaRepository.privateSpaceUnlocked.value) {
            _shareError.value = "Private Space is locked. Unlock Private Space with your PIN before authorizing sharing."
            return
        }
        val file = SelectedShareFile(
            uri = uri,
            name = name,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            isPrivate = isPrivate
        )
        _selectedFiles.value = (_selectedFiles.value + file).distinctBy { it.uri }
        _shareError.value = null
    }

    fun removeSelectedFile(uri: String) {
        _selectedFiles.value = _selectedFiles.value.filterNot { it.uri == uri }
    }

    fun clearSelectedFiles() {
        _selectedFiles.value = emptyList()
    }

    fun sendSelectedFiles(pinOverride: String? = null, keyOverride: String? = null) {
        val peer = _selectedPeer.value ?: run {
            _shareError.value = "Select or pair a receiving device first."
            return
        }
        val files = _selectedFiles.value
        val pin = pinOverride?.takeIf { it.isNotBlank() } ?: peer.pairingPin ?: run {
            _shareError.value = "Enter the receiver's 6-digit pairing PIN."
            return
        }
        val keyHex = keyOverride?.takeIf { it.length == 64 } ?: peer.sessionKeyHex ?: run {
            _shareError.value = "Pair via QR code to exchange the encrypted AES-256 session key."
            return
        }

        viewModelScope.launch {
            val alias = preferences.settingsFlow.first().deviceAlias
            val check = transferEngine.validateFilesForSharing(
                files = files,
                privateSpaceAuthorized = mediaRepository.privateSpaceUnlocked.value
            )
            if (check.isFailure) {
                _shareError.value = check.exceptionOrNull()?.message
                return@launch
            }
            _shareError.value = null
            transferEngine.sendFiles(
                peer = peer,
                files = files,
                senderAlias = alias,
                pairingPin = pin,
                sessionKeyHex = keyHex,
                privateSpaceAuthorized = mediaRepository.privateSpaceUnlocked.value
            )
        }
    }

    fun respondToIncomingConsent(accepted: Boolean) = transferEngine.respondToIncomingConsent(accepted)
    fun pauseTransfer() = transferEngine.pauseActiveTransfer()
    fun resumeTransfer() = transferEngine.resumeActiveTransfer()
    fun cancelTransfer() = transferEngine.cancelActiveTransfer()
    fun clearTransferBanner() = transferEngine.clearActiveTransferBanner()
    fun deleteTransferRecord(id: String) {
        viewModelScope.launch { shareRepository.deleteTransferRecord(id) }
    }
    fun clearTransferHistory() {
        viewModelScope.launch { shareRepository.clearTransferHistory() }
    }
    fun clearError() { _shareError.value = null }

    override fun onCleared() {
        stopDiscovery()
        super.onCleared()
    }
}
