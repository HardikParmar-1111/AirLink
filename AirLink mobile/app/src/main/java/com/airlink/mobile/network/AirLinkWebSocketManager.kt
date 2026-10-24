package com.airlink.mobile.network

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import com.airlink.mobile.accessibility.AirLinkAccessibilityService
import com.airlink.mobile.crypto.AirLinkCrypto
import com.airlink.mobile.notifications.AirLinkNotificationListener
import com.airlink.mobile.ringer.PhoneRingerManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * OkHttp WebSocket client for the AirLink macOS companion.
 *
 * Connection work is dispatched on [Dispatchers.IO]. Incoming frames are
 * emitted on [incomingMessages]; UI/clipboard consumers should hop to Main.
 * Includes try-catch crash prevention and AES-256-GCM encryption with graceful fallback.
 */
class AirLinkWebSocketManager(
    private val context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)
    private val socketRef = AtomicReference<WebSocket?>(null)
    private val crypto = AirLinkCrypto()

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(8, TimeUnit.SECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _incomingMessages = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val incomingMessages: SharedFlow<String> = _incomingMessages.asSharedFlow()

    var onStartMirroringRequested: (() -> Unit)? = null
    var onStopMirroringRequested: (() -> Unit)? = null
    var onPairingAccepted: (() -> Unit)? = null
    var onConnectionLost: (() -> Unit)? = null

    val callManager = com.airlink.mobile.call.AirLinkCallManager(context, this)

    fun sendStreamInfo() {
        val payload = JSONObject().apply {
            put("type", "mirror_stream")
            put("mode", "websocket_binary")
        }.toString()
        val sent = send(payload)
        Log.d(TAG, "Sent mirror_stream announcement to Mac: $sent")
    }

    fun sendStopMirroring(): Boolean {
        com.airlink.mobile.accessibility.AirLinkAccessibilityService.isTouchInjectionAllowed = false
        val payload = JSONObject().apply {
            put("type", "mirroring")
            put("action", "stop")
        }.toString()
        val sent = send(payload)
        // Also send legacy stop_mirror for maximum backwards compatibility
        val legacy = JSONObject().apply {
            put("type", "stop_mirror")
        }.toString()
        send(legacy)
        Log.d(TAG, "Sent mirroring stop signals to Mac: $sent")
        return sent
    }

    fun sendBinary(bytes: ByteArray): Boolean {
        val socket = socketRef.get() ?: return false
        if (_connectionState.value !is ConnectionState.Connected) {
            return false
        }
        // Drop stale frame if WebSocket buffer is currently congested (> 128KB)
        if (socket.queueSize() > 128 * 1024) {
            return false
        }
        return try {
            socket.send(bytes.toByteString())
        } catch (e: IOException) {
            Log.w(TAG, "IOException during binary send: ${e.message}")
            handleDisconnectionOnFailure(e)
            false
        } catch (t: Throwable) {
            Log.w(TAG, "Error during binary send: ${t.message}")
            false
        }
    }

    private fun getOrCreateDeviceId(): String {
        val prefs = context.getSharedPreferences("airlink_prefs", Context.MODE_PRIVATE)
        var id = prefs.getString("device_id", null)
        if (id.isNullOrEmpty()) {
            id = UUID.randomUUID().toString()
            prefs.edit().putString("device_id", id).apply()
        }
        return id
    }

    fun connect(host: String, port: Int = DEFAULT_PORT) {
        scope.launch {
            disconnectInternal(userInitiated = false)
            crypto.clearKey()
            _connectionState.value = ConnectionState.Connecting

            val formattedHost = if (host.contains(":") && !host.startsWith("[")) "[$host]" else host
            val url = "ws://$formattedHost:$port"
            val request = Request.Builder()
                .url(url)
                .build()

            Log.d(TAG, "Connecting to $url")
            try {
                val socket = client.newWebSocket(request, listener)
                socketRef.set(socket)
            } catch (e: IOException) {
                Log.e(TAG, "Failed to initiate WebSocket connection: ${e.message}", e)
                handleDisconnectionOnFailure(e)
            } catch (t: Throwable) {
                Log.e(TAG, "Unexpected error connecting to $url: ${t.message}", t)
                handleDisconnectionOnFailure(t)
            }
        }
    }

    fun disconnect() {
        scope.launch {
            disconnectInternal(userInitiated = true)
            crypto.clearKey()
            _connectionState.value = ConnectionState.Disconnected
        }
    }

    /**
     * Sends [text] on the current socket immediately. OkHttp's [WebSocket.send]
     * is thread-safe. Encrypts payload with AES-256-GCM if key was established.
     * Catches IOExceptions and cleans up safely without crashing.
     */
    fun send(text: String): Boolean {
        if (text.contains("\"incoming_call\"")) {
            Log.d("AirLink", "Sending incoming_call JSON to Mac")
        }
        val socket = socketRef.get()
        if (socket == null || _connectionState.value !is ConnectionState.Connected) {
            Log.w(TAG, "Send skipped: socket not connected")
            return false
        }
        return try {
            val payload = if (crypto.isKeyEstablished) {
                crypto.encryptText(text)
            } else {
                text
            }
            if (text.contains("\"incoming_call\"")) {
                Log.d("AirLink", "WebSocket pushing incoming_call frame (encrypted=${crypto.isKeyEstablished})")
            }
            val sent = socket.send(payload)
            if (!sent) {
                Log.w(TAG, "WebSocket send queue is full or socket is closed")
            }
            sent
        } catch (e: IOException) {
            Log.e(TAG, "IOException during WebSocket send: ${e.message}", e)
            handleDisconnectionOnFailure(e)
            false
        } catch (t: Throwable) {
            Log.e(TAG, "Error during WebSocket send: ${t.message}", t)
            handleDisconnectionOnFailure(t)
            false
        }
    }

    private fun handleDisconnectionOnFailure(t: Throwable) {
        Log.w(TAG, "Handling disconnect on failure: ${t.message}")
        disconnectInternal(userInitiated = false)
        crypto.clearKey()
        _connectionState.value = ConnectionState.Disconnected
        scope.launch(Dispatchers.Main) {
            onConnectionLost?.invoke()
        }
    }

    init {
        instance = this
        AirLinkNotificationListener.listenerCallback = { appName, title, text, _ ->
            if (_connectionState.value is ConnectionState.Connected) {
                try {
                    val payload = JSONObject().apply {
                        put("type", "notification")
                        put("app", appName)
                        put("title", title)
                        put("text", text)
                    }.toString()
                    val sent = send(payload)
                    Log.d(TAG, if (sent) "Forwarded notification to Mac: $appName - $title" else "Notification send skipped")
                } catch (e: Exception) {
                    Log.w(TAG, "Error forwarding notification: ${e.message}")
                }
            }
        }
    }

    fun shutdown() {
        if (instance == this) {
            instance = null
        }
        PhoneRingerManager.stopRinging()
        AirLinkNotificationListener.listenerCallback = null
        disconnect()
        scope.cancel()
        client.dispatcher.executorService.shutdown()
    }

    private fun disconnectInternal(userInitiated: Boolean) {
        com.airlink.mobile.accessibility.AirLinkAccessibilityService.isTouchInjectionAllowed = false
        socketRef.getAndSet(null)?.let { socket ->
            try {
                if (userInitiated) {
                    socket.close(1000, "Client disconnect")
                } else {
                    socket.cancel()
                }
            } catch (t: Throwable) {
                Log.w(TAG, "Error while closing socket", t)
            }
        }
    }

    private fun isImageFile(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext in setOf("png", "jpg", "jpeg", "webp", "gif", "bmp", "heic", "heif")
    }

    private fun saveFileToDownloads(fileName: String, fileData: String) {
        scope.launch(ioDispatcher) {
            try {
                val bytes = Base64.decode(fileData, Base64.DEFAULT)
                var saved = false
                val isImage = isImageFile(fileName)
                var writtenFile: File? = null
                var mediaStoreUri: Uri? = null

                // Attempt writing directly to Environment.DIRECTORY_DOWNLOADS
                try {
                    val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    if (!downloadsDir.exists()) {
                        downloadsDir.mkdirs()
                    }
                    var destFile = File(downloadsDir, fileName)
                    var counter = 1
                    val nameWithoutExt = destFile.nameWithoutExtension
                    val ext = destFile.extension
                    while (destFile.exists()) {
                        val newName = if (ext.isNotEmpty()) "$nameWithoutExt ($counter).$ext" else "$nameWithoutExt ($counter)"
                        destFile = File(downloadsDir, newName)
                        counter++
                    }
                    destFile.writeBytes(bytes)
                    saved = true
                    writtenFile = destFile
                    Log.d(TAG, "File saved to ${destFile.absolutePath} (${bytes.size} bytes)")
                } catch (t: Throwable) {
                    Log.w(TAG, "Direct file write failed: ${t.message}, attempting MediaStore fallback")
                }

                // Fallback to MediaStore if direct write was restricted by scoped storage
                if (!saved && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val contentValues = ContentValues().apply {
                        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                        put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    }
                    val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                    if (uri != null) {
                        context.contentResolver.openOutputStream(uri)?.use { os ->
                            os.write(bytes)
                        }
                        saved = true
                        mediaStoreUri = uri
                        Log.d(TAG, "File saved via MediaStore: $uri")
                    }
                }

                if (saved) {
                    if (isImage) {
                        val contentUri = try {
                            if (writtenFile != null && writtenFile.exists()) {
                                FileProvider.getUriForFile(
                                    context,
                                    "${context.packageName}.fileprovider",
                                    writtenFile
                                )
                            } else {
                                mediaStoreUri
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error obtaining FileProvider URI: ${e.message}", e)
                            mediaStoreUri
                        }

                        if (contentUri != null) {
                            try {
                                context.grantUriPermission(
                                    context.packageName,
                                    contentUri,
                                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                                )
                                val queryIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = context.contentResolver.getType(contentUri) ?: "image/*"
                                    putExtra(Intent.EXTRA_STREAM, contentUri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                val resInfoList = context.packageManager.queryIntentActivities(
                                    queryIntent,
                                    PackageManager.MATCH_DEFAULT_ONLY
                                )
                                for (resolveInfo in resInfoList) {
                                    context.grantUriPermission(
                                        resolveInfo.activityInfo.packageName,
                                        contentUri,
                                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    )
                                }
                            } catch (t: Throwable) {
                                Log.w(TAG, "Error granting read permissions: ${t.message}")
                            }

                            val clipData = ClipData.newUri(context.contentResolver, fileName, contentUri)

                            withContext(Dispatchers.Main) {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                clipboard.setPrimaryClip(clipData)
                                Toast.makeText(
                                    context,
                                    "Image saved & copied to clipboard!",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        } else {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "File saved: $fileName", Toast.LENGTH_LONG).show()
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            Toast.makeText(context, "File saved: $fileName", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to decode/save file $fileName: ${e.message}", e)
            }
        }
    }

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            Log.d(TAG, "WebSocket onOpen: connected to server, sending pairing_request")
            _connectionState.value = ConnectionState.Connecting

            try {
                val deviceId = getOrCreateDeviceId()

                // Query current battery status from sticky intent
                val batteryIntent = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
                val bLevel = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val bScale = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1) ?: -1
                val bPct = if (bLevel >= 0 && bScale > 0) ((bLevel / bScale.toFloat()) * 100).toInt() else bLevel
                val bStatus = batteryIntent?.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1) ?: -1
                val bCharging = bStatus == android.os.BatteryManager.BATTERY_STATUS_CHARGING || bStatus == android.os.BatteryManager.BATTERY_STATUS_FULL

                val pairingRequest = JSONObject().apply {
                    put("type", "pairing_request")
                    put("deviceName", android.os.Build.MODEL)
                    put("deviceId", deviceId)
                    if (bPct >= 0) {
                        put("level", bPct)
                        put("batteryLevel", bPct)
                        put("isCharging", bCharging)
                    }
                }.toString()
                webSocket.send(pairingRequest)
                Log.d(TAG, "Sent pairing request: $pairingRequest")
            } catch (e: IOException) {
                Log.e(TAG, "IOException during onOpen send: ${e.message}", e)
                handleDisconnectionOnFailure(e)
            } catch (t: Throwable) {
                Log.e(TAG, "Error in onOpen: ${t.message}", t)
                handleDisconnectionOnFailure(t)
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val plainText = try {
                crypto.decryptText(text)
            } catch (t: Throwable) {
                Log.w(TAG, "Decryption error, falling back to raw: ${t.message}")
                text
            }

            try {
                val json = JSONObject(plainText)
                val type = json.optString("type")
                when (type) {
                    "pairing_accepted" -> {
                        Log.d(TAG, "Pairing accepted by Mac, establishing AES-256-GCM symmetric key")
                        crypto.establishKeyFromHandshake(json, getOrCreateDeviceId())
                        _connectionState.value = ConnectionState.Connected
                        try {
                            callManager.startListening()
                        } catch (t: Throwable) {
                            Log.w(TAG, "Could not start call listening: ${t.message}")
                        }
                        scope.launch(Dispatchers.Main) {
                            onPairingAccepted?.invoke()
                        }
                    }
                    "pairing_rejected" -> {
                        Log.d(TAG, "Pairing rejected by Mac")
                        disconnectInternal(userInitiated = true)
                        crypto.clearKey()
                        _connectionState.value = ConnectionState.Disconnected
                        scope.launch(Dispatchers.Main) {
                            Toast.makeText(context, "Connection declined", Toast.LENGTH_LONG).show()
                        }
                        return
                    }
                    "ping" -> {
                        val pong = JSONObject().apply {
                            put("type", "pong")
                        }.toString()
                        val sent = webSocket.send(pong)
                        Log.d(TAG, "Received ping from Mac, responded with pong ($sent)")
                        return
                    }
                    "pong" -> {
                        Log.d(TAG, "Received pong from Mac")
                        return
                    }
                    "mirroring" -> {
                        val action = json.optString("action")
                        if (action.equals("stop", ignoreCase = true)) {
                            Log.d(TAG, "Received mirroring stop action from Mac")
                            com.airlink.mobile.accessibility.AirLinkAccessibilityService.isTouchInjectionAllowed = false
                            scope.launch(Dispatchers.Main) {
                                onStopMirroringRequested?.invoke()
                            }
                        } else if (action.equals("start", ignoreCase = true)) {
                            Log.d(TAG, "Received mirroring start action from Mac")
                            scope.launch(Dispatchers.Main) {
                                onStartMirroringRequested?.invoke()
                            }
                        }
                    }
                    "start_mirror", "start_mirroring" -> {
                        Log.d(TAG, "Received start_mirror request from Mac")
                        scope.launch(Dispatchers.Main) {
                            onStartMirroringRequested?.invoke()
                        }
                    }
                    "stop_mirror", "stop_mirroring" -> {
                        Log.d(TAG, "Received stop_mirror request from Mac")
                        com.airlink.mobile.accessibility.AirLinkAccessibilityService.isTouchInjectionAllowed = false
                        scope.launch(Dispatchers.Main) {
                            onStopMirroringRequested?.invoke()
                        }
                    }
                    "ring_phone", "ring", "find_my_phone", "find_phone" -> {
                        val action = json.optString("action").lowercase()
                        if (action == "stop") {
                            Log.d(TAG, "Received find_phone stop action from Mac — stopping ringtone")
                            PhoneRingerManager.stopRinging(context)
                        } else {
                            Log.d(TAG, "Received find_phone request from Mac — triggering full-screen alert & max volume ringtone")
                            PhoneRingerManager.startRinging(context)
                        }
                    }
                    "stop_ring", "stop_ringing", "stop_find_phone" -> {
                        Log.d(TAG, "Received stop_ring request from Mac — stopping ringtone")
                        PhoneRingerManager.stopRinging(context)
                    }
                    "decline_and_text", "decline_call" -> {
                        val message = when {
                            json.has("message") -> json.getString("message")
                            json.has("text") -> json.getString("text")
                            else -> "I will call back later, please text me."
                        }
                        val explicitNumber = when {
                            json.has("number") -> json.getString("number")
                            json.has("phoneNumber") -> json.getString("phoneNumber")
                            else -> null
                        }
                        Log.d(TAG, "Received decline_and_text command from Mac: '$message', number: $explicitNumber")
                        callManager.declineAndSendSMS(message, explicitNumber)
                    }
                    "touch" -> {
                        if (!com.airlink.mobile.accessibility.AirLinkAccessibilityService.isTouchInjectionAllowed) {
                            Log.d(TAG, "Blocked touch: isTouchInjectionAllowed is false")
                            return
                        }
                        val normX = (if (json.has("x")) json.optDouble("x", 0.0) else json.optDouble("normalizedX", 0.0)).toFloat()
                        val normY = (if (json.has("y")) json.optDouble("y", 0.0) else json.optDouble("normalizedY", 0.0)).toFloat()
                        val action = json.optString("action", "tap")
                        Log.d(TAG, "Received touch event from Mac: action=$action x=$normX y=$normY")
                        com.airlink.mobile.accessibility.AirLinkAccessibilityService.injectTouch(action, normX, normY)
                    }
                    "tap" -> {
                        if (!com.airlink.mobile.accessibility.AirLinkAccessibilityService.isTouchInjectionAllowed) {
                            Log.d(TAG, "Blocked tap: isTouchInjectionAllowed is false")
                            return
                        }
                        val normX = (if (json.has("x")) json.optDouble("x", 0.0) else json.optDouble("normalizedX", 0.0)).toFloat()
                        val normY = (if (json.has("y")) json.optDouble("y", 0.0) else json.optDouble("normalizedY", 0.0)).toFloat()
                        Log.d(TAG, "Received tap event from Mac: x=$normX y=$normY")
                        com.airlink.mobile.accessibility.AirLinkAccessibilityService.injectTap(normX, normY)
                    }
                    "scroll" -> {
                        if (!com.airlink.mobile.accessibility.AirLinkAccessibilityService.isTouchInjectionAllowed) {
                            Log.d(TAG, "Blocked scroll: isTouchInjectionAllowed is false")
                            return
                        }
                        val deltaX = (if (json.has("deltaX")) json.optDouble("deltaX") else json.optDouble("dx", 0.0)).toFloat()
                        val deltaY = (if (json.has("deltaY")) json.optDouble("deltaY") else json.optDouble("dy", 0.0)).toFloat()
                        val sensitivity = json.optDouble("sensitivity", 2.5).toFloat()
                        Log.d(TAG, "Received scroll event from Mac: deltaX=$deltaX, deltaY=$deltaY")
                        com.airlink.mobile.accessibility.AirLinkAccessibilityService.injectScroll(deltaX, deltaY, sensitivity)
                    }
                    "swipe" -> {
                        if (!com.airlink.mobile.accessibility.AirLinkAccessibilityService.isTouchInjectionAllowed) {
                            Log.d(TAG, "Blocked swipe: isTouchInjectionAllowed is false")
                            return
                        }
                        val startX = (if (json.has("startX")) json.optDouble("startX") else json.optDouble("x1", 0.5)).toFloat()
                        val startY = (if (json.has("startY")) json.optDouble("startY") else json.optDouble("y1", 0.5)).toFloat()
                        val endX = (if (json.has("endX")) json.optDouble("endX") else json.optDouble("x2", 0.5)).toFloat()
                        val endY = (if (json.has("endY")) json.optDouble("endY") else json.optDouble("y2", 0.5)).toFloat()
                        val duration = json.optLong("duration", json.optLong("durationMs", 200L))
                        Log.d(TAG, "Received swipe event from Mac: ($startX, $startY) -> ($endX, $endY), duration: ${duration}ms")
                        com.airlink.mobile.accessibility.AirLinkAccessibilityService.injectSwipe(startX, startY, endX, endY, duration)
                    }
                    "key_event", "key", "global_action" -> {
                        val key = when {
                            json.has("key") -> json.getString("key")
                            json.has("action") -> json.getString("action")
                            else -> ""
                        }
                        if (key.isNotEmpty()) {
                            Log.d(TAG, "Received key_event from Mac: $key")
                            AirLinkAccessibilityService.performGlobalAction(key)
                        }
                    }
                    "file" -> {
                        val fileName = when {
                            json.has("fileName") -> json.getString("fileName")
                            json.has("filename") -> json.getString("filename")
                            else -> "file"
                        }
                        val fileData = when {
                            json.has("fileData") -> json.getString("fileData")
                            json.has("data") -> json.getString("data")
                            else -> ""
                        }
                        if (fileData.isNotEmpty()) {
                            saveFileToDownloads(fileName, fileData)
                        }
                    }
                    "clipboard" -> {
                        val clipData = when {
                            json.has("data") -> json.getString("data")
                            json.has("text") -> json.getString("text")
                            json.has("content") -> json.getString("content")
                            else -> ""
                        }
                        if (clipData.isNotEmpty()) {
                            Log.d(TAG, "Received clipboard message from Mac (${clipData.length} chars)")
                        }
                    }
                }
            } catch (t: Throwable) {
                Log.d(TAG, "Message parsing handled non-fatal exception: ${t.message}")
            }

            val emitted = _incomingMessages.tryEmit(plainText)
            if (!emitted) {
                scope.launch { _incomingMessages.emit(plainText) }
            }
        }

        override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
            try {
                val decrypted = crypto.decryptBinary(bytes.toByteArray())
                Log.d(TAG, "Received binary frame (${bytes.size} bytes, decrypted ${decrypted.size} bytes)")
            } catch (t: Throwable) {
                Log.w(TAG, "Binary message error: ${t.message}")
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            try {
                webSocket.close(code, reason)
            } catch (t: Throwable) {
                Log.w(TAG, "Error closing onClosing: ${t.message}")
            }
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            Log.d(TAG, "WebSocket Closed: $code $reason")
            socketRef.compareAndSet(webSocket, null)
            crypto.clearKey()
            _connectionState.value = ConnectionState.Disconnected
            scope.launch(Dispatchers.Main) {
                onConnectionLost?.invoke()
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            Log.e(TAG, "WebSocket Failure (network drop/server closed): ${t.message}", t)
            socketRef.compareAndSet(webSocket, null)
            crypto.clearKey()
            _connectionState.value = ConnectionState.Disconnected
            scope.launch(Dispatchers.Main) {
                onConnectionLost?.invoke()
            }
        }
    }

    companion object {
        const val DEFAULT_PORT = 8080
        private const val TAG = "AirLinkSocket"

        @Volatile
        private var instance: AirLinkWebSocketManager? = null

        val isConnected: Boolean
            get() = instance?.let {
                it._connectionState.value is ConnectionState.Connected && it.socketRef.get() != null
            } ?: false

        fun sendBinaryFrame(bytes: ByteArray): Boolean {
            return instance?.sendBinary(bytes) ?: false
        }

        fun sendStopMirroring(): Boolean {
            return instance?.sendStopMirroring() ?: false
        }

        fun sendStopFindPhone(): Boolean {
            val payload = org.json.JSONObject().apply {
                put("type", "find_phone")
                put("action", "stop")
            }.toString()
            instance?.send(payload)
            val legacy = org.json.JSONObject().apply {
                put("type", "stop_ring")
            }.toString()
            return instance?.send(legacy) ?: false
        }
    }
}
