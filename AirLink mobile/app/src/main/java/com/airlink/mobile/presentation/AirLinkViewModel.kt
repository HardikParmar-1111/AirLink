package com.airlink.mobile.presentation

import android.app.Application
import android.content.ClipboardManager
import android.content.Intent
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.airlink.mobile.battery.BatteryMonitor
import com.airlink.mobile.clipboard.ClipboardSyncManager
import com.airlink.mobile.data.ClipboardPayload
import com.airlink.mobile.network.AirLinkDiscoveryManager
import com.airlink.mobile.network.AirLinkWebSocketManager
import com.airlink.mobile.network.ConnectionState
import com.airlink.mobile.network.DiscoveredDevice
import com.airlink.mobile.network.NetworkUtils
import com.airlink.mobile.ringer.PhoneRingerManager
import com.airlink.mobile.streaming.ScreenCaptureService
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import org.json.JSONObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AirLinkViewModel(application: Application) : AndroidViewModel(application) {
    private val gson = Gson()
    private val socketManager = AirLinkWebSocketManager(application)
    private val clipboardSync = ClipboardSyncManager(application)
    private val discoveryManager = AirLinkDiscoveryManager(application)

    /**
     * Strong ref to the Activity clipboard listener so OEM ClipboardManagers
     * that store listeners weakly cannot GC it while this ViewModel is alive.
     */
    var retainedClipboardListener: ClipboardManager.OnPrimaryClipChangedListener? = null

    /**
     * Variable for loop prevention: records the last clipboard text received from the Mac
     * so that Android avoids echoing it back.
     */
    @Volatile
    var lastReceivedText: String? = null

    var onRemoteClipboardReceived: ((String) -> Unit)? = null

    val connectionState: StateFlow<ConnectionState> = socketManager.connectionState
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ConnectionState.Disconnected
        )

    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = discoveryManager.discoveredDevices

    val isStreaming: StateFlow<Boolean> = ScreenCaptureService.isStreamingFlow
    val isRinging: StateFlow<Boolean> = PhoneRingerManager.isRinging

    private val _batteryState = MutableStateFlow<Pair<Int, Boolean>?>(null)
    val batteryState: StateFlow<Pair<Int, Boolean>?> = _batteryState.asStateFlow()

    private val batteryMonitor = BatteryMonitor(application) { level, isCharging ->
        _batteryState.value = level to isCharging
        val payload = JSONObject().apply {
            put("type", "battery")
            put("level", level)
            put("isCharging", isCharging)
        }.toString()
        val sent = socketManager.send(payload)
        Log.d(TAG, if (sent) "Sent battery payload to Mac: $payload" else "Battery payload buffered (not connected)")
    }

    private val _requestScreenCapturePrompt = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val requestScreenCapturePrompt: SharedFlow<Unit> = _requestScreenCapturePrompt.asSharedFlow()

    private val _connectedMacName = MutableStateFlow<String?>(null)
    val connectedMacName: StateFlow<String?> = _connectedMacName.asStateFlow()

    private val _statusDetail = MutableStateFlow<String?>(null)
    val statusDetail: StateFlow<String?> = _statusDetail.asStateFlow()

    var onConnectionLostCallback: (() -> Unit)? = null

    init {
        // Collect incoming WebSocket messages
        viewModelScope.launch {
            socketManager.incomingMessages.collect { raw ->
                handleIncoming(raw)
            }
        }

        socketManager.onPairingAccepted = {
            batteryMonitor.sendCurrentStatus()
        }

        socketManager.onConnectionLost = {
            Log.w(TAG, "Connection lost to Mac (abrupt disconnect / network drop) - performing clean teardown")
            ScreenCaptureService.stop(getApplication())
            PhoneRingerManager.stopRinging()
            clearClipboardListeners()
            _connectedMacName.value = null
            _statusDetail.value = null
            onConnectionLostCallback?.invoke()
        }

        socketManager.onStartMirroringRequested = {
            if (!ScreenCaptureService.isStreamingFlow.value) {
                _requestScreenCapturePrompt.tryEmit(Unit)
            } else {
                socketManager.sendStreamInfo()
            }
        }

        socketManager.onStopMirroringRequested = {
            Log.d(TAG, "Mac requested stop_mirroring -> halting capture session")
            ScreenCaptureService.stop(getApplication())
        }

        // Start battery monitoring
        batteryMonitor.start()

        // Start mDNS discovery immediately (without auto-connecting)
        discoveryManager.startDiscovery()
    }

    fun getLocalStreamUrl(): String {
        return NetworkUtils.getStreamUrl(getApplication())
    }

    fun getLocalIp(): String {
        return NetworkUtils.getLocalIpAddress(getApplication()) ?: "127.0.0.1"
    }

    fun requestStartScreenCapture() {
        _requestScreenCapturePrompt.tryEmit(Unit)
    }

    fun startScreenCapture(resultCode: Int, data: Intent) {
        ScreenCaptureService.start(getApplication(), resultCode, data)
        socketManager.sendStreamInfo()
    }

    fun stopScreenCapture() {
        Log.d(TAG, "User requested stop screen feed -> notifying Mac and stopping service")
        socketManager.sendStopMirroring()
        ScreenCaptureService.stop(getApplication())
    }

    fun connectToDevice(device: DiscoveredDevice) {
        _connectedMacName.value = device.name
        _statusDetail.value = null
        socketManager.connect(device.host, device.port)
    }

    fun stopRinging() {
        PhoneRingerManager.stopRinging()
    }

    fun clearClipboardListeners() {
        retainedClipboardListener = null
        lastReceivedText = null
    }

    fun onDisconnectClicked() {
        stopRinging()
        stopScreenCapture()
        clearClipboardListeners()
        socketManager.disconnect()
        _connectedMacName.value = null
        _statusDetail.value = null
        onConnectionLostCallback?.invoke()
    }

    /**
     * Called from the Activity clipboard listener. Builds the JSON payload and
     * writes it to the live OkHttp WebSocket on this calling thread.
     */
    fun sendClipboardImmediately(text: String) {
        if (text.isEmpty()) return
        if (text == lastReceivedText) return
        if (clipboardSync.shouldIgnoreLocalChange(text)) return
        clipboardSync.markLocalTextSent(text)

        val payload = JSONObject().apply {
            put("type", ClipboardPayload.TYPE_CLIPBOARD)
            put("data", text)
        }.toString()
        val sent = socketManager.send(payload)
        Log.d(TAG, if (sent) "Sent clipboard payload (${text.length} chars)" else "Clipboard send failed")
    }

    fun sendClipboardText(text: String) {
        sendClipboardImmediately(text)
    }

    private suspend fun handleIncoming(raw: String) {
        val (type, data) = withContext(Dispatchers.IO) {
            try {
                val json = JSONObject(raw)
                val type = json.optString("type")
                val data = when {
                    json.has("data") -> json.getString("data")
                    json.has("text") -> json.getString("text")
                    json.has("content") -> json.getString("content")
                    else -> ""
                }
                type to data
            } catch (_: Exception) {
                null to null
            }
        }

        if (type != ClipboardPayload.TYPE_CLIPBOARD || data.isNullOrEmpty()) return

        withContext(Dispatchers.Main.immediate) {
            lastReceivedText = data
            clipboardSync.writeRemoteText(data)
            onRemoteClipboardReceived?.invoke(data)
        }
    }

    override fun onCleared() {
        batteryMonitor.stop()
        stopScreenCapture()
        retainedClipboardListener = null
        discoveryManager.stopDiscovery()
        socketManager.shutdown()
        super.onCleared()
    }

    companion object {
        private const val TAG = "AirLinkViewModel"
    }
}
