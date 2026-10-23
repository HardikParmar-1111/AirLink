package com.airlink.mobile.network

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.net.Inet4Address
import java.util.ArrayDeque

data class DiscoveredDevice(
    val id: String,
    val name: String,
    val host: String,
    val port: Int
)

/**
 * Manages mDNS discovery of Mac AirLink WebSocket servers using Android's [NsdManager].
 * Discovered services are added to [discoveredDevices] StateFlow.
 * All state updates are explicitly posted to the main thread.
 */
class AirLinkDiscoveryManager(private val context: Context) {

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var multicastLock: WifiManager.MulticastLock? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var isDiscoveryActive = false

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

    private val resolveQueue = ArrayDeque<NsdServiceInfo>()
    private var isResolving = false

    private var onDiscoveryStateChangedCallback: ((Boolean) -> Unit)? = null

    init {
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            multicastLock = wifiManager?.createMulticastLock("AirLinkMulticastLock")?.apply {
                setReferenceCounted(true)
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Unable to initialize MulticastLock: ${t.message}")
        }
    }

    fun startDiscovery() {
        if (isDiscoveryActive) {
            Log.d(TAG, "Discovery already active")
            return
        }

        acquireMulticastLock()
        startDiscoveryInternal(SERVICE_TYPE_DOT)
    }

    private fun startDiscoveryInternal(serviceType: String) {
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {
                Log.d(TAG, "mDNS discovery started for type: $regType")
                isDiscoveryActive = true
                mainHandler.post {
                    onDiscoveryStateChangedCallback?.invoke(true)
                }
            }

            override fun onServiceFound(service: NsdServiceInfo) {
                Log.d(TAG, "Service found: ${service.serviceName}, type: ${service.serviceType}")
                val type = service.serviceType.orEmpty()
                if (type.contains("_airlink._tcp")) {
                    queueResolve(service)
                }
            }

            override fun onServiceLost(service: NsdServiceInfo) {
                Log.d(TAG, "Service lost: ${service.serviceName}")
                val lostName = service.serviceName.orEmpty()
                mainHandler.post {
                    _discoveredDevices.value = _discoveredDevices.value.filterNot { it.name == lostName }
                }
            }

            override fun onDiscoveryStopped(serviceType: String) {
                Log.d(TAG, "mDNS discovery stopped: $serviceType")
                isDiscoveryActive = false
                mainHandler.post {
                    onDiscoveryStateChangedCallback?.invoke(false)
                }
            }

            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Start discovery failed: code $errorCode for $serviceType")
                isDiscoveryActive = false
                releaseMulticastLock()
                mainHandler.post {
                    onDiscoveryStateChangedCallback?.invoke(false)
                }

                // If starting with trailing dot fails on this Android device, retry with fallback
                if (serviceType == SERVICE_TYPE_DOT) {
                    mainHandler.postDelayed({
                        startDiscoveryInternal(SERVICE_TYPE_NO_DOT)
                    }, 300)
                }
            }

            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                Log.e(TAG, "Stop discovery failed: code $errorCode")
                isDiscoveryActive = false
                releaseMulticastLock()
                mainHandler.post {
                    onDiscoveryStateChangedCallback?.invoke(false)
                }
            }
        }

        this.discoveryListener = listener

        try {
            nsdManager.discoverServices(serviceType, NsdManager.PROTOCOL_DNS_SD, listener)
        } catch (t: Throwable) {
            Log.e(TAG, "Exception starting service discovery: ${t.message}", t)
            isDiscoveryActive = false
            releaseMulticastLock()
            mainHandler.post {
                onDiscoveryStateChangedCallback?.invoke(false)
            }
        }
    }

    @Synchronized
    private fun queueResolve(serviceInfo: NsdServiceInfo) {
        if (resolveQueue.none { it.serviceName == serviceInfo.serviceName }) {
            resolveQueue.add(serviceInfo)
        }
        processNextResolve()
    }

    @Synchronized
    private fun processNextResolve() {
        if (isResolving || resolveQueue.isEmpty()) return
        val next = resolveQueue.removeFirst()
        isResolving = true

        val resolveListener = object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                Log.w(TAG, "Resolve failed for ${serviceInfo.serviceName}: code $errorCode")
                synchronized(this@AirLinkDiscoveryManager) {
                    isResolving = false
                    processNextResolve()
                }
            }

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                Log.d(TAG, "Service resolved: ${serviceInfo.serviceName} at ${serviceInfo.host}:${serviceInfo.port}")
                val hostIp = extractIpAddress(serviceInfo)
                val port = serviceInfo.port
                val name = serviceInfo.serviceName ?: "Mac"

                if (!hostIp.isNullOrEmpty() && port > 0) {
                    val device = DiscoveredDevice(
                        id = "${name}_${hostIp}_$port",
                        name = name,
                        host = hostIp,
                        port = port
                    )
                    mainHandler.post {
                        val current = _discoveredDevices.value.filterNot {
                            it.name == name || (it.host == hostIp && it.port == port)
                        }
                        _discoveredDevices.value = current + device
                    }
                }

                synchronized(this@AirLinkDiscoveryManager) {
                    isResolving = false
                    processNextResolve()
                }
            }
        }

        try {
            nsdManager.resolveService(next, resolveListener)
        } catch (t: Throwable) {
            Log.e(TAG, "Exception during resolveService", t)
            isResolving = false
            processNextResolve()
        }
    }

    private fun extractIpAddress(serviceInfo: NsdServiceInfo): String? {
        val host = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            serviceInfo.hostAddresses.firstOrNull { it is Inet4Address }
                ?: serviceInfo.hostAddresses.firstOrNull()
                ?: serviceInfo.host
        } else {
            serviceInfo.host
        }
        val rawIp = host?.hostAddress ?: return null
        return if (rawIp.contains("%")) rawIp.substringBefore("%") else rawIp
    }

    fun stopDiscovery() {
        if (!isDiscoveryActive) {
            Log.d(TAG, "Discovery not active; skipping stop")
            return
        }

        val listener = discoveryListener ?: return
        try {
            nsdManager.stopServiceDiscovery(listener)
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping service discovery: ${e.message}")
        } finally {
            isDiscoveryActive = false
            discoveryListener = null
            releaseMulticastLock()
            mainHandler.post {
                onDiscoveryStateChangedCallback?.invoke(false)
            }
        }
    }

    private fun acquireMulticastLock() {
        try {
            if (multicastLock?.isHeld == false) {
                multicastLock?.acquire()
                Log.d(TAG, "Acquired MulticastLock")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to acquire MulticastLock: ${t.message}")
        }
    }

    private fun releaseMulticastLock() {
        try {
            if (multicastLock?.isHeld == true) {
                multicastLock?.release()
                Log.d(TAG, "Released MulticastLock")
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Failed to release MulticastLock: ${t.message}")
        }
    }

    companion object {
        private const val TAG = "AirLinkDiscovery"
        const val SERVICE_TYPE_DOT = "_airlink._tcp."
        const val SERVICE_TYPE_NO_DOT = "_airlink._tcp"
    }
}
