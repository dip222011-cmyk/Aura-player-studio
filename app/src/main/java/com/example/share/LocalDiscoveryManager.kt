package com.example.share

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.example.domain.model.DiscoveredPeer
import com.example.domain.model.PeerDiscoverySource
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.Strategy
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.net.Inet4Address
import java.net.NetworkInterface
import javax.inject.Inject
import javax.inject.Singleton

data class DiscoveryCapabilities(
    val nsdSupported: Boolean = true,
    val wifiDirectSupported: Boolean = false,
    val nearbySupported: Boolean = false,
    val localIpAddress: String = "127.0.0.1"
)

@Singleton
class LocalDiscoveryManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val nsdManager: NsdManager? =
        context.getSystemService(Context.NSD_SERVICE) as? NsdManager

    private val wifiP2pManager: WifiP2pManager? =
        context.getSystemService(Context.WIFI_P2P_SERVICE) as? WifiP2pManager

    private val _discoveredPeers = MutableStateFlow<List<DiscoveredPeer>>(emptyList())
    val discoveredPeers: StateFlow<List<DiscoveredPeer>> = _discoveredPeers.asStateFlow()

    private val _isDiscovering = MutableStateFlow(false)
    val isDiscovering: StateFlow<Boolean> = _isDiscovering.asStateFlow()

    private val _isAdvertising = MutableStateFlow(false)
    val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()

    private var registrationListener: NsdManager.RegistrationListener? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var registeredServiceName: String? = null

    fun inspectCapabilities(): DiscoveryCapabilities {
        val hasWifiDirect = context.packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI_DIRECT) &&
            wifiP2pManager != null
        val gmsAvailable = runCatching {
            GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
        }.getOrDefault(false)
        return DiscoveryCapabilities(
            nsdSupported = nsdManager != null,
            wifiDirectSupported = hasWifiDirect,
            nearbySupported = gmsAvailable,
            localIpAddress = resolveLocalIpv4Address()
        )
    }

    fun hasWifiDirectPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.NEARBY_WIFI_DEVICES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun startAdvertising(deviceAlias: String, deviceId: String, port: Int) {
        val mgr = nsdManager ?: return
        stopAdvertising()
        val info = NsdServiceInfo().apply {
            serviceName = "AuraShare-${deviceAlias.take(16)}-${deviceId.take(4)}"
            serviceType = SERVICE_TYPE
            setPort(port)
            setAttribute("id", deviceId)
            setAttribute("name", deviceAlias)
        }
        val listener = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(nsdServiceInfo: NsdServiceInfo) {
                registeredServiceName = nsdServiceInfo.serviceName
                _isAdvertising.value = true
            }

            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                _isAdvertising.value = false
            }

            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) {
                _isAdvertising.value = false
            }

            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
        }
        registrationListener = listener
        runCatching {
            mgr.registerService(info, NsdManager.PROTOCOL_DNS_SD, listener)
        }
    }

    fun stopAdvertising() {
        val mgr = nsdManager ?: return
        registrationListener?.let { listener ->
            runCatching { mgr.unregisterService(listener) }
        }
        registrationListener = null
        _isAdvertising.value = false
    }

    @SuppressLint("MissingPermission")
    fun startDiscovery() {
        stopDiscovery()
        _discoveredPeers.value = emptyList()
        _isDiscovering.value = true

        // 1. NSD Local Wi-Fi / Hotspot discovery
        nsdManager?.let { mgr ->
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(regType: String) {}
                override fun onServiceFound(service: NsdServiceInfo) {
                    if (service.serviceType != SERVICE_TYPE) return
                    if (service.serviceName == registeredServiceName) return
                    @Suppress("DEPRECATION")
                    mgr.resolveService(service, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                        override fun onServiceResolved(resolved: NsdServiceInfo) {
                            @Suppress("DEPRECATION")
                            val host = resolved.host?.hostAddress ?: return
                            val peer = DiscoveredPeer(
                                id = resolved.serviceName,
                                displayName = resolved.serviceName.removePrefix("AuraShare-"),
                                hostAddress = host,
                                port = resolved.port,
                                source = PeerDiscoverySource.NSD_LOCAL_NETWORK
                            )
                            upsertPeer(peer)
                        }
                    })
                }

                override fun onServiceLost(service: NsdServiceInfo) {
                    _discoveredPeers.update { list -> list.filterNot { it.id == service.serviceName } }
                }

                override fun onDiscoveryStopped(serviceType: String) {}
                override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                    _isDiscovering.value = false
                }

                override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
            }
            discoveryListener = listener
            runCatching { mgr.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener) }
        }

        // 2. Wi-Fi Direct peer discovery where supported and permitted
        val p2pMgr = wifiP2pManager
        if (hasWifiDirectPermission() && p2pMgr != null) {
            runCatching {
                val channel = p2pMgr.initialize(context, context.mainLooper, null)
                if (channel != null) {
                    p2pMgr.discoverPeers(channel, object : WifiP2pManager.ActionListener {
                        override fun onSuccess() {
                            p2pMgr.requestPeers(channel) { peerList ->
                                peerList.deviceList.forEach { device ->
                                    upsertPeer(
                                        DiscoveredPeer(
                                            id = "p2p_${device.deviceAddress}",
                                            displayName = device.deviceName.ifBlank { "Wi-Fi Direct Peer" },
                                            hostAddress = device.deviceAddress,
                                            port = DEFAULT_SHARE_PORT,
                                            source = PeerDiscoverySource.WIFI_DIRECT
                                        )
                                    )
                                }
                            }
                        }

                        override fun onFailure(reason: Int) {}
                    })
                }
            }
        }

        // 3. Google Nearby Connections discovery where GMS is available
        if (inspectCapabilities().nearbySupported) {
            runCatching {
                val options = DiscoveryOptions.Builder().setStrategy(Strategy.P2P_POINT_TO_POINT).build()
                Nearby.getConnectionsClient(context).startDiscovery(
                    context.packageName,
                    object : EndpointDiscoveryCallback() {
                        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
                            upsertPeer(
                                DiscoveredPeer(
                                    id = "nearby_$endpointId",
                                    displayName = info.endpointName,
                                    hostAddress = endpointId,
                                    port = DEFAULT_SHARE_PORT,
                                    source = PeerDiscoverySource.NEARBY_CONNECTIONS
                                )
                            )
                        }

                        override fun onEndpointLost(endpointId: String) {
                            _discoveredPeers.update { list -> list.filterNot { it.id == "nearby_$endpointId" } }
                        }
                    },
                    options
                )
            }
        }
    }

    fun stopDiscovery() {
        discoveryListener?.let { listener ->
            runCatching { nsdManager?.stopServiceDiscovery(listener) }
        }
        discoveryListener = null
        runCatching { Nearby.getConnectionsClient(context).stopDiscovery() }
        _isDiscovering.value = false
    }

    fun addQrPairedPeer(payload: PairingPayload): DiscoveredPeer {
        val peer = DiscoveredPeer(
            id = payload.deviceId,
            displayName = payload.displayName,
            hostAddress = payload.hostAddress,
            port = payload.port,
            pairingPin = payload.pairingPin,
            sessionKeyHex = payload.sessionKeyHex,
            source = PeerDiscoverySource.QR_PAIRING,
            isTrusted = true
        )
        upsertPeer(peer)
        return peer
    }

    private fun upsertPeer(peer: DiscoveredPeer) {
        _discoveredPeers.update { current ->
            (current.filterNot { it.id == peer.id } + peer)
        }
    }

    fun resolveLocalIpv4Address(): String {
        return runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return "127.0.0.1"
            for (intf in interfaces) {
                if (!intf.isUp || intf.isLoopback) continue
                for (addr in intf.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        return addr.hostAddress ?: continue
                    }
                }
            }
            "127.0.0.1"
        }.getOrDefault("127.0.0.1")
    }

    companion object {
        const val SERVICE_TYPE = "_aurashare._tcp."
        const val DEFAULT_SHARE_PORT = 48721
    }
}
